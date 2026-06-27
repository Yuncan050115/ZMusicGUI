package com.ourcraft.zmusicgui.gui

import com.ourcraft.zmusicgui.ZMusicGUI
import com.ourcraft.zmusicgui.manager.Config
import com.ourcraft.zmusicgui.manager.Messages
import com.ourcraft.zmusicgui.manager.PlayerSettings
import com.ourcraft.zmusicgui.manager.SearchService
import com.ourcraft.zmusicgui.music.MusicPlayer
import com.ourcraft.zmusicgui.util.Debug
import com.ourcraft.zmusicgui.util.Items
import com.ourcraft.zmusicgui.util.SchedulerUtil
import org.bukkit.Material
import org.bukkit.entity.Player
import java.util.concurrent.ConcurrentHashMap

object ControlGui : ZGui {

    private val refreshTasks = ConcurrentHashMap<Player, Any>()

    private const val SLOT_PREV = 20
    private const val SLOT_STOP = 21
    private const val SLOT_NEXT = 22
    private const val SLOT_MODE = 23
    private const val SLOT_PLAYLIST = 24
    private const val SLOT_NOW = 13
    private const val SLOT_LYRIC = 14
    private const val SLOT_LYRIC_TOGGLE = 30
    private const val SLOT_REFRESH = 31
    private const val SLOT_BACK = 32

    override fun open(player: Player) {
        refreshTasks.remove(player)?.let { SchedulerUtil.cancelTask(it) }
        render(player)
        val task = SchedulerUtil.runSyncTimer(ZMusicGUI.plugin, Runnable {
            if (!player.isOnline) { refreshTasks.remove(player); return@Runnable }
            val topHolder = player.openInventory.topInventory.holder
            if (topHolder is GuiHolder && topHolder.gui == this@ControlGui) {
                render(player)
            } else {
                refreshTasks.remove(player)
            }
        }, 40L, 40L)
        refreshTasks[player] = task
    }

    private fun render(player: Player) {
        val holder = GuiHolder(this)
        val inv = holder.create(36, Items.deserialize(Config.controlTitle()))

        for (i in 0..8) { inv.setItem(i, Items.border()); inv.setItem(i + 27, Items.border()) }
        for (row in 1..3) { inv.setItem(row * 9, Items.border()); inv.setItem(row * 9 + 8, Items.border()) }

        inv.setItem(4, Items.divider("当前播放"))

        val state = MusicPlayer.getState(player)
        if (state != null) {
            val cur = formatTime(state.currentTime)
            val max = formatTime(state.song.time)
            val sourceName = SearchService.sourcePlainName(state.song.source)
            inv.setItem(SLOT_NOW, Items.build(Material.MUSIC_DISC_13,
                Messages.gui("control.now-playing"),
                Messages.gui("control.song-name", "name" to state.song.name),
                Messages.gui("control.singer", "singer" to state.song.singer),
                Messages.gui("control.progress", "current" to cur, "max" to max),
                "&7平台: &f$sourceName"))

            val lyricIdx = state.lastLyricIndex
            if (lyricIdx >= 0 && lyricIdx < state.lyrics.size) {
                val lyric = state.lyrics[lyricIdx].text
                if (lyric.isNotEmpty() && lyric.length < 60) {
                    inv.setItem(SLOT_LYRIC, Items.build(Material.PAPER,
                        Messages.gui("control.lyric-preview"), "&f\"$lyric\""))
                }
            }

            val queueSize = state.playlistQueue.size
            if (queueSize > 1) {
                inv.setItem(SLOT_PREV, Items.build(Material.ARROW, "&a◀ 上一首",
                    "&7队列: &f${state.playlistIndex + 1}/$queueSize"))
                inv.setItem(SLOT_NEXT, Items.build(Material.ARROW, "&a下一首 ▶",
                    "&7队列: &f${state.playlistIndex + 1}/$queueSize"))
            } else {
                inv.setItem(SLOT_PREV, Items.build(Material.GRAY_STAINED_GLASS_PANE, "&7上一首",
                    "&c当前无播放队列"))
                inv.setItem(SLOT_NEXT, Items.build(Material.GRAY_STAINED_GLASS_PANE, "&7下一首",
                    "&c当前无播放队列"))
            }

            if (queueSize > 0) {
                inv.setItem(SLOT_PLAYLIST, Items.build(Material.CHEST, "&6📋 播放列表",
                    "&7队列共 &f${queueSize} &7首",
                    "&7当前: &f${state.playlistIndex + 1}",
                    "",
                    "&a▸ 点击查看播放列表"))
            }
        } else {
            inv.setItem(SLOT_NOW, Items.build(Material.MUSIC_DISC_11,
                Messages.gui("control.not-playing"),
                Messages.gui("control.not-playing-hint")))
            inv.setItem(SLOT_PREV, Items.build(Material.GRAY_STAINED_GLASS_PANE, "&7上一首"))
            inv.setItem(SLOT_NEXT, Items.build(Material.GRAY_STAINED_GLASS_PANE, "&7下一首"))
        }

        inv.setItem(SLOT_STOP, Items.build(Material.REDSTONE_BLOCK, Messages.gui("control.stop")))

        val currentMode = PlayerSettings.getPlayMode(player)
        val modeEnum = MusicPlayer.PlayMode.from(currentMode)
        val modeMat = when (currentMode) {
            "loop_one" -> Material.REPEATER
            "shuffle" -> Material.NOTE_BLOCK
            else -> Material.COMPARATOR
        }
        inv.setItem(SLOT_MODE, Items.build(modeMat, "&6&l播放模式",
            "&7当前: ${modeEnum.display}",
            "&7点击循环切换"))

        val s = PlayerSettings.getSettings(player)
        inv.setItem(SLOT_LYRIC_TOGGLE, Items.build(
            if (s.lyricEnabled) Material.LIME_DYE else Material.GRAY_DYE,
            if (s.lyricEnabled) Messages.gui("control.lyric-on") else Messages.gui("control.lyric-off"),
            "&7点击切换"))

        if (Config.showCredits()) inv.setItem(27, Items.credits())
        inv.setItem(SLOT_REFRESH, Items.build(Material.EMERALD, Messages.gui("control.refresh"), "&a点击立即刷新"))
        inv.setItem(SLOT_BACK, Items.back())

        player.openInventory(inv)
    }

