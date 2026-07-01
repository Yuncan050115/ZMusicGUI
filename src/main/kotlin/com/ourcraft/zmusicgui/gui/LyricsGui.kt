package com.ourcraft.zmusicgui.gui

import com.ourcraft.zmusicgui.manager.Config
import com.ourcraft.zmusicgui.manager.LyricDisplayManager.LyricMode
import com.ourcraft.zmusicgui.manager.Messages
import com.ourcraft.zmusicgui.manager.PlayerSettings
import com.ourcraft.zmusicgui.util.Debug
import com.ourcraft.zmusicgui.util.Items
import org.bukkit.entity.Player

/**
 * 歌词设置 GUI v3.0.1 — TrMenu 风格 YAML 自定义
 *
 * 布局由 GUI/lyrics.yml 定义, 代码负责:
 *  - 填充状态占位符 (toggle_name/toggle_hint/mode_desc/bossbar_name/actionbar_name)
 *  - 点击路由 (toggle/mode/refresh/credits/back)
 */
object LyricsGui : ZGui {

    override fun open(player: Player) {
        val s = PlayerSettings.getSettings(player)
        val holder = GuiHolder(this)

        val toggleName = if (s.lyricEnabled) "&a&l✔  歌词: 开" else "&c&l✘  歌词: 关"
        val toggleHint = if (s.lyricEnabled) "&c▸ 点击关闭" else "&a▸ 点击开启"
        val modeDesc = if (s.lyricMode == LyricMode.BOSSBAR) "&bBossBar" else "&eActionBar"
        val bossbarName = if (s.lyricMode == LyricMode.BOSSBAR) "&d⬛  BossBar &b✔" else "&7⬛  BossBar"
        val actionbarName = if (s.lyricMode == LyricMode.ACTIONBAR) "&e⬛  ActionBar &b✔" else "&7⬛  ActionBar"

        val placeholders = mapOf(
            "toggle_name" to toggleName,
            "toggle_hint" to toggleHint,
            "mode_desc" to modeDesc,
            "bossbar_name" to bossbarName,
            "actionbar_name" to actionbarName
        )

        val inv = GuiLoader.render("lyrics", holder, placeholders) ?: run {
            player.sendMessage(Items.color("${Messages.prefix()} &cGUI 配置 lyrics.yml 缺失"))
            return
        }

        if (!Config.showCredits()) {
            GuiLoader.getIconAt("lyrics", 27)?.let {
                if (it.clickHandler == "credits") inv.setItem(27, Items.border())
            }
        }

        player.openInventory(inv)
        Debug.debug("歌词设置已打开: ${player.name}")
    }

    override fun handleClick(player: Player, slot: Int) {
        val handler = GuiLoader.getClickHandler("lyrics", slot) ?: return
        when (handler) {
            "toggle" -> {
                val s = PlayerSettings.getSettings(player)
                PlayerSettings.setLyricEnabled(player, !s.lyricEnabled)
                val status = if (!s.lyricEnabled) Messages.player("enabled") else Messages.player("disabled")
                player.sendMessage("${Messages.prefix()} ${Messages.player("lyric-toggled", "status" to status)}")
                Debug.debug("${player.name} 切换歌词: ${!s.lyricEnabled}")
                open(player)
            }
            "mode" -> {
                val s = PlayerSettings.getSettings(player)
                val next = if (s.lyricMode == LyricMode.BOSSBAR) LyricMode.ACTIONBAR else LyricMode.BOSSBAR
                PlayerSettings.setLyricMode(player, next)
                player.sendMessage("${Messages.prefix()} ${Messages.player("lyric-mode-changed", "mode" to next.name)}")
                Debug.debug("${player.name} 切换模式: $next")
                open(player)
            }
            "refresh" -> open(player)
            "credits" -> MainGui.openWebsite(player)
            "back" -> MainGui.open(player)
        }
    }
}
