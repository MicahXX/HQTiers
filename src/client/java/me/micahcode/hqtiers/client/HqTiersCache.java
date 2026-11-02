package me.micahcode.hqtiers.client;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;

import me.micahcode.hqtiers.Hqtiers;
import me.micahcode.hqtiers.client.model.HqTiersStats;

public class HqTiersCache {
    private static final long CACHE_TTL = 300_000;
    private static final long EMPTY_TTL = 30_000;
    private static final long[] RETRY_DELAYS = {2_000, 5_000, 15_000, 30_000, 60_000};
    private static final int MAX_ATTEMPTS = 3;
    private final HqTiersApiClient apiClient;
    private final HqTiersRequestQueue requests;
    private final LongSupplier clock;
    private final Map<UUID, Entry> entries = new HashMap<>();
    private long revision;
    private long nextManualRefresh;

    public HqTiersCache() {
        this(new HqTiersApiClient(), new HqTiersRequestQueue(), System::currentTimeMillis);
    }

    HqTiersCache(HqTiersApiClient apiClient, HqTiersRequestQueue requests, LongSupplier clock) {
        this.apiClient = apiClient;
        this.requests = requests;
        this.clock = clock;
    }

    public synchronized Optional<HqTiersStats> get(UUID uuid) {
        Entry entry = entries.get(uuid);
        if (entry == null || entry.inFlight == null && clock.getAsLong() >= entry.refreshAt) fetch(uuid);
        return Optional.ofNullable(entries.get(uuid).stats);
    }

    public synchronized CompletableFuture<HqTiersStats> fetch(UUID uuid) {
        Entry entry = entries.computeIfAbsent(uuid, ignored -> new Entry());
        if (entry.inFlight != null) return entry.inFlight;
        if (clock.getAsLong() < entry.refreshAt) return CompletableFuture.completedFuture(entry.stats);
        CompletableFuture<HqTiersStats> result = new CompletableFuture<>();
        entry.inFlight = result;
        request(uuid, entry, result, 1, 0);
        return result;
    }

    private void request(UUID uuid, Entry entry, CompletableFuture<HqTiersStats> result, int attempt, long delay) {
        requests.submit(() -> apiClient.fetchRanked(uuid), !hasTier(entry.stats), delay)
                .whenComplete((stats, failure) -> complete(uuid, entry, result, attempt, stats, failure));
    }

    private void complete(UUID uuid, Entry entry, CompletableFuture<HqTiersStats> result,
                          int attempt, HqTiersStats stats, Throwable failure) {
        HqTiersStats available;
        synchronized (this) {
            if (failure == null) {
                boolean ranked = hasTier(stats);
                entry.emptyResults = ranked ? 0 : entry.emptyResults + 1;
                if (ranked || !hasTier(entry.stats) || entry.emptyResults >= 2) entry.stats = stats;
                entry.failures = 0;
                entry.refreshAt = clock.getAsLong() + (ranked ? CACHE_TTL : EMPTY_TTL);
                revision++;
            } else {
                entry.failures++;
                boolean retryable = failure instanceof java.io.IOException
                        && (!(failure instanceof HqTiersHttpException http) || http.isRetryable());
                long delay = RETRY_DELAYS[Math.min(entry.failures - 1, RETRY_DELAYS.length - 1)];
                delay += ThreadLocalRandom.current().nextLong(delay / 4 + 1);
                if (failure instanceof HqTiersHttpException http) delay = Math.max(delay, http.retryAfterMillis());
                if (retryable && attempt < MAX_ATTEMPTS) {
                    request(uuid, entry, result, attempt + 1, delay);
                    return;
                }
                entry.refreshAt = clock.getAsLong() + (retryable ? delay : 60_000);
                Hqtiers.logger.warn("Failed to fetch HQPvP ranked stats for {}", uuid, failure);
            }
            available = entry.stats;
            entry.inFlight = null;
        }
        result.complete(available);
    }

    public synchronized void invalidate(UUID uuid) {
        Entry entry = entries.get(uuid);
        if (entry != null && entry.failures == 0) entry.refreshAt = 0;
    }

    public synchronized long revision() {
        return revision;
    }

    public synchronized boolean refreshPlayers(Collection<UUID> players) {
        long now = clock.getAsLong();
        if (now < nextManualRefresh) return false;
        nextManualRefresh = now + 10_000;
        for (UUID uuid : players) {
            invalidate(uuid);
            fetch(uuid);
        }
        return true;
    }

    private static boolean hasTier(HqTiersStats stats) {
        return stats != null && stats.ladders().values().stream().anyMatch(ladder ->
                ladder.tierName() != null && !ladder.tierName().isBlank()
                        && !ladder.tierName().equalsIgnoreCase("Unranked")
                        && ladder.placementGames() >= ladder.placementTarget());
    }

    private static final class Entry {
        private HqTiersStats stats;
        private long refreshAt;
        private int failures;
        private int emptyResults;
        private CompletableFuture<HqTiersStats> inFlight;
    }
}
