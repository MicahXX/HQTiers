package me.micahcode.hqtiers.client.leaderboard;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import me.micahcode.hqtiers.Hqtiers;
import me.micahcode.hqtiers.client.HqTiersClientConfig;
import me.micahcode.hqtiers.client.model.HqTiersRankSystem;
import net.minecraft.client.Minecraft;

/** Loads official leaderboard pages asynchronously and keeps each ladder independent. */
public final class HqTiersLeaderboardClient {
    private static final URI BASE_URI = URI.create("https://pvphq.com/api/");
    private static final Duration TIMEOUT = Duration.ofSeconds(8);
    private static final String USER_AGENT = "HQTiers/3.0 (micahcode)";
    private static final Gson GSON = new Gson();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final Map<String, PageState> states = new ConcurrentHashMap<>();

    public PageState state(String ladder) {
        return states.computeIfAbsent(ladder, ignored -> new PageState());
    }

    public void load(String ladder) {
        PageState state = state(ladder);
        if (!state.entries().isEmpty() || state.loading()) {
            return;
        }

        loadInitial(ladder);
    }

    public void refresh(String ladder) {
        loadInitial(ladder);
    }

    public void loadMore(String ladder) {
        PageState state = state(ladder);
        if (state.loading() || !state.hasMore()) {
            return;
        }

        loadPage(ladder, state.page() + 1, false);
    }

    /** Refreshes the first page in one request, keeping old rows visible until it succeeds. */
    private void loadInitial(String ladder) {
        if (!state(ladder).loading()) loadPage(ladder, 0, true);
    }

    /** Appends a page once and respects the server's pagination metadata. */
    private void loadPage(String ladder, int page, boolean replace) {
        PageState state = state(ladder);
        state.loading = true;
        state.error = null;
        CompletableFuture.supplyAsync(() -> fetchPage(ladder, page)).whenComplete((result, throwable) -> Minecraft.getInstance().execute(() -> {
            state.loading = false;
            if (throwable != null) {
                state.error = "Could not load leaderboard. Retry with Refresh.";
                Hqtiers.logger.warn("Failed to load HQTiers leaderboard for {} page {}", ladder, page, throwable);
                return;
            }
            if (replace) state.entries.clear();
            java.util.Set<String> seen = new java.util.HashSet<>();
            state.entries.forEach(entry -> seen.add(entry.uuid()));
            for (Entry entry : result.entries()) {
                if (seen.add(entry.uuid())) state.entries.add(entry);
            }
            state.page = page;
            state.hasMore = result.hasMore();
            state.total = result.total();
        }));
    }

    /** Uses the website's v1 route; page numbers are zero-based, ranks are one-based. */
    private LeaderboardPage fetchPage(String ladder, int page) {
        String gametype = switch (HqTiersClientConfig.normalizeLadder(ladder)) {
            case "GLOBAL" -> "overall";
            case "DIAMOND_POT" -> "pot";
            case "CART" -> "cart";
            default -> ladder.toLowerCase(Locale.ROOT);
        };
        try {
            URI uri = BASE_URI.resolve("v1/leaderboard/ranked/" + gametype + "?page=" + page + "&size=50");
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(TIMEOUT)
                    .header("Accept", "application/json").header("User-Agent", USER_AGENT).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("PvPHQ API returned HTTP " + response.statusCode());
            }
            return parsePage(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(exception);
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    /** Parses the public API contract without inferring rank from array position. */
    static LeaderboardPage parsePage(String json) {
        JsonElement parsed = GSON.fromJson(json, JsonElement.class);
        if (parsed == null || !parsed.isJsonObject()) throw new IllegalArgumentException("Missing leaderboard object");
        JsonObject root = parsed.getAsJsonObject();
        if (!root.has("entries") || !root.get("entries").isJsonArray()) {
            throw new IllegalArgumentException("Missing leaderboard entries");
        }
        List<Entry> entries = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("entries")) {
            if (!element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            JsonArray tiers = object.has("tiers") && object.get("tiers").isJsonArray()
                    ? object.getAsJsonArray("tiers") : new JsonArray();
            String uuid = string(object, "uuid", "");
            if (uuid.isBlank()) continue;
            entries.add(new Entry(intValue(object, "rank", 0), uuid, string(object, "name", "Unknown"),
                    intValue(object, "tr", 0), string(object, "tier", null), string(object, "tierColor", null),
                    intValue(object, "points", 0), tiers.size()));
        }
        JsonObject page = root.getAsJsonObject("page");
        boolean hasMore = page != null && page.has("hasNext") && page.get("hasNext").getAsBoolean();
        return new LeaderboardPage(List.copyOf(entries), hasMore && !entries.isEmpty(),
                page == null ? entries.size() : intValue(page, "total", entries.size()));
    }

    /** Carries one server page and its explicit continuation state. */
    record LeaderboardPage(List<Entry> entries, boolean hasMore, int total) {}

    private static String string(JsonObject object, String key, String fallback) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsString();
    }

    private static int intValue(JsonObject object, String key, int fallback) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsInt();
    }

