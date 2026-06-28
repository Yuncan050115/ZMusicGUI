# ZMusicGUI

[![Version](https://img.shields.io/badge/version-3.0.0-blue.svg)](https://github.com/Yuncan050115/ZMusicGUI)
[![Paper](https://img.shields.io/badge/Paper-1.13--1.21-green.svg)](https://papermc.io)
[![Folia](https://img.shields.io/badge/Folia-supported-success.svg)](https://papermc.io/software/folia)
[![License](https://img.shields.io/badge/license-GPL--3.0-orange.svg)](LICENSE)
[![bStats](https://img.shields.io/badge/bStats-enabled-brightgreen.svg)](https://bstats.org/plugin/bukkit/ZMusicGUI/31635)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.20-purple.svg)](https://kotlinlang.org)
[![JDK](https://img.shields.io/badge/JDK-21+-red.svg)](https://adoptium.net)

> Ourcraft 服务器音乐 GUI 插件 (Kotlin) — 内置 Mod 通信，服务端无需外部音乐插件

## 功能

- **多平台点歌**: 网易云 / 酷狗 / 酷我 (免登录, 仅免费歌曲)
- **歌单管理**: 网易云/酷狗歌单搜索、导入、收藏；个人/全服公开双区
- **播放范围**: 个人 / 领地 / 地皮 / 世界 / 全服 (Residence + PlotSquared 自动检测)
- **歌词同步**: BossBar / ActionBar，每秒更新
- **点歌历史**: 自动记录，置顶显示
- **全服点歌**: 搜索歌曲分享给全服玩家
- **Folia 兼容**: 自动检测 RegionizedServer，跨平台调度

> v3.0.0 起移除账号登录模块 (QQ/网易云 VIP 登录已删除), 仅播放免费歌曲。VIP 歌曲无法播放, 请尝试切换其他源或歌曲。

## 命令

| 命令 | 别名 | 说明 |
|------|------|------|
| `/zmusicgui` | `/zmg`, `/点歌`, `/dg`, `/zm` | 打开主菜单 |
| `/zmg reload` | — | 重载配置文件 (需 admin) |
| `/zmg mode` | — | 切换播放模式 |
| `/zmg stop` | — | 停止播放 |
| `/zmg list` | — | 查看播放列表 |
| `/zmg pushplay <平台:歌单ID:推送者:模式>` | — | 接收歌单推送 |

## 权限

| 权限节点 | 默认 | 说明 |
|----------|------|------|
| `zmusicgui.use` | 所有人 | 基础功能 (点歌/歌单/控制) |
| `zmusicgui.admin` | OP | 管理功能 (重载/全服播放/审批) |
| `zmusicgui.bypass` | 无 | 绕过点歌冷却 |

LuckPerms 示例:
```
/lp group default permission set zmusicgui.use true
/lp group admin permission set zmusicgui.admin true
```

## 配置文件

`plugins/ZMusicGUI/config.yml`:

```yaml
# ZMusicGUI v3.0.0 配置文件
debug: false
prefix: "&b[ZMusicGUI]&r "

# API 服务端地址 (建议自行部署: https://github.com/Yuncan050115/ourcraft-music-api)
api:
  ourcraft: "https://music.yuncan.xyz"

# 点歌
music:
  cost: 10                    # 点歌费用
  cooldown-seconds: 5         # 点歌冷却
  default-source: "netease"   # 默认源: netease/kugou/kuwo
  search-limit: 10            # 搜索结果数量

# 歌词
lyric:
  default-enabled: true       # 新玩家默认开启歌词
  default-mode: "BOSSBAR"     # BOSSBAR / ACTIONBAR
  update-ticks: 10            # 更新延迟
  display-format: "LYRIC"     # LYRIC / LYRIC_SINGER / LYRIC_NEXT / FULL

# 播放范围
scope:
  approval-timeout-seconds: 30
  self:       { cost: 1.0,  require-approval: false }
  residence:  { cost: 20.0, require-approval: true }
  plot:       { cost: 20.0, require-approval: true }
  world:      { cost: 50.0, require-approval: true, payee-account: "" }
  server:     { cost: 100.0, require-approval: true, payee-account: "" }

# 歌单
playlist:
  default-private: true
  cache-ttl-seconds: 30
  songs-per-page: 21
  history-limit: 200

# GUI
gui:
  show-credits: true
  main-menu-title: "&6&l☄ Ourcraft &8▸ &b音乐中心"
  quick-play-title: "&6&l🎵 快捷点歌"
  playlist-title: "&6&l歌单浏览"
  control-title: "&6&l播放控制"
```

## 语言文件

`plugins/ZMusicGUI/messages.yml` — 所有提示信息均可翻译为任意语言，支持 `&` 颜色代码和 `{变量}` 占位符。

## 依赖

| 插件 | 版本 | 必需 | 说明 |
|------|------|------|------|
| **客户端 ZMusic Mod** | 3.6.0+ | ✅ | 客户端音乐播放 (Fabric / NeoForge) |
| Vault + 经济插件 | 1.7+ | 软依赖 | 点歌扣费 |
| Residence | 6.x+ | 软依赖 | 领地范围 |
| PlotSquared | 7.x+ | 软依赖 | 地皮范围 |

> **无需 ZMusic 服务端插件** — 本插件内置 Mod 通信，服务端零外部依赖。
> **无需 PlaceholderAPI** — 歌词显示直接从 MusicPlayer 读取。

## 兼容性

| Minecraft | Paper / Purpur | Folia | 状态 |
|-----------|---------------|-------|------|
| 1.13.x | 1.13+ | — | ✅ |
| 1.19.x | 1.19+ | 1.19+ | ✅ |
| 1.20.x | 1.20+ | 1.20+ | ✅ |
| 1.21.x | 1.21+ | 1.21+ | ✅ |

## 构建

```bash
git clone https://github.com/Yuncan050115/ZMusicGUI.git
cd ZMusicGUI
./gradlew shadowJar
# 输出: build/libs/ZMusicGUI-3.0.0.jar
```

**要求**: JDK 21+, Kotlin 2.1.20, Gradle 9.x + Shadow

## 服务端 API

使用 [ourcraft-music-api](https://github.com/Yuncan050115/ourcraft-music-api) 作为后端:

- 网易云: enhanced 模式，免登录可播放免费歌曲
- 酷狗/酷我: 公开接口，仅免费歌曲

## bStats

本插件使用 [bStats](https://bstats.org/plugin/bukkit/ZMusicGUI/31635) 收集匿名使用数据。可在 `plugins/bStats/config.yml` 中关闭。

## 更新日志

### v3.0.0 (2026-06-28)
- 🗑️ **移除账号登录模块** — VIP 登录无法实现, 彻底删除账号绑定/扫码登录相关代码
- 🗑️ 移除 QQ 音乐源支持 (SUPPORTED_SOURCES 不再包含 qq, normalizeSource 保留 qq→qq 兼容映射)
- 🗑️ 删除 AccountManager / AccountGui / BindResult / verifyBindCode 等登录相关组件
- 🗑️ 移除 PlayerSettings 中的 accounts 字段及 getAccount/setAccount/removeAccount/hasAccount 方法
- ♻️ 简化 OurMusicApi.getSongDetail 签名 (移除 userId/token/cookie 参数)
- ♻️ 简化 SearchService.getSongDetailBySource (内部不再读取账号登录态)
- ♻️ 简化点歌失败提示为 "歌曲暂时无法播放, 请尝试其他源或歌曲"
- ♻️ PlaylistPushGui.startPlaylistForPlayer 移除 requester 参数
- 📝 更新 README, 移除账号绑定章节

### v2.5.0 (2026-06-28)
- 🗑️ **移除酷狗/酷我账号登录** — 服务器 IP 无法获取 VIP URL (token 绑浏览器 IP)
- 🗑️ 移除酷狗/酷我 VIP 歌曲提醒，改为提示使用网易云兜底
- ✨ 新增网易云扫码登录支持 (网页扫码 → 6位绑定码 → 游戏输入)
- ✨ QQ音乐支持网页提交 Cookie 获取绑定码 (除原有 F12 直接输入外)
- ♻️ AccountManager 重构，仅保留 QQ + 网易云 两个平台
- 📝 更新 README，添加 SVG 徽章

### v2.4.3 (2026-06-27)
- 🐛 修复 QQ音乐 cookie 透传问题
- 🔧 优化绑定流程，统一 F12 Cookie 复制方式

### v2.4.0 (2026-05-30)
- ✨ 多平台 cookie 透传 (酷狗/酷我从字段解析改为完整 cookie)
- ✨ 服务端 song_full 端点 (1次请求获取 name+url+lyric+time)
- 📦 Shadow + relocate 打包 Kotlin stdlib

### v2.2.1 (2026-05-28)
- 🌐 全版本兼容：Paper 1.13 / 1.19 / 1.20 / 1.21
- 🔧 BossBar 改为 Bukkit 原生 API
- 🔄 /zmg reload 命令重载配置文件

### v2.0.0 (2026-05-27)
- 🎉 重写为独立 Kotlin 实现 (不再依赖 ZMusic CE 服务端插件)
- 📦 内置 Mod 通信
- 🎵 多平台点歌 + 歌单管理 + 歌词同步

## 贡献指南

欢迎提交 Issue 和 Pull Request!

1. Fork 本仓库
2. 创建分支 (`git checkout -b feature/你的分支名字`)
3. 提交更改 (`git commit -m '你的分支名字'`)
4. 推送到分支 (`git push origin feature/你的分支名字`)
5. 创建 Pull Request

请确保代码风格一致，新功能有适当的配置项。

## 作者

- **ZMusicGUI**: [Yuncan](https://yuncan.xyz)
- **ZMusic Mod**: [ZMusic 开发团队](https://github.com/zmusic-dev/zmusic-mod)

## 许可证

本项目基于 [GPL-3.0](https://www.gnu.org/licenses/gpl-3.0.html) 许可证开源。

```
ZMusicGUI — Ourcraft 服务器音乐 GUI 插件
Copyright (C) 2026 Yuncan

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.
```
