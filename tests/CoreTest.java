import com.onlinetimestats.core.TimeEntry;
import com.onlinetimestats.core.TimeStore;
import com.onlinetimestats.core.SessionState;
import com.onlinetimestats.core.TimeUtil;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.List;
import java.util.UUID;

/**
 * 不依赖服务器的核心逻辑测试：时长格式化/解析、数据文件读写、
 * 排行榜、以及“在线计时 + 挂机判定”的行为。
 *
 * <p>运行方式见同目录 run-tests.ps1，报告写在 out/core-test-report.txt。
 */
public final class CoreTest {

    private static int passed;
    private static int failed;
    private static final StringBuilder REPORT = new StringBuilder();

    public static void main(String[] args) throws Exception {
        testFormat();
        testParse();
        testStoreRoundTrip();
        testStoreTolerantParsing();
        testRankingAndReset();
        testAccumulation();
        testAfkExclusion();

        REPORT.append("通过: ").append(passed).append("，失败: ").append(failed).append('\n');
        File outDir = new File("out");
        if (!outDir.isDirectory()) {
            outDir.mkdirs();
        }
        PrintWriter writer = new PrintWriter(new File(outDir, "core-test-report.txt"), "UTF-8");
        writer.print(REPORT.toString());
        writer.close();
        System.out.println("通过: " + passed + "，失败: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testFormat() {
        check("20 tick = 1秒", "1秒".equals(TimeUtil.formatTicks(20L)));
        check("0 tick = 0秒", "0秒".equals(TimeUtil.formatTicks(0L)));
        check("1200 tick = 1分钟", "1分钟".equals(TimeUtil.formatTicks(1200L)));
        check("72000 tick = 1小时", "1小时".equals(TimeUtil.formatTicks(72000L)));
        check("1天", "1天".equals(TimeUtil.formatTicks(86400L * 20L)));
        check("1天1小时1分钟1秒", "1天1小时1分钟1秒".equals(TimeUtil.formatTicks(90061L * 20L)));
        check("1小时1分钟1秒", "1小时1分钟1秒".equals(TimeUtil.formatTicks(3661L * 20L)));
        check("秒数被截断", "1秒".equals(TimeUtil.formatTicks(39L)));
    }

    private static void testParse() {
        expectTicks("2h30m", 9000L);
        expectTicks("90m", 5400L);
        expectTicks("1d", 86400L);
        expectTicks("3600", 3600L);
        expectTicks("1d2h3m4s", 93784L);
        expectTicks("2小时30分", 9000L);
        expectTicks("10分", 600L);
        boolean threw = false;
        try {
            TimeUtil.parseDurationToTicks("abc");
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check("非法时长被拒绝", threw);
        threw = false;
        try {
            TimeUtil.parseDurationToTicks("2h30x");
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check("带非法单位被拒绝", threw);
    }

    private static void expectTicks(String input, long seconds) {
        try {
            long ticks = TimeUtil.parseDurationToTicks(input);
            check("解析 " + input + " = " + seconds + " 秒", ticks == seconds * 20L);
        } catch (IllegalArgumentException e) {
            check("解析 " + input, false);
        }
    }

    private static void testStoreRoundTrip() throws Exception {
        File dir = tempDir("store-roundtrip");
        File file = new File(dir, "playtime.tsv");
        TimeStore store = new TimeStore(file);

        UUID alex = UUID.randomUUID();
        UUID steve = UUID.randomUUID();
        TimeEntry first = store.entry(alex, "Alex_CN");
        first.totalTicks(3600L * 20L);
        first.firstJoin(1_690_000_000_000L);
        first.lastJoin(1_690_100_000_000L);
        first.lastQuit(1_690_200_000_000L);
        first.sessions(7);
        store.entry(steve, "MC_Yeko").totalTicks(90L * 20L);
        store.save();

        TimeStore reloaded = new TimeStore(file);
        reloaded.load();
        check("读取玩家数量", reloaded.size() == 2);
        check("没有跳过任何行", reloaded.skippedLines() == 0);
        TimeEntry loaded = reloaded.get(alex);
        check("总时长保留", loaded != null && loaded.totalTicks() == 3600L * 20L);
        check("总秒数换算", loaded != null && loaded.totalSeconds() == 3600L);
        check("登录次数保留", loaded != null && loaded.sessions() == 7);
        check("首次加入时间保留", loaded != null && loaded.firstJoin() == 1_690_000_000_000L);
        check("大小写不敏感按名查找", reloaded.byName("alex_cn") != null);
        check("按名查找命中同一玩家", reloaded.byName("ALEX_CN").uuid().equals(alex));
        check("全服累计时长", reloaded.totalTicks() == (3600L + 90L) * 20L);
    }

    private static void testStoreTolerantParsing() throws Exception {
        File dir = tempDir("store-tolerant");
        File file = new File(dir, "playtime.tsv");
        PrintWriter writer = new PrintWriter(file, "UTF-8");
        writer.println(TimeStore.HEADER);
        writer.println("# 这是一行注释");
        writer.println("");
        writer.println("坏行");
        writer.println("not-a-uuid\tSteve\t100");
        writer.println(UUID.randomUUID() + "\tSteve\t200\t0\t1\t2\t3\t4");
        writer.close();

        TimeStore store = new TimeStore(file);
        store.load();
        check("坏行被跳过但合法行保留", store.size() == 1);
        check("统计了跳过的行数", store.skippedLines() == 2);
    }

    private static void testRankingAndReset() throws Exception {
        File dir = tempDir("store-ranking");
        TimeStore store = new TimeStore(new File(dir, "playtime.tsv"));
        UUID low = UUID.randomUUID();
        UUID mid = UUID.randomUUID();
        UUID high = UUID.randomUUID();
        store.entry(low, "Low").totalTicks(100L);
        store.entry(mid, "Mid").totalTicks(1000L);
        store.entry(high, "High").totalTicks(10_000L);

        List<TimeEntry> ranking = store.ranking(2);
        check("排行只取前 2 名", ranking.size() == 2);
        check("排行第 1 名", ranking.get(0).name().equals("High"));
        check("排行第 2 名", ranking.get(1).name().equals("Mid"));
        check("名次计算", store.rankOf(low) == 3);
        check("未知玩家名次为 -1", store.rankOf(UUID.randomUUID()) == -1);

        check("删除记录", store.remove(low));
        check("删除后不再能按名查到", store.byName("Low") == null);
        check("删除后人数减少", store.size() == 2);
        store.clear();
        check("清空后为 0", store.size() == 0 && store.totalTicks() == 0L);
    }

    /** 模拟 5 分钟在线：确认累计与换算口径。 */
    private static void testAccumulation() throws Exception {
        File dir = tempDir("accumulate");
        TimeStore store = new TimeStore(new File(dir, "playtime.tsv"));
        UUID uuid = UUID.randomUUID();
        TimeEntry entry = store.entry(uuid, "Tester");
        long now = 1_700_000_000_000L;
        SessionState session = new SessionState(uuid, now);

        long ticks = 20L * 300L;
        for (long i = 0; i < ticks; i++) {
            now += 50L;
            if (session.tick(now, false, 0L, true)) {
                entry.addTicks(1L);
            }
        }

        check("5 分钟在线 = 6000 tick", entry.totalTicks() == 6000L);
        check("5 分钟在线 = 300 秒", entry.totalSeconds() == 300L);
        check("5 分钟在线 = 5 分钟", entry.totalMinutes() == 5L);
        check("不足 1 小时", entry.totalHours() == 0L);
        check("ots_minutes 口径", entry.minuteInHour() == 5L);
        check("ots_seconds 口径", entry.secondInMinute() == 0L);
        check("本次会话时长", session.sessionSeconds() == 300L);
        check("格式化结果", "5分钟".equals(TimeUtil.formatTicks(entry.totalTicks())));

        // 再模拟一次性写入磁盘并读回，确认重启后时长不丢
        store.save();
        TimeStore reloaded = new TimeStore(new File(dir, "playtime.tsv"));
        reloaded.load();
        check("重启后时长仍在", reloaded.get(uuid).totalTicks() == 6000L);
    }

    /** 挂机判定：超过阈值后不再累计总时长，只涨挂机时长。 */
    private static void testAfkExclusion() {
        UUID uuid = UUID.randomUUID();
        long start = 1_700_000_000_000L;
        SessionState session = new SessionState(uuid, start);
        long threshold = 30_000L;
        long now = start;
        long counted = 0L;
        long afkTicks = 0L;

        // 前 30 秒活跃：每 5 秒动一下
        for (int i = 0; i < 20 * 30; i++) {
            now += 50L;
            if (i % 100 == 0) {
                session.markActive(now);
            }
            if (session.tick(now, true, threshold, true)) {
                counted++;
            }
            if (session.afk()) {
                afkTicks++;
            }
        }
        check("活跃期间正常计时", counted == 20 * 30);
        check("活跃期间不算挂机", afkTicks == 0);

        // 之后再挂机 90 秒：前 30 秒仍计时，后面的 60 秒应该只算挂机
        session.markActive(now);
        long countedBefore = counted;
        long afkBefore = afkTicks;
        for (int i = 0; i < 20 * 90; i++) {
            now += 50L;
            if (session.tick(now, true, threshold, true)) {
                counted++;
            }
            if (session.afk()) {
                afkTicks++;
            }
        }
        long countedIdle = counted - countedBefore;
        long afkIdle = afkTicks - afkBefore;
        check("挂机后停止累计总时长", countedIdle >= 20L * 29 && countedIdle <= 20L * 31);
        check("挂机时长被单独累计", afkIdle >= 20L * 59 && afkIdle <= 20L * 61);
        check("在线与挂机时长互补", countedIdle + afkIdle == 20L * 90);
        check("会话进入挂机状态", session.afk());

        // 恢复操作后继续计时
        session.markActive(now);
        now += 50L;
        check("恢复操作后重新计时", session.tick(now, true, threshold, true) && !session.afk());
    }

    private static void check(String name, boolean ok) {
        if (ok) {
            passed++;
            REPORT.append("[通过] ").append(name).append('\n');
        } else {
            failed++;
            REPORT.append("[失败] ").append(name).append('\n');
        }
    }

    private static File tempDir(String name) throws Exception {
        File dir = new File("out/" + name + "-" + System.nanoTime());
        if (!dir.mkdirs()) {
            throw new IllegalStateException("无法创建临时目录: " + dir);
        }
        return dir;
    }

    static {
        // 确保报告文件用 UTF-8 写出，避免中文断言名乱码
        System.setProperty("file.encoding", "UTF-8");
        if (!"UTF-8".equalsIgnoreCase(Charset.defaultCharset().name())) {
            // 仅提示，不影响测试结果
            System.err.println("提示: 默认字符集为 " + Charset.defaultCharset().name());
        }
    }
}
