package com.ourcraft.zmusicgui.manager

import com.ourcraft.zmusicgui.ZMusicGUI
import com.ourcraft.zmusicgui.music.OurMusicApi
import com.ourcraft.zmusicgui.util.Debug
import com.ourcraft.zmusicgui.util.Items
import com.ourcraft.zmusicgui.util.SchedulerUtil
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.bukkit.entity.Player
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 歌单聚合管理器 v2.1.0
 *
 * 自主管理歌单数据 (不依赖 ZMusic CE):
 *   个人: plugins/ZMusicGUI/playlist/netease/<玩家名>/<歌单ID>.json
 *   全服: plugins/ZMusicGUI/playlist/global/netease/<歌单ID>.json
 *
 * 个人歌单默认隐私, 全服歌单所有人可见。
 * 所有调度使用 SchedulerUtil (兼容 Folia)。
 */
object PlaylistManager {

    data class Song(
        val id: String,
        val name: String,
        val singer: String,
        val time: Long,
        val source: String = "netease"
    )

    data class AggregatedPlaylist(
        val platform: String,
        val id: String,
        val name: String,
        val songCount: Int,
        val owner: String,       // 玩家名或 "global"
        val songs: List<Song>,
        val isGlobal: Boolean,
        val isFavorite: Boolean = false,
        val isOwn: Boolean = false,
        val isPublic: Boolean = false,
        val isHistory: Boolean = false   // 点歌历史歌单 (特殊标记, 不允许收藏/删除)
    )

    /** 读取结果缓存: 玩家名 -> (时间戳, 歌单列表) */
    private val cache = ConcurrentHashMap<String, Pair<Long, List<AggregatedPlaylist>>>()

    private fun playlistDir(): File =
        File(ZMusicGUI.plugin.dataFolder, "playlist")

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    fun refresh(player: Player) {
        cache.remove(player.name)
    }

    /** 异步读取并聚合歌单，回调在主线程执行。使用 SchedulerUtil 兼容 Folia。 */
    fun loadAsync(player: Player, callback: (List<AggregatedPlaylist>) -> Unit) {
        val cached = cache[player.name]
        val ttl = Config.playlistCacheTtl()
        if (cached != null && System.currentTimeMillis() - cached.first < ttl) {
            SchedulerUtil.runSync(ZMusicGUI.plugin, Runnable { callback(cached.second) })
            return
        }

        SchedulerUtil.runAsync(ZMusicGUI.plugin, Runnable {
            val list = try { loadSync(player) } catch (e: Throwable) {
                Debug.error("歌单加载失败: ${e.message}")
                emptyList<AggregatedPlaylist>()
            }
            cache[player.name] = System.currentTimeMillis() to list
            SchedulerUtil.runSync(ZMusicGUI.plugin, Runnable { callback(list) })
        })
    }

