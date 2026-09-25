package com.onlinetimestats;

import com.onlinetimestats.core.TimeEntry;
import com.onlinetimestats.core.TimeStore;
import com.onlinetimestats.core.SessionState;
import com.onlinetimestats.core.TimeUtil;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 计时核心：负责把在线玩家的时间累加进数据仓库，并处理登录/退出与挂机状态。
 *
 * <p>所有方法都只在服务器主线程执行（定时任务与事件都在主线程），
 * 因此不需要对 {@link #sessions} 加锁；写盘由 {@link OnlineTimeStats#requestSave()} 异步完成。
 */
public final class TimeTracker {

    private final OnlineTimeStats plugin;
    private final TimeStore store;
    private final PluginConfig config;
    private final Messages messages;
    private final Map<UUID, SessionState> sessions = new LinkedHashMap<UUID, SessionState>();

    public TimeTracker(OnlineTimeStats plugin, TimeStore store, PluginConfig config, Messages messages) {
        this.plugin = plugin;
        this.store = store;
        this.config = config;
        this.messages = messages;
    }

    /**
     * 玩家进入服务器。
     *
     * @param fresh true 表示这是真实的加入事件（统计登录次数）；false 表示插件启用时玩家已在服内
     */
    public void attach(Player player, boolean fresh) {
        long now = System.currentTimeMillis();
        TimeEntry entry = store.entry(player.getUniqueId(), player.getName());
        if (entry.firstJoin() <= 0L) {
            entry.firstJoin(now);
        }
        if (fresh) {
            entry.lastJoin(now);
            entry.sessions(entry.sessions() + 1);
        }
        sessions.put(player.getUniqueId(), new SessionState(player.getUniqueId(), now));
        plugin.markDirty();
        if (fresh && config.joinQuitMessage()) {
            player.sendMessage(messages.formatPlain("join-notify",
                    "time", TimeUtil.formatTicks(entry.totalTicks())));
        }
    }

    /** 玩家退出服务器。 */
    public void detach(Player player) {
        SessionState session = sessions.remove(player.getUniqueId());
        TimeEntry entry = store.get(player.getUniqueId());
        if (entry != null) {
            entry.lastQuit(System.currentTimeMillis());
            if (session != null) {
                entry.addAfkTicks(session.afkTicks());
            }
        }
        plugin.markDirty();
        if (config.saveOnQuit()) {
            plugin.requestSave();
        }
    }

    /**
     * 累加一个周期的在线时长。
     *
     * @param amount 本次为每名在线玩家增加的 tick 数（TICK 模式为 1，WALL 模式为 20）
     */
    public void tickAll(long amount) {
        long now = System.currentTimeMillis();
        boolean afkEnabled = config.afkEnabled();
        long threshold = config.afkThresholdSeconds() * 1000L;
        boolean excludeAfk = config.afkExcludeFromTotal();

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            SessionState session = sessions.get(uuid);
            if (session == null) {
                // 兜底：正常流程不会走到这里（启用时漏掉的在线玩家）
                session = new SessionState(uuid, now);
                sessions.put(uuid, session);
            }
            TimeEntry entry = store.entry(uuid, player.getName());
            boolean afkBefore = session.afk();
            boolean counted = session.tick(now, afkEnabled, threshold, excludeAfk);
            if (counted) {
                entry.addTicks(amount);
            }
            if (session.afk()) {
                entry.addAfkTicks(amount);
            }
            if (afkEnabled && config.afkNotify() && afkBefore != session.afk()) {
                player.sendMessage(session.afk()
                        ? messages.formatPlain("afk-on")
                        : messages.formatPlain("afk-off"));
            }
        }
    }

    /** 记录玩家做了“有效操作”，用于挂机判定。 */
    public void markActive(UUID uuid) {
        if (!config.afkEnabled() || uuid == null) {
            return;
        }
        SessionState session = sessions.get(uuid);
        if (session != null) {
            session.markActive(System.currentTimeMillis());
        }
    }

    public SessionState session(UUID uuid) {
        return uuid == null ? null : sessions.get(uuid);
    }

    public int sessionCount() {
        return sessions.size();
    }

    public void clearSessions() {
        sessions.clear();
    }
}
