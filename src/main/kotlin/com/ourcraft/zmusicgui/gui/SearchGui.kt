package com.ourcraft.zmusicgui.gui

import com.ourcraft.zmusicgui.ZMusicGUI
import com.ourcraft.zmusicgui.gui.MainGui
import com.ourcraft.zmusicgui.listener.ChatListener
import com.ourcraft.zmusicgui.manager.Config
import com.ourcraft.zmusicgui.util.Debug
import com.ourcraft.zmusicgui.util.Items
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType

object SearchGui : ZGui {

    private val MODE_KEY = NamespacedKey.minecraft("zmg_src_mode")

    override fun open(player: Player) = open(player, "play")

    fun open(player: Player, mode: String) {
        val platforms = Config.getPlatforms()
        val isAdmin = ZMusicGUI.plugin.hasAdmin(player)
        val hasPlayall = (mode == "playall" || isAdmin)
        // +1 for playall button if admin
        val btnCount = platforms.size + (if (hasPlayall) 1 else 0)
        val rows = if (btnCount <= 5) 3 else if (btnCount <= 7) 4 else 5
        val holder = GuiHolder(this)
        val inv = holder.create(rows * 9, Items.deserialize("&6&l选择音乐平台"))

        for (i in 0..8) { inv.setItem(i, Items.border(3)); inv.setItem(i + (rows - 1) * 9, Items.border(3)) }
        for (r in 1 until rows - 1) { inv.setItem(r * 9, Items.border(3)); inv.setItem(r * 9 + 8, Items.border(3)) }

        val (modeName, modeColor, modeHint) = when (mode) {
            "play" -> Triple("个人播放", "&a", "&7只有你能听到音乐")
            "music" -> Triple("全服点歌", "&e", "&7分享到全服")
            "playall" -> Triple("全服强制播放", "&c", "&7管理员强制全服播放")
            else -> Triple("个人播放", "&a", "&7只有你能听到音乐")
        }
        inv.setItem(4, Items.build(Material.CLOCK, "&f当前模式: $modeColor$modeName", modeHint))

        // Platform buttons
        platforms.values.toList().forEachIndexed { i, p ->
            val slot = 10 + i
            val lines = (p.desc + listOf("", "&b▸ 点击选择此平台")).toTypedArray()
            inv.setItem(slot, Items.build(Material.RED_WOOL, "&l${p.name}", *lines))
        }

        // Playall button as last item (admin only)
        if (hasPlayall) {
            val slot = 10 + platforms.size
            inv.setItem(slot, Items.build(Material.COMMAND_BLOCK,
                "&c&l⚡  全服强制播放",
                "&7强制所有在线玩家播放",
                "&7需要管理员权限",
                "", "&c▸ 管理员专用"))
        }

        if (Config.showCredits()) inv.setItem(rows * 9 - 9, Items.credits())
        inv.setItem(rows * 9 - 5, Items.back())

        player.persistentDataContainer.set(MODE_KEY, PersistentDataType.STRING, mode)
        player.openInventory(inv)
        Debug.debug("搜索界面: ${player.name} mode=$mode platforms=${platforms.size}")
    }

    override fun handleClick(player: Player, slot: Int) {
        val platforms = Config.getPlatforms()
        val isAdmin = ZMusicGUI.plugin.hasAdmin(player)
        val hasPlayall = isAdmin
        val btnCount = platforms.size + (if (hasPlayall) 1 else 0)
        val rows = if (btnCount <= 5) 3 else if (btnCount <= 7) 4 else 5

        val platList = platforms.values.toList()
        val idx = slot - 10
        if (idx in platList.indices) {
            val p = platList[idx]
            val mode = player.persistentDataContainer.get(MODE_KEY, PersistentDataType.STRING) ?: "play"
            ChatListener.awaitInput(player, p.id, mode)
            player.closeInventory()
            return
        }
        // Playall button
        if (hasPlayall && idx == platforms.size) {
            ChatListener.awaitInput(player, platforms.values.first().id, "playall")
            player.closeInventory()
            return
        }
        if (slot == rows * 9 - 9) { com.ourcraft.zmusicgui.gui.MainGui.openWebsite(player); return }
        if (slot == rows * 9 - 5) MainGui.open(player)
    }
}