    private fun loadSync(player: Player): List<AggregatedPlaylist> {
        val result = mutableListOf<AggregatedPlaylist>()
        val dir = playlistDir()
        if (!dir.exists()) {
            Debug.debug("歌单目录不存在, 返回空列表")
            return result
        }

        // 点歌历史歌单: playlist/history/<玩家名>/default.json (置顶, 单独处理)
        loadHistoryPlaylist(player)?.let { result.add(it) }

        // 个人歌单: playlist/<平台>/<玩家名>/
        for (platformDir in dir.listFiles { f -> f.isDirectory } ?: emptyArray()) {
            val platform = platformDir.name
            if (platform == "global" || platform == "history") continue

            // 当前玩家的个人歌单
            val playerDir = File(platformDir, player.name)
            if (playerDir.exists()) {
                for (file in playerDir.listFiles { f -> f.name.endsWith(".json") } ?: emptyArray()) {
                    parsePlaylist(file, platform, player.name, false)?.let { pl ->
                        val isPub = PlayerSettings.isPublic(player, platform, pl.id)
                        result.add(pl.copy(isOwn = true, isPublic = isPub))
                    }
                }
            }

            // 其他玩家公开的歌单
            for (otherDir in platformDir.listFiles { f -> f.isDirectory } ?: emptyArray()) {
                val ownerName = otherDir.name
                if (ownerName == player.name) continue
                for (file in otherDir.listFiles { f -> f.name.endsWith(".json") } ?: emptyArray()) {
                    val playlistId = file.nameWithoutExtension
                    if (PlayerSettings.isPublicByOwner(ownerName, platform, playlistId)) {
                        parsePlaylist(file, platform, ownerName, false)?.let { result.add(it) }
                    }
                }
            }
        }

        // 全服歌单: playlist/global/<平台>/
        val globalDir = File(dir, "global")
        if (globalDir.exists()) {
            for (platformDir in globalDir.listFiles { f -> f.isDirectory } ?: emptyArray()) {
                val platform = platformDir.name
                for (file in platformDir.listFiles { f -> f.name.endsWith(".json") } ?: emptyArray()) {
                    parsePlaylist(file, platform, "global", true)?.let { result.add(it) }
                }
            }
        }

        // 标记收藏状态 (历史歌单不参与收藏)
        val withFav = result.map { pl ->
            if (pl.isHistory) pl
            else {
                val isFav = PlayerSettings.isFavorite(player, pl.platform, pl.id)
                pl.copy(isFavorite = isFav)
            }
        }

        Debug.debug("歌单聚合: ${player.name} 共 ${withFav.size} 个歌单")
        return withFav
    }

    /** 加载玩家点歌历史歌单 (history/<玩家名>/default.json) */
    private fun loadHistoryPlaylist(player: Player): AggregatedPlaylist? {
        val file = File(playlistDir(), "history/${player.name}/default.json")
        if (!file.exists()) return null
        val pl = parsePlaylist(file, "history", player.name, false) ?: return null
        val isPub = PlayerSettings.isPublic(player, "history", "default")
        return pl.copy(isOwn = true, isPublic = isPub, isHistory = true)
    }

    private fun parsePlaylist(file: File, platform: String, owner: String, isGlobal: Boolean): AggregatedPlaylist? {
        return try {
            val content = file.readText(Charsets.UTF_8)
            @Suppress("UNCHECKED_CAST")
            val data = gson.fromJson(content, Map::class.java) as Map<String, Any?>
            val name = data["name"]?.toString() ?: "未知歌单"
            val songCount = (data["songs"] as? Number)?.toInt() ?: 0
            val songs = mutableListOf<Song>()
            val listRaw = data["list"]
            if (listRaw is List<*>) {
                for (songEl in listRaw) {
                    val songMap = songEl as? Map<*, *> ?: continue
                    songs.add(Song(
                        id = songMap["id"]?.toString() ?: "",
                        name = songMap["name"]?.toString() ?: "未知",
                        singer = songMap["singer"]?.toString() ?: "未知",
                        time = (songMap["time"] as? Number)?.toLong() ?: 0L,
                        source = songMap["source"]?.toString() ?: platform
                    ))
                }
            }
            AggregatedPlaylist(
                platform = platform,
                id = file.nameWithoutExtension,
                name = name,
                songCount = if (songCount > 0) songCount else songs.size,
                owner = owner,
                songs = songs,
                isGlobal = isGlobal
            )
        } catch (e: Throwable) {
            Debug.debug("解析歌单失败: ${file.name} - ${e.message}")
            null
        }
    }

