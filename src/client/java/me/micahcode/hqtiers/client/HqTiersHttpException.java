package me.micahcode.hqtiers.client;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public final class HqTiersHttpException extends IOException {
    private final int statusCode;
    private final long retryAfterMillis;

    HqTiersHttpException(int statusCode, String retryAfter, long now) {
        super("PvPHQ API returned HTTP " + statusCode);
        this.statusCode = statusCode;
        this.retryAfterMillis = parseRetryAfter(retryAfter, now, statusCode == 429 ? 30_000 : 0);
    }

    boolean isRetryable() {
        return statusCode == 408 || statusCode == 425 || statusCode == 429 || statusCode >= 500;
    }

    long retryAfterMillis() {
        return retryAfterMillis;
    }

    private static long parseRetryAfter(String value, long now, long fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            long seconds = Long.parseLong(value.trim());
            return seconds < 0 ? fallback : Math.multiplyExact(seconds, 1_000);
        } catch (NumberFormatException | ArithmeticException ignored) {
            try {
                return Math.max(0, ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME)
                        .toInstant().toEpochMilli() - now);
            } catch (DateTimeParseException ignoredDate) {
                return fallback;
            }
        }
    }
}
