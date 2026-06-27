package com.ourcraft.zmusicgui

import com.ourcraft.zmusicgui.channel.ModChannel
import com.ourcraft.zmusicgui.gui.MainGui
import com.ourcraft.zmusicgui.listener.ChatListener
import com.ourcraft.zmusicgui.listener.GuiListener
import com.ourcraft.zmusicgui.manager.Config
import com.ourcraft.zmusicgui.manager.EconomyManager
import com.ourcraft.zmusicgui.manager.LyricDisplayManager
import com.ourcraft.zmusicgui.manager.Messages
import com.ourcraft.zmusicgui.manager.PlayerSettings
import com.ourcraft.zmusicgui.manager.ScopeManager
import com.ourcraft.zmusicgui.metrics.Metrics
import com.ourcraft.zmusicgui.music.MusicPlayer
import com.ourcraft.zmusicgui.util.Debug
import com.ourcraft.zmusicgui.util.SchedulerUtil
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.net.HttpURLConnection
import java.net.URL

/**
 * ZMusicGUI v2.2.1 — Ourcraft Yuncan
 *
 * 通过 OurMusicApi (服务端 ourcraft-music-api) 调用多平台音乐接口,
 * 自带 Mod 通信、播放与歌词同步。
 *
 * 支持平台: netease / kugou / kuwo / qq
 * 只需客户端安装 ZMusic Mod 即可播放音乐。
 */
class ZMusicGUI : JavaPlugin() {

    companion object {
        lateinit var plugin: ZMusicGUI
            private set
        private const val CURRENT_VERSION = "2.5.0"
        private const val GITHUB_API = "https://api.github.com/repos/Yuncan050115/ZMusicGUI/releases/latest"
    }

    override fun onEnable() {
        plugin = this

        Debug.banner()

        Messages.load(this)
        Debug.info(Messages.console("enabling", "version" to description.version))

        Config.load(this)
        Debug.info(Messages.console("config-loaded", "debug" to Config.debug().toString()))

        PlayerSettings.load(this)
        Debug.info(Messages.console("settings-loaded"))

        // Mod 通信
        ModChannel.register()
        Debug.info(Messages.console("mod-channels-registered"))

        // 检测 Residence / PlotSquared
        ScopeManager.setup()

        // Vault 经济系统
        EconomyManager.setup()

        LyricDisplayManager.start(this)
        Debug.info(Messages.console("lyric-mounted"))

        // 显示平台信息
        val platform = if (SchedulerUtil.isFolia()) "Folia" else server.name
        Debug.info(Messages.console("platform", "platform" to platform))

        server.pluginManager.registerEvents(GuiListener, this)
        server.pluginManager.registerEvents(ChatListener, this)
        Debug.info(Messages.console("events-registered"))

        getCommand("zmusicgui")?.setExecutor(this)
        Debug.info(Messages.console("command-registered"))

        // bStats metrics (pluginId 31635 — Ourcraft Yuncan)
        Metrics(this, 31635)
        Debug.info(Messages.console("bstats-mounted"))

        // 自建 checkUpdate — 检查 GitHub Releases
        checkUpdate()

        Debug.info(Messages.console("enabled", "version" to description.version))
    }

    override fun onDisable() {
        Debug.info(Messages.console("disabling"))
        MusicPlayer.stopAll()
        LyricDisplayManager.stop()
        PlayerSettings.save()
        SchedulerUtil.cancelAll(this)
        try { ModChannel.unregister() } catch (_: Throwable) {}
        Debug.info(Messages.console("disabled"))
        Debug.info(">> Ourcraft Yuncan - 感谢使用")
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        // 子命令: reload / update / consent / reject / list / stop
        if (args.isNotEmpty()) {
            when (args[0].lowercase()) {
                "reload" -> return handleReload(sender)
                "update" -> return handleUpdate(sender)
                "stop" -> return handleStop(sender)
                "consent" -> return handleConsent(sender, args.drop(1).firstOrNull())
                "reject" -> return handleReject(sender, args.drop(1).firstOrNull())
                "list" -> return handleList(sender)
                "pushplay" -> return handlePushPlay(sender, args.drop(1).firstOrNull())
                "push" -> return handlePush(sender, args.drop(1))
            }
        }

        // 默认: 打开主菜单 (仅玩家)
        if (sender is Player) {
            if (sender.hasPermission("zmusicgui.use")) {
                MainGui.open(sender)
            } else {
                sender.sendMessage(Messages.player("no-permission", "permission" to "zmusicgui.use"))
            }
            return true
        }
        sender.sendMessage("ZMusicGUI v$CURRENT_VERSION - 控制台无法打开 GUI, 请以玩家身份进入服务器")
        sender.sendMessage("用法: /zmusicgui [reload|update|consent|reject|list|stop]")
        return true
    }

