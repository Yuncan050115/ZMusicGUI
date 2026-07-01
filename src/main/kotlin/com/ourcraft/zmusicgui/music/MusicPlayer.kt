package com.ourcraft.zmusicgui.music

import com.ourcraft.zmusicgui.ZMusicGUI
import com.ourcraft.zmusicgui.channel.ModChannel
import com.ourcraft.zmusicgui.manager.Messages
import com.ourcraft.zmusicgui.manager.PlaylistManager
import com.ourcraft.zmusicgui.manager.PlayerSettings
import com.ourcraft.zmusicgui.manager.SearchService
import com.ourcraft.zmusicgui.util.Debug
import com.ourcraft.zmusicgui.util.Items
import com.ourcraft.zmusicgui.util.SchedulerUtil
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.concurrent.ConcurrentHashMap

/**
 * 音乐播放器
 *
 * 通过 ModChannel 发送播放指令给客户端 Mod,
 * 同时管理歌词同步 (每秒发送歌词和进度信息)。
 *
 * 播放队列:
 *  - playlistQueue  歌单队列 (持久, 搜索点歌不清除, 歌单结束后才停)
 *  - 临时播放 (search) 不清除 playlistQueue, 播完后自动恢复歌单下一首
 *
 * 支持播放模式:
 *  - sequence  顺序播放 (播完队列后停止)
 *  - loop_one  单曲循环 (当前歌曲循环)
 *  - shuffle   随机播放 (随机选择下一首)
 */
object MusicPlayer {

    enum class PlayMode(val id: String, val display: String) {
        SEQUENCE("sequence", "&a顺序播放"),
        LOOP_ONE("loop_one", "&e单曲循环"),
        SHUFFLE("shuffle", "&d随机播放");

        companion object {
            fun from(id: String): PlayMode = entries.firstOrNull { it.id == id } ?: SEQUENCE
            fun next(current: String): PlayMode {
                val idx = entries.indexOf(from(current))
                return entries[(idx + 1) % entries.size]
            }
        }
    }

    data class LyricLine(val time: Int, val text: String)

    data class PlayState(
        val song: OurMusicApi.SongDetail,
        var currentTime: Int = 0,
        val lyrics: List<LyricLine>,
        var lastLyricIndex: Int = -1,
        // 歌单队列 (持久; 搜索点歌不清除, 播完恢复)
        var playlistQueue: MutableList<OurMusicApi.SongDetail> = mutableListOf(),
        var playlistIndex: Int = 0,
        // 是否为临时打断播放 (搜索点歌)
        var isTemporary: Boolean = false,
        // v2.5.0: 手动推送队列 (优先级高于随机/顺序播放)
        // pushToQueue 添加到这里, onSongEnd 优先消费此队列
        var priorityQueue: MutableList<OurMusicApi.SongDetail> = mutableListOf(),
        // 播放历史栈 (用于"上一首"在随机模式下回退到实际上一首)
        var historyStack: MutableList<OurMusicApi.SongDetail> = mutableListOf(),
        // 歌曲结束/切换中标记, 防止懒加载期间定时器重复触发 onSongEnd 导致重复切歌
        var ending: Boolean = false
    )

    private val states = ConcurrentHashMap<Player, PlayState>()
    private val tasks = ConcurrentHashMap<Player, Any>()

    /** 临时播放单首歌曲 (搜索点歌, 不清除歌单队列, 同时记录到点歌历史) */
    fun play(player: Player, song: OurMusicApi.SongDetail) {
        // 保留已有的 playlistQueue, 临时播放后恢复
        val savedQueue = states[player]?.playlistQueue ?: mutableListOf()
        val savedIndex = states[player]?.playlistIndex ?: 0
        val savedPriority = states[player]?.priorityQueue ?: mutableListOf()
        val savedHistory = states[player]?.historyStack ?: mutableListOf()
        startPlay(player, song, savedQueue, savedIndex, isTemporary = true,
            priorityQueue = savedPriority, historyStack = savedHistory)
        // 异步记录到玩家的点歌历史 (不阻塞播放)
        PlaylistManager.recordHistory(player, song)
    }

    /** 歌单队列播放 (从指定位置开始, 设置歌单队列) */
    fun playPlaylist(player: Player, queue: MutableList<OurMusicApi.SongDetail>, startIndex: Int) {
        if (queue.isEmpty()) return
        val song = queue[startIndex]
        // 重置优先队列和历史栈 (新歌单开始)
        startPlay(player, song, queue, startIndex, isTemporary = false,
            priorityQueue = mutableListOf(), historyStack = mutableListOf())
    }

