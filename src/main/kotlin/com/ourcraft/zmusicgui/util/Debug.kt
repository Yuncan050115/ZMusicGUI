package com.ourcraft.zmusicgui.util

import com.ourcraft.zmusicgui.manager.Config
import org.bukkit.Bukkit

object Debug {

    // ConsoleSender.sendMessage() 支持 § 颜色码
    private fun log(msg: String) {
        Bukkit.getConsoleSender().sendMessage(msg)
    }

    fun info(msg: String) {
        log("§b[ZMusicGUI]§r $msg")
    }

    fun warn(msg: String) {
        log("§b[ZMusicGUI] §e⚠ $msg")
    }

    fun error(msg: String) {
        log("§b[ZMusicGUI] §c✘ $msg")
    }

    fun debug(msg: String) {
        if (Config.debug()) {
            log("§b[ZMusicGUI] §7[DEBUG]§r $msg")
        }
    }

    /** ASCII 字符画 — Yuncan-Ourcraft */
    fun banner() {
        val banner = """
§6██╗   ██╗██╗   ██╗███╗   ██╗ ██████╗ █████╗ ███╗   ██╗
§6╚██╗ ██╔╝██║   ██║████╗  ██║██╔════╝██╔══██╗████╗  ██║
§6 ╚████╔╝ ██║   ██║██╔██╗ ██║██║     ███████║██╔██╗ ██║
§6  ╚██╔╝  ██║   ██║██║╚██╗██║██║     ██╔══██║██║╚██╗██║
§6   ██║   ╚██████╔╝██║ ╚████║╚██████╗██║  ██║██║ ╚████║
§6   ╚═╝    ╚═════╝ ╚═╝  ╚═══╝ ╚═════╝╚═╝  ╚═╝╚═╝  ╚═══╝
§e        ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓
§e      ▓▓██§fYuncan-Ourcraft服务器出品§e████▓▓
§e    ▓▓██                      ████▓▓
§e      ▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀
§b[ZMusicGUI]§r §fv2.4.3 §7- §aOurcraft Yuncan 出品
§b[ZMusicGUI]§r §7Author: §fYuncan §7| §bhttps://github.com/Yuncan050115
""".trimIndent()
        log(banner)
    }
}
