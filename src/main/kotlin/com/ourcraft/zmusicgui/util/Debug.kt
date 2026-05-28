package com.ourcraft.zmusicgui.util

import com.ourcraft.zmusicgui.manager.Config
import org.bukkit.Bukkit

object Debug {

    // ConsoleSender.sendMessage() 支持 § 颜色码，Logger 不支持
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

    fun banner() {
        log("§b╔══════════════════════════════════╗")
        log("§b║   §eZMusicGUI §fv1.1 §b- §a点歌GUI插件  §b║")
        log("§b║   §7Author: §fYuncan               §b║")
        log("§b║   §7Site: §fhttps://yuncan.xyz      §b║")
        log("§b╚══════════════════════════════════╝")
    }
}