    /** 内部: 启动播放 */
    private fun startPlay(player: Player, song: OurMusicApi.SongDetail,
                          playlistQueue: MutableList<OurMusicApi.SongDetail>,
                          playlistIndex: Int, isTemporary: Boolean,
                          notify: Boolean = false,
                          priorityQueue: MutableList<OurMusicApi.SongDetail> = mutableListOf(),
                          historyStack: MutableList<OurMusicApi.SongDetail> = mutableListOf(),
                          skipHistoryPush: Boolean = false) {
        // 懒加载: 如果 url 为空 (歌单懒加载的后续歌曲), 异步获取详情后再播放
        if (song.url.isEmpty() && song.id.isNotEmpty()) {
            // 标记当前状态为"切换中", 防止定时器在异步获取期间重复触发 onSongEnd (导致重复切歌)
            states[player]?.ending = true
            player.sendMessage(Items.color("${Messages.prefix()} &7正在加载: &f${song.name}..."))
            val queueRef = playlistQueue
            val idxRef = playlistIndex
            val prioRef = priorityQueue
            val histRef = historyStack
            val skipRef = skipHistoryPush
            SchedulerUtil.runAsync(ZMusicGUI.plugin, Runnable {
                val detail = try { SearchService.getSongDetailBySource(song.id, song.source, player) } catch (_: Throwable) { null }
                SchedulerUtil.runSync(ZMusicGUI.plugin, Runnable {
                    if (!player.isOnline) return@Runnable
                    val finalDetail = if (detail != null && detail.url.isNotEmpty()) {
                        // 更新队列里的元素
                        if (idxRef in queueRef.indices) queueRef[idxRef] = detail
                        detail
                    } else song  // 用原 song (url 为空, 会提示错误)
                    startPlay(player, finalDetail, queueRef, idxRef, isTemporary, notify, prioRef, histRef, skipRef)
                })
            })
            return
        }

        // v2.5.1: 在 stop 之前捕获当前歌曲, 用于压入历史栈
        // (stop 会移除 states[player], 必须先读取)
        val prevSong = states[player]?.song
        stop(player)

        // url 为空且无 id → 无法播放
        if (song.url.isEmpty()) {
            player.sendMessage(Items.color("${Messages.prefix()} &c无法播放: &f${song.name} &7(无可用音源)"))
            // 自动切下一首
            SchedulerUtil.runSyncLater(ZMusicGUI.plugin, Runnable { playNext(player) }, 40L)
            return
        }

        // v2.5.1: 把当前歌曲压入历史栈 (用于上一首回退)
        // skipHistoryPush=true 时跳过 (playPrev 从历史栈弹出歌曲时, 不把当前歌曲压回栈, 避免循环)
        if (!skipHistoryPush && prevSong != null) {
            if (prevSong.id != song.id || prevSong.source != song.source) {
                historyStack.add(prevSong)
                if (historyStack.size > 50) historyStack.removeAt(0)
            }
        }

        val lyrics = parseLyrics(song.lyric)
        val state = PlayState(song, 0, lyrics, -1, playlistQueue, playlistIndex, isTemporary,
            priorityQueue = priorityQueue, historyStack = historyStack)
        states[player] = state

        // v3.0.1: BossBar/ActionBar 歌词显示统一由 LyricDisplayManager 管理
        // (此处不再创建独立 BossBar, 避免重复显示)

        // 发送播放指令
        ModChannel.play(player, song.url)
        Debug.debug("播放: ${player.name} - ${song.name} (url=${song.url.take(50)}...) playlistSize=${playlistQueue.size} idx=$playlistIndex temp=$isTemporary priority=${priorityQueue.size}")

        // 歌单切换通知: 发送歌曲信息 + [结束][推送] 按钮
        if (notify) {
            sendSongNotification(player, song)
        }

        // 启动歌词同步 (每秒)
        val task = SchedulerUtil.runSyncTimer(ZMusicGUI.plugin, Runnable {
            if (!player.isOnline) { stop(player); return@Runnable }
            val s = states[player] ?: return@Runnable
            s.currentTime++

            // 发送信息 HUD
            val info = buildInfo(s)
            ModChannel.sendInfo(player, info)

            // 发送歌词
            val lyricIdx = s.lyrics.indexOfLast { it.time <= s.currentTime }
            if (lyricIdx >= 0 && lyricIdx != s.lastLyricIndex) {
                s.lastLyricIndex = lyricIdx
                ModChannel.sendLyric(player, s.lyrics[lyricIdx].text)
            }

            // v3.0.1: BossBar/ActionBar 显示由 LyricDisplayManager 统一管理

            // 播放结束 (ending 标记防止懒加载期间重复触发)
            if (s.currentTime >= s.song.time && s.song.time > 0 && !s.ending) {
                s.ending = true
                onSongEnd(player, s)
            }
        }, 20L, 20L)
        tasks[player] = task
    }