    /** 重载配置 */
    private fun handleReload(sender: CommandSender): Boolean {
        if (!sender.hasPermission("zmusicgui.admin")) {
            sender.sendMessage(Messages.cmd("reload.no-permission"))
            return true
        }
        Config.reload(this)
        Messages.reload(this)
        sender.sendMessage(Messages.cmd("reload.success"))
        Debug.info(">> 配置已通过 /zmg reload 重载")
        return true
    }

    /** 手动检查更新 */
    private fun handleUpdate(sender: CommandSender): Boolean {
        if (!sender.hasPermission("zmusicgui.admin")) {
            sender.sendMessage(Messages.player("no-permission", "permission" to "zmusicgui.admin"))
            return true
        }
        sender.sendMessage(color("${Messages.prefix()} ${Messages.cmd("update.checking")}"))
        SchedulerUtil.runAsync(plugin, Runnable {
            try {
                val conn = URL(GITHUB_API).openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.setRequestProperty("User-Agent", "ZMusicGUI/$CURRENT_VERSION")
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val gson = com.google.gson.Gson()
                @Suppress("UNCHECKED_CAST")
                val data = gson.fromJson(response, Map::class.java) as Map<String, Any?>
                val latestVersion = data["tag_name"]?.toString()?.removePrefix("v") ?: "未知"
                val url = data["html_url"]?.toString() ?: ""
                SchedulerUtil.runSync(plugin, Runnable {
                    if (latestVersion != CURRENT_VERSION) {
                        sender.sendMessage(color("${Messages.prefix()} ${Messages.cmd("update.found",
                            "latest" to latestVersion, "version" to CURRENT_VERSION)}"))
                        if (url.isNotEmpty()) sender.sendMessage(color("${Messages.prefix()} ${Messages.cmd("update.url", "url" to url)}"))
                    } else {
                        sender.sendMessage(color("${Messages.prefix()} ${Messages.cmd("update.latest", "version" to CURRENT_VERSION)}"))
                    }
                })
            } catch (e: Throwable) {
                SchedulerUtil.runSync(plugin, Runnable {
                    sender.sendMessage(color("${Messages.prefix()} ${Messages.cmd("update.failed", "error" to (e.message ?: "未知"))}"))
                })
            }
        })
        return true
    }

    /** 停止播放 */
    private fun handleStop(sender: CommandSender): Boolean {
        val player = sender as? Player ?: run {
            sender.sendMessage(Messages.cmd("stop.not-playing"))
            return true
        }
        if (MusicPlayer.isPlaying(player)) {
            MusicPlayer.stop(player)
            sender.sendMessage(Messages.cmd("stop.success"))
        } else {
            sender.sendMessage(Messages.cmd("stop.not-playing"))
        }
        return true
    }

    /** 同意接收音乐 (无需管理员权限, 任何玩家都可执行) */
    private fun handleConsent(sender: CommandSender, idPrefix: String?): Boolean {
        val player = sender as? Player ?: run {
            sender.sendMessage(color("${Messages.prefix()} &c仅玩家可执行此命令"))
            return true
        }
        if (idPrefix == null) {
            sender.sendMessage(color("${Messages.prefix()} &c用法: /zmg consent <请求ID>"))
            return true
        }
        // 查找匹配的请求
        val pending = ScopeManager.getPendingConsents(player)
        val req = pending.firstOrNull { it.id.toString().startsWith(idPrefix) }
        if (req == null) {
            sender.sendMessage(color("${Messages.prefix()} &c未找到匹配的请求, 可能已超时"))
            return true
        }
        if (ScopeManager.consentReceive(req.id, player)) {
            // 成功
        } else {
            sender.sendMessage(color("${Messages.prefix()} &c你已同意或拒绝过此请求"))
        }
        return true
    }

