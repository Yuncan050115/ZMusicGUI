package com.ourcraft.zmusicgui.gui

import com.ourcraft.zmusicgui.ZMusicGUI
import com.ourcraft.zmusicgui.manager.Messages
import com.ourcraft.zmusicgui.manager.PlaylistManager
import com.ourcraft.zmusicgui.manager.PlaylistManager.AggregatedPlaylist
import com.ourcraft.zmusicgui.manager.PlayerSettings
import com.ourcraft.zmusicgui.manager.ScopeManager
import com.ourcraft.zmusicgui.manager.ScopeManager.Scope
import com.ourcraft.zmusicgui.manager.SearchService
import com.ourcraft.zmusicgui.music.MusicPlayer
import com.ourcraft.zmusicgui.util.Debug
import com.ourcraft.zmusicgui.util.Items
import com.ourcraft.zmusicgui.util.SchedulerUtil
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import org.bukkit.Material
import org.bukkit.entity.Player

/**
 * 歌单推送 — 将整个歌单推给选定范围内的玩家
 *
 * 流程:
 *  1. 推送者选择歌单
 *  2. 范围内的玩家收到可点击消息: [顺序播放] [随机播放]
 *  3. 接收者点击后异步加载歌单并播放
 */
object PlaylistPushGui : ZGui {

    private const val SLOT_BACK = 49
    private const val PER_PAGE = 36
    private val states = mutableMapOf<Player, PushState>()

    private data class PushState(
        var playlists: List<AggregatedPlaylist> = emptyList(),
        var page: Int = 0,
        var scope: Scope = Scope.SELF
    )

    fun open(player: Player, scope: Scope) {
        states[player] = PushState(scope = scope)
        showLoading(player)
        PlaylistManager.loadAsync(player) { all ->
            val s = states[player] ?: return@loadAsync
            // 只显示有歌曲的歌单
            s.playlists = all.filter { it.songs.isNotEmpty() }
            render(player)
        }
    }

    private fun showLoading(player: Player) {
        val holder = GuiHolder(this)
        val inv = holder.create(27, Items.deserialize("&6&l推送歌单 &7(加载中...)"))
        for (i in 0..26) inv.setItem(i, Items.border())
        inv.setItem(13, Items.build(Material.CLOCK, "&e加载中...", "&7正在读取歌单"))
        player.openInventory(inv)
    }

    private fun render(player: Player) {
        val state = states[player] ?: return
        val holder = GuiHolder(this)
        val inv = holder.create(54, Items.deserialize("&6&l推送歌单 &7→ ${state.scope.display}"))

        for (i in 0..8) inv.setItem(i, Items.border())
        for (i in 45..53) inv.setItem(i, Items.border())
        for (r in 0..5) { inv.setItem(r * 9, Items.border()); inv.setItem(r * 9 + 8, Items.border()) }

        inv.setItem(4, Items.build(Material.NETHER_STAR, "&6&l推送歌单",
            "${state.scope.icon}&f目标范围: ${state.scope.display}",
            "&7选择要推送的歌单",
            "&7范围内玩家可选择顺序/随机播放",
            "",
            "&7共 &f${state.playlists.size} &7个可用歌单"))

        if (state.playlists.isEmpty()) {
            inv.setItem(22, Items.build(Material.BARRIER, "&c暂无可用歌单",
                "&7你还没有任何歌单",
                "&7请先在歌单浏览中导入"))
        } else {
            val startIdx = state.page * PER_PAGE
            val pageList = state.playlists.drop(startIdx).take(PER_PAGE)
            val slots = (10..16) + (19..25) + (28..34) + (37..43)
            for (i in pageList.indices) {
                val slot = slots.getOrNull(i) ?: break
                val pl = pageList[i]
                // 应用本地重命名覆盖显示
                val displayName = PlayerSettings.getRename(player, pl.platform, pl.id, pl.name)
                inv.setItem(slot, Items.build(Material.MUSIC_DISC_CAT,
                    "&f$displayName",
                    "&7歌曲数: &f${pl.songCount}",
                    "&7平台: &f${platformName(pl.platform)}",
                    if (pl.isOwn) "&a[个人]" else if (pl.isFavorite) "&e[收藏]" else "&b[全服]",
                    "",
                    "${state.scope.icon}&a▸ 推送到 ${state.scope.display}"))
            }
        }

        val totalPages = maxOf(1, ((state.playlists.size + PER_PAGE - 1) / PER_PAGE))
        inv.setItem(45, Items.build(Material.ARROW, "&a上一页", "&7第 ${state.page + 1}/$totalPages 页"))
        inv.setItem(SLOT_BACK, Items.back())
        inv.setItem(53, Items.build(Material.ARROW, "&a下一页", "&7第 ${state.page + 1}/$totalPages 页"))

        player.openInventory(inv)
    }

