package com.ourcraft.zmusicgui.manager

import com.ourcraft.zmusicgui.ZMusicGUI
import com.ourcraft.zmusicgui.manager.LyricDisplayManager.LyricMode
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object PlayerSettings {

    data class Settings(
        var lyricEnabled: Boolean = true,
        var lyricMode: LyricMode = LyricMode.BOSSBAR
    )

    private val cache = ConcurrentHashMap<String, Settings>()
    private lateinit var configFile: File
    private lateinit var yaml: YamlConfiguration

    fun load(plugin: ZMusicGUI) {
        configFile = File(plugin.dataFolder, "players.yml")
        if (!configFile.exists()) {
            plugin.dataFolder.mkdirs()
            configFile.createNewFile()
        }
        yaml = YamlConfiguration.loadConfiguration(configFile)
    }

    fun save() {
        yaml.save(configFile)
    }

    fun getSettings(player: Player): Settings {
        return cache.getOrPut(player.uniqueId.toString()) {
            val section = yaml.getConfigurationSection("players.${player.uniqueId}")
            if (section != null) {
                Settings(
                    lyricEnabled = section.getBoolean("lyric-enabled", Config.lyricDefaultEnabled()),
                    lyricMode = try {
                        LyricMode.valueOf(section.getString("lyric-mode", Config.lyricDefaultMode())!!)
                    } catch (_: Exception) {
                        LyricMode.BOSSBAR
                    }
                )
            } else {
                Settings(
                    lyricEnabled = Config.lyricDefaultEnabled(),
                    lyricMode = try {
                        LyricMode.valueOf(Config.lyricDefaultMode())
                    } catch (_: Exception) {
                        LyricMode.BOSSBAR
                    }
                )
            }
        }
    }

    fun setLyricEnabled(player: Player, enabled: Boolean) {
        val s = getSettings(player)
        s.lyricEnabled = enabled
        yaml.set("players.${player.uniqueId}.lyric-enabled", enabled)
        save()
        LyricDisplayManager.refresh(player)
    }

    fun setLyricMode(player: Player, mode: LyricMode) {
        val s = getSettings(player)
        s.lyricMode = mode
        yaml.set("players.${player.uniqueId}.lyric-mode", mode.name)
        save()
        LyricDisplayManager.refresh(player)
    }
}
