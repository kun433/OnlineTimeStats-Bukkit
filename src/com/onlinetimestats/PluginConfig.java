package com.onlinetimestats;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** config.yml 的解析结果。任何一项缺失都会退回默认值，避免旧配置文件导致启动失败。 */
public final class PluginConfig {

    /** TICK = 每个服务器 tick 记 1；WALL = 每真实 1 秒记 20 tick。 */
    public enum CountingMode {
        TICK,
        WALL
    }

    /** 记分板分数使用的时间单位。 */
    public enum Unit {
        HOURS,
        MINUTES,
        SECONDS
    }

    private final JavaPlugin plugin;

    private CountingMode countingMode = CountingMode.TICK;
    private boolean countOnShutdown = true;

    private File dataFile;
    private int saveIntervalSeconds = 120;
    private boolean saveOnQuit = true;

    private boolean afkEnabled = false;
    private int afkThresholdSeconds = 300;
    private boolean afkExcludeFromTotal = true;
    private boolean afkNotify = true;

    private boolean scoreboardEnabled = true;
    private String scoreboardObjective = "playtime";
    private String scoreboardDisplayName = "&a[小时]";
    private Unit scoreboardUnit = Unit.HOURS;
    private List<String> scoreboardSlots = Arrays.asList("below_name", "list");
    private int scoreboardUpdateSeconds = 5;
    private boolean extraObjectives = false;

    private boolean joinQuitMessage = false;
    private boolean showSession = true;

    private final List<String> warnings = new ArrayList<String>();

    public PluginConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        warnings.clear();
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();

        countingMode = parseEnum(CountingMode.class, c.getString("counting.mode"), CountingMode.TICK,
                "counting.mode");
        countOnShutdown = c.getBoolean("counting.count-on-shutdown", true);

        String path = c.getString("storage.file", "data/playtime.tsv");
        if (path == null || path.trim().isEmpty()) {
            path = "data/playtime.tsv";
        }
        File configured = new File(path.trim());
        dataFile = configured.isAbsolute() ? configured : new File(plugin.getDataFolder(), path.trim());
        saveIntervalSeconds = clamp(c.getInt("storage.save-interval-seconds", 120), 5, 36000);
        saveOnQuit = c.getBoolean("storage.save-on-quit", true);

        afkEnabled = c.getBoolean("afk.enabled", false);
        afkThresholdSeconds = clamp(c.getInt("afk.threshold-seconds", 300), 10, 86400);
        afkExcludeFromTotal = c.getBoolean("afk.exclude-from-total", true);
        afkNotify = c.getBoolean("afk.notify", true);

        scoreboardEnabled = c.getBoolean("scoreboard.enabled", true);
        String objective = c.getString("scoreboard.objective", "playtime");
        scoreboardObjective = (objective == null || objective.trim().isEmpty()) ? "playtime" : objective.trim();
        String display = c.getString("scoreboard.display-name", "&a[小时]");
        scoreboardDisplayName = display == null ? "&a[小时]" : display;
        scoreboardUnit = parseEnum(Unit.class, c.getString("scoreboard.unit"), Unit.HOURS, "scoreboard.unit");
        List<String> slots = c.getStringList("scoreboard.display-slots");
        if (slots == null || slots.isEmpty()) {
            slots = Arrays.asList("below_name", "list");
        }
        List<String> normalized = new ArrayList<String>();
        for (String slot : slots) {
            if (slot == null) {
                continue;
            }
            String value = slot.trim().toLowerCase(Locale.ROOT);
            if (!"below_name".equals(value) && !"list".equals(value) && !"sidebar".equals(value)
                    && !"none".equals(value)) {
                warnings.add("scoreboard.display-slots 里有无法识别的位置: " + slot + "（可用 below_name / list / sidebar）");
                continue;
            }
            normalized.add(value);
        }
        if (normalized.isEmpty()) {
            normalized.add("below_name");
            normalized.add("list");
        }
        scoreboardSlots = normalized;
        scoreboardUpdateSeconds = clamp(c.getInt("scoreboard.update-seconds", 5), 1, 600);
        extraObjectives = c.getBoolean("scoreboard.extra-objectives", false);

        joinQuitMessage = c.getBoolean("options.join-quit-message", false);
        showSession = c.getBoolean("options.show-session", true);
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String raw, E fallback, String path) {
        if (raw == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            warnings.add(path + " 的值 " + raw + " 无法识别，已使用默认值 " + fallback.name());
            return fallback;
        }
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }

    public List<String> warnings() {
        return warnings;
    }

    public CountingMode countingMode() {
        return countingMode;
    }

    public boolean countOnShutdown() {
        return countOnShutdown;
    }

    public File dataFile() {
        return dataFile;
    }

    public int saveIntervalSeconds() {
        return saveIntervalSeconds;
    }

    public boolean saveOnQuit() {
        return saveOnQuit;
    }

    public boolean afkEnabled() {
        return afkEnabled;
    }

    public int afkThresholdSeconds() {
        return afkThresholdSeconds;
    }

    public boolean afkExcludeFromTotal() {
        return afkExcludeFromTotal;
    }

    public boolean afkNotify() {
        return afkNotify;
    }

    public boolean scoreboardEnabled() {
        return scoreboardEnabled;
    }

    public String scoreboardObjective() {
        return scoreboardObjective;
    }

    public String scoreboardDisplayName() {
        return scoreboardDisplayName;
    }

    public Unit scoreboardUnit() {
        return scoreboardUnit;
    }

    public List<String> scoreboardSlots() {
        return scoreboardSlots;
    }

    public int scoreboardUpdateSeconds() {
        return scoreboardUpdateSeconds;
    }

    public boolean extraObjectives() {
        return extraObjectives;
    }

    public boolean joinQuitMessage() {
        return joinQuitMessage;
    }

    public boolean showSession() {
        return showSession;
    }
}
