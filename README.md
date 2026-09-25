# OnlineTimeStats —— 服务端玩家在线时长统计（1.8 ~ 1.12.2）

面向 **Paper / Spigot 1.8 ~ 1.12.2** 的服务端插件：按服务器 tick 累计每位玩家的在线时长，
把时长显示在**玩家名字下方**与 **Tab 列表**，数据长期落盘，并提供查询、排行、离线查询与管理指令。

- 计时以服务器 tick 为唯一真实来源：20 tick = 1 秒、60 秒 = 1 分钟、60 分钟 = 1 小时。
- 时长同时写入记分板（名字下方 + Tab 列表）与纯文本数据文件，重启不丢。
- 离线玩家可按名字查询，支持在线时长排行榜与全服概览。
- 无第三方依赖，jar 内不打包任何外部库。

---

## 一、计时与显示模型

插件的计时模型只有一条主线：**每个服务器 tick 给在线玩家的累计 tick 数 +1**，
秒、分钟、小时都是显示时按 20 / 1200 / 72000 换算出来的结果。

这样做的直接好处是不丢精度：如果把秒、分、时分别存成独立计数器，进位时的余数很容易被覆盖掉；
只存总 tick 数则不会出现这类问题，总量始终准确。

对应的记分板行为：

| 项目 | 说明 |
| --- | --- |
| 主目标 | `objective` 配置项指定的目标名，默认 `playtime` |
| 显示位置 | `display-slots`，默认 `below_name`（名字下方）+ `list`（Tab 列表） |
| 分数单位 | `unit`，默认 `HOURS`（相当于总小时数）；可选 `MINUTES` / `SECONDS` |
| 镜像目标 | 第二个显示位置自动使用数值完全一致的镜像目标（默认 `playtime_list`），原因见第七节 |
| 额外目标 | 可选开启 `extra-objectives`，额外创建 `ots_tick` / `ots_seconds` / `ots_minutes` / `ots_hours` / `ots_total`，供命令方块或其它插件读取 |
| 存档 | 服务器自身的记分板存档 + 插件自己的 `data/playtime.tsv` 双重保存 |

---

## 二、兼容性

| 项目 | 说明 |
| --- | --- |
| 目标平台 | Paper / Spigot **1.8 ~ 1.12.2** |
| 字节码 | Java 8（`--release 8`），只使用 1.8 起就存在的 Bukkit API |
| 第三方依赖 | 无（不需要 Vault、PlaceholderAPI 等，jar 内也没有打包任何第三方库） |
| 已实测 | Paper 1.12.2 + JRE 8：加载、计时、指令、记分板、挂机判定、落盘、重置全部通过 |
| 1.13 及以上 | 未测试。插件没有用任何 1.13+ 专属 API，`plugin.yml` 声明了 `api-version: 1.12`，理论上可加载 |

---

## 三、安装

1. 把 `dist/OnlineTimeStats-1.0.0.jar` 放进服务器的 `plugins/` 目录。
2. 启动服务器，插件会生成 `plugins/OnlineTimeStats/config.yml`。
3. 按需修改配置后执行 `/playtime reload`（改 `counting.mode` 等需要重启更稳妥）。

---

## 四、指令与权限

主指令 `/playtime`，别名 `/pt`、`/pts`、`/onlinetime`、`/onlinetimestats`。

| 指令 | 说明 | 权限 |
| --- | --- | --- |
| `/playtime` | 查看自己的总时长、本次在线、首次加入、排名 | `onlinetimestats.use`（默认所有人） |
| `/playtime <玩家>` | 查看指定玩家，**支持离线玩家**（按名字索引查） | `onlinetimestats.others`（默认所有人） |
| `/playtime top [数量]` | 在线时长排行榜（默认 10，最多 50） | `onlinetimestats.top`（默认所有人） |
| `/playtime stats` | 全服概览：记录人数、累计时长、人均、最长 | `onlinetimestats.top` |
| `/playtime check <玩家>` | 与 `/playtime <玩家>` 等价，便于脚本调用 | `onlinetimestats.others` |
| `/playtime status` | 运行状态：计时模式、挂机开关、数据文件、记分板实际显示位置 | `onlinetimestats.admin`（默认 OP） |
| `/playtime set <玩家> <时长>` | 直接设置总时长 | `onlinetimestats.admin` |
| `/playtime add <玩家> <时长>` | 增加时长 | `onlinetimestats.admin` |
| `/playtime reset <玩家\|all>` | 清除记录；`all` 需要再加 `confirm` | `onlinetimestats.admin` |
| `/playtime reload` | 重载配置（含重建记分板、重启定时任务） | `onlinetimestats.admin` |
| `/playtime help` | 指令帮助 | 所有人 |