    override fun open(player: Player) {
        if (states.containsKey(player)) render(player) else QuickPlayGui.open(player)
    }

    override fun handleClick(player: Player, slot: Int) {
        val state = states[player] ?: return
        when (slot) {
            45 -> { state.page = (state.page - 1).coerceAtLeast(0); render(player) }
            SLOT_BACK -> QuickPlayGui.open(player)
            53 -> {
                val maxPage = ((state.playlists.size + PER_PAGE - 1) / PER_PAGE) - 1
                state.page = (state.page + 1).coerceAtMost(maxOf(0, maxPage))
                render(player)
            }
            else -> {
                val slots = (10..16) + (19..25) + (28..34) + (37..43)
                val idx = slots.indexOf(slot)
                if (idx >= 0) {
                    val actualIdx = state.page * PER_PAGE + idx
                    val pl = state.playlists.getOrNull(actualIdx) ?: return
                    pushPlaylist(player, pl, state.scope)
                }
            }
        }
    }

    /**
     * 推送歌单给范围内的玩家
     * - 推送者收到提示, 不自动播放
     * - 范围内所有玩家 (含推送者) 收到可点击消息: [顺序播放] [随机播放]
     */
    private fun pushPlaylist(player: Player, playlist: AggregatedPlaylist, scope: Scope) {
        player.closeInventory()
        // 推送者看到的显示名 (应用本地重命名)
        val displayName = PlayerSettings.getRename(player, playlist.platform, playlist.id, playlist.name)
        player.sendMessage(Items.color("${Messages.prefix()} &a已推送歌单: &f$displayName &a到 ${scope.display}"))

        // 获取范围内的玩家
        val targets = ScopeManager.getTargets(player, scope)
            .filter { it.isOnline }

        // 给范围内所有玩家 (含推送者) 发送可点击消息
        // 接收者看到的歌单名也按其本地 rename 覆盖 (若已收藏此歌单并自定义了名称)
        for (target in targets) {
            sendPlaylistPushMessage(target, player, playlist)
        }

        // 推送者自己也收到消息 (不在 targets 中时)
        if (targets.none { it.uniqueId == player.uniqueId }) {
            sendPlaylistPushMessage(player, player, playlist)
        }

        player.sendMessage(Items.color("${Messages.prefix()} &7共 &f${targets.size} &7名玩家收到推送"))
    }

