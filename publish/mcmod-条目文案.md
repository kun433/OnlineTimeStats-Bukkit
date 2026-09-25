# MC 百科条目文案（可直接粘贴）

> 说明：MC 百科的条目编辑是「勾选编辑规则 → 富文本编辑器 → 提交审核」的流程，
> 下面是按它的字段习惯整理好的文案（独立条目）。

## 一、条目基本字段

| 字段 | 建议填写值 |
| --- | --- |
| 名称 | 在线时间统计 · OnlineTimeStats |
| 别名 | OnlineTimeStats 插件 / 在线时长统计插件 |
| 分类 | 服务端插件（若百科没有该分类，则归入 辅助 / 实用） |
| 支持平台 | JAVA版（服务端） |
| 运作方式 | 服务端插件（Bukkit / Spigot / Paper） |
| 运行环境 | 服务端需装（客户端无需安装任何东西） |
| 支持的 MC 版本 | 1.8、1.8.9、1.9.4、1.10.2、1.11.2、1.12、1.12.1、1.12.2 |
| 模组标签 | 原版增强、显示、统计、数据、在线、玩家、时间 |
| 相关链接 | GitHub 仓库地址 |
| 作者 | 填写发布者本人的 MC 百科 ID |

## 二、正文（粘贴到编辑器）

```text
【这是什么】

这是一个服务端在线时长统计插件，适用于 Paper / Spigot 1.8 ~ 1.12.2。

它按服务器 tick 累计每位玩家的在线时长（20 tick = 1 秒、60 秒 = 1 分钟、60 分钟 = 1 小时），
并把小时数显示在玩家名字下方与 Tab 列表；数据长期落盘，重启不丢，
另外提供查询、排行榜、离线玩家查询与管理指令。

【功能】

- 按服务器 tick 累计在线时长，总量以 tick 为唯一真实来源，秒/分/时都是换算结果，不存在进位丢秒；
- 时长显示在玩家名字下方与 Tab 列表（默认按小时，可在配置里改成分钟或秒）；
- 数据长期落盘（plugins/OnlineTimeStats/data/playtime.tsv），可直接备份或用脚本处理；
- 离线玩家按名字查询；
- 在线时长排行榜、全服概览；
- 管理指令：设置 / 增加 / 清除时长、重载配置、查看运行状态；
- 可选挂机判定（默认关闭）：长时间不操作的玩家不再累计时长；
- 记分板显示位置、单位、显示名、消息文案全部可在 config.yml 里改。

【安装】

1. 把 OnlineTimeStats-1.0.0.jar 放进服务端的 plugins/ 目录（服务端需要 Paper / Spigot / CraftBukkit 1.8 ~ 1.12.2）；
2. 启动服务器，插件会生成 plugins/OnlineTimeStats/config.yml；
3. 按需修改配置后执行 /playtime reload。

不需要任何前置插件，jar 里也没有打包第三方库。

【指令】

/playtime                 查看自己的总时长、本次在线、首次加入、排名
/playtime <玩家>          查看指定玩家（支持离线玩家）
/playtime top [数量]      在线时长排行榜（默认 10 条）
/playtime stats           全服概览（记录人数、累计时长、人均、最长）
/playtime status          运行状态：计时模式、挂机开关、数据文件、记分板实际显示位置
/playtime set <玩家> <时长>   直接设置总时长
/playtime add <玩家> <时长>   增加时长
/playtime reset <玩家|all>    清除记录（all 需要再加 confirm）
/playtime reload          重载配置
/playtime help            帮助

别名：/pt、/pts、/onlinetime、/onlinetimestats
权限：onlinetimestats.use（查询自己）、onlinetimestats.others（查询他人）、
      onlinetimestats.top（排行榜与概览）、onlinetimestats.admin（管理指令，默认 OP）

时长写法：2h30m、90m、1d2h、2小时30分、3600（纯数字按秒）

【配置文件要点】

counting.mode        TICK（每个服务器 tick 记 1）或 WALL（每真实秒记 20）
storage.file         数据文件位置（默认 data/playtime.tsv）
save-interval-seconds 自动写盘间隔
afk.enabled          是否启用挂机判定（默认关闭）
afk.threshold-seconds 多久没操作算挂机（默认 300 秒）
scoreboard.objective 记分板目标名（默认 playtime）
scoreboard.unit      分数单位：HOURS / MINUTES / SECONDS
scoreboard.display-slots 显示位置：below_name（名字下方）/ list（Tab 列表）/ sidebar
scoreboard.display-name  显示名（默认 [小时]）
scoreboard.extra-objectives 是否额外创建 ots_tick / ots_seconds / ots_minutes / ots_hours / ots_total
messages.*           全部提示文案，支持 & 颜色代码

关于记分板的一个技术说明：Bukkit/Spigot 的 API 限制一个目标只能占一个显示位置，
所以插件会在第一个位置使用 playtime，其余位置自动创建数值完全相同的镜像目标（如 playtime_list），
玩家看到的显示效果与配置一致。

【数据与显示口径】

- 累计时长以 tick 为唯一真实来源，秒/分/小时都是换算结果，不存在进位丢秒的问题；
- 记分板分数是整数，默认按小时显示，2 小时 30 分会显示为 2；需要更细可以改成 MINUTES 或 SECONDS；
- 玩家退出、插件卸载、服务器关服都会写盘，另外按 save-interval-seconds 定期自动写盘；
- 写盘是原子替换（先写 .tmp 再改名），写盘在异步线程完成，不影响主线程性能。

【开源与许可】

- 源码与下载：GitHub 仓库地址（见相关链接）
- 许可：MIT License
```

## 三、发布时需要人工完成的动作

1. 上传封面图（建议：插件名 + 支持版本 1.8–1.12.2）。
2. 在条目里放 1–3 张使用截图（聊天栏查询结果、名字下方显示、Tab 列表显示）。
3. 上传下载文件（jar，或指向 GitHub Release 的链接）。
4. 提交后进入 MC 百科审核队列，审核通过才会公开显示。
