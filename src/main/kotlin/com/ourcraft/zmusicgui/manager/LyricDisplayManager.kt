package com.ourcraft.zmusicgui.manager

import com.ourcraft.zmusicgui.music.MusicPlayer
import com.ourcraft.zmusicgui.util.SchedulerUtil
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.boss.BossBar
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * 歌词显示引擎
 *
 * 从 MusicPlayer.getState 读取播放信息。
 * 兼容 Paper / Spigot / Folia
 * - BossBar: Bukkit 原生 API
 * - ActionBar: 反射调用 Paper 的 sendActionBar(Component), 回退到 Spigot 的 Spigot 方法
 */
object LyricDisplayManager {

    enum class LyricMode { BOSSBAR, ACTIONBAR }

    private val bossBars = ConcurrentHashMap<Player, BossBar>()
    private var task: Any? = null

    // 反射缓存: Paper 的 Player.sendActionBar(Component)
    private var sendActionBarPaper: Method? = null
    private var legacySer: Any? = null
    private var legacyDeserialize: Method? = null
    private var usePaperAction = false

    fun start(plugin: JavaPlugin) {
        // 检测 Paper API: Player.sendActionBar(Component)
        try {
            sendActionBarPaper = Player::class.java.getMethod("sendActionBar", Class.forName("net.kyori.adventure.text.Component"))
            val paperSerClass = Class.forName("net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer")
            val legacyAmpersand = paperSerClass.getMethod("legacyAmpersand")
            legacySer = legacyAmpersand.invoke(null)
            legacyDeserialize = paperSerClass.getMethod("deserialize", String::class.java)
            usePaperAction = true
        } catch (_: Throwable) {
            // 非 Paper 或老版本, 回退到 Spigot 的 spigot().sendMessage(ChatMessageType.ACTION_BAR, BaseComponent[])
            usePaperAction = false
        }
        task = SchedulerUtil.runSyncTimer(plugin, Runnable { tick() }, 20L, Config.lyricUpdateTicks())
    }

    fun stop() {
        task?.let { SchedulerUtil.cancelTask(it) }
        task = null
        bossBars.values.forEach { it.removeAll() }
        bossBars.clear()
    }

    private fun tick() {
        for (player in Bukkit.getOnlinePlayers()) {
            if (!player.isOnline) continue
            val s = PlayerSettings.getSettings(player)

            if (!s.lyricEnabled) {
                hideBossBar(player)
                continue
            }

            val state = MusicPlayer.getState(player)
            if (state == null) {
                hideBossBar(player)
                continue
            }

            val name = state.song.name
            val singer = state.song.singer
            // 取当前时间对应的歌词行
            val lyric = state.lyrics.getOrNull(state.lastLyricIndex)?.text ?: ""

            when (s.lyricMode) {
                LyricMode.BOSSBAR -> showBossBar(player, name, lyric, singer, state)
                LyricMode.ACTIONBAR -> showActionBar(player, name, lyric, singer, state)
            }
        }
    }

    private fun showBossBar(player: Player, name: String, lyric: String, singer: String, state: MusicPlayer.PlayState) {
        val bar = bossBars.getOrPut(player) {
            val key = NamespacedKey("zmusicgui", player.uniqueId.toString().replace("-", ""))
            Bukkit.createBossBar(key, "♪ 加载中...".color(), BarColor.PINK, BarStyle.SOLID)
        }

        if (!bar.players.contains(player)) bar.addPlayer(player)
        bar.isVisible = true
        // 进度 0.0 ~ 1.0
        val total = state.song.time.coerceAtLeast(1)
        bar.progress = (state.currentTime.toDouble() / total).coerceIn(0.0, 1.0)

        val hasLyric = lyric.isNotEmpty()
        val format = Config.lyricDisplayFormat()

        val displayText = when {
            !hasLyric -> "&b♪ &f$name &8| &7$singer"
            format == "LYRIC_SINGER" -> "&b♪ &f$lyric &8| &7$singer &8| &7$name"
            else -> "&b♪ &f$lyric"
        }
        val limited = if (displayText.length > 140) displayText.take(140) + "..." else displayText
        bar.setTitle(limited.color())
    }

    private fun showActionBar(player: Player, name: String, lyric: String, singer: String, state: MusicPlayer.PlayState) {
        hideBossBar(player)
        val hasLyric = lyric.isNotEmpty()
        val format = Config.lyricDisplayFormat()

        val text = when {
            !hasLyric -> "&b♪ &f$name &8- &7$singer"
            format == "LYRIC_SINGER" -> "&b♪ &f$lyric &8| &7$singer &8- &7$name"
            else -> "&b♪ &f$lyric"
        }.color()

        if (usePaperAction) {
            // Paper: sendActionBar(Component)
            try {
                val comp = legacyDeserialize!!.invoke(legacySer, text)
                sendActionBarPaper!!.invoke(player, comp)
                return
            } catch (_: Throwable) { }
        }
        // Spigot 回退: player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(text))
        try {
            val spigot = player.javaClass.getMethod("spigot").invoke(player)
            val chatMsgType = Class.forName("net.md_5.bungee.api.ChatMessageType")
            val actionBar = chatMsgType.enumConstants.first { it.toString() == "ACTION_BAR" }
            val textComponentClass = Class.forName("net.md_5.bungee.api.chat.TextComponent")
            val fromLegacy = textComponentClass.getMethod("fromLegacyText", String::class.java)
            val components = fromLegacy.invoke(null, text) as Array<*>
            val sendMessage = spigot.javaClass.getMethod("sendMessage", chatMsgType, java.lang.reflect.Array.newInstance(textComponentClass, 0).javaClass)
            sendMessage.invoke(spigot, actionBar, components)
        } catch (_: Throwable) {
            // 最终回退: 直接发送聊天消息
            player.sendMessage(text)
        }
    }

    private fun hideBossBar(player: Player) {
        bossBars[player]?.let { bar ->
            bar.isVisible = false
            bar.removePlayer(player)
        }
    }

    fun refresh(player: Player) {
        hideBossBar(player)
    }

    private fun String.color() = replace('&', '§')
}
