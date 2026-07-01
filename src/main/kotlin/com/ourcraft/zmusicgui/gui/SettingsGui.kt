package com.ourcraft.zmusicgui.gui

import com.ourcraft.zmusicgui.manager.Config
import com.ourcraft.zmusicgui.manager.LyricDisplayManager.LyricMode
import com.ourcraft.zmusicgui.manager.Messages
import com.ourcraft.zmusicgui.manager.PlayerSettings
import com.ourcraft.zmusicgui.manager.SearchService
import com.ourcraft.zmusicgui.util.Items
import org.bukkit.entity.Player

/**
 * 个人设置 v3.0.1 — TrMenu 风格 YAML 自定义
 *
 * 布局由 GUI/settings.yml 定义, 代码负责:
 *  - 填充占位符 (source_name/source_count/next_source/lyric_mode)
 *  - 点击路由 (source/lyric/back/credits)
 *  - 源循环切换 + 歌词模式循环切换
 */
object SettingsGui : ZGui {

    override fun open(player: Player) {
        val holder = GuiHolder(this)

        val currentSource = PlayerSettings.getCurrentSource(player)
        val sourceName = SearchService.sourceName(currentSource)
        val nextSource = nextSource(currentSource)
        val settings = PlayerSettings.getSettings(player)
        val modeText = if (settings.lyricEnabled) {
            if (settings.lyricMode == LyricMode.BOSSBAR)
                "&aBossBar &7/ &8ActionBar"
            else
                "&8BossBar &7/ &aActionBar"
        } else "&c已关闭"

        val placeholders = mapOf(
            "source_name" to sourceName,
            "source_count" to SearchService.SUPPORTED_SOURCES.size.toString(),
            "next_source" to SearchService.sourceName(nextSource),
            "lyric_mode" to modeText
        )

        val inv = GuiLoader.render("settings", holder, placeholders) ?: run {
            player.sendMessage(Items.color("${Messages.prefix()} &cGUI 配置 settings.yml 缺失"))
            return
        }

        if (!Config.showCredits()) {
            GuiLoader.getIconAt("settings", 18)?.let {
                if (it.clickHandler == "credits") inv.setItem(18, Items.border())
            }
        }

        player.openInventory(inv)
    }

    override fun handleClick(player: Player, slot: Int) {
        val handler = GuiLoader.getClickHandler("settings", slot) ?: return
        when (handler) {
            "source" -> {
                val current = PlayerSettings.getCurrentSource(player)
                val next = nextSource(current)
                PlayerSettings.setCurrentSource(player, next)
                player.sendMessage(Items.color("${Messages.prefix()} &a音乐源已切换为: ${SearchService.sourceName(next)}"))
                open(player)
            }
            "lyric" -> {
                // 切换歌词显示模式: 关闭 → BossBar → ActionBar → 关闭
                val s = PlayerSettings.getSettings(player)
                when {
                    !s.lyricEnabled -> {
                        PlayerSettings.setLyricEnabled(player, true)
                        PlayerSettings.setLyricMode(player, LyricMode.BOSSBAR)
                    }
                    s.lyricMode == LyricMode.BOSSBAR -> {
                        PlayerSettings.setLyricMode(player, LyricMode.ACTIONBAR)
                    }
                    else -> {
                        PlayerSettings.setLyricEnabled(player, false)
                    }
                }
                open(player)
            }
            "credits" -> MainGui.openWebsite(player)
            "back" -> MainGui.open(player)
        }
    }

    /** 循环获取下一个源 */
    private fun nextSource(current: String): String {
        val sources = SearchService.SUPPORTED_SOURCES
        val idx = sources.indexOf(current)
        return if (idx < 0) sources[0] else sources[(idx + 1) % sources.size]
    }
}