    /**
     * 保存导入的歌单到玩家目录 (支持多平台)
     * @param player 导入者
     * @param playlist 歌单数据 (来自 OurMusicApi)
     * @param platform 平台 (netease / kugou / kuwo)
     * @param isPrivate 是否隐私保存 (config.playlist.default-private 控制)
     */
    fun saveImportedPlaylist(player: Player, playlist: OurMusicApi.Playlist, isPrivate: Boolean, platform: String = "netease") {
        val dir = File(playlistDir(), "$platform/${player.name}")
        dir.mkdirs()

        val file = File(dir, "${playlist.id}.json")
        val songs = playlist.songs.map {
            Song(it.id, it.name, it.singer, 0L, platform)
        }

        val data = linkedMapOf<String, Any>(
            "name" to playlist.name,
            "platform" to platform,
            "songs" to playlist.songCount,
            "list" to songs.map { mapOf(
                "id" to it.id,
                "name" to it.name,
                "singer" to it.singer,
                "time" to it.time,
                "source" to it.source
            )}
        )
        file.writeText(gson.toJson(data), Charsets.UTF_8)

        // 默认隐私: 不调用 togglePublic (默认就是隐私)
        // 若 isPrivate=false, 则标记为公开
        if (!isPrivate) {
            PlayerSettings.togglePublic(player, platform, playlist.id)
        }
        Debug.info("歌单已保存: ${player.name}/$platform/${playlist.id} (${songs.size} 首)")
    }

    /**
     * 对歌单排序: 历史歌单置顶 → 我的歌单 → 收藏歌单 → 全服歌单
     */
    fun sortForDisplay(player: Player, all: List<AggregatedPlaylist>): List<AggregatedPlaylist> {
        val history = mutableListOf<AggregatedPlaylist>()
        val mine = mutableListOf<AggregatedPlaylist>()
        val favorites = mutableListOf<AggregatedPlaylist>()
        val others = mutableListOf<AggregatedPlaylist>()

        for (pl in all) {
            when {
                pl.isHistory -> history.add(pl)
                pl.isOwn -> mine.add(pl)
                pl.isFavorite -> favorites.add(pl)
                else -> others.add(pl)
            }
        }
        return history + mine + favorites + others
    }

    // ==================== 点歌历史 ====================

    /** 历史歌单文件路径 */
    private fun historyFile(player: Player): File {
        val dir = File(playlistDir(), "history/${player.name}")
        dir.mkdirs()
        return File(dir, "default.json")
    }

    /** 加载历史歌单的歌曲列表 (无缓存, 直接读盘) */
    private fun loadHistorySongs(player: Player): MutableList<Song> {
        val file = historyFile(player)
        if (!file.exists()) return mutableListOf()
        return try {
            val content = file.readText(Charsets.UTF_8)
            @Suppress("UNCHECKED_CAST")
            val data = gson.fromJson(content, Map::class.java) as Map<String, Any?>
            val listRaw = data["list"] as? List<*> ?: return mutableListOf()
            val songs = mutableListOf<Song>()
            for (songEl in listRaw) {
                val songMap = songEl as? Map<*, *> ?: continue
                songs.add(Song(
                    id = songMap["id"]?.toString() ?: "",
                    name = songMap["name"]?.toString() ?: "未知",
                    singer = songMap["singer"]?.toString() ?: "未知",
                    time = (songMap["time"] as? Number)?.toLong() ?: 0L,
                    source = songMap["source"]?.toString() ?: "netease"
                ))
            }
            songs
        } catch (e: Throwable) {
            Debug.debug("历史歌单加载失败: ${player.name} - ${e.message}")
            mutableListOf()
        }
    }

    /** 保存历史歌单的歌曲列表到 JSON */
    private fun saveHistorySongs(player: Player, songs: List<Song>) {
        val data = linkedMapOf<String, Any>(
            "name" to "我的点歌历史",
            "platform" to "history",
            "songs" to songs.size,
            "list" to songs.map { mapOf(
                "id" to it.id,
                "name" to it.name,
                "singer" to it.singer,
                "time" to it.time,
                "source" to it.source
            )}
        )
        historyFile(player).writeText(gson.toJson(data), Charsets.UTF_8)
    }

