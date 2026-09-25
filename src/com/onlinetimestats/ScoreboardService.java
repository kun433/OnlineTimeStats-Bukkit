package com.onlinetimestats;

import com.onlinetimestats.core.TimeEntry;
import com.onlinetimestats.core.TimeStore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 记分板输出：默认创建一个目标（默认名 {@code playtime}），
 * 把小时数挂到玩家名字下方与 Tab 列表。
 *
 * <p><b>关于多个显示位置：</b>CraftBukkit/Spigot 的 {@code Objective#setDisplaySlot}
 * 实现会先把该目标从其它槽位清掉（1.12.2 的 CraftObjective 字节码里就有一个遍历 0..2 的清槽循环），
 * 即<b>一个目标只能占据一个显示位置</b>。
 * 因此本插件会为第一个位置使用配置的目标名（默认 {@code playtime}），
 * 其余位置自动创建数值完全一致的镜像目标（如 {@code playtime_list}），
 * 这样只用公共 API 就能让同一个数值同时显示在多个位置。
 *
 * <p>另外可选创建 {@code ots_tick / ots_seconds / ots_minutes / ots_hours / ots_total}
 * 五个细粒度目标，供命令方块或其它插件直接读取。
 */
public final class ScoreboardService {

    private final OnlineTimeStats plugin;
    private final PluginConfig config;
    private final TimeStore store;

    private final Map<DisplaySlot, Boolean> occupiedByUs = new EnumMap<DisplaySlot, Boolean>(DisplaySlot.class);
    private final List<Binding> bindings = new ArrayList<Binding>();

    private Objective extraTick;
    private Objective extraSeconds;
    private Objective extraMinutes;
    private Objective extraHours;
    private Objective extraTotal;

    public ScoreboardService(OnlineTimeStats plugin, PluginConfig config, TimeStore store) {
        this.plugin = plugin;
        this.config = config;
        this.store = store;
    }

    /** 一个显示位置与它对应的目标。 */
    private static final class Binding {
        private final DisplaySlot slot;
        private final String label;
        private final Objective objective;

        private Binding(DisplaySlot slot, String label, Objective objective) {
            this.slot = slot;
            this.label = label;
            this.objective = objective;
        }
    }

    /** 建立（或接管）目标并刷新一次分数。必须主线程调用。 */
    public void setup() {
        if (!config.scoreboardEnabled()) {
            return;
        }
        Scoreboard board = mainBoard();
        if (board == null) {
            plugin.getLogger().warning("无法取得主记分板，记分板显示已跳过。");
            return;
        }

        clearOurSlots(board);
        bindings.clear();

        List<DisplaySlot> wanted = wantedSlots();
        for (int i = 0; i < wanted.size(); i++) {
            DisplaySlot slot = wanted.get(i);
            String label = slotLabel(slot);
            String name = i == 0 ? config.scoreboardObjective() : config.scoreboardObjective() + "_" + label;
            Objective objective = board.getObjective(name);
            if (objective == null) {
                objective = board.registerNewObjective(name, "dummy");
            }
            objective.setDisplayName(Messages.color(config.scoreboardDisplayName()));
            bindings.add(new Binding(slot, label, objective));
        }
        for (Binding binding : bindings) {
            binding.objective.setDisplaySlot(binding.slot);
            occupiedByUs.put(binding.slot, Boolean.TRUE);
        }

        if (config.extraObjectives()) {
            extraTick = ensure(board, "ots_tick", null);
            extraSeconds = ensure(board, "ots_seconds", null);
            extraMinutes = ensure(board, "ots_minutes", null);
            extraHours = ensure(board, "ots_hours", "&a[小时]");
            extraTotal = ensure(board, "ots_total", "&a[分钟]");
        }
        updateAll();
    }

    private Objective ensure(Scoreboard board, String name, String displayName) {
        Objective found = board.getObjective(name);
        if (found == null) {
            found = board.registerNewObjective(name, "dummy");
        }
        if (displayName != null) {
            found.setDisplayName(Messages.color(displayName));
        }
        return found;
    }

    private Scoreboard mainBoard() {
        return Bukkit.getScoreboardManager() == null ? null : Bukkit.getScoreboardManager().getMainScoreboard();
    }

    /** 只放开本插件自己占用的显示位置，不影响其它插件的侧边栏等。 */
    private void clearOurSlots(Scoreboard board) {
        for (Map.Entry<DisplaySlot, Boolean> entry : occupiedByUs.entrySet()) {
            if (Boolean.TRUE.equals(entry.getValue())) {
                board.clearSlot(entry.getKey());
            }
        }
        occupiedByUs.clear();
    }

    private List<DisplaySlot> wantedSlots() {
        List<DisplaySlot> slots = new ArrayList<DisplaySlot>();
        for (String raw : config.scoreboardSlots()) {
            String value = raw.toLowerCase(Locale.ROOT);
            if ("below_name".equals(value)) {
                addIfAbsent(slots, DisplaySlot.BELOW_NAME);
            } else if ("list".equals(value)) {
                addIfAbsent(slots, DisplaySlot.PLAYER_LIST);
            } else if ("sidebar".equals(value)) {
                addIfAbsent(slots, DisplaySlot.SIDEBAR);
            }
        }
        return slots;
    }

    private static void addIfAbsent(List<DisplaySlot> slots, DisplaySlot slot) {
        if (!slots.contains(slot)) {
            slots.add(slot);
        }
    }

    private static String slotLabel(DisplaySlot slot) {
        if (slot == DisplaySlot.BELOW_NAME) {
            return "below_name";
        }
        if (slot == DisplaySlot.PLAYER_LIST) {
            return "list";
        }
        return "sidebar";
    }

    /** 刷新所有在线玩家的分数。必须主线程调用。 */
    public void updateAll() {
        if (!config.scoreboardEnabled() || bindings.isEmpty()) {
            return;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            TimeEntry entry = store.get(player.getUniqueId());
            if (entry != null) {
                update(player, entry);
            }
        }
    }

    /** 刷新单个玩家的分数（退出时也要刷一次，否则名字下方会停在旧数值）。 */
    public void update(Player player, TimeEntry entry) {
        if (!config.scoreboardEnabled() || bindings.isEmpty() || entry == null) {
            return;
        }
        int value = objectiveValue(entry);
        for (Binding binding : bindings) {
            setScore(binding.objective, player.getName(), value);
        }
        if (extraTick != null) {
            setScore(extraTick, player.getName(), entry.tickInSecond());
            setScore(extraSeconds, player.getName(), entry.secondInMinute());
            setScore(extraMinutes, player.getName(), entry.minuteInHour());
            setScore(extraHours, player.getName(), entry.totalHours());
            setScore(extraTotal, player.getName(), entry.totalMinutes());
        }
    }

    private int objectiveValue(TimeEntry entry) {
        long value;
        switch (config.scoreboardUnit()) {
            case MINUTES:
                value = entry.totalMinutes();
                break;
            case SECONDS:
                value = entry.totalSeconds();
                break;
            case HOURS:
            default:
                value = entry.totalHours();
                break;
        }
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, value));
    }

    private static void setScore(Objective target, String entryName, long value) {
        long clamped = Math.max(0L, Math.min(Integer.MAX_VALUE, value));
        target.getScore(entryName).setScore((int) clamped);
    }

    /** 把主计分板上各显示位置当前指向的目标名读回来，用于自检。 */
    public String slotState() {
        Scoreboard board = mainBoard();
        if (board == null) {
            return "无法访问主记分板";
        }
        List<String> parts = new ArrayList<String>();
        appendSlot(parts, board, "below_name", DisplaySlot.BELOW_NAME);
        appendSlot(parts, board, "list", DisplaySlot.PLAYER_LIST);
        appendSlot(parts, board, "sidebar", DisplaySlot.SIDEBAR);
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(part);
        }
        return sb.toString();
    }

    private static void appendSlot(List<String> parts, Scoreboard board, String label, DisplaySlot slot) {
        Objective current = board.getObjective(slot);
        parts.add(label + "=" + (current == null ? "无" : current.getName()));
    }

    /** 清除某个玩家在主记分板上的所有分数（管理员重置数据后用）。 */
    public void resetScore(String playerName) {
        Scoreboard board = mainBoard();
        if (board == null || playerName == null) {
            return;
        }
        board.resetScores(playerName);
    }

    /** 卸载插件时把显示位置让出来，但保留目标与分数（数据仍可用）。 */
    public void shutdown() {
        Scoreboard board = mainBoard();
        clearOurSlotsQuietly(board);
        bindings.clear();
        extraTick = null;
        extraSeconds = null;
        extraMinutes = null;
        extraHours = null;
        extraTotal = null;
    }

    private void clearOurSlotsQuietly(Scoreboard board) {
        if (board == null) {
            occupiedByUs.clear();
            return;
        }
        try {
            clearOurSlots(board);
        } catch (Throwable t) {
            occupiedByUs.clear();
            plugin.getLogger().warning("清理记分板显示位置时出错: " + t.getMessage());
        }
    }

    /** 配置改动后重新应用（重载指令用）。 */
    public void reload() {
        shutdown();
        setup();
    }
}
