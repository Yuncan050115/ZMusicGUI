package com.ourcraft.zmusicgui.manager

import com.ourcraft.zmusicgui.ZMusicGUI
import com.ourcraft.zmusicgui.util.Debug
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

object Messages {

    private lateinit var yaml: YamlConfiguration
    private lateinit var file: File

    fun load(plugin: ZMusicGUI) {
        file = File(plugin.dataFolder, "messages.yml")
        plugin.dataFolder.mkdirs()
        // Always update from jar to ensure new keys are present
        plugin.saveResource("messages.yml", true)
        yaml = YamlConfiguration.loadConfiguration(file)
    }

    fun reload(plugin: ZMusicGUI) {
        yaml = YamlConfiguration.loadConfiguration(file)
        Debug.info(">> 语言文件已重载")
    }

    fun prefix(): String = color(yaml.getString("prefix", "&b[ZMusicGUI]&r")!!)

    // ---- Console ----
    fun console(key: String, vararg vars: Pair<String, String>): String =
        color(format(yaml.getString("console.$key", key)!!, *vars))

    // ---- Player messages ----
    fun player(key: String, vararg vars: Pair<String, String>): String =
        color(format(yaml.getString("player.$key", key)!!, *vars))

    // ---- GUI ----
    fun gui(key: String, vararg vars: Pair<String, String>): String =
        color(format(yaml.getString("gui.$key", key)!!, *vars))

    fun guiList(key: String, vararg vars: Pair<String, String>): List<String> =
        yaml.getStringList("gui.$key").map { color(format(it, *vars)) }

    // ---- Other sections ----
    fun cmd(key: String, vararg vars: Pair<String, String>): String =
        color(format(yaml.getString("command.$key", key)!!, *vars))

    fun perm(key: String): String = yaml.getString("permission.$key", key)!!

    // ---- Helpers ----
    private fun format(text: String, vararg vars: Pair<String, String>): String {
        var result = text
        for ((k, v) in vars) result = result.replace("{$k}", v)
        return result
    }

    private fun color(text: String): String = text.replace('&', '§')
}
