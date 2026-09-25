## OnlineTimeStats v1.0.0

服务端玩家在线时长统计插件，适用于 **Paper / Spigot 1.8 ~ 1.12.2**。
按服务器 tick 累计每位玩家的在线时长，把时长显示在玩家名字下方与 Tab 列表，
数据长期落盘，并提供查询、排行、离线查询与管理指令。

### 功能

- 按服务器 tick 累计在线时长（20 tick = 1 秒），总量以 tick 为唯一真实来源，不存在进位丢秒；
- 小时数显示在玩家名字下方与 Tab 列表；
- 数据长期落盘（`data/playtime.tsv`，原子写入），重启不丢；
- 离线玩家按名字查询、在线时长排行榜、全服概览；
- 管理指令：`set` / `add` / `reset` / `reload` / `status`；
- 可选挂机判定（默认关闭）；
- 记分板显示位置/单位/显示名与全部文案可在 `config.yml` 中配置。

### 兼容

- Paper / Spigot 1.8 ~ 1.12.2，Java 8 字节码，无第三方依赖；
- 已在 Paper 1.12.2 + JRE 8 上实测：53 项核心逻辑测试与 38 项真机测试（含真实客户端进服）全部通过；
- 1.13 及以上未测试。

### 安装

把 `OnlineTimeStats-1.0.0.jar` 放进服务端 `plugins/`，启动后修改 `config.yml` 并执行 `/playtime reload`。

### 许可

MIT License，见仓库根目录 `LICENSE`。
