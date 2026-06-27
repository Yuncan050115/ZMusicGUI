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
        var isTemporary: Boolean = false
    )

    private val states = ConcurrentHashMap<Player, PlayState>()
    private val tasks = ConcurrentHashMap<Player, Any>()

    /** 临时播放单首歌曲 (搜索点歌, 不清除歌单队列, 同时记录到点歌历史) */
    fun play(player: Player, song: OurMusicApi.SongDetail) {
        // 保留已有的 playlistQueue, 临时播放后恢复
        val savedQueue = states[player]?.playlistQueue ?: mutableListOf()
        val savedIndex = states[player]?.playlistIndex ?: 0
        startPlay(player, song, savedQueue, savedIndex, isTemporary = true)
        // 异步记录到玩家的点歌历史 (不阻塞播放)
        PlaylistManager.recordHistory(player, song)
    }

    /** 歌单队列播放 (从指定位置开始, 设置歌单队列) */
    fun playPlaylist(player: Player, queue: MutableList<OurMusicApi.SongDetail>, startIndex: Int) {
        if (queue.isEmpty()) return
        val song = queue[startIndex]
        startPlay(player, song, queue, startIndex, isTemporary = false)
    }

    /** 内部: 启动播放 */
    private fun startPlay(player: Player, song: OurMusicApi.SongDetail,
                          playlistQueue: MutableList<OurMusicApi.SongDetail>,
                          playlistIndex: Int, isTemporary: Boolean,
                          notify: Boolean = false) {
        // 懒加载: 如果 url 为空 (歌单懒加载的后续歌曲), 异步获取详情后再播放
        if (song.url.isEmpty() && song.id.isNotEmpty()) {
            player.sendMessage(Items.color("${Messages.prefix()} &7正在加载: &f${song.name}..."))
            val queueRef = playlistQueue
            val idxRef = playlistIndex
            SchedulerUtil.runAsync(ZMusicGUI.plugin, Runnable {
                val detail = try { SearchService.getSongDetailBySource(song.id, song.source, player) } catch (_: Throwable) { null }
                SchedulerUtil.runSync(ZMusicGUI.plugin, Runnable {
                    if (!player.isOnline) return@Runnable
                    val finalDetail = if (detail != null && detail.url.isNotEmpty()) {
                        // 更新队列里的元素
                        if (idxRef in queueRef.indices) queueRef[idxRef] = detail
                        detail
                    } else song  // 用原 song (url 为空, 会提示错误)
                    startPlay(player, finalDetail, queueRef, idxRef, isTemporary, notify)
                })
            })
            return
        }

        stop(player)

        // url 为空且无 id → 无法播放
        if (song.url.isEmpty()) {
            player.sendMessage(Items.color("${Messages.prefix()} &c无法播放: &f${song.name} &7(无可用音源)"))
            // 自动切下一首
            SchedulerUtil.runSyncLater(ZMusicGUI.plugin, Runnable { playNext(player) }, 40L)
            return
        }

        val lyrics = parseLyrics(song.lyric)
        val state = PlayState(song, 0, lyrics, -1, playlistQueue, playlistIndex, isTemporary)
        states[player] = state

        // 发送播放指令
        ModChannel.play(player, song.url)
        Debug.debug("播放: ${player.name} - ${song.name} (url=${song.url.take(50)}...) playlistSize=${playlistQueue.size} idx=$playlistIndex temp=$isTemporary")

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

            // 播放结束
            if (s.currentTime >= s.song.time && s.song.time > 0) {
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
        val mode = PlayerSettings.getPlayMode(player)
        when (mode) {
            "loop_one" -> {
                // 单曲循环: 重新播放当前歌曲
                Debug.debug("单曲循环: ${player.name} - ${state.song.name}")
                startPlay(player, state.song, state.playlistQueue, state.playlistIndex, state.isTemporary, notify = true)
            }
            "shuffle" -> {
                // 随机播放: 从歌单队列中随机选一首
                if (state.playlistQueue.size > 1) {
                    val nextIdx = (0 until state.playlistQueue.size).filter { it != state.playlistIndex }.random()
                    val nextSong = state.playlistQueue[nextIdx]
                    Debug.debug("随机播放: ${player.name} - ${nextSong.name} (idx=$nextIdx)")
                    startPlay(player, nextSong, state.playlistQueue, nextIdx, false, notify = true)
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
                        startPlay(player, nextSong, state.playlistQueue, nextIdx, false, notify = true)
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
                    startPlay(player, nextSong, state.playlistQueue, nextIdx, false, notify = true)
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
        val queue = state.playlistQueue
        if (queue.isEmpty()) return
        val prevIdx = if (state.playlistIndex > 0) state.playlistIndex - 1 else queue.size - 1
        startPlay(player, queue[prevIdx], queue, prevIdx, false, notify = true)
    }

    /** 下一首 */
    fun playNext(player: Player) {
        val state = states[player] ?: return
        val queue = state.playlistQueue
        if (queue.isEmpty()) return
        val mode = PlayerSettings.getPlayMode(player)
        val nextIdx = when {
            mode == "shuffle" && queue.size > 1 -> (0 until queue.size).filter { it != state.playlistIndex }.random()
            state.playlistIndex + 1 < queue.size -> state.playlistIndex + 1
            else -> 0
        }
        startPlay(player, queue[nextIdx], queue, nextIdx, false, notify = true)
    }

    /** 推曲: 将歌曲加入播放队列末尾 */
    fun pushToQueue(player: Player, song: OurMusicApi.SongDetail) {
        val state = states[player]
        if (state != null) {
            state.playlistQueue.add(song)
            player.sendMessage(Items.color("${Messages.prefix()} &a已加入播放队列: &f${song.name} &7- &f${song.singer}"))
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