**时长写法**（`set` / `add` 用）：

```
2h30m        → 2 小时 30 分
90m          → 90 分钟
1d2h         → 1 天 2 小时
2小时30分     → 中文单位同样识别
3600         → 纯数字按「秒」处理
```

查询结果示例：

```
===== TestBot 的在线时间 =====
总时长: 3小时12分钟45秒 (共 0 天 3 小时 12 分钟 / 45 秒)
本次在线: 12分钟30秒
首次加入: 2026-09-20 10:00:00
最近加入: 2026-09-25 22:30:00
最近退出: 2026-09-25 22:42:30
登录次数: 12 次
当前状态: 在线
全服排名: #3 / 128
```

---

## 五、配置说明（`config.yml`）

```yaml
counting:
  mode: TICK            # TICK=每个服务器 tick 记 1；WALL=每真实秒记 20
  count-on-shutdown: true

storage:
  file: data/playtime.tsv        # 数据文件，相对 plugins/OnlineTimeStats/
  save-interval-seconds: 120     # 自动写盘间隔
  save-on-quit: true             # 玩家退出时补一次写盘

afk:                             # 可选挂机判定，默认关闭
  enabled: false
  threshold-seconds: 300         # 多少秒没有操作算挂机
  exclude-from-total: true       # 挂机期间不计入总时长
  notify: true                   # 进出挂机时提示玩家

scoreboard:
  enabled: true
  objective: playtime
  display-name: "&a[小时]"
  unit: HOURS                    # HOURS / MINUTES / SECONDS
  display-slots: [below_name, list]
  update-seconds: 5
  extra-objectives: false        # 是否额外创建 ots_tick / ots_seconds / ots_minutes / ots_hours / ots_total

options:
  join-quit-message: false       # 进服时提示自己的累计时长
  show-session: true             # 查询时显示“本次在线”

messages: …                      # 全部文案可改，支持 & 颜色代码与占位符
```

要点：

- `unit` 是**整数**记分板分数：默认按小时，`2h30m` 会显示成 `2`。要看到更细的数值就改成 `MINUTES` 或 `SECONDS`（同时把 `update-seconds` 调小到 1）。
- `display-name`、`objective` 等以 `&` 开头的值**必须带引号**，否则 YAML 会把它当成锚点。
- `messages` 里可用的占位符：`{player} {time} {days} {hours} {minutes} {seconds} {session} {afk} {first_join} {last_join} {last_quit} {sessions} {rank} {total_players} {online} {total_time} {total_hours} {average} {limit} {delta} {before} {input}`。
- 配置缺项会退回内置默认值，所以用旧版 config.yml 覆盖新版本也不会启动失败。

---

## 六、计入口径与写盘时机

1. 玩家进入服务器时建立会话，之后**每个服务器 tick**给该玩家的总时长 +1（TICK 模式）。
2. 时长以 tick 为唯一真实来源，秒/分/小时都是换算结果，不会出现进位丢秒的问题。
3. 玩家退出、插件卸载、服务器关服都会立即写盘；此外每 `save-interval-seconds` 秒自动写一次。
   服务器崩溃时最多丢一个写盘间隔的时长；把间隔调小只会增加写盘频率，不影响性能主线程（写盘在异步线程完成，主线程只做内存快照）。
4. `WALL` 模式与 `TICK` 模式的差别：服务器掉帧（TPS < 20）时，TICK 模式会比真实时间少记，WALL 模式不会。

---

