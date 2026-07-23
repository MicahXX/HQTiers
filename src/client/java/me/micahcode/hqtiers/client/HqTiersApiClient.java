package me.micahcode.hqtiers.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
    private static final URI BASE_URI = URI.create("https://pvphq.com/api/v1/");
    private static final Duration TIMEOUT = Duration.ofSeconds(8);
    private static final String USER_AGENT = "HQTiers/1.0 (micahcode)";
    private static final Gson GSON = new Gson();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public HqTiersStats fetchRanked(UUID uuid) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(BASE_URI.resolve("players/" + uuid))
                .timeout(TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404 || response.body() == null || response.body().equals("null")) {
            return null;
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("PvPHQ API returned HTTP " + response.statusCode());
        }

        JsonObject root = GSON.fromJson(response.body(), JsonObject.class);
        if (root == null || root.isJsonNull()) {
            return null;
        }

        UUID playerUuid = UUID.fromString(string(root, "uuid", uuid.toString()));
        String name = string(root, "name", playerUuid.toString());

        Map<String, HqTiersStats.LadderStats> ladders = readLadders(root.getAsJsonArray("ranked"));
        ladders.put("GLOBAL", buildGlobal(root));

        return new HqTiersStats(playerUuid, name, ladders, System.currentTimeMillis());
    }

    private static Map<String, HqTiersStats.LadderStats> readLadders(JsonArray ranked) {
        Map<String, HqTiersStats.LadderStats> ladders = new HashMap<>();
        if (ranked == null) {
            return ladders;
        }

        for (JsonElement element : ranked) {
            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject entry = element.getAsJsonObject();
            String apiGametype = string(entry, "gametype", null);
            if (apiGametype == null) {
                continue;
            }

            String key = HqTiersClientConfig.fromApiLadder(apiGametype.toUpperCase());

            ladders.put(key, new HqTiersStats.LadderStats(
                    key,
                    intValue(entry, "rating", 1000),
                    intValue(entry, "peakRating", 1000),
                    intValue(entry, "rd", 350),
                    intValue(entry, "wins", 0),
                    intValue(entry, "losses", 0),
                    intValue(entry, "gamesPlayed", 0),
                    doubleValue(entry, "winRate", 0.0),
                    string(entry, "tier", null),
                    string(entry, "tierColor", null),
                    intValue(entry, "tierProgress", 0),
                    boolValue(entry, "unranked", true),
                    boolValue(entry, "inactive", false),
                    intValue(entry, "placementGames", 0),
                    intValue(entry, "placementTarget", 10),
                    longValue(entry, "lastPlayedAt", 0L),
                    intValue(entry, "tierFloor", 0),
                    intValue(entry, "tierCeiling", 0),
                    string(entry, "nextTier", null),
                    string(entry, "nextTierColor", null),
                    boolValue(entry, "nextIsTournamentGated", false),
                    boolValue(entry, "atRatingCap", false),
                    intValue(entry, "ratingAboveCap", 0),
                    boolValue(entry, "fromTournament", false),
                    intValue(entry, "promoProgress", 0),
                    0
            ));
        }

        return ladders;
    }

    private static HqTiersStats.LadderStats buildGlobal(JsonObject root) {
        JsonObject stats = root.getAsJsonObject("stats");
        int wins = stats != null ? intValue(stats, "wins", 0) : 0;
        int losses = stats != null ? intValue(stats, "losses", 0) : 0;
        int gamesPlayed = stats != null ? intValue(stats, "gamesPlayed", 0) : 0;
        double winRate = stats != null ? doubleValue(stats, "winRate", 0.0) : 0.0;

        return new HqTiersStats.LadderStats(
                "GLOBAL", 0, 0, 0, wins, losses, gamesPlayed, winRate,
                null, null, 0, true, false, 0, 10, 0L, 0, 0,
                null, null, false, false, 0, false, 0, 0
        );
    }

    private static String string(JsonObject object, String key, String fallback) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsString();
    }

    private static int intValue(JsonObject object, String key, int fallback) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsInt();
    }

    private static long longValue(JsonObject object, String key, long fallback) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsLong();
    }

    private static double doubleValue(JsonObject object, String key, double fallback) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsDouble();
    }

    private static boolean boolValue(JsonObject object, String key, boolean fallback) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsBoolean();
    }
}