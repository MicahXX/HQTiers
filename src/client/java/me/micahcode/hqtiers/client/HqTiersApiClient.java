package me.micahcode.hqtiers.client;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.micahcode.hqtiers.Hqtiers;
import me.micahcode.hqtiers.client.model.HqTiersStats;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HqTiersApiClient {
    private static final URI BASE_URI = URI.create("https://pvphq.com/api/ranked/");
    private static final Duration TIMEOUT = Duration.ofSeconds(8);
    private static final Gson GSON = new Gson();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public HqTiersStats fetchRanked(UUID uuid) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(BASE_URI.resolve(uuid.toString()))
                .timeout(TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", Hqtiers.USER_AGENT)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404 || response.body() == null || response.body().equals("null")) {
            return null;
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("PvPHQ API returned HTTP " + response.statusCode());
        }

        return parseRanked(uuid, response.body());
    }

    /** Parses ranked data independently of transport so API edge cases can be verified. */
    static HqTiersStats parseRanked(UUID uuid, String json) {
        JsonObject root = GSON.fromJson(json, JsonObject.class);
        if (root == null || root.isJsonNull()) {
            return null;
        }

        UUID playerUuid = uuid;
        String name = playerUuid.toString();

        Map<String, HqTiersStats.LadderStats> ladders = readLadders(root.getAsJsonObject("data"));
        ladders.put("GLOBAL", buildGlobal(root, ladders));

        return new HqTiersStats(playerUuid, name, ladders, System.currentTimeMillis());
    }

    private static Map<String, HqTiersStats.LadderStats> readLadders(JsonObject data) {
        Map<String, HqTiersStats.LadderStats> ladders = new HashMap<>();
        if (data == null) {
            return ladders;
        }

        for (Map.Entry<String, JsonElement> element : data.entrySet()) {
            if (!element.getValue().isJsonObject()) {
                continue;
            }

            JsonObject entry = element.getValue().getAsJsonObject();
            String key = canonicalLadder(element.getKey().toUpperCase(java.util.Locale.ROOT));

            int wins = intValue(entry, "wins", 0);
            int losses = intValue(entry, "losses", 0);
            int rating = intValue(entry, "rating", 1000);
            int tr = intValue(entry, "tr", rating);
            int placementGames = intValue(entry, "placementGames", 0);
            int placementTarget = intValue(entry, "placementTarget", 10);
            int leaderboardPosition = resolvePosition(intValue(entry, "leaderboardPosition", -1));
            int gamesPlayed = intValue(entry, "gamesPlayed", 0);
            String tier = string(entry, "grantedTier");
            double winRate = (wins + losses) > 0 ? (double) wins / (wins + losses) : 0.0;

            HqTiersStats.LadderStats existing = ladders.get(key);
            if (existing != null && existing.gamesPlayed() >= gamesPlayed) {
                continue;
            }

            ladders.put(key, new HqTiersStats.LadderStats(
                    key,
                    rating,
                    intValue(entry, "peakRating", 1000),
                    tr,
                    350,
                    wins,
                    losses,
                    gamesPlayed,
                    winRate,
                    tier,
                    string(entry, "tierColor"),
                    0,
                    tier == null,
                    false,
                    placementGames,
                    placementTarget,
                    0L,
                    0,
                    0,
                    null,
                    null,
                    false,
                    false,
                    0,
                    false,
                    0,
                    leaderboardPosition
            ));
        }

        return ladders;
    }

    /** Aggregates participation so an empty account never receives a global nametag. */
    private static HqTiersStats.LadderStats buildGlobal(JsonObject root, Map<String, HqTiersStats.LadderStats> ladders) {
        int wins = ladders.values().stream().mapToInt(HqTiersStats.LadderStats::wins).sum();
        int losses = ladders.values().stream().mapToInt(HqTiersStats.LadderStats::losses).sum();
        int games = ladders.values().stream().mapToInt(l -> Math.max(l.gamesPlayed(), l.wins() + l.losses())).sum();
        String globalRank = string(root, "rank");
        int globalPosition = resolvePosition(intValue(root, "globalPosition", -1));

        return new HqTiersStats.LadderStats(
                "GLOBAL", 0, 0, 0, 0, wins, losses, games,
                wins + losses > 0 ? (double) wins / (wins + losses) : 0.0,
                globalRank, null, 0, globalRank == null, false, 0, 0, 0L, 0, 0,
                null, null, false, false, 0, false, 0,
                globalPosition
        );
    }

    private static int resolvePosition(int rawPosition) {
        return rawPosition < 0 ? 0 : rawPosition + 1;
    }

    private static String canonicalLadder(String apiKey) {
        return HqTiersClientConfig.fromApiLadder(apiKey);
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    private static int intValue(JsonObject object, String key, int fallback) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsInt();
    }
}