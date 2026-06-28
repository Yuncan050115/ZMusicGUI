package com.ourcraft.zmusicgui.manager

import com.ourcraft.zmusicgui.ZMusicGUI
import com.ourcraft.zmusicgui.manager.LyricDisplayManager.LyricMode
import com.ourcraft.zmusicgui.util.Debug
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 玩家数据管理 — 合并歌词设置 + 偏好设置 + 歌单收藏
 *
 * 统一管理 players.yml, 避免多文件管理器写冲突。
 */
object PlayerSettings {

    data class Settings(
        // 歌词
        var lyricEnabled: Boolean = true,
        var lyricMode: LyricMode = LyricMode.BOSSBAR,
        // 偏好
        var preferredSources: MutableList<String> = mutableListOf("netease"),
        var currentSource: String = "netease",   // 当前选中源 (解决多源按默认搜索问题)
        var defaultScope: String = "self",
        var playMode: String = "sequence",  // 播放模式: sequence / loop_one / shuffle
        // 歌单收藏 (格式: "平台:歌单ID")
        var favorites: MutableList<String> = mutableListOf(),
        // 公开标记 (玩家自己的歌单设为公开, 格式: "平台:歌单ID")
        var publicPlaylists: MutableList<String> = mutableListOf(),
        // 歌单本地重命名覆盖 (key="平台:歌单ID", value=自定义名称)
        var playlistRenames: MutableMap<String, String> = mutableMapOf()
    )

    private val cache = ConcurrentHashMap<String, Settings>()
    private lateinit var configFile: File
    private lateinit var yaml: YamlConfiguration

    private fun base(plugin: ZMusicGUI) = "players.${plugin}"

    fun load(plugin: ZMusicGUI) {
        configFile = File(plugin.dataFolder, "players.yml")
        if (!configFile.exists()) {
            plugin.dataFolder.mkdirs()
            configFile.createNewFile()
        }
        yaml = YamlConfiguration.loadConfiguration(configFile)
    }

    fun save() {
        yaml.save(configFile)
    }

    fun getSettings(player: Player): Settings {
        // 保存玩家名, 供 isPublicByOwner 查找
        yaml.set("players.${player.uniqueId}.name", player.name)
        return cache.getOrPut(player.uniqueId.toString()) {
            val path = "players.${player.uniqueId}"
            val section = yaml.getConfigurationSection(path)
            if (section != null) {
                Settings(
                    lyricEnabled = section.getBoolean("lyric-enabled", Config.lyricDefaultEnabled()),
                    lyricMode = try { LyricMode.valueOf(section.getString("lyric-mode", Config.lyricDefaultMode())!!) }
                        catch (_: Exception) { LyricMode.BOSSBAR },
                    preferredSources = section.getStringList("preferred-sources")
                        .ifEmpty { mutableListOf("netease") }.toMutableList(),
                    currentSource = section.getString("current-source", "netease")!!,
                    defaultScope = section.getString("default-scope", "self")!!,
                    playMode = section.getString("play-mode", "sequence")!!,
                    favorites = section.getStringList("favorites").toMutableList(),
                    publicPlaylists = section.getStringList("public-playlists").toMutableList(),
                    playlistRenames = loadPlaylistRenames(section.getConfigurationSection("playlist-renames"))
                )
            } else {
                Settings(
                    lyricEnabled = Config.lyricDefaultEnabled(),
                    lyricMode = try { LyricMode.valueOf(Config.lyricDefaultMode()) }
                        catch (_: Exception) { LyricMode.BOSSBAR },
                    preferredSources = mutableListOf("netease"),
                    currentSource = "netease",
                    defaultScope = "self"
                )
            }
        }
    }

    // ==================== 歌词 ====================

    fun setLyricEnabled(player: Player, enabled: Boolean) {
        val s = getSettings(player)
        s.lyricEnabled = enabled
        yaml.set("players.${player.uniqueId}.lyric-enabled", enabled)
        save()
        LyricDisplayManager.refresh(player)
    }

    fun setLyricMode(player: Player, mode: LyricMode) {
        val s = getSettings(player)
        s.lyricMode = mode
        yaml.set("players.${player.uniqueId}.lyric-mode", mode.name)
        save()
        LyricDisplayManager.refresh(player)
    }

    // ==================== 偏好 ====================

    fun setPreferredSources(player: Player, sources: List<String>) {
        val s = getSettings(player)
        s.preferredSources = sources.toMutableList()
        yaml.set("players.${player.uniqueId}.preferred-sources", sources)
        // 如果当前源不在新列表中, 重置为第一个
        if (s.currentSource !in sources) {
            s.currentSource = sources.firstOrNull() ?: "netease"
            yaml.set("players.${player.uniqueId}.current-source", s.currentSource)
        }
        save()
    }

    /** 返回默认源 (预设源列表的第一个, 若为空则 netease) */
    fun getDefaultSource(player: Player): String {
        val s = getSettings(player)
        return s.preferredSources.firstOrNull() ?: "netease"
    }

