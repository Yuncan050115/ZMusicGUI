package com.ourcraft.zmusicgui.listener

import com.ourcraft.zmusicgui.gui.ControlGui
import com.ourcraft.zmusicgui.gui.GuiHolder
import com.ourcraft.zmusicgui.gui.PlaylistBrowserGui
import com.ourcraft.zmusicgui.gui.PlaylistDetailGui
import com.ourcraft.zmusicgui.gui.PlaylistPushGui
import com.ourcraft.zmusicgui.gui.QuickPlayGui
import com.ourcraft.zmusicgui.util.Debug
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.*
import org.bukkit.event.player.PlayerQuitEvent

object GuiListener : Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    fun onClick(event: InventoryClickEvent) {
        if (event.inventory.holder !is GuiHolder) return
        val holder = event.inventory.holder as GuiHolder
        val player = event.whoClicked as? Player ?: return

        // 阻止所有物品操作
        event.isCancelled = true
        event.result = Event.Result.DENY
        player.setItemOnCursor(null)

        // 只处理点击顶部 GUI 的事件 (忽略底部玩家背包)
        if (event.clickedInventory == null) return
        if (event.clickedInventory !== event.view.topInventory) return

        // 屏蔽无关点击类型
        val click = event.click
        if (click == ClickType.NUMBER_KEY || click == ClickType.DOUBLE_CLICK ||
            click == ClickType.SWAP_OFFHAND || click == ClickType.MIDDLE ||
            click == ClickType.DROP || click == ClickType.CONTROL_DROP) return

        val clicked = event.currentItem ?: return
        if (clicked.type == Material.AIR) return

        val isShift = click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT
        val isRight = click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT

        Debug.debug("GUI点击: 玩家=${player.name} 槽位=${event.slot} shift=$isShift right=$isRight gui=${holder.gui::class.simpleName}")

        // QuickPlayGui: 所有点击路由到 handleClickWithEvent (内部根据视图状态分发)
        if (holder.gui is QuickPlayGui) {
            (holder.gui as QuickPlayGui).handleClickWithEvent(player, event.slot, isShift, isRight)
        }
        // 歌单浏览器支持 Shift/右键
        else if (holder.gui is PlaylistBrowserGui) {
            (holder.gui as PlaylistBrowserGui).handleClickWithEvent(player, event.slot, isShift, isRight)
        }
        // 歌单详情支持 Shift+右键删除歌曲
        else if (holder.gui is PlaylistDetailGui) {
            (holder.gui as PlaylistDetailGui).handleClickWithEvent(player, event.slot, isShift, isRight)
        }
        // 其他 GUI: 仅处理普通左键
        else if (!isShift && !isRight) {
            holder.gui.handleClick(player, event.slot)
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onDrag(event: InventoryDragEvent) {
        if (event.inventory.holder !is GuiHolder) return
        event.isCancelled = true
        event.result = Event.Result.DENY
        (event.whoClicked as? Player)?.setItemOnCursor(null)
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onMove(event: InventoryMoveItemEvent) {
        if (event.destination.holder is GuiHolder || event.source.holder is GuiHolder) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onClose(event: InventoryCloseEvent) {
        if (event.inventory.holder is GuiHolder) {
            Debug.debug("GUI关闭: 玩家=${event.player.name}")
            val player = event.player as? Player ?: return
            player.setItemOnCursor(null)
            // 注意: 不在这里清理 QuickPlayGui/PlaylistBrowserGui/PlaylistDetailGui 的 state,
            // 因为切换 GUI 会触发 closeInventory, 导致 state 丢失 (例如 scope 切换后 state.scope 被重置)
            // 这些 state 改为在 PlayerQuitEvent 时清理
            ControlGui.cleanup(player)
        }
    }

    /** 玩家退出时清理所有 GUI state */
    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val player = event.player
        PlaylistBrowserGui.cleanup(player)
        PlaylistDetailGui.cleanup(player)
        PlaylistPushGui.cleanup(player)
        QuickPlayGui.cleanup(player)
        ControlGui.cleanup(player)
        Debug.debug("玩家退出, 已清理 GUI state: ${player.name}")
    }
}
