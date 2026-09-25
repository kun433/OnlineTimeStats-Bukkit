package com.onlinetimestats.core;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 在线时长数据仓库：内存索引 + 制表符分隔的文本文件持久化。
 *
 * <p>文件格式（UTF-8，一行一名玩家，字段用制表符分隔）：
 * <pre>
 * # OnlineTimeStats data v1
 * uuid    name    totalTicks    afkTicks    firstJoin    lastJoin    lastQuit    sessions
 * </pre>
 * 选择纯文本而不是 YAML，是为了让存盘与读取完全不依赖 Bukkit，
 * 从而可以在没有服务器的情况下做单元测试，也方便管理员直接查看或用脚本处理。
 */
public final class TimeStore {

    public static final String HEADER = "# OnlineTimeStats data v1";
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private final File file;
    private final Map<UUID, TimeEntry> entries = new LinkedHashMap<UUID, TimeEntry>();
    private final Map<String, UUID> nameIndex = new HashMap<String, UUID>();

    private int loadedEntries;
    private int skippedLines;

    public TimeStore(File file) {
        this.file = file;
    }

    public File file() {
        return file;
    }

    public int loadedEntries() {
        return loadedEntries;
    }

    public int skippedLines() {
        return skippedLines;
    }

    /** 从文件读取；文件不存在时视为空数据。 */
    public void load() throws IOException {
        entries.clear();
        nameIndex.clear();
        loadedEntries = 0;
        skippedLines = 0;
        if (file == null || !file.isFile()) {
            return;
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), UTF8));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                TimeEntry entry = parse(trimmed);
                if (entry == null) {
                    skippedLines++;
                    continue;
                }
                entries.put(entry.uuid(), entry);
                index(entry);
                loadedEntries++;
            }
        } finally {
            reader.close();
        }
    }

    private void index(TimeEntry entry) {
        if (entry.name().isEmpty()) {
            return;
        }
        nameIndex.put(entry.name().toLowerCase(Locale.ROOT), entry.uuid());
    }

    /** 取得（必要时创建）玩家记录。 */
    public TimeEntry entry(UUID uuid, String name) {
        TimeEntry entry = entries.get(uuid);
        if (entry == null) {
            entry = new TimeEntry(uuid, name);
            entries.put(uuid, entry);
            index(entry);
            return entry;
        }
        if (name != null && !name.isEmpty() && !name.equals(entry.name())) {
            entry.name(name);
            index(entry);
        }
        return entry;
    }

    public TimeEntry get(UUID uuid) {
        return uuid == null ? null : entries.get(uuid);
    }

    /** 按名字查（大小写不敏感），只查本插件自己的索引，不会触发 Mojang 查询。 */
    public TimeEntry byName(String name) {
        if (name == null) {
            return null;
        }
        UUID uuid = nameIndex.get(name.toLowerCase(Locale.ROOT));
        return uuid == null ? null : entries.get(uuid);
    }

    public boolean remove(UUID uuid) {
        TimeEntry removed = entries.remove(uuid);
        if (removed == null) {
            return false;
        }
        if (!removed.name().isEmpty()) {
            nameIndex.remove(removed.name().toLowerCase(Locale.ROOT));
        }
        return true;
    }

    public void clear() {
        entries.clear();
        nameIndex.clear();
    }

    public int size() {
        return entries.size();
    }

    /** 需要独立于主线程读写时，先在主线程取快照。 */
    public List<TimeEntry> snapshot() {
        List<TimeEntry> copy = new ArrayList<TimeEntry>(entries.size());
        for (TimeEntry entry : entries.values()) {
            copy.add(entry.copy());
        }
        return copy;
    }

    /** 时长降序排行；limit <= 0 表示不限。 */
    public List<TimeEntry> ranking(int limit) {
        List<TimeEntry> list = snapshot();
        Collections.sort(list, new Comparator<TimeEntry>() {
            @Override
            public int compare(TimeEntry a, TimeEntry b) {
                int byTicks = Long.compare(b.totalTicks(), a.totalTicks());
                if (byTicks != 0) {
                    return byTicks;
                }
                return a.name().compareToIgnoreCase(b.name());
            }
        });
        if (limit > 0 && list.size() > limit) {
            return new ArrayList<TimeEntry>(list.subList(0, limit));
        }
        return list;
    }

    /** 1 起算的名次；没记录时返回 -1。 */
    public int rankOf(UUID uuid) {
        if (uuid == null) {
            return -1;
        }
        List<TimeEntry> list = ranking(0);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).uuid().equals(uuid)) {
                return i + 1;
            }
        }
        return -1;
    }

    public long totalTicks() {
        long sum = 0L;
        for (TimeEntry entry : entries.values()) {
            sum += entry.totalTicks();
        }
        return sum;
    }

    /** 原子写盘：先写临时文件再替换，避免半截文件。 */
    public void saveTo(List<TimeEntry> snapshot) throws IOException {
        if (file == null) {
            return;
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("无法创建数据目录: " + parent);
        }
        File temp = new File(file.getAbsolutePath() + ".tmp");
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(temp), UTF8));
        try {
            writer.write(HEADER);
            writer.newLine();
            for (TimeEntry entry : snapshot) {
                writer.write(serialize(entry));
                writer.newLine();
            }
            writer.flush();
        } finally {
            writer.close();
        }
        try {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFailed) {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public void save() throws IOException {
        saveTo(snapshot());
    }

    public static String serialize(TimeEntry entry) {
        StringBuilder sb = new StringBuilder();
        sb.append(entry.uuid().toString()).append('\t');
        sb.append(entry.name().replace('\t', '_').replace('\n', '_')).append('\t');
        sb.append(entry.totalTicks()).append('\t');
        sb.append(entry.afkTicks()).append('\t');
        sb.append(entry.firstJoin()).append('\t');
        sb.append(entry.lastJoin()).append('\t');
        sb.append(entry.lastQuit()).append('\t');
        sb.append(entry.sessions());
        return sb.toString();
    }

    /** 解析一行；字段不足或 UUID 非法时返回 null。 */
    public static TimeEntry parse(String line) {
        String[] parts = line.split("\t", -1);
        if (parts.length < 3) {
            return null;
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(parts[0].trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
        TimeEntry entry = new TimeEntry(uuid, parts[1]);
        entry.totalTicks(parseLong(parts, 2));
        entry.afkTicks(parseLong(parts, 3));
        entry.firstJoin(parseLong(parts, 4));
        entry.lastJoin(parseLong(parts, 5));
        entry.lastQuit(parseLong(parts, 6));
        entry.sessions((int) parseLong(parts, 7));
        return entry;
    }

    private static long parseLong(String[] parts, int index) {
        if (index >= parts.length) {
            return 0L;
        }
        String raw = parts[index].trim();
        if (raw.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
