package com.onlinetimestats.core;

import java.util.UUID;

/**
 * 一次登录期间的会话状态（只在玩家在线时存在）。
 *
 * <p>它只做两件事：累计本次在线 tick，以及判断玩家是否已经挂机。
 * 判定挂机的逻辑不依赖 Bukkit，方便离线测试。
 */
public final class SessionState {

    private final UUID uuid;
    private final long startMillis;
    private long sessionTicks;
    private long afkTicks;
    private long lastActiveMillis;
    private boolean afk;

    public SessionState(UUID uuid, long startMillis) {
        this.uuid = uuid;
        this.startMillis = startMillis;
        this.lastActiveMillis = startMillis;
    }

    public UUID uuid() {
        return uuid;
    }

    public long startMillis() {
        return startMillis;
    }

    public long sessionTicks() {
        return sessionTicks;
    }

    public long sessionSeconds() {
        return sessionTicks / TimeUtil.TICKS_PER_SECOND;
    }

    public long afkTicks() {
        return afkTicks;
    }

    public boolean afk() {
        return afk;
    }

    public long lastActiveMillis() {
        return lastActiveMillis;
    }

    /** 玩家做了任何“有交互”的动作时调用。 */
    public void markActive(long nowMillis) {
        this.lastActiveMillis = nowMillis;
        this.afk = false;
    }

    /**
     * 根据距上次活跃的时间判断是否刚进入挂机状态。
     *
     * @return true 表示“这一次判定刚好从活跃变成挂机”，可用于只提示一次
     */
    public boolean isAfk(long nowMillis, long thresholdMillis) {
        return nowMillis - lastActiveMillis >= thresholdMillis;
    }

    /**
     * 结算一个 tick。
     *
     * @param nowMillis       当前时间
     * @param afkEnabled      是否启用挂机判定
     * @param thresholdMillis 挂机阈值
     * @param excludeFromTotal 挂机期间是否不计入总时长
     * @return 本次是否应计入总时长（true 表示正常在线）
     */
    public boolean tick(long nowMillis, boolean afkEnabled, long thresholdMillis, boolean excludeFromTotal) {
        boolean nowAfk = afkEnabled && isAfk(nowMillis, thresholdMillis);
        afk = nowAfk;
        if (nowAfk) {
            afkTicks++;
            if (excludeFromTotal) {
                return false;
            }
        }
        sessionTicks++;
        return true;
    }
}