## 七、记分板

- 默认在**主记分板**上创建 `playtime`（显示名 `[小时]`），挂在 `below_name`（名字下方）与 `list`（Tab 列表）。
- ⚠️ **CraftBukkit/Spigot 的一个 API 限制**：`Objective#setDisplaySlot()` 会先把该目标从其它槽位清掉（1.12.2 的 `CraftObjective` 字节码里就有一个遍历 0..2 的清槽循环），也就是**一个目标只能占据一个显示位置**。
  因此插件会把第一个位置留给配置的 `objective`，其余位置**自动创建数值完全一致的镜像目标**（默认 `playtime_list`）。玩家看到的显示效果与预期一致。
  用 `/playtime status` 可以读回实际状态：

  ```text
  ===== 在线时间统计状态 =====
  计时模式: TICK | 挂机判定: 关
  记录玩家: 128 人 | 在线会话: 12 | 全服累计: 4210小时33分钟
  数据文件: .../plugins/OnlineTimeStats/data/playtime.tsv
  记分板: playtime (单位 HOURS, 显示名 [小时])
  显示位置: below_name=playtime, list=playtime_list, sidebar=无
  额外记分板目标: 关
  ```

- 离线玩家的分数不会实时刷新（只有在线玩家每次刷新时写入），但离线玩家的分数本来就看不见，重新进服会立即补上。
- 若还需要细粒度的目标（供命令方块或其它插件读取），把 `extra-objectives` 打开：
  会额外创建 `ots_tick`（tick%20）、`ots_seconds`（秒%60）、`ots_minutes`（分%60）、`ots_hours`（总小时）、`ots_total`（总分钟）。
- 卸载插件时会主动放开自己占用的显示位置，不会留下一个显示着旧数值的死目标（其它插件的 sidebar 不受影响）。

---

## 八、数据文件

`plugins/OnlineTimeStats/data/playtime.tsv`，UTF-8、制表符分隔，一行一名玩家：

```text
# OnlineTimeStats data v1
30fecbe1-2271-3418-8553-d3ded0e95f56	TestBot	216320	0	1790346612171	1790346612171	1790346720635	1
```

| 列 | 含义 |
| --- | --- |
| 1 | UUID |
| 2 | 最后一次见到的玩家名 |
| 3 | 累计在线 tick（÷20 = 秒） |
| 4 | 挂机 tick（仅在开启挂机判定且不计入总时长时增长） |
| 5 / 6 / 7 | 首次加入 / 最近加入 / 最近退出的时间戳（毫秒） |
| 8 | 登录次数 |

- 选纯文本而不是 YAML，是为了让读写逻辑完全不依赖 Bukkit，从而能脱离服务器做单元测试，也方便你用脚本处理或备份（直接复制这一个文件即可）。
- 写盘是**原子替换**：先写 `.tmp` 再改名，进程在写盘中途被杀不会留下半截文件。
- 无法解析的行会被跳过，启动时在控制台提示跳过行数，不会因为一行坏数据导致整个文件读不进来。

---

## 九、目录结构

```text
OnlineTimeStats/
├── build.ps1                     # 编译 + 打包（只需 JDK 11 的 javac/jar）
├── dist/OnlineTimeStats-1.0.0.jar
├── lib/                          # 仅编译用的 API jar，不会打进插件
│   ├── paper-api-1.12.2.jar
│   └── bungeecord-chat.jar
├── resources/                    # 打进 jar 的 plugin.yml 与 config.yml
├── src/com/onlinetimestats/
│   ├── OnlineTimeStats.java      # 插件主类：任务调度、异步写盘、重载
│   ├── PluginConfig.java         # 配置解析
│   ├── Messages.java             # 文案与占位符
│   ├── TimeTracker.java          # 计时核心：会话、tick 累加、挂机
│   ├── ScoreboardService.java    # 记分板：目标、显示位置、镜像目标、自检
│   ├── PlayerListener.java       # 加入/退出与“有效操作”事件
│   ├── StatsCommand.java         # 指令与 Tab 补全
│   └── core/                     # 不依赖 Bukkit，可离线测试
│       ├── TimeEntry.java
│       ├── TimeStore.java
│       ├── SessionState.java
│       └── TimeUtil.java
└── tests/
    ├── CoreTest.java             # 53 项断言：格式化、解析、读写、排行、计时、挂机
    ├── run-tests.ps1
    ├── run-smoke-test.ps1
    └── bot/smoke-test.js         # 真机冒烟测试（启动 Paper 1.12.2 + 机器人进服）
```

