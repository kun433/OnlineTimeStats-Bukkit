package com.onlinetimestats;

import com.onlinetimestats.core.TimeEntry;
import com.onlinetimestats.core.TimeStore;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * OnlineTimeStats —— 面向 1.8 ~ 1.12.2 的 Paper/Spigot 服务器，统计并显示玩家在线时长。
 *
 * <p>按服务器 tick 累计在线时间，并把小时数显示在玩家名字下方与 Tab 列表；
 * 额外提供查询、排行、离线玩家查询与管理指令，数据写入纯文本文件长期保存。
 */
public final class OnlineTimeStats extends JavaPlugin {

    private PluginConfig pluginConfig;
    private Messages messages;
    private TimeStore store;
    private TimeTracker tracker;
    private ScoreboardService scoreboard;

    private BukkitTask tickTask;
    private BukkitTask saveTask;
    private BukkitTask scoreboardTask;

    private final AtomicBoolean saveInFlight = new AtomicBoolean(false);
    private final AtomicBoolean savePending = new AtomicBoolean(false);
    private volatile boolean dirty = false;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        pluginConfig = new PluginConfig(this);
        pluginConfig.load();
        messages = new Messages(this);

        store = new TimeStore(pluginConfig.dataFile());
        try {
            store.load();
        } catch (IOException e) {
            getLogger().severe("读取数据文件失败（将按空数据启动）: " + e.getMessage());
        }
        for (String warning : pluginConfig.warnings()) {
            getLogger().warning(warning);
        }

        tracker = new TimeTracker(this, store, pluginConfig, messages);
        scoreboard = new ScoreboardService(this, pluginConfig, store);

        getServer().getPluginManager().registerEvents(new PlayerListener(this, tracker), this);

        StatsCommand executor = new StatsCommand(this);
        PluginCommand command = getCommand("playtime");
        if (command == null) {
            getLogger().severe("plugin.yml 中没有 playtime 指令，插件无法正常工作。");
        } else {
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        scoreboard.setup();
        if (pluginConfig.scoreboardEnabled()) {
            getLogger().info("记分板: objective=" + pluginConfig.scoreboardObjective()
                    + "，单位=" + pluginConfig.scoreboardUnit().name()
                    + "，显示位置: " + scoreboard.slotState());
        }

        // 插件被 /reload 或在有玩家时启用：这些玩家不算新的登录
        for (Player player : getServer().getOnlinePlayers()) {
            tracker.attach(player, false);
        }
        scoreboard.updateAll();
        startTasks();

        getLogger().info("OnlineTimeStats 已启用（数据文件: " + pluginConfig.dataFile()
                + "，已记录 " + store.size() + " 名玩家）");
        if (store.skippedLines() > 0) {
            getLogger().warning("数据文件中跳过了 " + store.skippedLines() + " 行无法解析的内容。");
        }
    }

    @Override
    public void onDisable() {
        cancelTasks();
        tracker.clearSessions();
        scoreboard.shutdown();
        try {
            store.save();
            dirty = false;
        } catch (IOException e) {
            getLogger().severe("保存数据失败: " + e.getMessage());
        }
    }

    private void startTasks() {
        boolean tickMode = pluginConfig.countingMode() == PluginConfig.CountingMode.TICK;
        long period = tickMode ? 1L : 20L;
        final long amount = tickMode ? 1L : 20L;
        tickTask = getServer().getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                tracker.tickAll(amount);
            }
        }, period, period);

        long savePeriod = pluginConfig.saveIntervalSeconds() * 20L;
        saveTask = getServer().getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                if (dirty) {
                    requestSave();
                }
            }
        }, savePeriod, savePeriod);

        long scoreboardPeriod = pluginConfig.scoreboardUpdateSeconds() * 20L;
        scoreboardTask = getServer().getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                scoreboard.updateAll();
            }
        }, scoreboardPeriod, scoreboardPeriod);
    }

    private void cancelTasks() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        if (scoreboardTask != null) {
            scoreboardTask.cancel();
            scoreboardTask = null;
        }
    }

    /** 重载配置：重读 config.yml、重建记分板、按新的间隔重启定时任务。 */
    public void reloadEverything() {
        pluginConfig.load();
        for (String warning : pluginConfig.warnings()) {
            getLogger().warning(warning);
        }
        scoreboard.reload();
        cancelTasks();
        startTasks();
        scoreboard.updateAll();
        getLogger().info("记分板: objective=" + pluginConfig.scoreboardObjective()
                + "，单位=" + pluginConfig.scoreboardUnit().name()
                + "，显示位置: " + scoreboard.slotState());
    }

    public void markDirty() {
        dirty = true;
    }

    /**
     * 请求把内存中的时长写盘。主线程先取快照，再由异步线程写文件；
     * 同时最多只有一个写盘任务在跑，期间到来的请求会合并成一次。
     */
    public void requestSave() {
        if (!isEnabled()) {
            return;
        }
        if (!getServer().isPrimaryThread()) {
            savePending.set(true);
            return;
        }
        if (saveInFlight.get()) {
            savePending.set(true);
            return;
        }
        saveInFlight.set(true);
        dirty = false;
        final List<TimeEntry> snapshot = store.snapshot();
        try {
            getServer().getScheduler().runTaskAsynchronously(this, new Runnable() {
                @Override
                public void run() {
                    try {
                        store.saveTo(snapshot);
                    } catch (Throwable t) {
                        dirty = true;
                        getLogger().severe("写盘失败: " + t.getMessage());
                    } finally {
                        saveInFlight.set(false);
                        if (savePending.compareAndSet(true, false) && isEnabled()) {
                            getServer().getScheduler().runTask(OnlineTimeStats.this, new Runnable() {
                                @Override
                                public void run() {
                                    requestSave();
                                }
                            });
                        }
                    }
                }
            });
        } catch (Throwable t) {
            saveInFlight.set(false);
            getLogger().warning("无法调度异步写盘任务: " + t.getMessage());
        }
    }

    public PluginConfig pluginConfig() {
        return pluginConfig;
    }

    public Messages messages() {
        return messages;
    }

    public TimeStore store() {
        return store;
    }

    public TimeTracker tracker() {
        return tracker;
    }

    public ScoreboardService scoreboard() {
        return scoreboard;
    }
}
