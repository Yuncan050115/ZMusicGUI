package com.ourcraft.zmusicgui.gui

import com.ourcraft.zmusicgui.listener.ChatListener
import com.ourcraft.zmusicgui.manager.Config
import com.ourcraft.zmusicgui.manager.Messages
import com.ourcraft.zmusicgui.manager.ScopeManager
import com.ourcraft.zmusicgui.util.Debug
import com.ourcraft.zmusicgui.util.Items
import org.bukkit.entity.Player

/**
 * 主菜单 v3.0.1 — TrMenu 风格 YAML 自定义
 *
 * 布局/材质/名称/描述全部由 GUI/main.yml 定义, 代码只负责:
 *  - 占位符填充 (residence/plot)
 *  - 点击路由 (按 handler id 分发)
 */
object MainGui : ZGui {

    override fun open(player: Player) {
        ChatListener.cancel(player)
        val holder = GuiHolder(this)

        // 占位符: 当前位置
        val resName = ScopeManager.getCurrentResidenceName(player) ?: "无"
        val plotId = ScopeManager.getCurrentPlotId(player) ?: "无"
        val placeholders = mapOf(
            "residence" to resName,
            "plot" to plotId
        )

        val inv = GuiLoader.render("main", holder, placeholders) ?: run {
            // YAML 缺失时降级
            player.sendMessage(Items.color("${Messages.prefix()} &cGUI 配置 main.yml 缺失"))
            return
        }

        // credits 图标按配置显示/隐藏
        if (!Config.showCredits()) {
            GuiLoader.getIconAt("main", 36)?.let {
                if (it.clickHandler == "credits") inv.setItem(36, Items.border())
            }
        }

        player.openInventory(inv)
        Debug.debug("主菜单已打开: ${player.name}")
    }

    override fun handleClick(player: Player, slot: Int) {
        val handler = GuiLoader.getClickHandler("main", slot) ?: return
        when (handler) {
            "quick-play" -> QuickPlayGui.open(player)
            "playlists" -> PlaylistBrowserGui.open(player)
            "control" -> ControlGui.open(player)
            "settings" -> SettingsGui.open(player)
            "credits" -> openWebsite(player)
            "close" -> player.closeInventory()
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