    override fun handleClick(player: Player, slot: Int) {
        if (slot == SLOT_BACK) refreshTasks.remove(player)?.let { SchedulerUtil.cancelTask(it) }

        when (slot) {
            SLOT_PREV -> {
                if (MusicPlayer.getState(player)?.playlistQueue?.isNotEmpty() == true) {
                    MusicPlayer.playPrev(player)
                    render(player)
                }
            }
            SLOT_NEXT -> {
                if (MusicPlayer.getState(player)?.playlistQueue?.isNotEmpty() == true) {
                    MusicPlayer.playNext(player)
                    render(player)
                }
            }
            SLOT_STOP -> {
                MusicPlayer.stop(player)
                player.sendMessage(Items.color("${Messages.prefix()} ${Messages.player("playback-stopped")}"))
                render(player)
            }
            SLOT_MODE -> {
                val currentMode = PlayerSettings.getPlayMode(player)
                val nextMode = MusicPlayer.PlayMode.next(currentMode)
                PlayerSettings.setPlayMode(player, nextMode.id)
                player.sendMessage(Items.color("${Messages.prefix()} &a播放模式: ${nextMode.display}"))
                render(player)
            }
            SLOT_PLAYLIST -> {
                showPlaylistQueue(player)
            }
            SLOT_LYRIC_TOGGLE -> {
                val s = PlayerSettings.getSettings(player)
                PlayerSettings.setLyricEnabled(player, !s.lyricEnabled)
                val status = if (!s.lyricEnabled) Messages.player("enabled") else Messages.player("disabled")
                player.sendMessage(Items.color("${Messages.prefix()} ${Messages.player("lyric-toggled", "status" to status)}"))
                render(player)
            }
            SLOT_NOW, SLOT_LYRIC, 24, SLOT_REFRESH -> render(player)
            27 -> MainGui.openWebsite(player)
            SLOT_BACK -> MainGui.open(player)
        }
    }

    private fun showPlaylistQueue(player: Player) {
        val state = MusicPlayer.getState(player) ?: return
        val queue = state.playlistQueue
        if (queue.isEmpty()) {
            player.sendMessage(Items.color("${Messages.prefix()} &7当前没有播放队列"))
            return
        }

        player.closeInventory()
        val p = Messages.prefix()
        player.sendMessage(Items.color("$p &6━━━ 播放列表 ━━━"))
        val start = (state.playlistIndex - 4).coerceAtLeast(0)
        val end = minOf(queue.size, start + 10)
        for (i in start until end) {
            val song = queue[i]
            val marker = if (i == state.playlistIndex) "&a▶ 当前" else "&7${i + 1}"
            val sourceTag = when (song.source) {
                "netease" -> "&c[网易云]"
                "kugou" -> "&a[酷狗]"
                "kuwo" -> "&6[酷我]"
                "qq" -> "&d[QQ]"
                else -> "&7[${song.source}]"
            }
            player.sendMessage(Items.color("$p $marker &f${song.name} &7- &f${song.singer} $sourceTag"))
        }
        player.sendMessage(Items.color("$p &7共 &f${queue.size} &7首, 当前第 &f${state.playlistIndex + 1} &7首"))
        render(player)
    }

    fun cleanup(player: Player) {
        refreshTasks.remove(player)?.let { SchedulerUtil.cancelTask(it) }
    }

    private fun formatTime(sec: Int): String {
        val m = sec / 60
        val s = sec % 60
        return "%02d:%02d".format(m, s)
    }
}
