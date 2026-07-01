package com.ourcraft.zmusicgui.manager

import com.ourcraft.zmusicgui.ZMusicGUI
import com.ourcraft.zmusicgui.util.Debug
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

/**
 * 配置管理 (config.yml)
 *
 * 包含 api / 点歌 / 歌词 / 范围 / GUI 等配置项。
 */
object Config {

    private lateinit var yaml: YamlConfiguration
    private lateinit var file: File

    fun load(plugin: ZMusicGUI) {
        file = File(plugin.dataFolder, "config.yml")
        plugin.dataFolder.mkdirs()
        // false: 文件已存在则不覆盖 (避免重启时玩家自定义配置被重置)
        plugin.saveResource("config.yml", false)
        yaml = YamlConfiguration.loadConfiguration(file)
    }

    fun reload(plugin: ZMusicGUI) {
        yaml = YamlConfiguration.loadConfiguration(file)
    }

    fun debug() = yaml.getBoolean("debug", false)

    /** 聊天栏消息前缀 */
    fun prefix(): String = yaml.getString("prefix", "&b[ZMusicGUI]&r")!!

    // ==================== API ====================
    /** Ourcraft Music API 根地址 (服务端支持多平台 netease/kugou/kuwo) */
    fun ourcraftApi(): String = yaml.getString("api.ourcraft", yaml.getString("api.netease", "https://music.yuncan.xyz"))!!

    // ==================== 点歌 ====================
    fun musicCost(): Double = yaml.getDouble("music.cost", 10.0)
    fun cooldownSeconds(): Int = yaml.getInt("music.cooldown-seconds", 5)
    fun defaultSource(): String = yaml.getString("music.default-source", "netease")!!
    fun searchLimit(): Int = yaml.getInt("music.search-limit", 10)

    // ==================== 歌词 ====================
    fun lyricDefaultEnabled() = yaml.getBoolean("lyric.default-enabled", true)
    fun lyricDefaultMode(): String = yaml.getString("lyric.default-mode", "BOSSBAR")!!
    fun lyricUpdateTicks(): Long = yaml.getLong("lyric.update-ticks", 10L)
    fun lyricDisplayFormat(): String = yaml.getString("lyric.display-format", "LYRIC")!!

    // ==================== 范围点歌 ====================
    /** 领地插件类型: residence / lands / dominion (用于 RESIDENCE scope 底层实现) */
    fun regionPlugin(): String = yaml.getString("scope.region-plugin", "residence")!!.lowercase()
    fun scopeCost(scope: String): Double = yaml.getDouble("scope.$scope.cost", 0.0)
    fun scopeRequireApproval(scope: String): Boolean = yaml.getBoolean("scope.$scope.require-approval", true)
    fun scopeApprovalTimeout(): Long = yaml.getLong("scope.approval-timeout-seconds", 60L)
    fun worldPayeeAccount(): String = yaml.getString("scope.world.payee-account", "") ?: ""

    // ==================== 歌单 ====================
    fun playlistDefaultPrivate(): Boolean = yaml.getBoolean("playlist.default-private", true)
    fun playlistCacheTtl(): Long = yaml.getLong("playlist.cache-ttl-seconds", 30L) * 1000L
    fun playlistSongsPerPage(): Int = yaml.getInt("playlist.songs-per-page", 21)
    fun historyLimit(): Int = yaml.getInt("playlist.history-limit", 200).coerceIn(10, 1000)

    // ==================== GUI ====================
    fun showCredits() = yaml.getBoolean("gui.show-credits", true)
    fun mainMenuTitle() = yaml.getString("gui.main-menu-title", "&6&l☄ Ourcraft &8▸ &b音乐中心")!!
    fun quickPlayTitle() = yaml.getString("gui.quick-play-title", "&6&l🎵 快捷点歌")!!
    fun playlistTitle() = yaml.getString("gui.playlist-title", "&6&l歌单浏览")!!
    fun controlTitle() = yaml.getString("gui.control-title", "&6&l播放控制")!!

    /** 兼容旧 API (cooldown -> cooldownSeconds) */
    fun cooldown() = cooldownSeconds()
    fun publicRequestEnabled() = true  // 始终允许全服范围
}
