package com.ourcraft.zmusicgui.util

import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta

/**
 * 物品构建工具 — 兼容 1.13+ Bukkit API (不使用 Paper 独有的 editMeta)
 */
object Items {

    fun build(material: Material, name: String, lore: List<String> = emptyList(), glow: Boolean = false): ItemStack {
        val item = ItemStack(material)
        val meta: ItemMeta = item.itemMeta ?: return item
        meta.setDisplayName(color(name))
        if (lore.isNotEmpty()) {
            meta.lore = lore.map { color(it) }
        }
        if (glow) {
            meta.addEnchant(Enchantment.LURE, 1, true)
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
        }
        item.itemMeta = meta
        return item
    }

    fun build(material: Material, name: String, vararg lore: String): ItemStack =
        build(material, name, lore.toList())

    fun buildGlowing(material: Material, name: String, vararg lore: String): ItemStack =
        build(material, name, lore.toList(), glow = true)

    fun border(paneColor: Short = 3): ItemStack {
        val item = ItemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE, 1)
        val meta: ItemMeta = item.itemMeta ?: return item
        meta.setDisplayName(" ")
        item.itemMeta = meta
        return item
    }

    fun back(): ItemStack = build(
        Material.ARROW,
        "&a← 返回主菜单",
        "&7点击回到主页"
    )

    fun close(): ItemStack = build(
        Material.BARRIER,
        "&c✕ 关闭菜单",
        "&7点击关闭"
    )

    fun divider(title: String): ItemStack = build(
        Material.NAME_TAG,
        "&8▬▬▬ &6&l$title &8▬▬▬",
        "&7"
    )

    fun credits(): ItemStack = build(
        Material.KNOWLEDGE_BOOK,
        "&8&l📖  关于",
        "&7GUI插件作者: &fYuncan",
        "&7ZMusic插件作者: &f真心",
        "&7CE增强: &f0XiaoMai0",
        "&7网站: &fhttps://github.com/Yuncan050115"
    )

    /** 颜色代码转换 (& → §) */
    fun color(text: String): String = ChatColor.translateAlternateColorCodes('&', text)

    /** 反序列化文本为带颜色的 String (供 GuiHolder.create 使用) */
    fun deserialize(text: String): String = color(text)
}
