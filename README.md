# ZMusicGUI

一个为 [ZMusic](https://github.com/zmusic-dev/zmusic-plugin) 设计的图形界面插件，提供歌单管理、点歌播放、歌词显示等功能。

## 前置依赖

| 插件 | 版本[以26.1为例] | 必需 | 说明 |
|------|------|------|------|
| **[ZMusic](https://github.com/zmusic-dev/zmusic-plugin)** | 2.12.0+ | ✅ | 核心音乐插件，提供播放/搜索/歌单功能 |
| **[ZMusic Mod](https://github.com/zmusic-dev/zmusic-mod)** | 3.6.0+ (客户端) | ✅ | 客户端音频解码模组，玩家需安装 |
| **[PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/)** | 2.11.6+ | ✅ | 歌词显示引擎的数据来源 |

> ⚠️ **重要**: ZMusic 插件 + ZMusic Mod + PlaceholderAPI 三者缺一不可。
> - 服务端安装 ZMusic 插件和 PlaceholderAPI
> - 客户端安装 ZMusic Mod（Fabric / NeoForge）

## 兼容性

| Minecraft | Paper / Purpur | 状态 |
|-----------|---------------|------|
| 1.19.x | 1.19+ | ✅ |
| 1.20.x | 1.20+ | ✅ |
| 1.21.x | 1.21+ | ✅ |
| 26.1.x | 26.1+ | ✅ |

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
  display-lines: 1        # 1=仅当前句歌词 / 2=歌词+歌手+歌名

music:
  cooldown-seconds: 5     # 点歌冷却时间
  public-request: true    # 是否开启全服点歌

gui:
  show-credits: true      # 左下角是否显示作者信息
  main-menu-title: ...    # 主菜单标题
```

## 语言文件

`plugins/ZMusicGUI/messages.yml` — 所有提示信息均可翻译为任意语言，支持 `&` 颜色代码和 `{变量}` 占位符。插件每次加载自动更新语言文件（旧文件会被覆盖），如需自定义请备份后编辑。

## 构建

```bash
git clone <repo-url>
cd ZMusicGUI
./gradlew build
# 输出: build/libs/ZMusicGUI-<version>.jar
```

- JDK 21+
- Kotlin 2.1
- Gradle 9.x + Shadow

## 更新日志

### v1.1.3 (2026-05-29)
- 🐛 修复歌单管理平台选择界面无法返回主菜单的问题
- 🐛 修复导入歌单提示显示 raw key 的问题（语言文件现在强制更新）
- ✨ 新增 `lyric.display-lines` 配置，可选择显示 1 行纯歌词或 2 行完整信息
- 🎨 默认歌词只显示当前一句，不再显示长串歌手歌名
- 📝 README 新增完整更新日志

### v1.1.2 (2026-05-29)
- ✨ 全服强制播放移至平台选择界面末位
- 🖱️ 左下角作者附魔书可点击打开网站
- ⚙️ `show-credits` 配置开关作者信息显示
- ⚙️ `public-request` 配置开关全服点歌功能

### v1.1.1 (2026-05-28)
- 🌐 版本兼容：Paper 1.19 / 1.20 / 1.21 / 26.1
- 🔧 BossBar 改为 Bukkit 原生 API
- 🎵 歌单查看/导入使用 ZMusic 原生点击按钮
- ✨ 新增全服强制播放功能 (Admin)

### v1.1.0 (2026-05-27)
- 📦 改用 Shadow + relocate 打包 Kotlin stdlib
- 🛡️ 修复 GUI 物品防盗（InventoryHolder 模式）
- 🌐 语言文件 messages.yml，支持翻译
- 🔄 /zmg reload 命令重载配置文件
- 🐛 调试模式控制台日志

### v1.0.0 (2026-05-27)
- 🎉 初始发布
- 🎵 点歌播放
- 📋 歌单管理
- 🎤 歌词显示修复（BossBar / ActionBar）
- ⏯ 播放控制
- 🎨 精美 GUI 界面

## 作者

- **ZMusicGUI**: [Yuncan](https://yuncan.xyz)
- **ZMusic 插件**: [真心 (ZhenXin)](https://github.com/zmusic-dev)
- **ZMusic Mod**: [ZMusic 开发团队](https://github.com/zmusic-dev/zmusic-mod)

## 许可证

GPL-3.0