    /** 返回当前选中源 (解决多源按默认搜索问题) */
    fun getCurrentSource(player: Player): String {
        val s = getSettings(player)
        // 如果当前源不在预设源列表中, 重置
        if (s.currentSource !in s.preferredSources) {
            s.currentSource = s.preferredSources.firstOrNull() ?: "netease"
        }
        return s.currentSource
    }

    /** 设置当前选中源 */
    fun setCurrentSource(player: Player, source: String) {
        val s = getSettings(player)
        s.currentSource = source
        yaml.set("players.${player.uniqueId}.current-source", source)
        save()
    }

    fun setDefaultScope(player: Player, scope: String) {
        val s = getSettings(player)
        s.defaultScope = scope
        yaml.set("players.${player.uniqueId}.default-scope", scope)
        save()
    }

    fun getPlayMode(player: Player): String {
        val s = getSettings(player)
        return s.playMode
    }

    fun setPlayMode(player: Player, mode: String) {
        val s = getSettings(player)
        s.playMode = mode
        yaml.set("players.${player.uniqueId}.play-mode", mode)
        save()
    }

    // ==================== 收藏 ====================

    fun toggleFavorite(player: Player, platform: String, playlistId: String): Boolean {
        val s = getSettings(player)
        val key = "$platform:$playlistId"
        val added = if (key in s.favorites) {
            s.favorites.remove(key); false
        } else {
            s.favorites.add(key); true
        }
        yaml.set("players.${player.uniqueId}.favorites", s.favorites)
        save()
        return added
    }

    fun isFavorite(player: Player, platform: String, playlistId: String): Boolean {
        return "$platform:$playlistId" in getSettings(player).favorites
    }

    // ==================== 公开标记 ====================

    fun togglePublic(player: Player, platform: String, playlistId: String): Boolean {
        val s = getSettings(player)
        val key = "$platform:$playlistId"
        val made = if (key in s.publicPlaylists) {
            s.publicPlaylists.remove(key); false
        } else {
            s.publicPlaylists.add(key); true
        }
        yaml.set("players.${player.uniqueId}.public-playlists", s.publicPlaylists)
        save()
        return made
    }

    fun isPublic(player: Player, platform: String, playlistId: String): Boolean {
        return "$platform:$playlistId" in getSettings(player).publicPlaylists
    }

    /** 检查某玩家是否将自己的某歌单设为公开 */
    fun isPublicByOwner(ownerName: String, platform: String, playlistId: String): Boolean {
        val section = yaml.getConfigurationSection("players") ?: return false
        for (uuidKey in section.getKeys(false)) {
            val playerSec = section.getConfigurationSection(uuidKey) ?: continue
            val name = playerSec.getString("name") ?: continue
            if (name.equals(ownerName, ignoreCase = true)) {
                val pubs = playerSec.getStringList("public-playlists")
                return "$platform:$playlistId" in pubs
            }
        }
        return false
    }

    // ==================== 歌单本地重命名 ====================

    /** 从 YAML 读取歌单重命名映射 (key="平台:歌单ID" -> 自定义名称) */
    private fun loadPlaylistRenames(section: org.bukkit.configuration.ConfigurationSection?): MutableMap<String, String> {
        val map = mutableMapOf<String, String>()
        if (section == null) return map
        // 直接以键值对形式存储: playlist-renames.<平台:歌单ID>: 自定义名称
        for (key in section.getKeys(false)) {
            val name = section.getString(key) ?: continue
            if (name.isNotEmpty()) map[key] = name
        }
        return map
    }

    /** 获取歌单的本地重命名 (无则返回原名) */
    fun getRename(player: Player, platform: String, playlistId: String, originalName: String): String {
        val s = getSettings(player)
        return s.playlistRenames["$platform:$playlistId"] ?: originalName
    }

    /** 查询歌单是否有本地重命名 */
    fun getRenameOpt(player: Player, platform: String, playlistId: String): String? {
        val s = getSettings(player)
        return s.playlistRenames["$platform:$playlistId"]
    }

    /** 设置/更新歌单本地重命名; 传入空字符串则等价于 removeRename */
    fun setRename(player: Player, platform: String, playlistId: String, newName: String) {
        val s = getSettings(player)
        val key = "$platform:$playlistId"
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) {
            removeRename(player, platform, playlistId)
            return
        }
        s.playlistRenames[key] = trimmed
        yaml.set("players.${player.uniqueId}.playlist-renames.$key", trimmed)
        save()
        Debug.info("歌单重命名: ${player.name} - $key → $trimmed")
    }

    /** 删除歌单的本地重命名 */
    fun removeRename(player: Player, platform: String, playlistId: String): Boolean {
        val s = getSettings(player)
        val key = "$platform:$playlistId"
        val removed = s.playlistRenames.remove(key) != null
        if (removed) {
            yaml.set("players.${player.uniqueId}.playlist-renames.$key", null)
            save()
            Debug.info("歌单重命名已删除: ${player.name} - $key")
        }
        return removed
    }
}
