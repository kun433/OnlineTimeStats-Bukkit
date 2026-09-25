package com.onlinetimestats.core;

import java.util.UUID;

/**
 * 单个玩家的在线时长记录。
 *
 * <p>纯数据对象，不依赖任何 Bukkit API，方便脱离服务器做单元测试。
 * 计时口径：以 1 tick 为最小单位，20 tick = 1 秒、60 秒 = 1 分钟、60 分钟 = 1 小时。
 */
public final class TimeEntry {

    private final UUID uuid;
    private String name;
    private long totalTicks;
    private long afkTicks;
    private long firstJoin;
    private long lastJoin;
    private long lastQuit;
    private int sessions;

    public TimeEntry(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name == null ? "" : name;
    }

    public TimeEntry copy() {
        TimeEntry c = new TimeEntry(uuid, name);
        c.totalTicks = totalTicks;
        c.afkTicks = afkTicks;
        c.firstJoin = firstJoin;
        c.lastJoin = lastJoin;
        c.lastQuit = lastQuit;
        c.sessions = sessions;
        return c;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public void name(String name) {
        if (name != null && !name.isEmpty()) {
            this.name = name;
        }
    }

    /** 累计在线 tick 数（唯一真实来源）。 */
    public long totalTicks() {
        return totalTicks;
    }

    public void totalTicks(long totalTicks) {
        this.totalTicks = Math.max(0L, totalTicks);
    }

    public void addTicks(long ticks) {
        if (ticks > 0) {
            this.totalTicks += ticks;
        }
    }

    /** 被判定为挂机、未计入总时长的 tick 数（仅当 afk.enabled 时才会增长）。 */
    public long afkTicks() {
        return afkTicks;
    }

    public void afkTicks(long afkTicks) {
        this.afkTicks = Math.max(0L, afkTicks);
    }

    public void addAfkTicks(long ticks) {
        if (ticks > 0) {
            this.afkTicks += ticks;
        }
    }

    public long firstJoin() {
        return firstJoin;
    }

    public void firstJoin(long firstJoin) {
        this.firstJoin = firstJoin;
    }

    public long lastJoin() {
        return lastJoin;
    }

    public void lastJoin(long lastJoin) {
        this.lastJoin = lastJoin;
    }

    public long lastQuit() {
        return lastQuit;
    }

    public void lastQuit(long lastQuit) {
        this.lastQuit = lastQuit;
    }

    public int sessions() {
        return sessions;
    }

    public void sessions(int sessions) {
        this.sessions = Math.max(0, sessions);
    }

    public long totalSeconds() {
        return totalTicks / 20L;
    }

    public long totalMinutes() {
        return totalTicks / 1200L;
    }

    public long totalHours() {
        return totalTicks / 72000L;
    }

    /** 不满一分钟的秒数（对应 ots_seconds 目标）。 */
    public long secondInMinute() {
        return totalSeconds() % 60L;
    }

    /** 不满一小时的分钟数（对应 ots_minutes 目标）。 */
    public long minuteInHour() {
        return totalMinutes() % 60L;
    }

    public long tickInSecond() {
        return totalTicks % 20L;
    }

    @Override
    public String toString() {
        return name + "(" + uuid + "): " + totalTicks + " ticks";
    }
}
