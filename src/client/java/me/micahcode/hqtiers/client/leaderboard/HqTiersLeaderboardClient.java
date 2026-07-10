package me.micahcode.hqtiers.client.leaderboard;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import me.micahcode.hqtiers.Hqtiers;
import net.minecraft.client.MinecraftClient;

public final class HqTiersLeaderboardClient {
    // todo: a lot will change here
    private static final URI BASE_URI = URI.create("https://pvphq.com/api");
    private static final Duration TIMEOUT = Duration.ofSeconds(8);
    private static final String USER_AGENT = "HQTiers/1 (micahcode)";
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

    private void loadInitial(String ladder) {
        PageState state = state(ladder);
        if (state.loading()) {
            return;
        }

        state.loading = true;
        state.error = null;
        CompletableFuture.supplyAsync(() -> {
            List<Entry> combined = new ArrayList<>();
            int lastPage = 0;
            boolean hasMorePages = true;
            for (int page = 1; page <= 5; page++) {
                List<Entry> entries = fetchPage(ladder, page);
                if (entries.isEmpty()) {
                    hasMorePages = false;
                    break;
                }
                combined.addAll(entries);
                lastPage = page;
            }
            return new InitialLoad(combined, lastPage, hasMorePages);
        }).whenComplete((result, throwable) -> MinecraftClient.getInstance().execute(() -> {
            if (throwable != null) {
                state.error = "Failed to load leaderboard.";
                state.loading = false;
                Hqtiers.logger.warn("Failed to load initial HQTiers leaderboard for {}", ladder, throwable);
                return;
            }

            state.entries.clear();
            state.entries.addAll(result.entries());
            state.page = result.page();
            state.hasMore = result.hasMore();
            state.loading = false;
        }));
    }

    private void loadPage(String ladder, int page, boolean replace) {
        PageState state = state(ladder);
        state.loading = true;
        state.error = null;

        CompletableFuture.supplyAsync(() -> fetchPage(ladder, page)).whenComplete((entries, throwable) -> MinecraftClient.getInstance().execute(() -> {
            if (throwable != null) {
                state.error = "Failed to load leaderboard.";
                state.loading = false;
                Hqtiers.logger.warn("Failed to load HQTiers leaderboard for {} page {}", ladder, page, throwable);
                return;
            }

            if (replace) {
                state.entries.clear();
            }

            state.entries.addAll(entries);
            state.page = page;
            state.hasMore = !entries.isEmpty();
            state.loading = false;
        }));
    }

    private List<Entry> fetchPage(String ladder, int page) {
        try {
            URI uri = BASE_URI.resolve("leaderboard/" + ladder + "?page=" + page);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(TIMEOUT)
                    .header("Accept", "application/json")
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("HQPvP API returned HTTP " + response.statusCode());
            }

            JsonArray array = GSON.fromJson(response.body(), JsonArray.class);
            List<Entry> entries = new ArrayList<>();
            if (array == null) {
                return entries;
            }

            for (JsonElement element : array) {
                if (!element.isJsonObject()) {
                    continue;
                }

                JsonObject object = element.getAsJsonObject();
                entries.add(new Entry(
                        intValue(object, "position", entries.size() + 1),
                        string(object, "uuid", ""),
                        string(object, "name", "Unknown"),
                        ratingValue(object)
                ));
            }
            return entries;
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private static String string(JsonObject object, String key, String fallback) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsString();
    }

    private static int intValue(JsonObject object, String key, int fallback) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsInt();
    }

    private static int ratingValue(JsonObject object) {
        for (String key : new String[]{"sr", "skillRating", "rating", "elo"}) {
            JsonElement value = object.get(key);
            if (value != null && !value.isJsonNull()) {
                return value.getAsInt();
            }
        }
        return 0;
    }

    public record Entry(int position, String uuid, String name, int elo) {
    }

    private record InitialLoad(List<Entry> entries, int page, boolean hasMore) {
    }

    public static final class PageState {
        private final List<Entry> entries = new ArrayList<>();
        private int page;
        private boolean loading;
        private boolean hasMore = true;
        private String error;

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
    }

    public record HistoryPoint(int elo, long timestamp) {
    }

    public CompletableFuture<List<HistoryPoint>> fetchHistory(String playerUuid, String ladder) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                URI uri = BASE_URI.resolve("ranked-history?playerId=" + playerUuid + "&ladder=" + ladder);
                HttpRequest request = HttpRequest.newBuilder(uri)
                        .timeout(TIMEOUT)
                        .header("Accept", "application/json")
                        .header("User-Agent", USER_AGENT)
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) return List.of();

                JsonArray array = GSON.fromJson(response.body(), JsonArray.class);
                List<HistoryPoint> points = new ArrayList<>();
                if (array == null) return points;

                for (JsonElement el : array) {
                    if (!el.isJsonObject()) continue;
                    JsonObject obj = el.getAsJsonObject();
                    points.add(new HistoryPoint(
                            historyRatingValue(obj),
                            obj.has("date") ? obj.get("date").getAsLong() : 0L
                    ));
                }
                return points;
            } catch (Exception e) {
                return List.of();
            }
        });
    }

    private static int historyRatingValue(JsonObject object) {
        for (String key : new String[]{"srAfter", "skillRatingAfter", "ratingAfter", "eloAfter", "elo"}) {
            JsonElement value = object.get(key);
            if (value != null && !value.isJsonNull()) {
                return value.getAsInt();
            }
        }
        return 0;
    }
}