    public record Entry(int position, String uuid, String name, int elo, String tierName, String tierColorHex, int points, int gamemodes) {
        public String tierLabel() {
            if (tierName != null && !tierName.isBlank() && !tierName.equalsIgnoreCase("Unranked")) {
                return tierName;
            }
            return "";
        }

        public int tierColorInt() {
            if (tierColorHex != null && !tierColorHex.isBlank()) {
                return HqTiersRankSystem.hexToColor(tierColorHex);
            }
            return HqTiersRankSystem.tierColor(HqTiersRankSystem.normalizeRank(tierName));
        }
    }

    public static final class PageState {
        private final List<Entry> entries = new ArrayList<>();
        private int page;
        private boolean loading;
        private boolean hasMore = true;
        private String error;
        private int total;

        public List<Entry> entries() {
            return entries;
        }

        public int page() {
            return page;
        }

        public boolean loading() {
            return loading;
        }

        public boolean hasMore() {
            return hasMore;
        }

        public String error() {
            return error;
        }

        /** Returns the server's total player count for this leaderboard. */
        public int total() {
            return total;
        }
    }

    public record HistoryPoint(int elo, long timestamp) {
    }

    public CompletableFuture<List<HistoryPoint>> fetchHistory(String playerUuid, String ladder) {
        Optional<String> apiLadder = HqTiersClientConfig.toApiLadder(ladder);

        if (apiLadder.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                URI uri = BASE_URI.resolve(
                        "ranked-history?playerId=" + playerUuid
                                + "&ladder=" + apiLadder.get().toUpperCase()
                );

                HttpRequest request = HttpRequest.newBuilder(uri)
                        .timeout(TIMEOUT)
                        .header("Accept", "application/json")
                        .header("User-Agent", USER_AGENT)
                        .GET()
                        .build();

                HttpResponse<String> response =
                        httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    Hqtiers.logger.warn(
                            "History API failed: {} {}",
                            response.statusCode(),
                            response.body()
                    );
                    return List.of();
                }

                JsonArray array = GSON.fromJson(response.body(), JsonArray.class);

                List<HistoryPoint> points = new ArrayList<>();

                if (array == null) {
                    return points;
                }

                for (JsonElement el : array) {
                    if (!el.isJsonObject())
                        continue;

                    JsonObject obj = el.getAsJsonObject();

                    points.add(new HistoryPoint(
                            obj.get("rating").getAsInt(),
                            obj.get("playedAt").getAsLong()
                    ));
                }

                return points;

            } catch (Exception e) {
                Hqtiers.logger.warn(
                        "Failed fetching rating history for {} {}",
                        playerUuid,
                        ladder,
                        e
                );

                return List.of();
            }
        });
    }
}