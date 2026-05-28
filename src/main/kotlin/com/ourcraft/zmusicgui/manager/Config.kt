package com.ourcraft.zmusicgui.manager

import com.ourcraft.zmusicgui.ZMusicGUI
import com.ourcraft.zmusicgui.util.Debug
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

object Config {

    private lateinit var yaml: YamlConfiguration
    private lateinit var file: File

    fun load(plugin: ZMusicGUI) {
        file = File(plugin.dataFolder, "config.yml")
        plugin.dataFolder.mkdirs()
        plugin.saveResource("config.yml", true)
        yaml = YamlConfiguration.loadConfiguration(file)
        Debug.info(">> 配置文件已挂载 (debug=${debug()})")
    }

    fun reload(plugin: ZMusicGUI) {
        yaml = YamlConfiguration.loadConfiguration(file)
        Debug.info(">> 配置文件已重载")
    }

    fun debug() = yaml.getBoolean("debug", false)

    fun getPlatforms(): Map<String, PlatformConfig> {
        val map = linkedMapOf<String, PlatformConfig>()
        val sec = yaml.getConfigurationSection("platforms") ?: return map
        for (key in sec.getKeys(false)) {
            val ps = sec.getConfigurationSection(key) ?: continue
            if (!ps.getBoolean("enabled", true)) continue
            map[key] = PlatformConfig(key, ps.getString("name", key)!!, ps.getStringList("desc"))
        }
        return map
    }

    fun lyricDefaultEnabled() = yaml.getBoolean("lyric.default-enabled", true)
    fun lyricDefaultMode() = yaml.getString("lyric.default-mode", "BOSSBAR")!!
    fun lyricUpdateTicks() = yaml.getLong("lyric.update-ticks", 10L)
    fun lyricDisplayFormat() = yaml.getString("lyric.display-format", "LYRIC")!!

    fun cooldown() = yaml.getInt("music.cooldown-seconds", 5)
    fun publicRequestEnabled() = yaml.getBoolean("music.public-request", true)

    fun showCredits() = yaml.getBoolean("gui.show-credits", true)
    fun mainMenuTitle() = yaml.getString("gui.main-menu-title", "&6&l☄ Ourcraft &8▸ &b音乐中心")!!

    data class PlatformConfig(val id: String, val name: String, val desc: List<String>)
}