    /** 发送歌曲切换通知 (聊天栏 [结束][推曲] 按钮) */
    private fun sendSongNotification(player: Player, song: OurMusicApi.SongDetail) {
        try {
            val sourcePlain = SearchService.sourcePlainName(song.source)
            // [结束] 按钮
            val stopBtn = Component.text("[结束]")
                .color(NamedTextColor.RED)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/zmg stop"))
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(Component.text("点击停止播放")))

            // [推送] 按钮 — 把这首歌推送到当前范围 (加入播放队列)
            val pushBtn = Component.text("[推送]")
                .color(NamedTextColor.GREEN)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/zmg push ${song.id} ${song.source}"))
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(Component.text("点击把这首歌推送到当前范围")))

            val msg = Component.empty()
                .append(Component.text("♪ ").color(NamedTextColor.AQUA))
                .append(Component.text(song.name).color(NamedTextColor.YELLOW))
                .append(Component.text(" - ").color(NamedTextColor.GRAY))
                .append(Component.text(song.singer).color(NamedTextColor.WHITE))
                .append(Component.text(" [$sourcePlain]").color(NamedTextColor.GRAY))
                .append(Component.text("  "))
                .append(stopBtn)
                .append(Component.text(" "))
                .append(pushBtn)

            player.sendMessage(msg)
        } catch (e: Throwable) {
            Debug.debug("通知发送失败: ${e.message}")
        }
    }

    /** 歌曲播放结束处理 */
    private fun onSongEnd(player: Player, state: PlayState) {
        // v2.5.1: 优先消费 priorityQueue (手动推送的下一首)
        // 手动添加的下一首播放优先级高于随机/顺序播放
        if (state.priorityQueue.isNotEmpty()) {
            val nextSong = state.priorityQueue.removeAt(0)
            Debug.debug("优先队列播放: ${player.name} - ${nextSong.name} (剩余${state.priorityQueue.size})")
            startPlay(player, nextSong, state.playlistQueue, state.playlistIndex, false, notify = true,
                priorityQueue = state.priorityQueue, historyStack = state.historyStack)
            return
        }

        val mode = PlayerSettings.getPlayMode(player)
        when (mode) {
            "loop_one" -> {
                // 单曲循环: 重新播放当前歌曲
                Debug.debug("单曲循环: ${player.name} - ${state.song.name}")
                startPlay(player, state.song, state.playlistQueue, state.playlistIndex, state.isTemporary, notify = true,
                    priorityQueue = state.priorityQueue, historyStack = state.historyStack)
            }
            "shuffle" -> {
                // 随机播放: 从歌单队列中随机选一首
                if (state.playlistQueue.size > 1) {
                    val nextIdx = (0 until state.playlistQueue.size).filter { it != state.playlistIndex }.random()
                    val nextSong = state.playlistQueue[nextIdx]
                    Debug.debug("随机播放: ${player.name} - ${nextSong.name} (idx=$nextIdx)")
                    startPlay(player, nextSong, state.playlistQueue, nextIdx, false, notify = true,
                        priorityQueue = state.priorityQueue, historyStack = state.historyStack)
                } else if (state.playlistQueue.size == 1) {
                    // 只有一首歌, 重新播放
                    startPlay(player, state.playlistQueue[0], state.playlistQueue, 0, false, notify = true,
                        priorityQueue = state.priorityQueue, historyStack = state.historyStack)
                } else {
                    stop(player)
                }
            }
            else -> {
                // 顺序播放
                // 临时播放 (搜索点歌) 结束 → 恢复歌单队列下一首
                if (state.isTemporary) {
                    val nextIdx = state.playlistIndex + 1
                    if (nextIdx < state.playlistQueue.size) {
                        val nextSong = state.playlistQueue[nextIdx]
                        Debug.debug("恢复歌单: ${player.name} - ${nextSong.name} (idx=$nextIdx)")
                        startPlay(player, nextSong, state.playlistQueue, nextIdx, false, notify = true,
                            priorityQueue = state.priorityQueue, historyStack = state.historyStack)
                    } else {
                        stop(player)
                        Debug.debug("播放结束: ${player.name} 歌单已空")
                    }
                    return
                }
                // 歌单播放 → 下一首
                val nextIdx = state.playlistIndex + 1
                if (nextIdx < state.playlistQueue.size) {
                    val nextSong = state.playlistQueue[nextIdx]
                    Debug.debug("顺序播放: ${player.name} - ${nextSong.name} (idx=$nextIdx)")
                    startPlay(player, nextSong, state.playlistQueue, nextIdx, false, notify = true,
                        priorityQueue = state.priorityQueue, historyStack = state.historyStack)
                } else {
                    stop(player)
                    Debug.debug("播放结束: ${player.name} 歌单已空")
                }
            }
        }
    }

    /** 停止播放 */
    fun stop(player: Player) {
        tasks.remove(player)?.let { SchedulerUtil.cancelTask(it) }
        states.remove(player)
        ModChannel.stop(player)
        ModChannel.clearLyric(player)
        ModChannel.clearInfo(player)
    }

    /** 上一首 */
    fun playPrev(player: Player) {
        val state = states[player] ?: return
        // v2.5.1: 优先使用 historyStack 回退到实际上一首 (随机模式下也能正确回退)
        if (state.historyStack.isNotEmpty()) {
            val prevSong = state.historyStack.removeAt(state.historyStack.size - 1)
            Debug.debug("历史回退: ${player.name} - ${prevSong.name} (历史栈剩${state.historyStack.size})")
            // skipHistoryPush=true: 不把当前歌曲压回历史栈, 避免上一首循环
            startPlay(player, prevSong, state.playlistQueue, state.playlistIndex, false, notify = true,
                priorityQueue = state.priorityQueue, historyStack = state.historyStack, skipHistoryPush = true)
            return
        }
        // 历史栈为空, 走顺序回退
        val queue = state.playlistQueue
        if (queue.isEmpty()) return
        val prevIdx = if (state.playlistIndex > 0) state.playlistIndex - 1 else queue.size - 1
        startPlay(player, queue[prevIdx], queue, prevIdx, false, notify = true,
            priorityQueue = state.priorityQueue, historyStack = state.historyStack)
    }

    /** 下一首 */
    fun playNext(player: Player) {
        val state = states[player] ?: return
        // v2.5.1: 优先消费 priorityQueue (手动推送的下一首)
        if (state.priorityQueue.isNotEmpty()) {
            val nextSong = state.priorityQueue.removeAt(0)
            Debug.debug("优先下一首: ${player.name} - ${nextSong.name} (剩余${state.priorityQueue.size})")
            startPlay(player, nextSong, state.playlistQueue, state.playlistIndex, false, notify = true,
                priorityQueue = state.priorityQueue, historyStack = state.historyStack)
            return
        }
        val queue = state.playlistQueue
        if (queue.isEmpty()) return
        val mode = PlayerSettings.getPlayMode(player)
        val nextIdx = when {
            mode == "shuffle" && queue.size > 1 -> (0 until queue.size).filter { it != state.playlistIndex }.random()
            state.playlistIndex + 1 < queue.size -> state.playlistIndex + 1
            else -> 0
        }
        startPlay(player, queue[nextIdx], queue, nextIdx, false, notify = true,
            priorityQueue = state.priorityQueue, historyStack = state.historyStack)
    }

    /** 推曲: 将歌曲加入下一首播放队列 (优先级高于随机/顺序播放) */
    fun pushToQueue(player: Player, song: OurMusicApi.SongDetail) {
        val state = states[player]
        if (state != null) {
            // v2.5.1: 加入 priorityQueue (下一首播放), 而不是 playlistQueue 末尾
            state.priorityQueue.add(song)
            val pos = state.priorityQueue.size
            player.sendMessage(Items.color("${Messages.prefix()} &a已加入下一首播放: &f${song.name} &7- &f${song.singer} &7(队列位置: $pos)"))
        } else {
            // 没在播放, 直接播放这首歌
            play(player, song)
        }
    }

    /** 是否正在播放 */
    fun isPlaying(player: Player): Boolean = states.containsKey(player)

    /** 获取播放状态 */
    fun getState(player: Player): PlayState? = states[player]

    /** 构建信息 HUD 文本 */
    private fun buildInfo(state: PlayState): String {
        val cur = formatTime(state.currentTime)
        val max = formatTime(state.song.time)
        return "歌名: ${state.song.name}\n歌手: ${state.song.singer}\n平台: ${SearchService.sourcePlainName(state.song.source)}\n进度: $cur/$max"
    }

    private fun formatTime(sec: Int): String {
        val m = sec / 60
        val s = sec % 60
        return "%02d:%02d".format(m, s)
    }

    /** 解析 LRC 歌词格式 */
    private fun parseLyrics(lrc: String): List<LyricLine> {
        if (lrc.isEmpty()) return emptyList()
        val list = mutableListOf<LyricLine>()
        for (line in lrc.split("\n")) {
            // [mm:ss.xx]歌词文本
            val match = Regex("\\[(\\d+):(\\d+)(?:\\.(\\d+))?](.*)").find(line) ?: continue
            val min = match.groupValues[1].toIntOrNull() ?: 0
            val sec = match.groupValues[2].toIntOrNull() ?: 0
            val time = min * 60 + sec
            val text = match.groupValues[4].trim()
            if (text.isNotEmpty()) list.add(LyricLine(time, text))
        }
        return list.sortedBy { it.time }
    }

    /** 停止所有玩家的播放 */
    fun stopAll() {
        states.keys.toList().forEach { stop(it) }
    }
}
