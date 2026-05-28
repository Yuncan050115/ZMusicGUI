package com.ourcraft.zmusicgui

import com.ourcraft.zmusicgui.gui.MainGui
import com.ourcraft.zmusicgui.listener.ChatListener
import com.ourcraft.zmusicgui.listener.GuiListener
import com.ourcraft.zmusicgui.manager.Config
import com.ourcraft.zmusicgui.manager.LyricDisplayManager
import com.ourcraft.zmusicgui.manager.Messages
import com.ourcraft.zmusicgui.manager.PlayerSettings
import com.ourcraft.zmusicgui.util.Debug
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

class ZMusicGUI : JavaPlugin() {

    companion object {
        lateinit var plugin: ZMusicGUI
            private set
    }

    override fun onEnable() {
        plugin = this

        Debug.banner()

        // First load messages so we can use them
        Messages.load(this)

        Debug.info(Messages.console("enabling", "version" to "1.1"))

        if (server.pluginManager.getPlugin("ZMusic") == null) {
            Debug.error(Messages.console("zmusic-missing"))
            server.pluginManager.disablePlugin(this)
            return
        }
        Debug.info(Messages.console("zmusic-detected"))

        Config.load(this)
        Debug.info(Messages.console("config-loaded", "debug" to Config.debug().toString()))

        PlayerSettings.load(this)
        Debug.info(Messages.console("settings-loaded"))

        val hasPapi = server.pluginManager.getPlugin("PlaceholderAPI") != null
        LyricDisplayManager.start(this)
        Debug.info(Messages.console("lyric-mounted", "papi" to if (hasPapi) "已连接" else "未安装"))

        server.pluginManager.registerEvents(GuiListener, this)
        server.pluginManager.registerEvents(ChatListener, this)
        Debug.info(Messages.console("events-registered"))

        Debug.info(Messages.console("command-registered"))
        Debug.info(Messages.console("enabled", "version" to "1.1"))
    }

    override fun onDisable() {
        Debug.info(Messages.console("disabling"))
        LyricDisplayManager.stop()
        PlayerSettings.save()
        Debug.info(Messages.console("disabled"))
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isNotEmpty() && args[0].equals("reload", ignoreCase = true)) {
            if (!sender.hasPermission("zmusicgui.admin")) {
                sender.sendMessage(Messages.cmd("reload.no-permission"))
                return true
            }
            Config.reload(this)
            Messages.reload(this)
            sender.sendMessage(Messages.cmd("reload.success"))
            Debug.info(">> 配置已通过 /zmg reload 重载")
            return true
        }

        if (sender is Player) {
            if (sender.hasPermission("zmusicgui.use")) {
                Debug.debug("命令: ${sender.name} 打开主菜单")
                MainGui.open(sender)
            } else {
                sender.sendMessage(Messages.player("no-permission", "permission" to "zmusicgui.use"))
            }
        }
        return true
    }

    fun hasAdmin(player: Player) = player.hasPermission("zmusicgui.admin")
}