    /**
     * 记录一首歌到玩家点歌历史
     * - 同 id + source 不重复添加 (移到队首)
     * - 超过 historyLimit 时丢弃队尾
     * - 整个操作异步执行, 不阻塞调用方
     */
    fun recordHistory(player: Player, song: OurMusicApi.SongDetail) {
        SchedulerUtil.runAsync(ZMusicGUI.plugin, Runnable {
            try {
                val songs = loadHistorySongs(player)
                // 去重: 移除已有相同 id+source 的项
                songs.removeAll { it.id == song.id && it.source == song.source }
                // 插入队首 (最近点歌在最前)
                songs.add(0, Song(
                    id = song.id,
                    name = song.name,
                    singer = song.singer,
                    time = song.time.toLong(),
                    source = song.source
                ))
                // 截断到限制
                val limit = Config.historyLimit()
                while (songs.size > limit) songs.removeAt(songs.size - 1)
                saveHistorySongs(player, songs)
                refresh(player)
                Debug.debug("历史记录: ${player.name} <- ${song.name} (${songs.size} 首)")
            } catch (e: Throwable) {
                Debug.debug("历史记录失败: ${player.name} - ${e.message}")
            }
        })
    }

    /**
     * 从玩家点歌历史中删除一首歌 (按 id + source)
     * - 删除后立即刷新 GUI (在主线程)
     */
    fun removeSongFromHistory(player: Player, songId: String, source: String, callback: () -> Unit) {
        SchedulerUtil.runAsync(ZMusicGUI.plugin, Runnable {
            try {
                val songs = loadHistorySongs(player)
                val removed = songs.removeAll { it.id == songId && it.source == source }
                if (removed) saveHistorySongs(player, songs)
                refresh(player)
            } catch (e: Throwable) {
                Debug.debug("历史删除失败: ${player.name} - ${e.message}")
            }
            SchedulerUtil.runSync(ZMusicGUI.plugin, Runnable { callback() })
        })
    }

    // ==================== 歌单本地重命名 ====================

    /**
     * 重命名玩家保存的歌单 (本地覆盖, 不修改外部平台)
     *
     * 由于外部平台歌单名称由服务端返回, 无法真正修改,
     * 这里在 PlayerSettings.playlistRenames 中保存本地覆盖名称, 显示时优先使用。
     *
     * @param player     玩家
     * @param platform   平台 id
     * @param playlistId 歌单 ID
     * @param newName    新名称 (空串则等价于移除重命名)
     */
    fun renamePlaylist(player: Player, platform: String, playlistId: String, newName: String) {
        PlayerSettings.setRename(player, platform, playlistId, newName)
        // 清缓存让列表重新读取
        refresh(player)
    }

    /**
     * 处理玩家在聊天栏输入的歌单重命名文本 (由 ChatListener 调用)
     *
     * @param player 玩家
     * @param key    "平台:歌单ID" (来自 ChatListener Ctx.extra)
     * @param input  玩家输入的新名称
     */
    fun handleRenameInput(player: Player, key: String, input: String) {
        if (key.isBlank()) {
            player.sendMessage(Items.color("${Messages.prefix()} &c无效的歌单重命名请求"))
            return
        }
        val parts = key.split(":", limit = 2)
        if (parts.size != 2) {
            player.sendMessage(Items.color("${Messages.prefix()} &c无效的歌单标识: $key"))
            return
        }
        val platform = parts[0]
        val playlistId = parts[1]
        val newName = input.trim()
        if (newName.isEmpty()) {
            // 输入空 → 移除重命名 (恢复原歌单名)
            PlayerSettings.removeRename(player, platform, playlistId)
            refresh(player)
            player.sendMessage(Items.color("${Messages.prefix()} &a已恢复原始歌单名"))
        } else {
            renamePlaylist(player, platform, playlistId, newName)
            player.sendMessage(Items.color("${Messages.prefix()} &a歌单已重命名为: &f$newName"))
        }
        // 重新打开歌单浏览器
        com.ourcraft.zmusicgui.gui.PlaylistBrowserGui.open(player)
    }
}
