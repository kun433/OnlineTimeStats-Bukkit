# OnlineTimeStats — Player Online-Time Statistics for Paper/Spigot 1.8–1.12.2

[English](README.md) | [中文](README.zh-CN.md)

A server-side plugin for **Paper / Spigot 1.8–1.12.2**: it accumulates each player's online time by
server tick, displays it **below the player's name** and in the **tab list**, persists it to disk,
and provides lookup, ranking, offline lookup and admin commands.

- The server tick is the single source of truth for timing: 20 ticks = 1 second, 60 s = 1 minute, 60 min = 1 hour.
- Time is written both to the scoreboard (below name + tab list) and to a plain-text data file, so it survives restarts.
- Offline players can be looked up by name; an online-time leaderboard and a server-wide summary are included.
- No third-party dependencies, and no external libraries are bundled into the jar.

---

## 1. Timing and display model

The plugin's timing model has exactly one spine: **every server tick adds 1 to the online player's
accumulated tick count**. Seconds, minutes and hours are derived at display time by dividing by
20 / 1200 / 72000.

The practical benefit is that nothing is lost to rounding. If seconds, minutes and hours were kept as
separate counters, the remainders would easily be overwritten on each carry; storing only the total
tick count avoids that entirely and the total is always accurate.

The corresponding scoreboard behaviour:

| Item | Description |
| --- | --- |
| Main objective | The objective name from the `objective` setting, `playtime` by default |
| Display slots | `display-slots`, by default `below_name` (below the player's name) + `list` (tab list) |
| Score unit | `unit`, `HOURS` by default (i.e. total hours); `MINUTES` / `SECONDS` also available |
| Mirror objective | The second display slot automatically uses a mirror objective with identical values (`playtime_list` by default); see section 7 for why |
| Extra objectives | Optionally enable `extra-objectives` to also create `ots_tick` / `ots_seconds` / `ots_minutes` / `ots_hours` / `ots_total` for command blocks or other plugins |
| Storage | Both the server's own scoreboard save and the plugin's own `data/playtime.tsv` |

---

## 2. Compatibility

| Item | Description |
| --- | --- |
| Target platform | Paper / Spigot **1.8 – 1.12.2** |
| Bytecode | Java 8 (`--release 8`); only Bukkit APIs that exist since 1.8 are used |
| Third-party dependencies | None (no Vault, no PlaceholderAPI, and no third-party library is bundled in the jar) |
| Verified on | Paper 1.12.2 + JRE 8: loading, timing, commands, scoreboard, AFK detection, persistence and reset all pass |
| 1.13 and above | Not tested. The plugin uses no 1.13+-only API and declares `api-version: 1.12`, so it should load in theory |

---

## 3. Installation

1. Put `dist/OnlineTimeStats-1.0.0.jar` into your server's `plugins/` directory.
2. Start the server; the plugin generates `plugins/OnlineTimeStats/config.yml`.
3. Adjust the configuration and run `/playtime reload` (changing `counting.mode` and similar is safer with a restart).

---

## 4. Commands and permissions

Main command `/playtime`, with aliases `/pt`, `/pts`, `/onlinetime`, `/onlinetimestats`.

| Command | Description | Permission |
| --- | --- | --- |
| `/playtime` | Your own total time, current session, first join and rank | `onlinetimestats.use` (everyone by default) |
| `/playtime <player>` | A specific player, **including offline players** (looked up by name index) | `onlinetimestats.others` (everyone by default) |
| `/playtime top [count]` | Online-time leaderboard (10 by default, 50 max) | `onlinetimestats.top` (everyone by default) |
| `/playtime stats` | Server-wide summary: tracked players, accumulated time, average, longest | `onlinetimestats.top` |
| `/playtime check <player>` | Equivalent to `/playtime <player>`, convenient for scripts | `onlinetimestats.others` |
| `/playtime status` | Runtime status: counting mode, AFK switch, data file, actual scoreboard slots | `onlinetimestats.admin` (OP by default) |
| `/playtime set <player> <duration>` | Set the total time directly | `onlinetimestats.admin` |
| `/playtime add <player> <duration>` | Add to the total time | `onlinetimestats.admin` |
| `/playtime reset <player\|all>` | Clear records; `all` also requires `confirm` | `onlinetimestats.admin` |
| `/playtime reload` | Reload the configuration (rebuilds the scoreboard, restarts the timers) | `onlinetimestats.admin` |
| `/playtime help` | Command help | Everyone |

**Duration syntax** (used by `set` / `add`):

```
2h30m        → 2 hours 30 minutes
90m          → 90 minutes
1d2h         → 1 day 2 hours
2小时30分     → Chinese units are recognised as well
3600         → a bare number is treated as seconds
```

Example lookup output:

```
===== TestBot's online time =====
Total: 3h12m45s (0 days 3 hours 12 minutes / 45 seconds)
Current session: 12m30s
First join: 2026-09-20 10:00:00
Last join: 2026-09-25 22:30:00
Last quit: 2026-09-25 22:42:30
Logins: 12
Status: online
Server rank: #3 / 128
```

---

## 5. Configuration (`config.yml`)

```yaml
counting:
  mode: TICK            # TICK = 1 per server tick; WALL = 20 per real second
  count-on-shutdown: true

storage:
  file: data/playtime.tsv        # data file, relative to plugins/OnlineTimeStats/
  save-interval-seconds: 120     # autosave interval
  save-on-quit: true             # extra save when a player quits

afk:                             # optional AFK detection, off by default
  enabled: false
  threshold-seconds: 300         # idle seconds before a player counts as AFK
  exclude-from-total: true       # do not count AFK time towards the total
  notify: true                   # notify players on entering/leaving AFK

scoreboard:
  enabled: true
  objective: playtime
  display-name: "&a[小时]"
  unit: HOURS                    # HOURS / MINUTES / SECONDS
  display-slots: [below_name, list]
  update-seconds: 5
  extra-objectives: false        # also create ots_tick / ots_seconds / ots_minutes / ots_hours / ots_total

options:
  join-quit-message: false       # show your own accumulated time when you join
  show-session: true             # include "current session" in lookups

messages: …                      # every message is configurable, & colour codes and placeholders supported
```

Notes:

- `unit` is an **integer** scoreboard score: with the default `HOURS`, `2h30m` is displayed as `2`. For finer values switch to `MINUTES` or `SECONDS` (and reduce `update-seconds` to 1).
- Values starting with `&` such as `display-name` and `objective` **must be quoted**, otherwise YAML treats `&` as an anchor.
- Available placeholders in `messages`: `{player} {time} {days} {hours} {minutes} {seconds} {session} {afk} {first_join} {last_join} {last_quit} {sessions} {rank} {total_players} {online} {total_time} {total_hours} {average} {limit} {delta} {before} {input}`.
- Missing keys fall back to built-in defaults, so an older `config.yml` copied over a newer build will not break startup.

---

## 6. Counting semantics and when data is written

1. A session starts when a player joins; from then on the player's total gains +1 **every server tick** (TICK mode).
2. Ticks are the single source of truth; seconds/minutes/hours are derived, so no seconds are lost to carrying.
3. Data is flushed immediately on quit, plugin disable and server shutdown, plus once every `save-interval-seconds`.
   A server crash loses at most one save interval. Shortening the interval only increases write frequency and does not
   affect the main thread's performance (writes happen on an async thread; the main thread only takes an in-memory snapshot).
4. `WALL` versus `TICK`: when the server lags (TPS < 20), TICK under-counts compared to real time, WALL does not.

---

## 7. Scoreboard

- By default the plugin creates `playtime` (display name `[小时]`) on the main scoreboard and shows it in `below_name` and `list`.
- ⚠️ **A CraftBukkit/Spigot API limitation**: `Objective#setDisplaySlot()` first clears the objective from all other slots
  (the 1.12.2 `CraftObjective` bytecode contains a loop clearing slots 0..2), which means **an objective can occupy only one display slot**.
  The plugin therefore keeps the first slot for the configured `objective` and **automatically creates a mirror objective with identical values**
  (`playtime_list` by default) for the remaining slots, so players see the expected result.
  `/playtime status` reports the actual state:

  ```text
  ===== Online time statistics status =====
  Counting mode: TICK | AFK detection: off
  Tracked players: 128 | active sessions: 12 | server total: 4210h33m
  Data file: .../plugins/OnlineTimeStats/data/playtime.tsv
  Scoreboard: playtime (unit HOURS, display name [小时])
  Display slots: below_name=playtime, list=playtime_list, sidebar=none
  Extra scoreboard objectives: off
  ```

- Offline players' scores are not refreshed in real time (only online players are written on each refresh), but offline scores
  are not visible anyway and are filled in as soon as the player rejoins.
- If you also need the fine-grained objectives (for command blocks or other plugins), enable `extra-objectives`:
  the plugin additionally creates `ots_tick` (tick%20), `ots_seconds` (second%60), `ots_minutes` (minute%60), `ots_hours` (total hours) and `ots_total` (total minutes).
- On plugin disable it releases the display slots it occupies, so no dead objective keeps showing stale values (other plugins' sidebars are unaffected).

---

## 8. Data file

`plugins/OnlineTimeStats/data/playtime.tsv`, UTF-8, tab-separated, one player per line:

```text
# OnlineTimeStats data v1
30fecbe1-2271-3418-8553-d3ded0e95f56	TestBot	216320	0	1790346612171	1790346612171	1790346720635	1
```

| Column | Meaning |
| --- | --- |
| 1 | UUID |
| 2 | Last seen player name |
| 3 | Accumulated online ticks (÷20 = seconds) |
| 4 | AFK ticks (only grows when AFK detection is enabled and AFK time is excluded from the total) |
| 5 / 6 / 7 | First join / last join / last quit timestamps (milliseconds) |
| 8 | Login count |

- Plain text rather than YAML keeps the read/write logic completely independent of Bukkit, which makes it unit-testable
  outside a server and easy to process or back up with scripts (just copy the one file).
- Writes are **atomic replacements**: a `.tmp` file is written and then renamed, so killing the process mid-write cannot
  leave a half-written file.
- Unparsable lines are skipped; the console reports how many lines were skipped at startup, so one bad line cannot prevent the file from loading.

---

## 9. Directory layout

```text
OnlineTimeStats/
├── build.ps1                     # compile + package (only needs javac/jar from JDK 11)
├── dist/OnlineTimeStats-1.0.0.jar
├── lib/                          # compile-time API jars, not bundled into the plugin
│   ├── paper-api-1.12.2.jar
│   └── bungeecord-chat.jar
├── resources/                    # plugin.yml and config.yml packaged into the jar
├── src/com/onlinetimestats/
│   ├── OnlineTimeStats.java      # main class: task scheduling, async saving, reload
│   ├── PluginConfig.java         # configuration parsing
│   ├── Messages.java             # messages and placeholders
│   ├── TimeTracker.java          # timing core: sessions, tick accumulation, AFK
│   ├── ScoreboardService.java    # scoreboard: objectives, slots, mirror objectives, self-check
│   ├── PlayerListener.java       # join/quit and "meaningful activity" events
│   ├── StatsCommand.java         # commands and tab completion
│   └── core/                     # Bukkit-independent, testable offline
│       ├── TimeEntry.java
│       ├── TimeStore.java
│       ├── SessionState.java
│       └── TimeUtil.java
└── tests/
    ├── CoreTest.java             # 53 assertions: formatting, parsing, I/O, ranking, timing, AFK
    ├── run-tests.ps1
    ├── run-smoke-test.ps1
    └── bot/smoke-test.js         # real-server smoke test (starts Paper 1.12.2 + a bot joins)
```

---

## 10. Building and testing

```powershell
cd OnlineTimeStats
.\build.ps1              # requires JDK 11 (--release 8); output lands in dist/
```

```powershell
# 1) Core logic tests, no server required
cd tests
.\run-tests.ps1          # report is written to tests/out/core-test-report.txt
```

```powershell
# 2) Real-server smoke test: starts a Paper 1.12.2 test server and joins with a mineflayer bot
#    Requires: node (uses the mineflayer installed under tests/bot), JRE 8, .testserver/1122/server.jar
cd tests
.\run-smoke-test.ps1     # report is written to tests/out/smoke-report.txt
```

What the smoke test actually covers (38 checks passed on 2026-09-26):

- the plugin is loaded/enabled, the startup log has no errors, and commands work from the console;
- after the bot joins, time accumulates per second (70 s online → more than 1 minute), leaderboard and stats are correct;
- after `set 2h30m`, both the main and the mirror objective show `2` (hours);
- a quitted player can still be looked up and `add 30m` works (2h30m → 3 hours), offline status is reported correctly;
- `playtime.tsv` on disk holds the correct login count and first-join time;
- after `reload`, the scoreboard slots and the online players' scores are still correct;
- with AFK detection on (15 s threshold), standing still for 60 s accumulates only 13 s, with 41 s recorded as AFK (the bot moved 0.00 blocks);
- with `extra-objectives` on, all five objectives `ots_tick / ots_seconds / ots_minutes / ots_hours / ots_total`
  are created and four of them actually receive scores for online players;
- `reset all confirm` clears the records and the data file;
- the scoreboard packets the bot receives confirm that `playtime` is on `belowName` (slot 2) and `playtime_list` is in the tab list (slot 0), **including for players who join later**.

> The test script first sets the test world to daytime + peaceful + no mob spawning. Otherwise night-time mobs push the
> standing bot around, and the plugin correctly treats that as "activity", which would invalidate the AFK assertions.

---

## 11. Known limitations and possible extensions

- Only the **cumulative total** is tracked; there is no per-day or per-week breakdown. It could be added on top of the existing event model.
- AFK detection is off by default and keys off the timestamps of "move/chat/command/interact/drop item/sneak" actions.
  Being pushed by water or knocked back by a mob also counts as activity (in testing, night-time mobs kept the bot marked as active),
  which is good enough for most situations but is not a precise anti-AFK measure.
- Scoreboard scores are integers, so hours are truncated.
- Not tested on 1.13+ servers (the target platform is 1.8–1.12.2).
- Possible future extensions: PlaceholderAPI placeholders, daily and weekly statistics, MySQL/SQLite storage, bStats metrics,
  finer subcommands such as `/playtime me`.

---

## 12. License

MIT License, see `LICENSE`. Please report issues in this repository's Issues.

## 13. Changelog

### 1.0.0 (2026-09-26)

- First release: TICK/WALL counting, persistence, scoreboard (belowName + tab list), lookup/ranking/stats/admin commands, AFK detection, `/playtime status` self-check.
- Worked around CraftBukkit's "one objective can occupy one display slot" limitation by using mirror objectives to display the value in two places at once.
- Admin commands can add time for offline players the server knows about but the plugin has not recorded yet.
