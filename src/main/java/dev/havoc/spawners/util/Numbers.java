package dev.havoc.spawners.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Number and duration formatting shared by every dialog. */
public final class Numbers {

    private static final DecimalFormat PLAIN =
            new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.US));
    private static final DecimalFormat MONEY =
            new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
    private static final DecimalFormat COMPACT =
            new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US));
    private static final String[] SUFFIX = {"", "K", "M", "B", "T", "Q"};

    private Numbers() {
    }

    public static String plain(long value) {
        return PLAIN.format(value);
    }

    public static String money(double value) {
        return MONEY.format(value);
    }

    /** 1 234 567 -> "1.23M". Used where dialog width is tight. */
    public static String compact(long value) {
        if (value > -1000 && value < 1000) {
            return Long.toString(value);
        }
        double working = value;
        int index = 0;
        while ((working >= 1000 || working <= -1000) && index < SUFFIX.length - 1) {
            working /= 1000.0D;
            index++;
        }
        return COMPACT.format(BigDecimal.valueOf(working).setScale(2, RoundingMode.DOWN)) + SUFFIX[index];
    }

    public static String compactMoney(double value) {
        if (value < 1000.0D) {
            return MONEY.format(value);
        }
        return compact((long) value);
    }

    public static String percent(double fraction) {
        return COMPACT.format(fraction * 100.0D) + "%";
    }

    /** Milliseconds -> "2h 14m 3s". */
    public static String duration(long millis) {
        if (millis < 0) {
            millis = 0;
        }
        long seconds = millis / 1000L;
        long days = seconds / 86400L;
        long hours = (seconds % 86400L) / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long secs = seconds % 60L;
        StringBuilder builder = new StringBuilder();
        if (days > 0) {
            builder.append(days).append("d ");
        }
        if (hours > 0) {
            builder.append(hours).append("h ");
        }
        if (minutes > 0) {
            builder.append(minutes).append("m ");
        }
        if (builder.isEmpty() || secs > 0) {
            builder.append(secs).append('s');
        }
        return builder.toString().trim();
    }

    /** Parses "25s", "5m", "1h", "1d_2h_30m" into ticks. */
    public static long parseTicks(String input, long fallbackTicks) {
        if (input == null || input.isBlank()) {
            return fallbackTicks;
        }
        String trimmed = input.trim().toLowerCase(Locale.ROOT);
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException ignored) {
            // fall through to unit parsing
        }
        long totalSeconds = 0L;
        for (String part : trimmed.split("[_\\s]+")) {
            if (part.isEmpty()) {
                continue;
            }
            char unit = part.charAt(part.length() - 1);
            String digits = part.substring(0, part.length() - 1);
            long amount;
            try {
                amount = Long.parseLong(digits);
            } catch (NumberFormatException ex) {
                return fallbackTicks;
            }
            totalSeconds += switch (unit) {
                case 's' -> amount;
                case 'm' -> amount * 60L;
                case 'h' -> amount * 3600L;
                case 'd' -> amount * 86400L;
                case 'w' -> amount * 604800L;
                default -> 0L;
            };
        }
        return totalSeconds <= 0 ? fallbackTicks : totalSeconds * 20L;
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }
}