    /** 拒绝接收音乐 (无需管理员权限, 任何玩家都可执行) */
    private fun handleReject(sender: CommandSender, idPrefix: String?): Boolean {
        val player = sender as? Player ?: run {
            sender.sendMessage(color("${Messages.prefix()} &c仅玩家可执行此命令"))
            return true
        }
        if (idPrefix == null) {
            sender.sendMessage(color("${Messages.prefix()} &c用法: /zmg reject <请求ID>"))
            return true
        }
        val pending = ScopeManager.getPendingConsents(player)
        val req = pending.firstOrNull { it.id.toString().startsWith(idPrefix) }
        if (req == null) {
            sender.sendMessage(color("${Messages.prefix()} &c未找到匹配的请求, 可能已超时"))
            return true
        }
        if (ScopeManager.rejectReceive(req.id, player)) {
            // 成功
        } else {
            sender.sendMessage(color("${Messages.prefix()} &c你已同意或拒绝过此请求"))
        }
        return true
    }

    /** 列出待响应的接收请求 (仅玩家, 无需管理员权限) */
    private fun handleList(sender: CommandSender): Boolean {
        val player = sender as? Player ?: run {
            sender.sendMessage(color("${Messages.prefix()} &c仅玩家可执行此命令"))
            return true
        }
        val pending = ScopeManager.getPendingConsents(player)
        if (pending.isEmpty()) {
            sender.sendMessage(color("${Messages.prefix()} &7${Messages.cmd("list.empty")}"))
            return true
        }
        sender.sendMessage(color("${Messages.prefix()} &6待响应的音乐接收请求 (&e${pending.size}&6):"))
        for (req in pending) {
            val idShort = req.id.toString().take(8)
            sender.sendMessage(color("${Messages.prefix()} &7- &f${req.requester.name} &7→ &f${req.song.name} &8[$idShort]"))
        }
        sender.sendMessage(color("${Messages.prefix()} &7使用 &f/zmg consent [id] &7或 &f/zmg reject [id]"))
        return true
    }

    /** 推曲: /zmg push <songId> <source> — 异步获取歌曲详情并加入播放队列 */
    private fun handlePush(sender: CommandSender, args: List<String>): Boolean {
        val player = sender as? Player ?: run {
            sender.sendMessage(color("${Messages.prefix()} &c仅玩家可执行此命令"))
            return true
        }
        if (args.size < 2) {
            player.sendMessage(color("${Messages.prefix()} &c用法: /zmg push <歌曲ID> <来源>"))
            return true
        }
        val songId = args[0]
        val source = args[1]
        player.sendMessage(color("${Messages.prefix()} &7正在获取歌曲信息..."))
        com.ourcraft.zmusicgui.util.SchedulerUtil.runAsync(plugin, Runnable {
            val song = com.ourcraft.zmusicgui.manager.SearchService.getSongDetailBySource(songId, source, player)
            com.ourcraft.zmusicgui.util.SchedulerUtil.runSync(plugin, Runnable {
                if (song != null) {
                    MusicPlayer.pushToQueue(player, song)
                } else {
                    player.sendMessage(color("${Messages.prefix()} &c获取歌曲失败, 可能已下架"))
                }
            })
        })
        return true
    }

