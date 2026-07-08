package me.micahcode.hqtiers.client;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import me.micahcode.hqtiers.Hqtiers;

public final class MojangProfileResolver {
    // this should be fine
    private static final Duration TIMEOUT = Duration.ofSeconds(8);
    private static final Gson GSON = new Gson();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final Map<String, Result> cache = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Result>> inFlight = new ConcurrentHashMap<>();

    public CompletableFuture<Result> resolve(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        Result cached = cache.get(key);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        return inFlight.computeIfAbsent(key, ignored -> CompletableFuture.supplyAsync(() -> {
            Result result = fetch(name);
            if (result.status() != Status.ERROR) {
                cache.put(key, result);
            }
            return result;
        }).whenComplete((result, throwable) -> inFlight.remove(key)));
    }

    private Result fetch(String name) {
        try {
            String encodedName = URLEncoder.encode(name, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.mojang.com/users/profiles/minecraft/" + encodedName))
                    .timeout(TIMEOUT)
                    .header("Accept", "application/json")
                    .header("User-Agent", "HQTiers Minecraft Tier Tagger Mod")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 204 || response.statusCode() == 404) {
                return Result.notFound();
            }

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("Mojang API returned HTTP " + response.statusCode());
            }

            JsonObject root = GSON.fromJson(response.body(), JsonObject.class);
            if (root == null || !root.has("id")) {
                return Result.notFound();
            }

            String resolvedName = root.has("name") ? root.get("name").getAsString() : name;
            return Result.found(new Profile(parseUndashedUuid(root.get("id").getAsString()), resolvedName));
        } catch (Exception exception) {
            Hqtiers.logger.warn("Failed to resolve Mojang profile for {}", name, exception);
            return Result.error();
        }
    }

    private static UUID parseUndashedUuid(String id) {
        return UUID.fromString(id.replaceFirst(
                "([0-9a-fA-F]{8})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{12})",
                "$1-$2-$3-$4-$5"
        ));
    }

    public record Profile(UUID uuid, String name) {
    }

    public record Result(Status status, Profile profile) {
        public static Result found(Profile profile) {
            return new Result(Status.FOUND, profile);
        }

        public static Result notFound() {
            return new Result(Status.NOT_FOUND, null);
        }

        public static Result error() {
            return new Result(Status.ERROR, null);
        }
    }

    public enum Status {
        FOUND,
        NOT_FOUND,
        ERROR
    }
}
