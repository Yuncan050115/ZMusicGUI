package com.ourcraft.zmusicgui.gui

import com.ourcraft.zmusicgui.ZMusicGUI
import com.ourcraft.zmusicgui.gui.MainGui
import com.ourcraft.zmusicgui.manager.Config
import com.ourcraft.zmusicgui.manager.Messages
import com.ourcraft.zmusicgui.manager.PlayerSettings
import com.ourcraft.zmusicgui.util.Debug
import com.ourcraft.zmusicgui.util.Items
import me.clip.placeholderapi.PlaceholderAPI
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.util.concurrent.ConcurrentHashMap

object ControlGui : ZGui {

    private val TITLE = Items.deserialize("&6&l播放控制")
    private val refreshTasks = ConcurrentHashMap<Player, BukkitTask>()

    override fun open(player: Player) {
        // Cancel previous refresh
        refreshTasks.remove(player)?.cancel()

        val holder = GuiHolder(this)
        val inv = holder.create(36, TITLE)
        val hasPapi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null

        for (i in 0..8) { inv.setItem(i, Items.border(3)); inv.setItem(i + 27, Items.border(3)) }
        for (row in 1..3) { inv.setItem(row * 9, Items.border(3)); inv.setItem(row * 9 + 8, Items.border(3)) }

        inv.setItem(4, Items.divider("当前播放"))

        if (hasPapi) {
            val name = papi(player, "%zmusic_playing_name%")
            val singer = papi(player, "%zmusic_playing_singer%")
            val cur = papi(player, "%zmusic_time_current%")
            val max = papi(player, "%zmusic_time_max%")

            if (name != "无") {
                inv.setItem(13, Items.build(Material.MUSIC_DISC_13, "&b&l♪  正在播放",
                    "&f歌名: &e$name", "&f歌手: &d$singer",
                    "&f进度: &a$cur &7/ &a$max"))
                val lyric = papi(player, "%zmusic_playing_lyric%")
                if (lyric != "无" && lyric.length < 60) {
                    inv.setItem(22, Items.build(Material.PAPER, "&d&l📝  歌词", "&f\"$lyric\""))
                }
            } else {
                inv.setItem(13, Items.build(Material.MUSIC_DISC_11,
                    "&7&l♪  当前没有播放", "&7使用点歌功能来播放一首吧!"))
            }
        } else {
            inv.setItem(13, Items.build(Material.MUSIC_DISC_11, "&7&l♪  播放信息", "&c需要 PlaceholderAPI"))
        }

        inv.setItem(20, Items.build(Material.REDSTONE_BLOCK, "&c&l⏹  停止播放"))
        inv.setItem(24, Items.build(Material.COMPASS, "&a&l🔄  刷新"))

        val s = PlayerSettings.getSettings(player)
        inv.setItem(30, Items.build(
            if (s.lyricEnabled) Material.LIME_DYE else Material.GRAY_DYE,
            if (s.lyricEnabled) "&a&l🎤  歌词: 开" else "&c&l🎤  歌词: 关",
            "&7点击切换"))

        if (Config.showCredits()) inv.setItem(27, Items.credits())
        inv.setItem(32, Items.back())

        player.openInventory(inv)
        Debug.debug("播放控制已打开: ${player.name} hasPapi=$hasPapi")

        // Auto refresh every 2 seconds
        val task = object : BukkitRunnable() {
            override fun run() {
                if (!player.isOnline) { refreshTasks.remove(player); return }
                val topHolder = player.openInventory.topInventory.holder
                if (topHolder is GuiHolder && topHolder.gui == this@ControlGui) {
                    refreshTasks.remove(player)?.cancel()
                    open(player)
                } else {
                    refreshTasks.remove(player)
                }
            }
        }.runTaskLater(ZMusicGUI.plugin, 40L)
        refreshTasks[player] = task
    }

    override fun handleClick(player: Player, slot: Int) {
        // Cancel auto-refresh when navigating away
        val navigating = slot in listOf(32)
        if (navigating) refreshTasks.remove(player)?.cancel()

        when (slot) {
            20 -> {
                player.performCommand("zm stop")
                player.sendMessage("${Messages.prefix()} ${Messages.player("playback-stopped")}")
                Debug.debug("${player.name} 执行 zm stop")
            }
            24, 13, 22 -> open(player)
            30 -> {
                val s = PlayerSettings.getSettings(player)
                PlayerSettings.setLyricEnabled(player, !s.lyricEnabled)
                val status = if (!s.lyricEnabled) Messages.player("enabled") else Messages.player("disabled")
                player.sendMessage("${Messages.prefix()} ${Messages.player("lyric-toggled", "status" to status)}")
                open(player)
            }
            27 -> MainGui.openWebsite(player)
            32 -> MainGui.open(player)
        }
    }

    private fun papi(player: Player, placeholder: String): String =
        try { val r = PlaceholderAPI.setPlaceholders(player, placeholder); if (r == placeholder) "无" else r }
        catch (_: Exception) { "无" }
}
