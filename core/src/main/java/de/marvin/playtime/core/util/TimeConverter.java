package de.marvin.playtime.core.util;

import de.marvin.api.core.utils.ChatColor;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Utility class for converting time in milliseconds to formatted strings and vice versa.
 */
public class TimeConverter {

    /**
     * Pattern to match time strings (e.g., {@code 1d2h3m4s}, {@code 1H 30m}, {@code 250ms}).
     */
    private static final Pattern TIME_PATTERN = Pattern.compile("(?i)(\\d+)\\s*(ms|d|h|m|s)");

    /**
     * {@link DateTimeFormatter} in the format {@code dd.MM.yyyy, HH:mm:ss}.
     */
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm:ss");

    /**
     * Do not instantiate this class. It is a utility class and should only be used statically.
     */
    private TimeConverter() {
        throw new AssertionError("Utility classes cannot be instantiated.");
    }

    /**
     * Converts milliseconds to a string in the format {@code dd.MM.yyyy, HH:mm:ss}.
     *
     * @param millis Time in milliseconds
     * @return Formatted date string
     */
    public static @NotNull String convertMillisToDateTime(
            long millis
    ) {
        var instant = Instant.ofEpochMilli(millis);
        var zoneId = ZoneId.systemDefault();
        return instant.atZone(zoneId).format(DATE_TIME_FORMATTER);
    }

    /**
     * Converts milliseconds to a formatted string showing days, hours and minutes.
     *
     * @param millis            Time in milliseconds
     * @param showFullTimeNames Whether to show full time unit names (e.g., "days") or abbreviations (e.g., "d")
     * @param colorize          Whether to colorize the output
     * @return Formatted time string
     */
    public static @NotNull String convertMillisToDaysHoursMinutes(
            long millis,
            boolean showFullTimeNames,
            boolean colorize
    ) {
        long totalSeconds = millis / 1000;
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;

        var result = new StringBuilder();
        if (days > 0) result
                .append(colorize ? ChatColor.YELLOW : "").append(days)
                .append(colorize ? ChatColor.GRAY : "").append(showFullTimeNames
                        ? (days == 1 ? " day " : " days ")
                        : "d ");

        if (hours > 0 || days > 0) result
                .append(colorize ? ChatColor.YELLOW : "").append(hours)
                .append(colorize ? ChatColor.GRAY : "").append(showFullTimeNames
                        ? (hours == 1 ? " hour " : " hours ")
                        : "h ");

        result
                .append(colorize ? ChatColor.YELLOW : "").append(minutes)
                .append(colorize ? ChatColor.GRAY : "").append(showFullTimeNames
                        ? (minutes == 1 ? " minute" : " minutes")
                        : "m");

        return result.toString().trim();
    }

    /**
     * Converts milliseconds to a formatted string showing hours and minutes.
     *
     * @param millis            Time in milliseconds
     * @param showFullTimeNames Whether to show full time unit names (e.g., "hours") or abbreviations (e.g., "h")
     * @param colorize          Whether to colorize the output
     * @return Formatted time string
     */
    public static @NotNull String convertMillisToHoursMinutes(
            long millis,
            boolean showFullTimeNames,
            boolean colorize
    ) {
        long totalSeconds = millis / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;

        var result = new StringBuilder();
        if (hours > 0) result
                .append(colorize ? ChatColor.YELLOW : "").append(hours)
                .append(colorize ? ChatColor.GRAY : "").append(showFullTimeNames
                        ? (hours == 1 ? " hour " : " hours ")
                        : "h ");

        result
                .append(colorize ? ChatColor.YELLOW : "").append(minutes)
                .append(colorize ? ChatColor.GRAY : "").append(showFullTimeNames
                        ? (minutes == 1 ? " minute" : " minutes")
                        : "min");

        return result.toString().trim();
    }

    /**
     * Converts a formatted time string to milliseconds. The time string can contain days (d), hours (h),
     * minutes (m), seconds (s), and milliseconds (ms) in any order, separated by optional whitespace.
     * Examples of valid strings: "1d 2h 30m", "45m15s", "2h", "500ms", "1d2h3m4s500ms".
     *
     * @param timeString Formatted time string
     * @return Time in milliseconds
     * @throws IllegalArgumentException If the input string is invalid or results in an overflow
     */
    public static @NotNull Long convertTimeStringToLong(
            @NotNull String timeString
    ) {
        Objects.requireNonNull(timeString, "The time parameter must not be empty");

        var input = timeString.strip();
        if (input.isEmpty()) throw new IllegalArgumentException("The time parameter must not be empty");


        var matcher = TIME_PATTERN.matcher(input);
        var total = Duration.ZERO;

        int pos = 0;
        while (matcher.find()) {
            // Ensure there are only whitespaces between tokens
            for (int i = pos; i < matcher.start(); i++) {
                if (!Character.isWhitespace(input.charAt(i)))
                    throw new IllegalArgumentException(
                            "Unexpected character at position " + i + ": '" + input.charAt(i) + "'");
            }

            var value = Long.parseLong(matcher.group(1));
            var unit = matcher.group(2).toLowerCase(Locale.ROOT);

            try {
                total = switch (unit) {
                    case "d" -> total.plus(Duration.ofDays(value));
                    case "h" -> total.plus(Duration.ofHours(value));
                    case "m" -> total.plus(Duration.ofMinutes(value));
                    case "s" -> total.plus(Duration.ofSeconds(value));
                    case "ms" -> total.plus(Duration.ofMillis(value));
                    default -> throw new IllegalArgumentException("Unknown unit: " + unit);
                };
            } catch (ArithmeticException ex) {
                // Catches overflows within Duration (e.g., extremely large values)
                throw new IllegalArgumentException("Duration is too big", ex);
            }

            pos = matcher.end();
        }

        // After the last match, only whitespace is allowed
        for (int i = pos; i < input.length(); i++) {
            if (!Character.isWhitespace(input.charAt(i)))
                throw new IllegalArgumentException(
                        "Invalid suffix at position " + i + ": '" + input.charAt(i) + "'");
        }

        try {
            return total.toMillis();
        } catch (ArithmeticException ex) {
            // Throws ArithmeticException on overflow → catch and re-label
            throw new IllegalArgumentException("Milliseconds conversion overflow.", ex);
        }
    }

}
