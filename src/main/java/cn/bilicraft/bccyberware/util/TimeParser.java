package cn.bilicraft.bccyberware.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses configuration durations into server ticks without depending on Bukkit. */
public final class TimeParser {
    private static final Pattern PATTERN = Pattern.compile("^([0-9]+(?:\\.[0-9]+)?)\\s*(t|s|m)$", Pattern.CASE_INSENSITIVE);

    private TimeParser() {
    }

    public static long parseTicks(String input) {
        if (input == null) {
            throw new IllegalArgumentException("时间不能为空");
        }
        Matcher matcher = PATTERN.matcher(input.trim().toLowerCase(Locale.ROOT));
        if (!matcher.matches()) {
            throw new IllegalArgumentException("需要时间格式，例如 20t、5s 或 1m");
        }
        double number = Double.parseDouble(matcher.group(1));
        double multiplier = switch (matcher.group(2)) {
            case "t" -> 1.0;
            case "s" -> 20.0;
            case "m" -> 1_200.0;
            default -> throw new IllegalStateException("未处理的时间单位");
        };
        long ticks = Math.round(number * multiplier);
        if (ticks < 0) {
            throw new IllegalArgumentException("时间不能为负数");
        }
        return ticks;
    }
}

