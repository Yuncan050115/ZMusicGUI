package com.ourcraft.zmusicgui.gui

import com.ourcraft.zmusicgui.gui.MainGui
import com.ourcraft.zmusicgui.manager.Config
import com.ourcraft.zmusicgui.manager.LyricDisplayManager.LyricMode
import com.ourcraft.zmusicgui.manager.Messages
import com.ourcraft.zmusicgui.manager.PlayerSettings
import com.ourcraft.zmusicgui.util.Debug
import com.ourcraft.zmusicgui.util.Items
import org.bukkit.Material
import org.bukkit.entity.Player

object LyricsGui : ZGui {

    private val TITLE = Items.deserialize("&6&l歌词显示设置")

    override fun open(player: Player) {
        val s = PlayerSettings.getSettings(player)
        val holder = GuiHolder(this)
        val inv = holder.create(36, TITLE)

        for (i in 0..8) { inv.setItem(i, Items.border(3)); inv.setItem(i + 27, Items.border(3)) }
        for (row in 1..3) { inv.setItem(row * 9, Items.border(3)); inv.setItem(row * 9 + 8, Items.border(3)) }

        inv.setItem(4, Items.divider("个人歌词设置"))

        inv.setItem(11, if (s.lyricEnabled)
            Items.buildGlowing(Material.LIME_DYE, "&a&l✔  歌词: 开", "", "&c▸ 点击关闭")
        else
            Items.build(Material.GRAY_DYE, "&c&l✘  歌词: 关", "", "&a▸ 点击开启"))

        val desc = if (s.lyricMode == LyricMode.BOSSBAR) "&bBossBar" else "&eActionBar"
        inv.setItem(13, Items.build(Material.COMPARATOR, "&6&l🔄  显示模式", "&f当前: $desc", "", "&a▸ 点击切换"))

        inv.setItem(15, Items.build(
            if (s.lyricMode == LyricMode.BOSSBAR) Material.PINK_STAINED_GLASS_PANE else Material.WHITE_STAINED_GLASS_PANE,
            if (s.lyricMode == LyricMode.BOSSBAR) "&d⬛  BossBar &b✔" else "&7⬛  BossBar"))
        inv.setItem(20, Items.build(
            if (s.lyricMode == LyricMode.ACTIONBAR) Material.PINK_STAINED_GLASS_PANE else Material.WHITE_STAINED_GLASS_PANE,
            if (s.lyricMode == LyricMode.ACTIONBAR) "&e⬛  ActionBar &b✔" else "&7⬛  ActionBar"))

        inv.setItem(24, Items.build(Material.CLOCK, "&a🔄  刷新"))
        if (Config.showCredits()) inv.setItem(27, Items.credits())
        inv.setItem(31, Items.back())

        player.openInventory(inv)
        Debug.debug("歌词设置已打开: ${player.name}")
    }

    override fun handleClick(player: Player, slot: Int) {
        when (slot) {
            11 -> {
                val s = PlayerSettings.getSettings(player)
                PlayerSettings.setLyricEnabled(player, !s.lyricEnabled)
                val status = if (!s.lyricEnabled) Messages.player("enabled") else Messages.player("disabled")
                player.sendMessage("${Messages.prefix()} ${Messages.player("lyric-toggled", "status" to status)}")
                Debug.debug("${player.name} 切换歌词: ${!s.lyricEnabled}")
            }
            13 -> {
                val s = PlayerSettings.getSettings(player)
                val next = if (s.lyricMode == LyricMode.BOSSBAR) LyricMode.ACTIONBAR else LyricMode.BOSSBAR
                PlayerSettings.setLyricMode(player, next)
                player.sendMessage("${Messages.prefix()} ${Messages.player("lyric-mode-changed", "mode" to next.name)}")
                Debug.debug("${player.name} 切换模式: $next")
            }
            24 -> {}
            27 -> MainGui.openWebsite(player)
            31 -> MainGui.open(player)
        }
        if (slot != 31) open(player)
    }
}
