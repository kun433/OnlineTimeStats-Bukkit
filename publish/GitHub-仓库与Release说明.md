# GitHub 发布信息（仓库 / Release）

## 一、仓库字段

| 字段 | 取值 |
| --- | --- |
| Owner | kun433 |
| Repository name | `OnlineTimeStats-Bukkit` |
| Description | 统计并显示玩家在线时长，适用于 Paper/Spigot 1.8 ~ 1.12.2 |
| Visibility | Public |
| Topics | `minecraft`, `minecraft-plugin`, `bukkit`, `spigot`, `paper`, `playtime`, `playtime-statistics`, `legacy`, `1-12-2` |
| License | MIT |
| Default branch | main |

## 二、仓库里放什么

```
README.md                 # 功能、指令、配置、限制说明
LICENSE                   # MIT
build.ps1                 # 编译打包脚本（JDK 11 --release 8）
.gitignore
resources/plugin.yml
resources/config.yml
src/com/onlinetimestats/… # 插件源码（11 个类，core/ 不依赖 Bukkit）
tests/                    # 53 项核心测试 + 真机冒烟测试脚本（不含 node_modules / .testserver）
publish/                  # 发布用文案（本地材料，随仓库保留）
lib/                      # 仅编译用的 API jar（Paper 1.12.2、bungeecord-chat）
dist/OnlineTimeStats-1.0.0.jar
```

不上传：`.testserver/`（本地测试服）、`tests/bot/node_modules/`、`build/`、`tests/out/`。

## 三、Release 信息（tag v1.0.0）

- Tag：`v1.0.0`
- Title：`OnlineTimeStats v1.0.0 —— Paper/Spigot 1.8 ~ 1.12.2 玩家在线时长统计`
- 附件（Assets）：
  - `OnlineTimeStats-1.0.0.jar`
  - `OnlineTimeStats-1.0.0.zip`（jar + README + LICENSE）
- Release notes：见 `GitHub-Release-说明.md` 或 Release 正文

## 四、发布方式

推送走本机代理 `http://127.0.0.1:7890`（`github.com:443` 直连不可达，`api.github.com` 可直连）；
Release 与附件通过 GitHub REST API 创建和上传。
