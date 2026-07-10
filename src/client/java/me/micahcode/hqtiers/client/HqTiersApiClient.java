package me.micahcode.hqtiers.client;

import com.google.gson.Gson;
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
    private static final URI BASE_URI = URI.create("https://pvphq.com/api");
    private static final Duration TIMEOUT = Duration.ofSeconds(8);
    private static final String USER_AGENT = "HQTiers/1.0 (micahcode)";
    private static final Gson GSON = new Gson();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public HqTiersStats fetchRanked(UUID uuid) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(BASE_URI.resolve("ranked/" + uuid))
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
            throw new IOException("HQPvP API returned HTTP " + response.statusCode());
        }

        JsonObject root = GSON.fromJson(response.body(), JsonObject.class);
        if (root == null || root.isJsonNull()) {
            return null;
        }

        UUID playerUuid = UUID.fromString(string(root, "_id", uuid.toString()));
        String name = string(root, "lastKnownName", playerUuid.toString());
        long lastUpdated = longValue(root, "lastUpdated", 0L);
        Map<String, HqTiersStats.LadderStats> ladders = readLadders(root.getAsJsonObject("perLadder"));
        addGlobalStats(root, ladders);
        return new HqTiersStats(playerUuid, name, ladders, lastUpdated);
    }

    private static Map<String, HqTiersStats.LadderStats> readLadders(JsonObject perLadder) {
        Map<String, HqTiersStats.LadderStats> ladders = new HashMap<>();
        if (perLadder == null) {
            return ladders;
        }

        for (Map.Entry<String, JsonElement> entry : perLadder.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }

            JsonObject ladder = entry.getValue().getAsJsonObject();
            String key = HqTiersClientConfig.normalizeLadder(entry.getKey());
            ladders.put(key, new HqTiersStats.LadderStats(
                    key,
                    ratingValue(ladder),
                    intValue(ladder, "wins", 0),
                    intValue(ladder, "losses", 0),
                    intValue(ladder, "currentStreak", 0),
                    intValue(ladder, "placementMatchesPlayed", 0),
                    firstString(ladder, null, "currentRank", "tier", "tierTag", "rank"),
                    intValue(ladder, "position", 0)
            ));
        }

        return ladders;
    }

    private static void addGlobalStats(JsonObject root, Map<String, HqTiersStats.LadderStats> ladders) {
        if (!hasAny(root, "globalElo", "globalSr", "globalSkillRating", "globalRating")) {
            return;
        }

        int wins = ladders.values().stream().mapToInt(HqTiersStats.LadderStats::wins).sum();
        int losses = ladders.values().stream().mapToInt(HqTiersStats.LadderStats::losses).sum();
        int placements = ladders.values().stream().mapToInt(HqTiersStats.LadderStats::placementMatchesPlayed).sum();
        ladders.put("GLOBAL", new HqTiersStats.LadderStats(
                "GLOBAL",
                firstInt(root, 0, "globalSr", "globalSkillRating", "globalRating", "globalElo"),
                wins,
                losses,
                0,
                placements,
                null,
                intValue(root, "globalPosition", 0)
        ));
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
        return firstInt(object, 0, "sr", "skillRating", "rating", "totalRating", "elo");
    }

    private static int firstInt(JsonObject object, int fallback, String... keys) {
        for (String key : keys) {
            JsonElement value = object.get(key);
            if (value != null && !value.isJsonNull()) {
                return value.getAsInt();
            }
        }
        return fallback;
    }

    private static String firstString(JsonObject object, String fallback, String... keys) {
        for (String key : keys) {
            JsonElement value = object.get(key);
            if (value != null && !value.isJsonNull()) {
                return value.getAsString();
            }
        }
        return fallback;
    }

    private static boolean hasAny(JsonObject object, String... keys) {
        for (String key : keys) {
            if (object.has(key)) {
                return true;
            }
        }
        return false;
    }

    private static long longValue(JsonObject object, String key, long fallback) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsLong();
    }
}
