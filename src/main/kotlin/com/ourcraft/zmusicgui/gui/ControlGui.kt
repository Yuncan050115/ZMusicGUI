package com.ourcraft.zmusicgui.gui

import com.ourcraft.zmusicgui.ZMusicGUI
import com.ourcraft.zmusicgui.manager.Config
import com.ourcraft.zmusicgui.manager.Messages
import com.ourcraft.zmusicgui.manager.PlayerSettings
import com.ourcraft.zmusicgui.manager.SearchService
import com.ourcraft.zmusicgui.music.MusicPlayer
import com.ourcraft.zmusicgui.util.Items
import com.ourcraft.zmusicgui.util.SchedulerUtil
import org.bukkit.entity.Player
import java.util.concurrent.ConcurrentHashMap

/**
 * 播放控制 GUI v3.0.1 — TrMenu 风格 YAML 自定义
 *
 * 布局由 GUI/control.yml 定义, 代码负责:
 *  - 填充播放状态占位符 (song_name/singer/progress/mode/queue_info 等)
 *  - 点击路由 (prev/stop/next/mode/playlist/refresh/back/credits)
 *  - 自动刷新 (40L 周期)
 */
object ControlGui : ZGui {

    private val refreshTasks = ConcurrentHashMap<Player, Any>()

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
        val state = MusicPlayer.getState(player)

        val placeholders = if (state != null) {
            val cur = formatTime(state.currentTime)
            val max = formatTime(state.song.time)
            val sourceName = SearchService.sourcePlainName(state.song.source)
            val prioritySize = state.priorityQueue.size
            val queueSize = state.playlistQueue.size
            mapOf(
                "song_name" to state.song.name,
                "singer" to state.song.singer,
                "current" to cur,
                "max" to max,
                "source" to sourceName,
                "priority_info" to if (prioritySize > 0) "&a下一首队列: &f$prioritySize 首" else "",
                "queue_info" to if (queueSize > 0) "&7播放队列: &f${state.playlistIndex + 1}/$queueSize" else "",
                "history_info" to if (state.historyStack.isNotEmpty()) "&7历史回退: &f${state.historyStack.size} 首" else "&7无历史记录",
                "next_info" to if (prioritySize > 0) "&a下一首队列: &f$prioritySize 首" else if (queueSize > 0) "&7队列: &f${state.playlistIndex + 1}/$queueSize" else "&7无队列",
                "mode" to MusicPlayer.PlayMode.from(PlayerSettings.getPlayMode(player)).display
            )
        } else {
            mapOf(
                "song_name" to "&c未在播放",
                "singer" to "",
                "current" to "--:--",
                "max" to "--:--",
                "source" to "",
                "priority_info" to "",
                "queue_info" to "",
                "history_info" to "&7无历史记录",
                "next_info" to "&c当前无播放队列",
                "mode" to MusicPlayer.PlayMode.from(PlayerSettings.getPlayMode(player)).display
            )
        }

        val inv = GuiLoader.render("control", holder, placeholders) ?: run {
            player.sendMessage(Items.color("${Messages.prefix()} &cGUI 配置 control.yml 缺失"))
            return
        }

        // credits 按配置显示/隐藏
        if (!Config.showCredits()) {
            GuiLoader.getIconAt("control", 27)?.let {
                if (it.clickHandler == "credits") inv.setItem(27, Items.border())
            }
        }

        player.openInventory(inv)
    }

    override fun handleClick(player: Player, slot: Int) {
        val handler = GuiLoader.getClickHandler("control", slot) ?: return

        if (handler == "back") refreshTasks.remove(player)?.let { SchedulerUtil.cancelTask(it) }

        when (handler) {
            "prev" -> {
                if (MusicPlayer.getState(player) != null) {
                    MusicPlayer.playPrev(player)
                    render(player)
                }
            }
            "next" -> {
                if (MusicPlayer.getState(player) != null) {
                    MusicPlayer.playNext(player)
                    render(player)
                }
            }
            "stop" -> {
                MusicPlayer.stop(player)
                player.sendMessage(Items.color("${Messages.prefix()} ${Messages.player("playback-stopped")}"))
                render(player)
            }
            "mode" -> {
                val currentMode = PlayerSettings.getPlayMode(player)
                val nextMode = MusicPlayer.PlayMode.next(currentMode)
                PlayerSettings.setPlayMode(player, nextMode.id)
                player.sendMessage(Items.color("${Messages.prefix()} &a播放模式: ${nextMode.display}"))
                render(player)
            }
            "playlist" -> showPlaylistQueue(player)
            "refresh" -> render(player)
            "credits" -> MainGui.openWebsite(player)
            "back" -> MainGui.open(player)
        }
    }

    private fun showPlaylistQueue(player: Player) {
        val state = MusicPlayer.getState(player) ?: return
        val queue = state.playlistQueue
        val priority = state.priorityQueue
        if (queue.isEmpty() && priority.isEmpty()) {
            player.sendMessage(Items.color("${Messages.prefix()} &7当前没有播放队列"))
            return
        }

        player.closeInventory()
        val p = Messages.prefix()
        player.sendMessage(Items.color("$p &6━━━ 播放列表 ━━━"))

        if (priority.isNotEmpty()) {
            player.sendMessage(Items.color("$p &a▼ 下一首播放队列 (&f${priority.size}&a):"))
            priority.forEachIndexed { i, song ->
                val sourceTag = sourceTag(song.source)
                player.sendMessage(Items.color("$p &a${i + 1} &f${song.name} &7- &f${song.singer} $sourceTag"))
            }
        }

        if (queue.isNotEmpty()) {
            player.sendMessage(Items.color("$p &7▼ 播放队列 (&f${queue.size}&7):"))
            val start = (state.playlistIndex - 4).coerceAtLeast(0)
            val end = minOf(queue.size, start + 10)
            for (i in start until end) {
                val song = queue[i]
                val marker = if (i == state.playlistIndex) "&a▶ 当前" else "&7${i + 1}"
                val sourceTag = sourceTag(song.source)
                player.sendMessage(Items.color("$p $marker &f${song.name} &7- &f${song.singer} $sourceTag"))
            }
            player.sendMessage(Items.color("$p &7共 &f${queue.size} &7首, 当前第 &f${state.playlistIndex + 1} &7首"))
        }

        render(player)
    }

    private fun sourceTag(source: String): String = when (source) {
        "netease" -> "&c[网易云]"
        "kugou" -> "&a[酷狗]"
        "kuwo" -> "&6[酷我]"
        "qq" -> "&d[QQ]"
        else -> "&7[$source]"
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
