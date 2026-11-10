package me.micahcode.hqtiers.client.fetching;

import me.micahcode.hqtiers.client.config.HqTiersClientConfig;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
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
    private static final Duration TIMEOUT = Duration.ofSeconds(4);
    private static final Gson GSON = new Gson();

    private final HttpClient httpClient;
    private final URI baseUri;

    public HqTiersApiClient() {
        this(HttpClient.newBuilder().connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL).build(), BASE_URI);
    }

    HqTiersApiClient(HttpClient httpClient, URI baseUri) {
        this.httpClient = httpClient;
        this.baseUri = baseUri;
    }

    public HqTiersStats fetchRanked(UUID uuid) throws IOException, InterruptedException {
        return fetchRanked(uuid, TIMEOUT);
    }

    HqTiersStats fetchRanked(UUID uuid, Duration timeout) throws IOException, InterruptedException {
        String body = fetchJson(baseUri.resolve(uuid.toString()), timeout);
        if (body == null) return null;
        try {
            return parseRanked(uuid, body);
        } catch (JsonParseException | IllegalStateException exception) {
            throw new IOException("PvPHQ API returned invalid ranked stats", exception);
        }
    }

    HqTiersStats fetchProgress(HqTiersStats stats) throws IOException, InterruptedException {
        String body = fetchJson(baseUri.resolve("../v1/players/" + stats.uuid()), TIMEOUT);
        if (body == null) return stats;
        return applyProgress(stats, GSON.fromJson(body, JsonObject.class));
    }

    static HqTiersStats applyProgress(HqTiersStats stats, JsonObject profile) {
        var ladders = new HashMap<>(stats.ladders());
        for (JsonElement element : profile.getAsJsonArray("ranked")) {
            JsonObject mode = element.getAsJsonObject();
            String key = string(mode, "gametype");
            if (key == null || !"percent".equals(string(mode, "trUnit"))
                    || !mode.has("tierProgress") || mode.get("tierProgress").isJsonNull()) continue;
            key = canonicalLadder(key);
            var ladder = ladders.get(key);
            if (ladder != null && ladder.tierName() != null && ladder.tierName().equalsIgnoreCase(string(mode, "tier"))) {
                ladders.put(key, ladder.withTierProgress(mode.get("tierProgress").getAsInt()));
            }
        }
        return new HqTiersStats(stats.uuid(), stats.name(), Map.copyOf(ladders), stats.lastUpdated());
    }

    private String fetchJson(URI uri, Duration timeout) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Accept", "application/json")
                .header("User-Agent", Hqtiers.USER_AGENT)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) {
            return null;
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new HqTiersHttpException(response.statusCode(),
                    response.headers().firstValue("Retry-After").orElse(null), System.currentTimeMillis());
        }

        if (response.body() == null || response.body().isBlank()) throw new IOException("PvPHQ API returned an empty response");

        return response.body();
    }

    static HqTiersStats parseRanked(UUID uuid, String json) {
        JsonElement parsed = GSON.fromJson(json, JsonElement.class);
        if (parsed == null || parsed.isJsonNull()) {
            return null;
        }
        if (!parsed.isJsonObject()) throw new JsonParseException("Expected a ranked stats object");
        JsonObject root = parsed.getAsJsonObject();

        UUID playerUuid = uuid;
        String name = playerUuid.toString();

        Map<String, HqTiersStats.LadderStats> ladders = readLadders(root.getAsJsonObject("data"));
        ladders.put("GLOBAL", buildGlobal(root, ladders));

        return new HqTiersStats(playerUuid, name, ladders, System.currentTimeMillis());
    }

    // pretty much every single available thing I can use is here
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
                    null,
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
