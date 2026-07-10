package me.micahcode.hqtiers.client;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import me.micahcode.hqtiers.Hqtiers;
import me.micahcode.hqtiers.client.model.HqTiersStats;

public class HqTiersCache {
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final Duration FAILED_TTL = Duration.ofMinutes(1);

    private final HqTiersApiClient apiClient = new HqTiersApiClient();
    private final Map<UUID, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<HqTiersStats>> inFlight = new ConcurrentHashMap<>();

    public Optional<HqTiersStats> getIfFresh(UUID uuid) {
        CacheEntry entry = cache.get(uuid);
        if (entry == null || entry.isExpired()) {
            return Optional.empty();
        }
        return Optional.ofNullable(entry.stats());
    }

    public CompletableFuture<HqTiersStats> fetch(UUID uuid) {
        CacheEntry entry = cache.get(uuid);
        if (entry != null && !entry.isExpired()) {
            return CompletableFuture.completedFuture(entry.stats());
        }

        return inFlight.computeIfAbsent(uuid, key -> CompletableFuture.supplyAsync(() -> {
            try {
                HqTiersStats stats = apiClient.fetchRanked(key);
                cache.put(key, new CacheEntry(stats, System.currentTimeMillis(), stats == null ? FAILED_TTL : CACHE_TTL));
                return stats;
            } catch (Exception exception) {
                cache.put(key, new CacheEntry(null, System.currentTimeMillis(), FAILED_TTL));
                Hqtiers.logger.warn("Failed to fetch HQPvP ranked stats for {}", key, exception);
                return null;
            }
        }).whenComplete((stats, throwable) -> inFlight.remove(key)));
    }

    public void invalidate(UUID uuid) {
        cache.remove(uuid);
    }

    private record CacheEntry(HqTiersStats stats, long fetchedAt, Duration ttl) {
        boolean isExpired() {
            return System.currentTimeMillis() - fetchedAt > ttl.toMillis();
        }
    }
}
