package com.ourcraft.zmusicgui.gui

import com.ourcraft.zmusicgui.listener.ChatListener
import com.ourcraft.zmusicgui.manager.Config
import com.ourcraft.zmusicgui.util.Debug
import com.ourcraft.zmusicgui.util.Items
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType

object PlaylistGui : ZGui {

    private val ACT_KEY = NamespacedKey.minecraft("zmg_pl_action")
    private val PLAT_KEY = NamespacedKey.minecraft("zmg_pl_plat")
    private val TYPE_KEY = NamespacedKey.minecraft("zmg_pl_type")

    // ====== 第一级：个人/全服 ======
    override fun open(player: Player) {
        val holder = GuiHolder(this)
        val inv = holder.create(27, Items.deserialize("&6&l歌单管理"))

        for (i in 0..8) { inv.setItem(i, Items.border(3)); inv.setItem(i + 18, Items.border(3)) }
        inv.setItem(9, Items.border(3)); inv.setItem(17, Items.border(3))

        inv.setItem(4, Items.build(Material.NETHER_STAR, "&6&l选择歌单类型"))

        inv.setItem(12, Items.buildGlowing(Material.BOOK,
            "&a&l📖  个人歌单",
            "&7管理你自己的歌单",
            "&7查看列表 · 导入链接",
            "", "&a▸ 点击选择"))
        inv.setItem(14, Items.build(Material.ENCHANTED_BOOK,
            "&e&l🌐  全服歌单",
            "&7管理全服共享的歌单",
            "&7查看列表 · 导入链接",
            "", "&e▸ 点击选择"))

        inv.setItem(22, Items.back())
        if (Config.showCredits()) inv.setItem(18, Items.credits())

        player.openInventory(inv)
        Debug.debug("歌单管理: ${player.name}")
    }

    override fun handleClick(player: Player, slot: Int) {
        when (slot) {
            12 -> { set(player, TYPE_KEY, "personal"); openActionMenu(player) }
            14 -> { set(player, TYPE_KEY, "global"); openActionMenu(player) }
            18 -> MainGui.openWebsite(player)
            22 -> MainGui.open(player)
        }
    }

    // ====== 第二级：查看/导入 ======
    private fun openActionMenu(player: Player) {
        val type = get(player, TYPE_KEY)
        val isPersonal = type == "personal"
        val holder = GuiHolder(actionSubGui)
        val inv = holder.create(27, Items.deserialize(
            if (isPersonal) "&6&l个人歌单" else "&6&l全服歌单"
        ))

        for (i in 0..8) { inv.setItem(i, Items.border(3)); inv.setItem(i + 18, Items.border(3)) }
        inv.setItem(9, Items.border(3)); inv.setItem(17, Items.border(3))

        inv.setItem(4, Items.build(Material.CLOCK,
            if (isPersonal) "&f类型: &a个人歌单" else "&f类型: &e全服歌单"))

        inv.setItem(12, Items.buildGlowing(Material.KNOWLEDGE_BOOK,
            if (isPersonal) "&a&l📋  查看歌单列表" else "&e&l📋  查看歌单列表",
            "&7选择平台 → 输出歌单到聊天栏",
            "&7ZMusic 输出含播放/查看按钮",
            "", "&b▸ 点击选择平台"))
        inv.setItem(14, Items.build(Material.WRITABLE_BOOK,
            if (isPersonal) "&d&l📥  导入歌单" else "&6&l📥  导入歌单",
            "&7选择平台 → 输入歌单链接",
            "&7格式: https://...",
            "", "&b▸ 点击导入"))

        inv.setItem(22, Items.back())
        if (Config.showCredits()) inv.setItem(18, Items.credits())

        player.openInventory(inv)
        Debug.debug("歌单操作: ${player.name} type=$type")
    }

    private val actionSubGui = object : ZGui {
        override fun open(player: Player) = openActionMenu(player)
        override fun handleClick(player: Player, slot: Int) {
            when (slot) {
                12 -> { set(player, ACT_KEY, if (get(player, TYPE_KEY) == "personal") "list" else "list_global"); openPlatformPicker(player) }
                14 -> { set(player, ACT_KEY, if (get(player, TYPE_KEY) == "personal") "import" else "import_global"); openPlatformPicker(player) }
                18 -> MainGui.openWebsite(player)
                22 -> this@PlaylistGui.open(player)
            }
        }
    }

    // ====== 平台选择 ======
    private fun openPlatformPicker(player: Player) {
        val platforms = Config.getPlatforms()
        val rows = if (platforms.size <= 5) 3 else 4
        val holder = GuiHolder(platformSubGui)
        val inv = holder.create(rows * 9, Items.deserialize("&6&l选择平台"))

        for (i in 0..8) { inv.setItem(i, Items.border(3)); inv.setItem(i + (rows - 1) * 9, Items.border(3)) }
        for (r in 1 until rows - 1) { inv.setItem(r * 9, Items.border(3)); inv.setItem(r * 9 + 8, Items.border(3)) }

        val actionName = when (get(player, ACT_KEY)) {
            "list" -> "&a个人歌单列表"
            "import" -> "&d导入个人歌单"
            "list_global" -> "&e全服歌单列表"
            "import_global" -> "&6导入全服歌单"
            else -> ""
        }
        inv.setItem(4, Items.build(Material.CLOCK, "&f操作: $actionName"))

        platforms.values.toList().forEachIndexed { i, p ->
            val slot = 10 + i
            val lines = (p.desc + listOf("", "&b▸ 点击选择")).toTypedArray()
            inv.setItem(slot, Items.build(Material.BOOK, "&l${p.name}", *lines))
        }

        inv.setItem(rows * 9 - 5, Items.back())
        player.openInventory(inv)
    }

    private val platformSubGui = object : ZGui {
        override fun open(player: Player) = openPlatformPicker(player)
        override fun handleClick(player: Player, slot: Int) {
            val platforms = Config.getPlatforms().values.toList()
            val idx = slot - 10
            if (idx in platforms.indices) {
                set(player, PLAT_KEY, platforms[idx].id)
                executeAction(player)
                return
            }
            val rows = if (platforms.size <= 5) 3 else 4
            if (slot == rows * 9 - 5) openActionMenu(player)
        }
    }

    private fun executeAction(player: Player) {
        val plat = get(player, PLAT_KEY)
        when (get(player, ACT_KEY)) {
            "list" -> {
                player.performCommand("zm playlist $plat list")
                player.closeInventory()
            }
            "list_global" -> {
                player.performCommand("zm playlist global $plat list")
                player.closeInventory()
            }
            "import" -> {
                ChatListener.awaitInput(player, plat, "import_personal", "import-prompt")
                player.closeInventory()
            }
            "import_global" -> {
                ChatListener.awaitInput(player, plat, "import_global", "import-prompt")
                player.closeInventory()
            }
        }
    }

    private fun set(player: Player, key: NamespacedKey, value: String) {
        player.persistentDataContainer.set(key, PersistentDataType.STRING, value)
    }
    private fun get(player: Player, key: NamespacedKey) =
        player.persistentDataContainer.get(key, PersistentDataType.STRING) ?: ""
}
