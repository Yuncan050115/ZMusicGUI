# ZMusicGUI

[![Version](https://img.shields.io/badge/version-1.1.3-blue.svg)](https://github.com/Yuncan050115/ZMusicGUI)
[![Paper](https://img.shields.io/badge/Paper-1.19--26.1-green.svg)](https://papermc.io)
[![License](https://img.shields.io/badge/license-GPL--3.0-orange.svg)](LICENSE)
[![bStats](https://img.shields.io/badge/bStats-enabled-brightgreen.svg)](https://bstats.org/plugin/bukkit/ZMusicGUI/31635)

一个为 [ZMusic](https://github.com/zmusic-dev/zmusic-plugin) 设计的精美图形界面插件，提供歌单管理、点歌播放、歌词显示等功能。

## 前置依赖

| 插件 | 版本[以26.1为例] | 必需 | 说明 |
|------|------|------|------|
| **[ZMusic](https://github.com/zmusic-dev/zmusic-plugin)** | 2.12.0+ | ✅ | zmusic插件 |
| **[ZMusic Mod](https://github.com/zmusic-dev/zmusic-mod)** | 3.6.0+ (客户端) | ✅ | Zmusic客户端模组 |
| **[PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/)** | 2.11.6+ | ✅ | 歌词显示 |

>  ZMusic 插件 + ZMusic Mod + PlaceholderAPI 三者缺一不可。
> - 服务端安装 ZMusic 插件和 PlaceholderAPI
> - 客户端安装 ZMusic Mod（Fabric / NeoForge）

## 兼容性

| Minecraft | Paper / Purpur | 状态 |
|-----------|---------------|------|
| 1.19.x | 1.19+ | ✅ |
| 1.20.x | 1.20+ | ✅ |
| 1.21.x | 1.21+ | ✅ |
| 26.1.x | 26.1+ | ✅ |

## 功能

### 点歌播放
- 选择音乐平台（网易云 / QQ / 酷狗 / 酷我 / Bilibili）
- Tips: 需要bilibili渠道请联系zmusic交流群，可在配置取消显示不想看见的平台
- 输入歌名即可播放

### 全服点歌
- 搜索歌曲分享给全服玩家
- 可配置开关

### 歌单管理
- 两级菜单：个人歌单 / 全服歌单 → 查看列表 / 导入链接
- 支持从网页复制歌单链接（`https://` 开头）导入
- 查看时 ZMusic 原生输出含播放/查看按钮

### 播放控制
- 停止播放、查看当前歌曲信息（歌名/歌手/进度/来源）
- 实时歌词预览（需 PlaceholderAPI）
- 歌词快速开关

### 歌词显示引擎
- 自建歌词显示引擎，通过 PAPI 获取实时歌词
- 支持 BossBar / ActionBar 两种显示模式
- 个人独立开关，实时切换
- 可配置显示格式：LYRIC（仅当前句）/ LYRIC_SINGER（歌词+歌手歌名）

### 全服强制播放
- 管理员强制所有在线玩家播放歌曲
- 需要 `zmusicgui.admin` 权限，在平台选择界面末位，无权限者此按钮隐藏显示

## 命令

| 命令 | 别名 | 说明 |
|------|------|------|
| `/zmusicgui` | `/zmg`, `/点歌`, `/dg` | 打开主菜单 |
| `/zmg reload` | — | 重载配置文件（需 admin） |

## 权限

| 权限节点 | 默认 | 说明 |
|----------|------|------|
| `zmusicgui.use` | 所有人 | 基础功能（点歌/歌单/控制） |
| `zmusicgui.admin` | OP | 管理功能（全服播放/歌词设置/重载） |
| `zmusicgui.bypass` | 无 | 绕过点歌冷却 |

LuckPerms 示例：
```
/lp group default permission set zmusicgui.use true
/lp group admin permission set zmusicgui.admin true
```

## 配置文件

`plugins/ZMusicGUI/config.yml`：

```yaml
debug: false              # 调试模式，开启后控制台输出详细交互日志

platforms:                # 音乐平台开关，设置 enabled: false 可隐藏
  netease: ...
  bilibili:
    enabled: true

lyric:
  default-enabled: true   # 新玩家默认开启歌词
  default-mode: BOSSBAR   # BOSSBAR / ACTIONBAR
  update-ticks: 10  # 更新延迟
  # 歌词显示格式:
  #   LYRIC         — 仅当前句歌词 (默认)
  #   LYRIC_SINGER  — 当前句 + 歌手 + 歌名
  #   LYRIC_NEXT    — 当前句 + 下一句 (需ZMusic支持)
  #   FULL          — 全部: 歌词 + 歌手 + 歌名 + 下一句
  display-format: LYRIC

music:
  cooldown-seconds: 5     # 点歌冷却时间
  public-request: true    # 是否开启全服点歌

gui:
  show-credits: true      # 左下角是否显示作者信息
  main-menu-title: ...    # 主菜单标题
```

## 语言文件

`plugins/ZMusicGUI/messages.yml` — 所有提示信息均可翻译为任意语言，支持 `&` 颜色代码和 `{变量}` 占位符。

## 构建

```bash
git clone https://github.com/Yuncan050115/ZMusicGUI.git
cd ZMusicGUI
./gradlew build
# 输出: build/libs/ZMusicGUI-<version>.jar
```

**要求**: JDK 21+, Kotlin 2.1, Gradle 9.x + Shadow

## bStats

本插件使用 [bStats](https://bstats.org) 收集匿名使用数据，帮助开发者了解插件使用情况。你可以在 `plugins/bStats/config.yml` 中关闭统计。

## 更新日志

### v1.1.3 (2026-05-29)
- 🐛 修复歌单管理平台选择界面无法返回主菜单的问题
- 🐛 修复导入歌单提示显示 raw key 的问题（语言文件现在强制更新）
- 🐛 修复选择平台后菜单不自动关闭的问题
- ✨ 新增 `lyric.display-format` 配置
- 🎨 默认歌词只显示当前句
- 🎨 歌单管理改为两级菜单结构
- 📊 集成 bStats 匿名数据统计
- 🗑️ 移除歌词设置中的 ZMusic 原生 BossBar 切换

### v1.1.2 (2026-05-28)
- ✨ 全服强制播放移至平台选择界面末位
- 🖱️ 左下角作者附魔书可点击打开网站
- ⚙️ `show-credits` 配置开关作者信息显示
- ⚙️ `public-request` 配置开关全服点歌功能

### v1.1.1 (2026-05-28)
- 🌐 全版本兼容：Paper 1.19 / 1.20 / 1.21 / 26.1
- 🔧 BossBar 改为 Bukkit 原生 API
- 🎵 歌单查看/导入使用 ZMusic 原生点击按钮
- ✨ 新增全服强制播放功能 (Admin)

### v1.1.0 (2026-05-27)
- 📦 改用 Shadow + relocate 打包 Kotlin stdlib
- 🛡️ 修复 GUI 物品防盗（InventoryHolder 模式）
- 🌐 语言文件 messages.yml，支持国际化
- 🔄 /zmg reload 命令重载配置文件
- 🐛 调试模式控制台日志

### v1.0.0 (2026-05-27)
- 🎉 初始发布

## 贡献指南

欢迎提交 Issue 和 Pull Request！

1. Fork 本仓库
2. 创建分支 (`git checkout -b feature/你的分支名字`)
3. 提交更改 (`git commit -m '你的分支名字'`)
4. 推送到分支 (`git push origin feature/你的分支名字`)
5. 创建 Pull Request

请确保代码风格一致，新功能有适当的配置项。

## 作者

- **ZMusicGUI**: [Yuncan](https://yuncan.xyz)
- **ZMusic 插件**: [真心 (ZhenXin)](https://github.com/zmusic-dev)
- **ZMusic Mod**: [ZMusic 开发团队](https://github.com/zmusic-dev/zmusic-mod)

## 许可证

本项目基于 [GPL-3.0](https://www.gnu.org/licenses/gpl-3.0.html) 许可证开源。

```
ZMusicGUI — A beautiful GUI for ZMusic
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
