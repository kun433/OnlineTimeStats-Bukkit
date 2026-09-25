package com.onlinetimestats.core;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 时长格式化与解析工具（纯 Java，可离线测试）。 */
public final class TimeUtil {

    public static final long TICKS_PER_SECOND = 20L;

    private static final Pattern DURATION = Pattern.compile("(\\d+)\\s*(d|h|m|s|天|时|小时|分|分钟|秒)?",
            Pattern.CASE_INSENSITIVE);

    private TimeUtil() {
    }

    /**
     * 把 tick 数格式化成中文时长，例如 {@code 90061 秒 -> "1天1小时1分钟1秒"}。
     * 为 0 时返回 "0秒"。
     */
    public static String formatTicks(long ticks) {
        long totalSeconds = Math.max(0L, ticks) / TICKS_PER_SECOND;
        return formatSeconds(totalSeconds);
    }

    public static String formatSeconds(long totalSeconds) {
        long rest = Math.max(0L, totalSeconds);
        long days = rest / 86400L;
        long hours = (rest % 86400L) / 3600L;
        long minutes = (rest % 3600L) / 60L;
        long seconds = rest % 60L;

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("天");
        }
        if (hours > 0) {
            sb.append(hours).append("小时");
        }
        if (minutes > 0) {
            sb.append(minutes).append("分钟");
        }
        if (seconds > 0 || sb.length() == 0) {
            sb.append(seconds).append("秒");
        }
        return sb.toString();
    }

    /**
     * 解析一段时长文本，返回 tick 数。
     *
     * <p>支持 {@code 1d2h3m4s}、{@code 90m}、{@code 2小时30分}、{@code 3600}
     * （纯数字按 <b>秒</b> 处理）。
     *
     * @throws IllegalArgumentException 文本为空或不含任何有效数字
     */
    public static long parseDurationToTicks(String text) {
        if (text == null) {
            throw new IllegalArgumentException("时长不能为空");
        }
        String input = text.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        if (input.isEmpty()) {
            throw new IllegalArgumentException("时长不能为空");
        }

        long seconds = 0L;
        int matched = 0;
        Matcher matcher = DURATION.matcher(input);
        int consumed = 0;
        while (matcher.find()) {
            if (matcher.start() != consumed) {
                throw new IllegalArgumentException("无法识别的时长: " + text);
            }
            consumed = matcher.end();
            long value;
            try {
                value = Long.parseLong(matcher.group(1));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("数值过大: " + matcher.group(1));
            }
            String unit = matcher.group(2);
            matched++;
            if (unit == null) {
                seconds += value;
            } else if (unit.equals("d") || unit.equals("天")) {
                seconds += value * 86400L;
            } else if (unit.equals("h") || unit.equals("时") || unit.equals("小时")) {
                seconds += value * 3600L;
            } else if (unit.equals("m") || unit.equals("分") || unit.equals("分钟")) {
                seconds += value * 60L;
            } else {
                seconds += value;
            }
        }
        if (matched == 0 || consumed != input.length()) {
            throw new IllegalArgumentException("无法识别的时长: " + text);
        }
        return seconds * TICKS_PER_SECOND;
    }

    /** 按服务器默认时区格式化时间戳；0 或负数返回 "-"。 */
    public static String formatDate(long millis) {
        if (millis <= 0L) {
            return "-";
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
        return format.format(new Date(millis));
    }

    /** 把秒数拆成 [天, 小时, 分钟, 秒]。 */
    public static long[] splitSeconds(long totalSeconds) {
        long rest = Math.max(0L, totalSeconds);
        return new long[] {
                rest / 86400L,
                (rest % 86400L) / 3600L,
                (rest % 3600L) / 60L,
                rest % 60L,
        };
    }

    /** 生成缩略的“进度条”式文本，例如 {@code 2h30m}（用于排行榜）。 */
    public static String formatShort(long ticks) {
        long[] parts = splitSeconds(Math.max(0L, ticks) / TICKS_PER_SECOND);
        List<String> list = new ArrayList<String>();
        if (parts[0] > 0) {
            list.add(parts[0] + "d");
        }
        if (parts[1] > 0) {
            list.add(parts[1] + "h");
        }
        if (parts[2] > 0) {
            list.add(parts[2] + "m");
        }
        if (list.isEmpty()) {
            list.add(parts[3] + "s");
        }
        StringBuilder sb = new StringBuilder();
        for (String part : list) {
            sb.append(part);
        }
        return sb.toString();
    }
}
