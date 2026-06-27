package com.ourcraft.zmusicgui.gui

import org.bukkit.Bukkit
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder

/**
 * GUI 容器持有者 — 用于识别本插件打开的 GUI
 * 使用 String 标题兼容 1.13+ Bukkit API (不依赖 Paper Component)
 */
class GuiHolder(val gui: ZGui) : InventoryHolder {
    private lateinit var inv: Inventory

    fun create(size: Int, title: String): Inventory {
        inv = Bukkit.createInventory(this, size, title)
        return inv
    }

    override fun getInventory(): Inventory = inv
}