    /** 歌单推送播放: /zmg pushplay <platform:playlistId:pusherName:mode> */
    private fun handlePushPlay(sender: CommandSender, arg: String?): Boolean {
        val player = sender as? Player ?: run {
            sender.sendMessage(color("${Messages.prefix()} &c仅玩家可执行此命令"))
            return true
        }
        if (arg.isNullOrBlank()) {
            player.sendMessage(color("${Messages.prefix()} &c用法: /zmg pushplay <platform:id:pusher:mode>"))
            return true
        }
        // 解析参数: platform:playlistId:pusherName:mode
        val parts = arg.split(":")
        if (parts.size < 4) {
            player.sendMessage(color("${Messages.prefix()} &c参数格式错误"))
            return true
        }
        val platform = parts[0]
        val playlistId = parts[1]
        val pusherName = parts[2]
        val mode = parts[3]  // sequence / shuffle

        player.sendMessage(color("${Messages.prefix()} &7正在接收推送歌单..."))

        // 设置播放模式
        if (mode == "shuffle") {
            com.ourcraft.zmusicgui.manager.PlayerSettings.setPlayMode(player, "shuffle")
        } else {
            com.ourcraft.zmusicgui.manager.PlayerSettings.setPlayMode(player, "sequence")
        }

        // 查找推送者 (用于携带 Token)
        val pusher = org.bukkit.Bukkit.getPlayerExact(pusherName)

        // 异步查找歌单并播放
        com.ourcraft.zmusicgui.util.SchedulerUtil.runAsync(plugin, Runnable {
            // 从 PlaylistManager 获取歌单 (查找推送者的公开歌单)
            val playlist = findPlaylistForPush(playlistId, platform, pusherName, pusher)
            if (playlist == null) {
                com.ourcraft.zmusicgui.util.SchedulerUtil.runSync(plugin, Runnable {
                    player.sendMessage(color("${Messages.prefix()} &c未找到推送的歌单 (可能已被删除)"))
                })
                return@Runnable
            }
            com.ourcraft.zmusicgui.gui.PlaylistPushGui.startPlaylistForPlayer(player, playlist, pusher ?: player)
        })
        return true
    }

    /** 查找用于推送的歌单 (优先全局/公开歌单) */
    private fun findPlaylistForPush(
        playlistId: String, platform: String, pusherName: String, pusher: Player?
    ): com.ourcraft.zmusicgui.manager.PlaylistManager.AggregatedPlaylist? {
        // 读取推送者的歌单文件
        val file = java.io.File(dataFolder, "playlist/${pusherName}.yml")
        if (!file.exists()) return null
        try {
            val yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file)
            val key = "$platform:$playlistId"
            val section = yaml.getConfigurationSection(key) ?: return null
            val name = section.getString("name", "未知歌单") ?: "未知歌单"
            val isPublic = section.getBoolean("public", false)
            if (!isPublic) {
                Debug.debug("推送歌单不可用 (非公开): $pusherName/$key")
            }
            val songsList = section.getMapList("songs")
            val songs = songsList.mapNotNull { map ->
                val id = map["id"]?.toString() ?: return@mapNotNull null
                val sName = map["name"]?.toString() ?: "未知"
                val singer = map["singer"]?.toString() ?: "未知"
                val time = (map["time"] as? Number)?.toLong() ?: 0L
                com.ourcraft.zmusicgui.manager.PlaylistManager.Song(id, sName, singer, time, platform)
            }
            return com.ourcraft.zmusicgui.manager.PlaylistManager.AggregatedPlaylist(
                platform = platform, id = playlistId, name = name,
                songCount = songs.size, owner = pusherName, songs = songs,
                isGlobal = false, isFavorite = false, isOwn = false, isPublic = isPublic
            )
        } catch (e: Throwable) {
            Debug.warn("查找推送歌单失败: ${e.message}")
            return null
        }
    }

    fun hasAdmin(player: Player) = player.hasPermission("zmusicgui.admin")

    /** 自建更新检查 — GitHub Releases API */
    private fun checkUpdate() {
        SchedulerUtil.runAsync(plugin, Runnable {
            try {
                val conn = URL(GITHUB_API).openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.setRequestProperty("User-Agent", "ZMusicGUI/$CURRENT_VERSION")
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val gson = com.google.gson.Gson()
                @Suppress("UNCHECKED_CAST")
                val data = gson.fromJson(response, Map::class.java) as Map<String, Any?>
                val latestVersion = data["tag_name"]?.toString()?.removePrefix("v") ?: return@Runnable
                if (latestVersion != CURRENT_VERSION) {
                    Debug.warn(Messages.console("update-found",
                        "latest" to latestVersion, "version" to CURRENT_VERSION))
                    Debug.warn(Messages.console("update-url", "url" to (data["html_url"]?.toString() ?: "")))
                } else {
                    Debug.info(Messages.console("update-latest", "version" to CURRENT_VERSION))
                }
            } catch (e: Throwable) {
                Debug.debug("更新检查失败: ${e.message}")
            }
        })
    }

    private fun color(text: String) = text.replace('&', '§')
}
