package com.ourcraft.zmusicgui.gui

import org.bukkit.Bukkit
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import net.kyori.adventure.text.Component

class GuiHolder(val gui: ZGui) : InventoryHolder {
    private lateinit var inv: Inventory

    fun create(size: Int, title: Component): Inventory {
        inv = Bukkit.createInventory(this, size, title)
        return inv
    }

    override fun getInventory(): Inventory = inv
}