    /** 给目标玩家发送可点击的歌单推送消息 (歌单名按接收者本地 rename 覆盖显示) */
    private fun sendPlaylistPushMessage(target: Player, pusher: Player, playlist: AggregatedPlaylist) {
        val prefix = Messages.prefix()
        // 接收者看到的歌单名 (若其本地有 rename 则覆盖)
        val displayName = PlayerSettings.getRename(target, playlist.platform, playlist.id, playlist.name)
        // 文本提示
        target.sendMessage(Items.color("$prefix &e${pusher.name} &7向你推送了歌单: &f$displayName &7(${playlist.songCount} 首)"))

        // 可点击按钮: [顺序播放] [随机播放]
        try {
            val seqCmd = "/zmg pushplay ${playlist.platform}:${playlist.id}:${pusher.name}:sequence"
            val shufCmd = "/zmg pushplay ${playlist.platform}:${playlist.id}:${pusher.name}:shuffle"

            val seqBtn = Component.text("§a§l[顺序播放]")
                .clickEvent(ClickEvent.runCommand(seqCmd))
                .hoverEvent(HoverEvent.showText(Component.text("§a点击顺序播放此歌单")))
            val shufBtn = Component.text("§d§l[随机播放]")
                .clickEvent(ClickEvent.runCommand(shufCmd))
                .hoverEvent(HoverEvent.showText(Component.text("§d点击随机播放此歌单")))

            target.sendMessage(
                Component.text("§b[ZMusicGUI] §7点击选择播放方式: ").append(seqBtn).append(Component.text(" ")).append(shufBtn)
            )
        } catch (_: Throwable) {
            // 非 Paper 环境降级: 纯文本命令提示
            target.sendMessage(Items.color("$prefix &a输入 /zmg pushplay ${playlist.platform}:${playlist.id}:${pusher.name}:sequence 顺序播放"))
            target.sendMessage(Items.color("$prefix &d输入 /zmg pushplay ${playlist.platform}:${playlist.id}:${pusher.name}:shuffle 随机播放"))
        }
    }

    /** 异步加载歌单并为玩家播放 — 懒加载模式 (只加载第一首, 后续切歌时按需加载) */
    fun startPlaylistForPlayer(player: Player, playlist: AggregatedPlaylist) {
        val source = playlist.platform
        val songs = playlist.songs
        if (songs.isEmpty()) return

        SchedulerUtil.runAsync(ZMusicGUI.plugin, Runnable {
            // 懒加载: 只加载第一首歌的详情, 后续歌曲切歌时由 MusicPlayer 按需加载
            // (避免大量并发请求压垮服务端, 也避免歌单开始前的长时间等待)
            val firstSong = songs[0]
            val firstDetail = try { SearchService.getSongDetailBySource(firstSong.id, source, player) } catch (_: Throwable) { null }

            if (firstDetail == null || firstDetail.url.isEmpty()) {
                SchedulerUtil.runSync(ZMusicGUI.plugin, Runnable {
                    player.sendMessage(Items.color("${Messages.prefix()} &c无法播放: &f${firstSong.name}"))
                })
                return@Runnable
            }

            // 后续歌曲只放元信息, url 留空 — MusicPlayer.startPlay 会懒加载
            val queue = songs.mapIndexed { idx, s ->
                if (idx == 0) firstDetail
                else com.ourcraft.zmusicgui.music.OurMusicApi.SongDetail(
                    id = s.id, name = s.name, singer = s.singer,
                    url = "", lyric = "", time = s.time.toInt(),
                    source = source
                )
            }.toMutableList()

            SchedulerUtil.runSync(ZMusicGUI.plugin, Runnable {
                MusicPlayer.playPlaylist(player, queue, 0)
                val displayName = PlayerSettings.getRename(player, playlist.platform, playlist.id, playlist.name)
                player.sendMessage(Items.color("${Messages.prefix()} &a正在播放歌单: &f$displayName &7(${queue.size} 首)"))
                player.sendMessage(Items.color("${Messages.prefix()} &7后续歌曲切歌时自动加载"))
                Debug.debug("歌单推送播放: ${player.name} - ${playlist.name} (${queue.size}首) 懒加载模式")
            })
        })
    }

    private fun platformName(id: String): String = when (id) {
        "netease", "163" -> "网易云"
        "kugou" -> "酷狗"
        "kuwo" -> "酷我"
        else -> id
    }

    fun cleanup(player: Player) {
        states.remove(player)
    }
}
