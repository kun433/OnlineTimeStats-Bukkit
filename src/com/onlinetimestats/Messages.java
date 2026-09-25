package com.onlinetimestats;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 消息与占位符处理。
 *
 * <p>内置一份中文默认文案：即使 config.yml 是被旧版本生成的、缺少某些键，
 * 插件也不会输出空白消息。
 */
public final class Messages {

    private static final Map<String, String> DEFAULTS;

    static {
        Map<String, String> map = new HashMap<String, String>();
        map.put("prefix", "&8[&a在线时间&8] &r");
        map.put("no-permission", "&c你没有权限执行这个操作。");
        map.put("usage-player", "&7用法: &f/playtime [玩家] &8| &f/playtime top [数量] &8| &f/playtime help");
        map.put("player-not-found", "&c找不到玩家 &f{player} &c的记录。");
        map.put("help-header", "&6===== 在线时间统计 =====");
        map.put("help-self", "&f/playtime &7- 查看自己的在线时间");
        map.put("help-other", "&f/playtime <玩家> &7- 查看指定玩家（支持离线）");
        map.put("help-top", "&f/playtime top [数量] &7- 在线时长排行榜（默认 10）");
        map.put("help-stats", "&f/playtime stats &7- 全服概览");
        map.put("help-status", "&f/playtime status &7- 查看运行状态（计时模式/数据文件/记分板）");
        map.put("help-set", "&f/playtime set <玩家> <时长> &7- 直接设置总时长");
        map.put("help-add", "&f/playtime add <玩家> <时长> &7- 增加时长");
        map.put("help-reset", "&f/playtime reset <玩家|all> &7- 清除记录");
        map.put("help-reload", "&f/playtime reload &7- 重载配置");
        map.put("help-duration", "&7时长写法: &f2h30m&7、&f90m&7、&f1d2h&7、&f3600&7（纯数字按秒）");

        map.put("info-header-self", "&6===== 你的在线时间 =====");
        map.put("info-header-other", "&6===== {player} 的在线时间 =====");
        map.put("info-total", "&e总时长: &f{time} &7(共 {days} 天 {hours} 小时 {minutes} 分钟 / {seconds} 秒)");
        map.put("info-session", "&e本次在线: &f{session}");
        map.put("info-afk", "&e挂机时长: &f{afk}");
        map.put("info-first-join", "&e首次加入: &f{first_join}");
        map.put("info-last-join", "&e最近加入: &f{last_join}");
        map.put("info-last-quit", "&e最近退出: &f{last_quit}");
        map.put("info-sessions", "&e登录次数: &f{sessions} 次");
        map.put("info-state-online", "&e当前状态: &a在线");
        map.put("info-state-offline", "&e当前状态: &7离线");
        map.put("info-rank", "&e全服排名: &f#{rank} &7/ {total_players}");

        map.put("top-header", "&6===== 在线时长排行 Top{limit} =====");
        map.put("top-line", "&e{rank}. &f{player} &7- &f{time} &7({hours} 小时)");
        map.put("top-footer", "&7共 {total_players} 名玩家有记录，全服累计 &f{total_time}&7。");
        map.put("top-empty", "&7还没有任何在线时长记录。");

        map.put("stats-header", "&6===== 在线时间统计概览 =====");
        map.put("stats-players", "&e有记录的玩家: &f{total_players} 人 &7(当前在线 {online})");
        map.put("stats-total", "&e全服累计在线: &f{total_time} &7({total_hours} 小时)");
        map.put("stats-average", "&e人均在线: &f{average}");
        map.put("stats-longest", "&e最长在线: &f{player} &7(&f{time}&7)");

        map.put("status-header", "&6===== 在线时间统计状态 =====");
        map.put("status-basic", "&e计时模式: &f{mode} &7| 挂机判定: {afk}");
        map.put("status-players", "&e记录玩家: &f{total_players} 人 &7| 在线会话: {online} &7| 全服累计: &f{total_time}");
        map.put("status-data", "&e数据文件: &f{data_file}");
        map.put("status-scoreboard", "&e记分板: &f{objective} &7(单位 {unit}, 显示名 {display_name}&7)");
        map.put("status-slots", "&e显示位置: &f{slots}");
        map.put("status-extra", "&e额外记分板目标: &f{extra}");

        map.put("set-done", "&a已把 &f{player} &a的总时长设为 &f{time}&a。");
        map.put("add-done", "&a已为 &f{player} &a增加 &f{delta}&a，现在共 &f{time}&a。");
        map.put("reset-done", "&a已清除 &f{player} &a的记录。");
        map.put("reset-all-confirm", "&e这会清除全部 &f{total_players} &e名玩家的数据。确认请输入 &f/playtime reset all confirm&e。");
        map.put("reset-all-done", "&a已清除全部 &f{total_players} &a名玩家的记录。");
        map.put("reload-done", "&a配置已重载。");
        map.put("usage-set", "&7用法: &f/playtime set <玩家> <时长>");
        map.put("usage-add", "&7用法: &f/playtime add <玩家> <时长>");
        map.put("usage-reset", "&7用法: &f/playtime reset <玩家|all>");
        map.put("invalid-duration", "&c无法识别时长 &f{input}&c。示例: 2h30m / 90m / 1d2h / 3600（秒）");
        map.put("save-failed", "&c数据写盘失败，请查看控制台日志。");

        map.put("afk-on", "&7你已进入挂机状态，这段时间不会计入在线时长。");
        map.put("afk-off", "&7欢迎回来，继续为你计时。");
        map.put("join-notify", "&7你的累计在线时长: &f{time}&7。");
        map.put("other-total", "&f{player} &7的累计在线时长: &f{time}&7。");
        DEFAULTS = Collections.unmodifiableMap(map);
    }

    private final JavaPlugin plugin;

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 读取一条原始文案（未翻译颜色代码）。 */
    public String raw(String key) {
        String fallback = DEFAULTS.get(key);
        FileConfiguration config = plugin.getConfig();
        String value = config.getString("messages." + key);
        if (value == null) {
            return fallback == null ? key : fallback;
        }
        if (fallback != null && value.trim().isEmpty()) {
            return fallback;
        }
        return value;
    }

    /** 生成带前缀的彩色文本。 */
    public String format(String key, String... placeholders) {
        String text = replace(raw(key), placeholders);
        String prefix = replace(raw("prefix"), placeholders);
        return color(prefix + text);
    }

    /** 生成不带前缀的彩色文本。 */
    public String formatPlain(String key, String... placeholders) {
        return color(replace(raw(key), placeholders));
    }

    public void send(CommandSender to, String key, String... placeholders) {
        String text = format(key, placeholders);
        if (!text.trim().isEmpty()) {
            to.sendMessage(text);
        }
    }

    public void sendPlain(CommandSender to, String key, String... placeholders) {
        String text = formatPlain(key, placeholders);
        if (!text.trim().isEmpty()) {
            to.sendMessage(text);
        }
    }

    public static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private static String replace(String text, String... placeholders) {
        if (text == null) {
            return "";
        }
        if (placeholders == null) {
            return text;
        }
        String result = text;
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            result = result.replace("{" + placeholders[i] + "}", placeholders[i + 1]);
        }
        return result;
    }
}
