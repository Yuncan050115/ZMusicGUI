package com.ourcraft.zmusicgui.gui

import com.ourcraft.zmusicgui.ZMusicGUI
import com.ourcraft.zmusicgui.listener.ChatListener
import com.ourcraft.zmusicgui.manager.Config
import com.ourcraft.zmusicgui.util.Debug
import com.ourcraft.zmusicgui.util.Items
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Material
import org.bukkit.entity.Player

object MainGui : ZGui {

    override fun open(player: Player) {
        ChatListener.cancel(player)
        val isAdmin = ZMusicGUI.plugin.hasAdmin(player)
        val holder = GuiHolder(this)
        val inv = holder.create(45, Items.deserialize(Config.mainMenuTitle()))

        for (i in 0..8) { inv.setItem(i, Items.border(3)); inv.setItem(i + 36, Items.border(3)) }
        for (row in 1..4) { inv.setItem(row * 9, Items.border(3)); inv.setItem(row * 9 + 8, Items.border(3)) }

        inv.setItem(4, Items.build(Material.NETHER_STAR, "&6&l☄ &e&lOurcraft 音乐中心 &6&l☄", "",
            "&7欢迎使用新一代点歌系统", "&7由 ZMusic 强力驱动"))

        inv.setItem(20, Items.buildGlowing(Material.MUSIC_DISC_CAT,
            "&a&l🎵  点歌播放", "&7搜索歌曲 → 播放给自己听", "", "&a▸ 点击开始"))

        if (Config.publicRequestEnabled()) {
            inv.setItem(21, Items.build(Material.NOTE_BLOCK,
                "&e&l📢  全服点歌", "&7分享歌曲给全服玩家", "", "&e▸ 点击开始"))
        }

        inv.setItem(22, Items.build(Material.BOOKSHELF,
            "&d&l📋  歌单管理", "&7查看 / 导入歌单", "", "&d▸ 点击管理"))

        inv.setItem(23, Items.build(Material.JUKEBOX,
            "&6&l⏯  播放控制", "&7停止 / 当前播放信息", "", "&6▸ 点击查看"))

        inv.setItem(24, Items.build(Material.WRITABLE_BOOK,
            "&b&l🎤  歌词显示", "&7BossBar / ActionBar", "", "&b▸ 点击设置"))

        if (Config.showCredits()) {
            inv.setItem(37, Items.credits())
        }
        inv.setItem(40, Items.close())

        player.openInventory(inv)
        Debug.debug("主菜单已打开: ${player.name} admin=$isAdmin")
    }

    override fun handleClick(player: Player, slot: Int) {
        when (slot) {
            20 -> SearchGui.open(player, "play")
            21 -> { if (Config.publicRequestEnabled()) SearchGui.open(player, "music") }
            22 -> PlaylistGui.open(player)
            23 -> ControlGui.open(player)
            24 -> LyricsGui.open(player)
            37 -> openWebsite(player)
            40 -> player.closeInventory()
        }
    }

    fun openWebsite(player: Player) {
        val ser = LegacyComponentSerializer.legacyAmpersand()
        player.sendMessage(ser.deserialize("&b[ZMusicGUI] &a作者网站: "))
        player.sendMessage(
            Component.text("https://yuncan.xyz")
                .clickEvent(ClickEvent.openUrl("https://yuncan.xyz"))
        )
    }
}