---

## 十、自己编译与测试

```powershell
cd OnlineTimeStats
.\build.ps1              # 需要 JDK 11（--release 8）；产物在 dist/
```

```powershell
# 1) 脱离服务器的核心逻辑测试（不需要服务器）
cd tests
.\run-tests.ps1          # 报告写在 tests/out/core-test-report.txt
```

```powershell
# 2) 真机冒烟测试：启动一个 Paper 1.12.2 测试服，用 mineflayer 机器人进服验证
#    前置：node（会自动使用 tests/bot 下已安装的 mineflayer）、JRE 8、.testserver/1122/server.jar
cd tests
.\run-smoke-test.ps1     # 报告写在 tests/out/smoke-report.txt
```

冒烟测试实际覆盖的内容（2026-09-26 实测 38 项全通过）：

- 插件被加载/启用、启动日志无异常、控制台可执行指令；
- 机器人进服后时长按秒累计（在线 70 秒 → 1 分钟以上）、排行榜与 stats 正确；
- `set 2h30m` 后主目标与镜像目标的分数都变成 `2`（小时）；
- 玩家退出后仍可查询、可 `add 30m`（2h30m → 3 小时）、离线状态显示正确；
- `playtime.tsv` 落盘内容与登录次数、首次加入时间正确；
- `reload` 后记分板显示位置与在线玩家分数仍然正确；
- 打开挂机判定（阈值 15 秒）后站着不动 60 秒，只累计 13 秒，挂机时长 41 秒（机器人位移 0.00 格）；
- 打开 `extra-objectives` 后 `ots_tick / ots_seconds / ots_minutes / ots_hours / ots_total`
  五个目标都被创建，并且在线玩家的分数确实写入了其中的四个；
- `reset all confirm` 清空记录且数据文件同步清空；
- 机器人收到的记分板网络包确认 `playtime` 挂在 `belowName`（位置 2）、`playtime_list` 挂在 Tab 列表（位置 0），**后来进服的玩家也能收到**。

> 测试脚本会先把测试世界设成白天 + 和平 + 关闭刷怪。否则夜晚的怪物会把站着的机器人推来推去，
> 那种情况下插件把机器人判为“有操作”是正确行为，会让挂机断言失去意义。

---

## 十一、已知限制与可扩展方向

- 只统计**累计总时长**，不含「每日/每周时长拆分」。需要的话可以基于现有事件模型加每日聚合。
- 挂机判定默认关闭，依据「移动/聊天/指令/交互/丢弃物品/潜行」这些操作的时间戳；被水流推动、被怪物击退
  也算「有操作」（实测中夜晚怪物推挤会让机器人一直被判定为活跃），对绝大多数场景够用，但不是精确的反挂机方案。
- 记分板分数是整数，小时数会向下取整。
- 未在 1.13+ 服务器上测试（目标平台是 1.8 ~ 1.12.2）。
- 可选的后续扩展：PlaceholderAPI 占位符、每日时长与周报、MySQL/SQLite 存储、bStats 统计、`/playtime me` 之类的更细指令。

---

## 十二、许可

MIT License，见 `LICENSE`。相关的问题请提到本仓库的 Issues。

## 十三、变更记录

### 1.0.0（2026-09-26）

- 首次发布：TICK/WALL 计时、持久化、记分板（belowName + Tab 列表）、查询/排行/统计/管理指令、挂机判定、`/playtime status` 自检。
- 针对 CraftBukkit「一个目标只能占一个显示位置」的限制，用镜像目标实现了同时显示在两个位置。
- 管理指令支持对「本插件尚未记录但服务器认识」的离线玩家补时长。
