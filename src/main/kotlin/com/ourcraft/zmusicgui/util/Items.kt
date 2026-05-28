package com.ourcraft.zmusicgui.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack

object Items {

    private val ser = LegacyComponentSerializer.legacyAmpersand()

    // ▸ Builders

    fun build(material: Material, name: String, lore: List<String> = emptyList(), glow: Boolean = false): ItemStack {
        val item = ItemStack(material)
        item.editMeta { meta ->
            meta.displayName(ser.deserialize(name).decoration(TextDecoration.ITALIC, false))
            if (lore.isNotEmpty()) {
                meta.lore(lore.map { ser.deserialize(it).decoration(TextDecoration.ITALIC, false) })
            }
            if (glow) {
                meta.addEnchant(Enchantment.LURE, 1, true)
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
            }
        }
        return item
    }

    fun build(material: Material, name: String, vararg lore: String): ItemStack =
        build(material, name, lore.toList())

    fun buildGlowing(material: Material, name: String, vararg lore: String): ItemStack =
        build(material, name, lore.toList(), glow = true)

    // ▸ Presets

    fun border(paneColor: Short = 3): ItemStack {
        val item = ItemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE, 1)
        item.editMeta { meta ->
            meta.displayName(Component.text(" "))
        }
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
        "&7网站: &fhttps://yuncan.xyz"
    )

    // Utility
    fun deserialize(text: String): Component = ser.deserialize(text)
}
