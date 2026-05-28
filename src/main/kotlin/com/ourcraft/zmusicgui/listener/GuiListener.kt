package com.ourcraft.zmusicgui.listener

import com.ourcraft.zmusicgui.gui.GuiHolder
import com.ourcraft.zmusicgui.util.Debug
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.*

object GuiListener : Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    fun onClick(event: InventoryClickEvent) {
        // Only handle our GUIs
        if (event.inventory.holder !is GuiHolder) return
        val holder = event.inventory.holder as GuiHolder
        val player = event.whoClicked as? Player ?: return

        // Completely block all item manipulation
        event.isCancelled = true
        event.result = Event.Result.DENY

        // Clear any item on cursor
        player.setItemOnCursor(null)

        // Close any open inventory on bottom to prevent shift-click issues
        if (event.click == ClickType.SHIFT_LEFT || event.click == ClickType.SHIFT_RIGHT) return
        if (event.click == ClickType.NUMBER_KEY) return
        if (event.click == ClickType.DOUBLE_CLICK) return
        if (event.click == ClickType.SWAP_OFFHAND) return

        val clicked = event.currentItem ?: return
        if (clicked.type == Material.AIR) return

        Debug.debug("GUI点击: 玩家=${player.name} 槽位=${event.slot} 物品=${clicked.type}")
        holder.gui.handleClick(player, event.slot)
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
            (event.player as? Player)?.setItemOnCursor(null)
        }
    }
}
