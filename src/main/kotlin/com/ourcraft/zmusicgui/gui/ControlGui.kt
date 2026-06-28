package com.ourcraft.zmusicgui.gui

import com.ourcraft.zmusicgui.ZMusicGUI
import com.ourcraft.zmusicgui.manager.Config
import com.ourcraft.zmusicgui.manager.Messages
import com.ourcraft.zmusicgui.manager.PlayerSettings
import com.ourcraft.zmusicgui.manager.SearchService
import com.ourcraft.zmusicgui.music.MusicPlayer
import com.ourcraft.zmusicgui.util.Items
import com.ourcraft.zmusicgui.util.SchedulerUtil
import org.bukkit.Material
import org.bukkit.entity.Player
import java.util.concurrent.ConcurrentHashMap

/**
 * 播放控制 GUI v2.5.4 — 精简版
 *
 * v2.5.4: 精简按钮
 *  - 移除歌词预览 (BossBar 已显示歌词)
 *  - 移除歌词开关 (BossBar 默认开启)
 *  - 移除刷新按钮 (已有自动刷新)
 *  - 当前播放信息含进度和优先队列状态
 */
object ControlGui : ZGui {

    private val refreshTasks = ConcurrentHashMap<Player, Any>()

    private const val SLOT_PREV = 20
    private const val SLOT_STOP = 21
    private const val SLOT_NEXT = 22
    private const val SLOT_MODE = 23
    private const val SLOT_PLAYLIST = 24
    private const val SLOT_NOW = 13
    private const val SLOT_BACK = 31

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
            val prioritySize = state.priorityQueue.size
            val queueSize = state.playlistQueue.size

            inv.setItem(SLOT_NOW, Items.build(Material.MUSIC_DISC_13,
                Messages.gui("control.now-playing"),
                Messages.gui("control.song-name", "name" to state.song.name),
                Messages.gui("control.singer", "singer" to state.song.singer),
                Messages.gui("control.progress", "current" to cur, "max" to max),
                "&7平台: &f$sourceName",
                if (prioritySize > 0) "&a下一首队列: &f$prioritySize 首" else "",
                if (queueSize > 0) "&7播放队列: &f${state.playlistIndex + 1}/$queueSize" else ""))

            val hasQueue = queueSize > 1 || prioritySize > 0
            if (hasQueue) {
                inv.setItem(SLOT_PREV, Items.build(Material.ARROW, "&a◀ 上一首",
                    if (state.historyStack.isNotEmpty()) "&7历史回退: &f${state.historyStack.size} 首" else "&7无历史记录"))
                inv.setItem(SLOT_NEXT, Items.build(Material.ARROW, "&a下一首 ▶",
                    if (prioritySize > 0) "&a下一首队列: &f$prioritySize 首" else "&7队列: &f${state.playlistIndex + 1}/$queueSize"))
            } else {
                inv.setItem(SLOT_PREV, Items.build(Material.GRAY_STAINED_GLASS_PANE, "&7上一首",
                    "&c当前无播放队列"))
                inv.setItem(SLOT_NEXT, Items.build(Material.GRAY_STAINED_GLASS_PANE, "&7下一首",
                    "&c当前无播放队列"))
            }

            if (queueSize > 0 || prioritySize > 0) {
                inv.setItem(SLOT_PLAYLIST, Items.build(Material.CHEST, "&6📋 播放列表",
                    if (queueSize > 0) "&7播放队列: &f${queueSize} 首 &7(当前: ${state.playlistIndex + 1})" else "",
                    if (prioritySize > 0) "&a下一首队列: &f$prioritySize 首" else "",
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

        if (Config.showCredits()) inv.setItem(27, Items.credits())
        inv.setItem(SLOT_BACK, Items.back())

        player.openInventory(inv)
    }

    override fun handleClick(player: Player, slot: Int) {
        if (slot == SLOT_BACK) refreshTasks.remove(player)?.let { SchedulerUtil.cancelTask(it) }

        when (slot) {
            SLOT_PREV -> {
                if (MusicPlayer.getState(player) != null) {
                    MusicPlayer.playPrev(player)
                    render(player)
                }
            }
            SLOT_NEXT -> {
                if (MusicPlayer.getState(player) != null) {
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
            SLOT_NOW -> render(player)
            27 -> MainGui.openWebsite(player)
            SLOT_BACK -> MainGui.open(player)
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

        // 优先队列 (下一首播放)
        if (priority.isNotEmpty()) {
            player.sendMessage(Items.color("$p &a▼ 下一首播放队列 (&f${priority.size}&a):"))
            priority.forEachIndexed { i, song ->
                val sourceTag = sourceTag(song.source)
                player.sendMessage(Items.color("$p &a${i + 1} &f${song.name} &7- &f${song.singer} $sourceTag"))
            }
        }

        // 播放队列
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
