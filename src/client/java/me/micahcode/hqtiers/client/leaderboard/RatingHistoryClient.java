package me.micahcode.hqtiers.client.leaderboard;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.micahcode.hqtiers.Hqtiers;
import me.micahcode.hqtiers.client.config.HqTiersClientConfig;
import me.micahcode.hqtiers.client.model.HqTiersRankSystem;
import me.micahcode.hqtiers.client.model.HqTiersRanks;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

final class RatingHistoryClient {
    private static final int RECENT = 96;
    private static final int EXTENDED = 500;
    private final HttpClient http;
    private final URI base;
    private final LinkedHashMap<URI, Cached> cache = new LinkedHashMap<>(16, 0.75f, true);

    RatingHistoryClient(HttpClient http, URI base) {
        this.http = http;
        this.base = base;
    }

    CompletableFuture<List<Point>> load(UUID uuid, String ladder, HistoryRange range) {
        var apiLadder = HqTiersClientConfig.toApiLadder(ladder);
        if (apiLadder.isEmpty()) return CompletableFuture.completedFuture(List.of());
        String mode = apiLadder.get();
        String slug = mode.equals("HT_CART") ? "cart" : mode.toLowerCase(Locale.ROOT);
        String path = "v1/players/" + uuid + "/rating-history/" + slug + "?limit=";
        URI archive = base.resolve("ranked-history?playerId=" + uuid + "&ladder=" + mode);
        long now = System.currentTimeMillis();
        long cutoff = range.cutoff(now);
        int first = range == HistoryRange.WEEK || range == HistoryRange.MONTH || hasFresh(base.resolve(path + RECENT))
                ? RECENT : EXTENDED;
        return fetch(base.resolve(path + first), true).thenCompose(points -> {
            if (covers(points, first, cutoff)) return CompletableFuture.completedFuture(points);
            var extended = first == EXTENDED ? CompletableFuture.completedFuture(points)
                    : fetch(base.resolve(path + EXTENDED), true);
            return extended.thenCompose(more -> {
                if (covers(more, EXTENDED, cutoff)) return CompletableFuture.completedFuture(more);
                return fetch(archive, false).thenApply(older -> {
                    long firstDetailed = HistoryRange.epochMillis(more.getFirst().timestamp());
                    var all = new ArrayList<Point>();
                    for (Point point : older) {
                        if (HistoryRange.epochMillis(point.timestamp()) < firstDetailed) all.add(point);
                    }
                    all.addAll(more);
                    return List.copyOf(all);
                });
            });
        }).exceptionallyCompose(error -> fetch(archive, false)).thenApply(points -> range.filter(points, now));
    }

    private static boolean covers(List<Point> points, int limit, long cutoff) {
        return points.size() < limit || HistoryRange.epochMillis(points.getFirst().timestamp()) <= cutoff;
    }

    private synchronized boolean hasFresh(URI uri) {
        Cached existing = cache.get(uri);
        return existing != null && existing.fresh(System.nanoTime());
    }

    private synchronized CompletableFuture<List<Point>> fetch(URI uri, boolean detailed) {
        Cached existing = cache.get(uri);
        long now = System.nanoTime();
        if (existing != null && existing.fresh(now)) {
            return existing.future();
        }
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8))
                .header("Accept", "application/json").header("User-Agent", Hqtiers.USER_AGENT).GET().build();
        var future = http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
            if (response.statusCode() != 200) {
                throw new CompletionException(new IOException("History API returned HTTP " + response.statusCode()));
            }
            JsonElement root = JsonParser.parseString(response.body());
            return parse(detailed ? root.getAsJsonObject().getAsJsonArray("points") : root.getAsJsonArray());
        });
        if (cache.size() >= 96 && existing == null) cache.pollFirstEntry();
        cache.put(uri, new Cached(now, future));
        return future;
    }

    private static List<Point> parse(JsonArray array) {
        var points = new ArrayList<Point>(array.size());
        for (JsonElement item : array) {
            JsonObject obj = item.getAsJsonObject();
            String tier = text(obj, "tier");
            String color = text(obj, "tierColor");
            var rank = HqTiersRankSystem.normalizeRank(tier);
            int baseColor = color != null ? HqTiersRankSystem.hexToColor(color)
                    : rank != null ? HqTiersRankSystem.tierColor(rank) : 0x8899AB;
            points.add(new Point(obj.get("rating").getAsInt(), HistoryRange.epochMillis(obj.get("playedAt").getAsLong()),
                    tier, rank, baseColor,
                    number(obj, "tr"), number(obj, "trDelta"), number(obj, "delta")));
        }
        points.sort(Comparator.comparingLong(p -> HistoryRange.epochMillis(p.timestamp())));
        return List.copyOf(points);
    }

    private static Integer number(JsonObject obj, String key) {
        var value = obj.get(key);
        return value == null || value.isJsonNull() ? null : value.getAsInt();
    }

    private static String text(JsonObject obj, String key) {
        var value = obj.get(key);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    record Point(int elo, long timestamp, String tier, HqTiersRanks rank, int baseColor, Integer tr, Integer trDelta, Integer delta) {
        int color() {
            return HqTiersRankSystem.displayColor(rank, baseColor);
        }

        String progressText() {
            return HqTiersRankSystem.progressText(tr, HqTiersRankSystem.usesPercent(tier), true);
        }
    }

    private record Cached(long created, CompletableFuture<List<Point>> future) {
        boolean fresh(long now) {
            return !future.isCompletedExceptionally() && (!future.isDone() || now - created < TimeUnit.MINUTES.toNanos(5));
        }
    }
}
