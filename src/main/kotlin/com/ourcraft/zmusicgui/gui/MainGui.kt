package com.ourcraft.zmusicgui.gui

import com.ourcraft.zmusicgui.listener.ChatListener
import com.ourcraft.zmusicgui.manager.Config
import com.ourcraft.zmusicgui.manager.Messages
import com.ourcraft.zmusicgui.manager.ScopeManager
import com.ourcraft.zmusicgui.util.Debug
import com.ourcraft.zmusicgui.util.Items
import org.bukkit.Material
import org.bukkit.entity.Player

/**
 * 主菜单 v2.1.0 — 一级菜单
 *
 * 核心入口:
 *  - 快捷点歌     (slot 20)
 *  - 歌单         (slot 24)  — 展示个人/收藏/全服歌单
 *  - 播放控制     (slot 30)
 *  - 个人设置     (slot 32)
 *
 * 收藏置顶, 个人歌单默认隐私
 */
object MainGui : ZGui {

    private const val SLOT_QUICK_PLAY = 20
    private const val SLOT_PLAYLISTS = 24
    private const val SLOT_CONTROL = 30
    private const val SLOT_SETTINGS = 32
    private const val SLOT_LOCATION = 22
    private const val SLOT_CLOSE = 40
    private const val SLOT_CREDITS = 36

    override fun open(player: Player) {
        ChatListener.cancel(player)
        val holder = GuiHolder(this)
        val inv = holder.create(45, Items.deserialize(Config.mainMenuTitle()))

        // 边框
        for (i in 0..8) { inv.setItem(i, Items.border()); inv.setItem(i + 36, Items.border()) }
        for (r in 1..4) { inv.setItem(r * 9, Items.border()); inv.setItem(r * 9 + 8, Items.border()) }

        // 标题
        inv.setItem(4, Items.build(Material.NETHER_STAR,
            Messages.gui("main.welcome"),
            Messages.gui("main.welcome-sub"),
            Messages.gui("main.welcome-footer")))

        // 核心入口 (快捷点歌 + 歌单)
        inv.setItem(SLOT_QUICK_PLAY, Items.buildGlowing(Material.MUSIC_DISC_CAT,
            Messages.gui("main.quick-play"),
            *Messages.guiList("main.quick-play-lore").toTypedArray()))

        inv.setItem(SLOT_PLAYLISTS, Items.build(Material.BOOKSHELF,
            Messages.gui("main.playlists"),
            *Messages.guiList("main.playlists-lore").toTypedArray()))

        // 播放控制 + 设置
        inv.setItem(SLOT_CONTROL, Items.build(Material.JUKEBOX,
            Messages.gui("main.control"),
            *Messages.guiList("main.control-lore").toTypedArray()))

        inv.setItem(SLOT_SETTINGS, Items.build(Material.COMPARATOR,
            Messages.gui("main.settings"),
            *Messages.guiList("main.settings-lore").toTypedArray()))

        // 当前位置 (仅当身处领地/地皮时显示)
        val resName = ScopeManager.getCurrentResidenceName(player)
        val plotId = ScopeManager.getCurrentPlotId(player)
        if (resName != null || plotId != null) {
            val lore = mutableListOf<String>()
            resName?.let { lore.add("&6领地: &f$it") }
            plotId?.let { lore.add("&d地皮: &f$it") }
            lore.add("")
            lore.add("&7点歌时可选对应范围")
            inv.setItem(SLOT_LOCATION, Items.build(Material.COMPASS,
                Messages.gui("main.location"), *lore.toTypedArray()))
        }

        // 关闭
        inv.setItem(SLOT_CLOSE, Items.close())
        if (Config.showCredits()) inv.setItem(SLOT_CREDITS, Items.credits())

        player.openInventory(inv)
        Debug.debug("主菜单已打开: ${player.name}")
    }

    override fun handleClick(player: Player, slot: Int) {
        when (slot) {
            SLOT_QUICK_PLAY -> QuickPlayGui.open(player)
            SLOT_PLAYLISTS -> PlaylistBrowserGui.open(player)
            SLOT_CONTROL -> ControlGui.open(player)
            SLOT_SETTINGS -> SettingsGui.open(player)
            SLOT_CREDITS -> openWebsite(player)
            SLOT_CLOSE -> player.closeInventory()
        }
    }

    fun openWebsite(player: Player) {
        player.sendMessage(Items.color(Messages.player("website")))
        try {
            val comp = net.kyori.adventure.text.Component.text("§a§nhttps://github.com/Yuncan050115")
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.openUrl("https://github.com/Yuncan050115"))
            player.sendMessage(comp)
        } catch (_: Throwable) {
            // 非 Paper 环境, 仅输出文本
        }
    }
}
