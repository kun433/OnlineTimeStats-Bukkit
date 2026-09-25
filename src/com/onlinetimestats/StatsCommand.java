package com.onlinetimestats;

import com.onlinetimestats.core.TimeEntry;
import com.onlinetimestats.core.TimeStore;
import com.onlinetimestats.core.SessionState;
import com.onlinetimestats.core.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** /playtime 及其子命令。 */
public final class StatsCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "help", "top", "check", "stats", "status", "set", "add", "reset", "reload");
    private static final List<String> DURATION_SUGGESTIONS = Arrays.asList(
            "30m", "1h", "2h30m", "1d", "7d", "3600");

    private final OnlineTimeStats plugin;
    private final TimeStore store;
    private final Messages messages;
    private final PluginConfig config;

    public StatsCommand(OnlineTimeStats plugin) {
        this.plugin = plugin;
        this.store = plugin.store();
        this.messages = plugin.messages();
        this.config = plugin.pluginConfig();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                showInfo(sender, store.get(player.getUniqueId()), player.getName());
            } else {
                messages.send(sender, "usage-player");
            }
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("help") || sub.equals("?")) {
            help(sender);
        } else if (sub.equals("top") || sub.equals("rank")) {
            top(sender, args);
        } else if (sub.equals("stats")) {
            stats(sender);
        } else if (sub.equals("status")) {
            status(sender);
        } else if (sub.equals("check")) {
            if (args.length < 2) {
                messages.send(sender, "usage-player");
            } else {
                infoByName(sender, args[1]);
            }
        } else if (sub.equals("set")) {
            modify(sender, args, true);
        } else if (sub.equals("add")) {
            modify(sender, args, false);
        } else if (sub.equals("reset")) {
            reset(sender, args);
        } else if (sub.equals("reload")) {
            reload(sender);
        } else {
            infoByName(sender, args[0]);
        }
        return true;
    }

    private void help(CommandSender sender) {
        messages.sendPlain(sender, "help-header");
        messages.sendPlain(sender, "help-self");
        if (sender.hasPermission("onlinetimestats.others")) {
            messages.sendPlain(sender, "help-other");
        }
        if (sender.hasPermission("onlinetimestats.top")) {
            messages.sendPlain(sender, "help-top");
            messages.sendPlain(sender, "help-stats");
        }
        if (sender.hasPermission("onlinetimestats.admin")) {
            messages.sendPlain(sender, "help-set");
            messages.sendPlain(sender, "help-add");
            messages.sendPlain(sender, "help-reset");
            messages.sendPlain(sender, "help-status");
            messages.sendPlain(sender, "help-reload");
        }
        messages.sendPlain(sender, "help-duration");
    }

    /** 自检：把计时模式、数据文件、记分板实际状态直接读回来。 */
    private void status(CommandSender sender) {
        if (!sender.hasPermission("onlinetimestats.admin")) {
            messages.send(sender, "no-permission");
            return;
        }
        PluginConfig cfg = plugin.pluginConfig();
        String afk = cfg.afkEnabled()
                ? "开(" + cfg.afkThresholdSeconds() + "s" + (cfg.afkExcludeFromTotal() ? ",不计入" : ",仍计入") + ")"
                : "关";
        String[] placeholders = new String[] {
                "mode", cfg.countingMode().name(),
                "afk", afk,
                "total_players", String.valueOf(store.size()),
                "online", String.valueOf(plugin.tracker().sessionCount()),
                "total_time", TimeUtil.formatTicks(store.totalTicks()),
                "data_file", cfg.dataFile().getAbsolutePath(),
                "objective", cfg.scoreboardObjective(),
                "display_name", Messages.color(cfg.scoreboardDisplayName()),
                "unit", cfg.scoreboardUnit().name(),
                "slots", plugin.scoreboard().slotState(),
                "extra", cfg.extraObjectives() ? "开" : "关",
        };
        messages.sendPlain(sender, "status-header", placeholders);
        messages.sendPlain(sender, "status-basic", placeholders);
        messages.sendPlain(sender, "status-players", placeholders);
        messages.sendPlain(sender, "status-data", placeholders);
        messages.sendPlain(sender, "status-scoreboard", placeholders);
        messages.sendPlain(sender, "status-slots", placeholders);
        messages.sendPlain(sender, "status-extra", placeholders);
    }

    private void infoByName(CommandSender sender, String name) {
        TimeEntry entry = findEntry(name);
        if (entry == null) {
            messages.send(sender, "player-not-found", "player", name);
            return;
        }
        boolean self = sender instanceof Player
                && ((Player) sender).getUniqueId().equals(entry.uuid());
        if (!self && !sender.hasPermission("onlinetimestats.others")) {
            messages.send(sender, "no-permission");
            return;
        }
        showInfo(sender, entry, name);
    }

    private void showInfo(CommandSender sender, TimeEntry entry, String requestedName) {
        if (entry == null) {
            messages.send(sender, "player-not-found", "player", requestedName);
            return;
        }
        long[] split = TimeUtil.splitSeconds(entry.totalSeconds());
        int rank = store.rankOf(entry.uuid());
        boolean online = plugin.getServer().getPlayer(entry.uuid()) != null;
        SessionState session = plugin.tracker().session(entry.uuid());

        String[] placeholders = new String[] {
                "player", entry.name(),
                "time", TimeUtil.formatTicks(entry.totalTicks()),
                "days", String.valueOf(split[0]),
                "hours", String.valueOf(split[1]),
                "minutes", String.valueOf(split[2]),
                "seconds", String.valueOf(split[3]),
                "afk", TimeUtil.formatTicks(entry.afkTicks()),
                "first_join", TimeUtil.formatDate(entry.firstJoin()),
                "last_join", TimeUtil.formatDate(entry.lastJoin()),
                "last_quit", TimeUtil.formatDate(entry.lastQuit()),
                "sessions", String.valueOf(entry.sessions()),
                "rank", rank <= 0 ? "-" : String.valueOf(rank),
                "total_players", String.valueOf(store.size()),
                "online", String.valueOf(plugin.getServer().getOnlinePlayers().size()),
        };

        messages.sendPlain(sender, sender instanceof Player
                && ((Player) sender).getUniqueId().equals(entry.uuid())
                ? "info-header-self" : "info-header-other", placeholders);
        messages.sendPlain(sender, "info-total", placeholders);
        if (config.showSession()) {
            messages.sendPlain(sender, "info-session", concat(placeholders,
                    "session", session == null ? "-" : TimeUtil.formatTicks(session.sessionTicks())));
        }
        if (config.afkEnabled()) {
            messages.sendPlain(sender, "info-afk", placeholders);
        }
        messages.sendPlain(sender, "info-first-join", placeholders);
        messages.sendPlain(sender, "info-last-join", placeholders);
        messages.sendPlain(sender, "info-last-quit", placeholders);
        messages.sendPlain(sender, "info-sessions", placeholders);
        messages.sendPlain(sender, online ? "info-state-online" : "info-state-offline", placeholders);
        messages.sendPlain(sender, "info-rank", placeholders);
    }

    private static String[] concat(String[] placeholders, String... extra) {
        String[] result = new String[placeholders.length + extra.length];
        System.arraycopy(placeholders, 0, result, 0, placeholders.length);
        System.arraycopy(extra, 0, result, placeholders.length, extra.length);
        return result;
    }

    private void top(CommandSender sender, String[] args) {
        if (!sender.hasPermission("onlinetimestats.top")) {
            messages.send(sender, "no-permission");
            return;
        }
        int limit = 10;
        if (args.length >= 2) {
            try {
                limit = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                messages.send(sender, "usage-player");
                return;
            }
        }
        limit = Math.max(1, Math.min(50, limit));

        List<TimeEntry> ranking = store.ranking(limit);
        if (store.size() == 0) {
            messages.send(sender, "top-empty");
            return;
        }
        String[] common = new String[] {
                "limit", String.valueOf(limit),
                "total_players", String.valueOf(store.size()),
                "total_time", TimeUtil.formatTicks(store.totalTicks()),
                "total_hours", String.valueOf(store.totalTicks() / 72000L),
        };
        messages.sendPlain(sender, "top-header", common);
        int rank = 1;
        for (TimeEntry entry : ranking) {
            messages.sendPlain(sender, "top-line",
                    "rank", String.valueOf(rank++),
                    "player", entry.name(),
                    "time", TimeUtil.formatTicks(entry.totalTicks()),
                    "hours", String.valueOf(entry.totalHours()));
        }
        messages.sendPlain(sender, "top-footer", common);
    }

    private void stats(CommandSender sender) {
        if (!sender.hasPermission("onlinetimestats.top")) {
            messages.send(sender, "no-permission");
            return;
        }
        List<TimeEntry> ranking = store.ranking(1);
        long totalTicks = store.totalTicks();
        int players = store.size();
        long average = players == 0 ? 0L : totalTicks / players;
        String[] placeholders = new String[] {
                "total_players", String.valueOf(players),
                "online", String.valueOf(plugin.getServer().getOnlinePlayers().size()),
                "total_time", TimeUtil.formatTicks(totalTicks),
                "total_hours", String.valueOf(totalTicks / 72000L),
                "average", TimeUtil.formatTicks(average),
                "player", ranking.isEmpty() ? "-" : ranking.get(0).name(),
                "time", ranking.isEmpty() ? "-" : TimeUtil.formatTicks(ranking.get(0).totalTicks()),
        };
        messages.sendPlain(sender, "stats-header", placeholders);
        messages.sendPlain(sender, "stats-players", placeholders);
        messages.sendPlain(sender, "stats-total", placeholders);
        messages.sendPlain(sender, "stats-average", placeholders);
        messages.sendPlain(sender, "stats-longest", placeholders);
    }

    private void modify(CommandSender sender, String[] args, boolean absolute) {
        if (!sender.hasPermission("onlinetimestats.admin")) {
            messages.send(sender, "no-permission");
            return;
        }
        if (args.length < 3) {
            messages.send(sender, absolute ? "usage-set" : "usage-add");
            return;
        }
        long ticks;
        try {
            ticks = TimeUtil.parseDurationToTicks(args[2]);
        } catch (IllegalArgumentException e) {
            messages.send(sender, "invalid-duration", "input", args[2]);
            return;
        }
        TimeEntry entry = requireEntry(sender, args[1]);
        if (entry == null) {
            return;
        }
        String before = TimeUtil.formatTicks(entry.totalTicks());
        if (absolute) {
            entry.totalTicks(ticks);
        } else {
            entry.addTicks(ticks);
        }
        String after = TimeUtil.formatTicks(entry.totalTicks());
        flushEntry(entry);
        if (absolute) {
            messages.send(sender, "set-done", "player", entry.name(), "time", after);
        } else {
            messages.send(sender, "add-done", "player", entry.name(),
                    "delta", TimeUtil.formatTicks(ticks), "time", after, "before", before);
        }
    }

    private void reset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("onlinetimestats.admin")) {
            messages.send(sender, "no-permission");
            return;
        }
        if (args.length < 2) {
            messages.send(sender, "usage-reset");
            return;
        }
        if (args[1].equalsIgnoreCase("all")) {
            if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
                messages.send(sender, "reset-all-confirm",
                        "total_players", String.valueOf(store.size()));
                return;
            }
            int count = store.size();
            List<TimeEntry> snapshot = store.snapshot();
            store.clear();
            for (TimeEntry entry : snapshot) {
                Player online = plugin.getServer().getPlayer(entry.uuid());
                if (online != null) {
                    plugin.scoreboard().resetScore(online.getName());
                }
            }
            plugin.markDirty();
            plugin.requestSave();
            messages.send(sender, "reset-all-done", "total_players", String.valueOf(count));
            return;
        }
        TimeEntry entry = findEntry(args[1]);
        if (entry == null) {
            messages.send(sender, "player-not-found", "player", args[1]);
            return;
        }
        String name = entry.name();
        Player online = plugin.getServer().getPlayer(entry.uuid());
        store.remove(entry.uuid());
        if (online != null) {
            plugin.scoreboard().resetScore(online.getName());
        }
        plugin.markDirty();
        plugin.requestSave();
        messages.send(sender, "reset-done", "player", name);
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("onlinetimestats.admin")) {
            messages.send(sender, "no-permission");
            return;
        }
        plugin.reloadEverything();
        messages.send(sender, "reload-done");
    }

    /** 找到记录；对没有记录的在线玩家会即时建一条空记录，方便管理操作。 */
    private TimeEntry requireEntry(CommandSender sender, String name) {
        TimeEntry entry = findEntry(name);
        if (entry == null) {
            messages.send(sender, "player-not-found", "player", name);
        }
        return entry;
    }

    /**
     * 先查本插件自己的名称索引与在线玩家；都没有时，再看服务器是否认识这个名字
     * （比如以前玩过但插件还没记录的人），这样管理员能直接给离线玩家补时长。
     *
     * <p>{@code Bukkit.getOfflinePlayer(String)} 在 CraftBukkit 里被标记为过时，
     * 因为第一次查一个服务器不熟悉的名字可能需要读 usercache，会有短暂阻塞；
     * 它只会在管理员执行 set/add/reset 且名字不在本插件索引里时才会走到。
     */
    @SuppressWarnings("deprecation")
    private TimeEntry findEntry(String name) {
        TimeEntry entry = store.byName(name);
        if (entry != null) {
            return entry;
        }
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return store.entry(online.getUniqueId(), online.getName());
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (offline != null && offline.hasPlayedBefore() && offline.getName() != null
                && offline.getName().equalsIgnoreCase(name)) {
            return store.entry(offline.getUniqueId(), offline.getName());
        }
        return null;
    }

    private void flushEntry(TimeEntry entry) {
        Player online = plugin.getServer().getPlayer(entry.uuid());
        if (online != null) {
            plugin.scoreboard().update(online, entry);
        }
        plugin.markDirty();
        plugin.requestSave();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            Set<String> options = new LinkedHashSet<String>(SUBCOMMANDS);
            options.addAll(knownNames());
            return filter(options, prefix);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            if (sub.equals("check") || sub.equals("set") || sub.equals("add")) {
                return filter(knownNames(), prefix);
            }
            if (sub.equals("reset")) {
                Set<String> options = new LinkedHashSet<String>();
                options.add("all");
                options.addAll(knownNames());
                return filter(options, prefix);
            }
            if (sub.equals("top")) {
                return filter(Arrays.asList("5", "10", "20", "50"), prefix);
            }
            return Collections.emptyList();
        }
        if (args.length == 3) {
            if (sub.equals("set") || sub.equals("add")) {
                return filter(DURATION_SUGGESTIONS, prefix);
            }
            if (sub.equals("reset") && args[1].equalsIgnoreCase("all")) {
                return filter(Collections.singletonList("confirm"), prefix);
            }
        }
        return Collections.emptyList();
    }

    private Set<String> knownNames() {
        Set<String> names = new LinkedHashSet<String>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            names.add(player.getName());
        }
        for (TimeEntry entry : store.snapshot()) {
            if (!entry.name().isEmpty()) {
                names.add(entry.name());
            }
        }
        return names;
    }

    private static List<String> filter(Iterable<String> options, String prefix) {
        List<String> result = new ArrayList<String>();
        for (String option : options) {
            if (prefix.isEmpty() || option.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                result.add(option);
            }
            if (result.size() >= 50) {
                break;
            }
        }
        return result;
    }
}
