package com.ourcraft.zmusicgui.gui

import com.ourcraft.zmusicgui.manager.Config
import com.ourcraft.zmusicgui.manager.LyricDisplayManager.LyricMode
import com.ourcraft.zmusicgui.manager.Messages
import com.ourcraft.zmusicgui.manager.PlayerSettings
import com.ourcraft.zmusicgui.manager.SearchService
import com.ourcraft.zmusicgui.util.Items
import org.bukkit.Material
import org.bukkit.entity.Player

/**
 * 个人设置 v2.2.1 — 默认源循环切换 + 歌词模式循环切换
 *
 * 支持 4 个源: 网易云 / 酷狗 / 酷我 / QQ音乐
 * 所有源共用 OurMusicApi (调用 ourcraft-music-api 服务端)
 */
object SettingsGui : ZGui {

    private const val SLOT_SOURCE = 11
    private const val SLOT_LYRIC = 13
    private const val SLOT_ACCOUNT = 15
    private const val SLOT_BACK = 22

    override fun open(player: Player) {
        val holder = GuiHolder(this)
        val inv = holder.create(27, Items.deserialize("&6&l⚙ 个人设置"))

        for (i in 0..8) { inv.setItem(i, Items.border()); inv.setItem(i + 18, Items.border()) }
        inv.setItem(9, Items.border()); inv.setItem(17, Items.border())

        // 当前默认源
        val currentSource = PlayerSettings.getCurrentSource(player)
        val sourceName = SearchService.sourceName(currentSource)
        inv.setItem(4, Items.build(Material.COMPARATOR, "&6&l⚙ 个人设置",
            "&7默认音乐源: $sourceName", "",
            "&7点击下方切换源", "&7共 ${SearchService.SUPPORTED_SOURCES.size} 个源可选",
            "&7网易云由 &fYuncan &7提供 API"))

        // 源切换按钮 (点击循环切换)
        val nextSource = nextSource(currentSource)
        inv.setItem(SLOT_SOURCE, Items.buildGlowing(Material.MUSIC_DISC_CAT,
            "&f🎵 音乐源: $sourceName",
            "&7点击循环切换音乐源",
            "&7当前: $sourceName",
            "&b▸ 下一个: ${SearchService.sourceName(nextSource)}",
            "",
            "&7支持: &c网易云 &7| &a酷狗 &7| &6酷我 &7| &dQQ"))

        // 歌词显示模式切换
        val settings = PlayerSettings.getSettings(player)
        val modeText = if (settings.lyricEnabled) {
            if (settings.lyricMode == LyricMode.BOSSBAR)
                "&aBossBar &7/ &8ActionBar"
            else
                "&8BossBar &7/ &aActionBar"
        } else "&c已关闭"
        inv.setItem(SLOT_LYRIC, Items.build(Material.WRITABLE_BOOK, "&b&l歌词显示",
            "&7点击循环切换显示模式", "&7当前: $modeText", "",
            "&7循环: 关闭 → BossBar → ActionBar → 关闭",
            "&7BossBar: 顶部血条样式", "&7ActionBar: 物品栏上方"))

        // 账号管理入口 (2 平台: QQ + 网易云)
        val bindCount = listOf("qq", "netease").count { PlayerSettings.hasAccount(player, it) }
        inv.setItem(SLOT_ACCOUNT, Items.buildGlowing(Material.NAME_TAG, "&e&l🔗 账号管理",
            "&7绑定音乐平台账号播放 VIP 歌曲",
            "&7已绑定: &f$bindCount &7个平台",
            "",
            "&7QQ: ${if (PlayerSettings.hasAccount(player, "qq")) "&a✓ 已绑定" else "&c✗ 未绑定"}",
            "&7网易云: ${if (PlayerSettings.hasAccount(player, "netease")) "&a✓ 已绑定" else "&c✗ 未绑定"}",
            "&8酷狗/酷我 VIP 已下线",
            "",
            "&a▸ 点击管理"))

        inv.setItem(SLOT_BACK, Items.back())
        if (Config.showCredits()) inv.setItem(18, Items.credits())

        player.openInventory(inv)
    }

    override fun handleClick(player: Player, slot: Int) {
        when (slot) {
            SLOT_SOURCE -> {
                // 循环切换到下一个源
                val current = PlayerSettings.getCurrentSource(player)
                val next = nextSource(current)
                PlayerSettings.setCurrentSource(player, next)
                player.sendMessage(Items.color("${Messages.prefix()} &a音乐源已切换为: ${SearchService.sourceName(next)}"))
                open(player)
            }
            SLOT_LYRIC -> {
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
            SLOT_ACCOUNT -> AccountGui.open(player)
            18 -> MainGui.openWebsite(player)
            SLOT_BACK -> MainGui.open(player)
        }
    }

    /** 循环获取下一个源 */
    private fun nextSource(current: String): String {
        val sources = SearchService.SUPPORTED_SOURCES
        val idx = sources.indexOf(current)
        return if (idx < 0) sources[0] else sources[(idx + 1) % sources.size]
    }
}
