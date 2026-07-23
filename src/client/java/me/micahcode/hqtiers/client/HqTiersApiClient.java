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

    // Sentinel value the API uses for globalPosition when a player is unranked globally.
    private static final int UNRANKED_GLOBAL_POSITION = 99999;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * Fetches a player's ranked stats across all ladders.
     * <p>
     * NOTE: the /ranked/{playerId} response contains no player name field at all
     * (only _id, data, globalPosition, rank) - the caller is responsible for
     * supplying a display name from elsewhere (online player list, leaderboard
     * entry, or the Mojang profile resolver).
     */
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
        Map<String, HqTiersStats.LadderStats> ladders = readLadders(root.getAsJsonObject("data"));
        addGlobalStats(root, ladders);

        // No name field on this endpoint - fall back to the UUID string; callers
        // that already know the player's name should overwrite this themselves.
        return new HqTiersStats(playerUuid, playerUuid.toString(), ladders, System.currentTimeMillis());
    }

    /**
     * Reads the "data" object: a map of ladder key (RankedLadder enum, e.g. SWORD,
     * AXE, MACE, UHC, NETHERITE_POT, POT, SMP, DIAMOND_SMP, VANILLA) to per-ladder
     * Glicko-2 stats. Ladder keys are translated to this mod's internal naming via
     * HqTiersClientConfig.fromApiLadder (e.g. API "POT" -> internal "DIAMOND_POT").
     */
    private static Map<String, HqTiersStats.LadderStats> readLadders(JsonObject data) {
        Map<String, HqTiersStats.LadderStats> ladders = new HashMap<>();
        if (data == null) {
            return ladders;
        }

        for (Map.Entry<String, JsonElement> entry : data.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }

            JsonObject ladder = entry.getValue().getAsJsonObject();
            String key = HqTiersClientConfig.fromApiLadder(entry.getKey());
            ladders.put(key, new HqTiersStats.LadderStats(
                    key,
                    intValue(ladder, "rating", 0),
                    intValue(ladder, "wins", 0),
                    intValue(ladder, "losses", 0),
                    0, // API does not expose a currentStreak field for ranked ladders
                    intValue(ladder, "placementGames", 0),
                    firstString(ladder, null, "grantedTier"),
                    intValue(ladder, "leaderboardPosition", 0)
            ));
        }

        return ladders;
    }

    /**
     * Synthesizes a "GLOBAL" pseudo-ladder from the top-level globalPosition and
     * rank fields. There is no global numeric SR/ELO on this API - only a tier id
     * string (rank) and a leaderboard position (99999 = unranked sentinel).
     * Global wins/losses/placements are summed across the known per-ladder stats
     * since the API does not expose separate global counters for these.
     */
    private static void addGlobalStats(JsonObject root, Map<String, HqTiersStats.LadderStats> ladders) {
        int wins = ladders.values().stream().mapToInt(HqTiersStats.LadderStats::wins).sum();
        int losses = ladders.values().stream().mapToInt(HqTiersStats.LadderStats::losses).sum();
        int placements = ladders.values().stream().mapToInt(HqTiersStats.LadderStats::placementMatchesPlayed).sum();

        int globalPosition = intValue(root, "globalPosition", 0);
        String globalRank = string(root, "rank", null);

        ladders.put("GLOBAL", new HqTiersStats.LadderStats(
                "GLOBAL",
                0, // no numeric global rating exists on this API
                wins,
                losses,
                0,
                placements,
                globalRank,
                globalPosition == UNRANKED_GLOBAL_POSITION ? 0 : globalPosition
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

    private static String firstString(JsonObject object, String fallback, String... keys) {
        for (String key : keys) {
            JsonElement value = object.get(key);
            if (value != null && !value.isJsonNull()) {
                return value.getAsString();
            }
        }
        return fallback;
    }
}