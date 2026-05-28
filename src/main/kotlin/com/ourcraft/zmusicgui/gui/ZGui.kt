package com.ourcraft.zmusicgui.gui

import org.bukkit.entity.Player

interface ZGui {
    fun open(player: Player)
    fun handleClick(player: Player, slot: Int)
}
