package com.ourcraft.zmusicgui.manager

import me.clip.placeholderapi.PlaceholderAPI
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.boss.BossBar
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.ConcurrentHashMap

object LyricDisplayManager {

    enum class LyricMode { BOSSBAR, ACTIONBAR }

    private val bossBars = ConcurrentHashMap<Player, BossBar>()
    private var taskId: Int = -1
    private val ser = LegacyComponentSerializer.legacyAmpersand()

    fun start(plugin: JavaPlugin) {
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, Runnable { tick() }, 20L, 10L).taskId
    }

    fun stop() {
        if (taskId >= 0) Bukkit.getScheduler().cancelTask(taskId)
        bossBars.values.forEach { it.removeAll() }
        bossBars.clear()
    }

    private fun tick() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) return

        for (player in Bukkit.getOnlinePlayers()) {
            if (!player.isOnline) continue
            val s = PlayerSettings.getSettings(player)

            if (!s.lyricEnabled) {
                hideBossBar(player)
                continue
            }

            val name = try { PlaceholderAPI.setPlaceholders(player, "%zmusic_playing_name%") } catch (_: Exception) { "" }
            val lyric = try { PlaceholderAPI.setPlaceholders(player, "%zmusic_playing_lyric%") } catch (_: Exception) { "" }
            val singer = try { PlaceholderAPI.setPlaceholders(player, "%zmusic_playing_singer%") } catch (_: Exception) { "" }

            if (name.isEmpty() || name == "%zmusic_playing_name%") {
                hideBossBar(player)
                continue
            }

            when (s.lyricMode) {
                LyricMode.BOSSBAR -> showBossBar(player, name, lyric, singer)
                LyricMode.ACTIONBAR -> showActionBar(player, name, lyric, singer)
            }
        }
    }

    private fun showBossBar(player: Player, name: String, lyric: String, singer: String) {
        val bar = bossBars.getOrPut(player) {
            val key = NamespacedKey("zmusicgui", player.uniqueId.toString().replace("-", ""))
            Bukkit.createBossBar(key, "&b♪ &f加载中...".color(), BarColor.PINK, BarStyle.SOLID)
        }

        if (!bar.players.contains(player)) bar.addPlayer(player)
        bar.isVisible = true
        bar.progress = 1.0

        val hasLyric = lyric.isNotEmpty() && lyric != "%zmusic_playing_lyric%"
        val format = Config.lyricDisplayFormat()

        val displayText = when {
            !hasLyric -> "&b♪ &f$name &8| &7$singer"
            format == "LYRIC_SINGER" -> "&b♪ &f$lyric &8| &7$singer &8| &7$name"
            // LYRIC is default: just current line
            else -> "&b♪ &f$lyric"
        }
        val limited = if (displayText.length > 140) displayText.take(140) + "..." else displayText
        bar.setTitle(limited.color())
    }

    private fun showActionBar(player: Player, name: String, lyric: String, singer: String) {
        hideBossBar(player)
        val text = if (lyric.isNotEmpty() && lyric != "%zmusic_playing_lyric%") {
            "&b♪ &f$lyric &8| &7$singer &8- &7$name"
        } else {
            "&b♪ &f$name &8- &7$singer"
        }
        player.sendActionBar(ser.deserialize(text))
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
