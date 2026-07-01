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
 * 歌单推送 v3.0.1 — TrMenu 风格 YAML 自定义
 *
 * 布局由 GUI/playlist_push.yml 定义, 代码负责:
 *  - 占位符填充 (scope/scope_icon/count/页码/歌单 owner_line)
 *  - 动态歌单列表填充
 *  - 点击路由 (翻页/返回/选择歌单推送)
 *  - 推送逻辑 (发送可点击消息给范围内玩家)
 */
object PlaylistPushGui : ZGui {

    private const val PER_PAGE = 28
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

        val totalPages = maxOf(1, ((state.playlists.size + PER_PAGE - 1) / PER_PAGE))
        val placeholders = mapOf(
            "scope" to state.scope.display,
            "scope_icon" to state.scope.icon,
            "count" to state.playlists.size.toString(),
            "current" to (state.page + 1).toString(),
            "total_pages" to totalPages.toString()
        )

        val inv = GuiLoader.render("playlist_push", holder, placeholders) ?: run {
            player.sendMessage(Items.color("${Messages.prefix()} &cGUI 配置 playlist_push.yml 缺失"))
            return
        }

        // 动态填充歌单列表
        if (state.playlists.isEmpty()) {
            inv.setItem(22, Items.build(Material.BARRIER, "&c暂无可用歌单",
                "&7你还没有任何歌单",
                "&7请先在歌单浏览中导入"))
        } else {
            val startIdx = state.page * PER_PAGE
            val pageList = state.playlists.drop(startIdx).take(PER_PAGE)
            val dynDef = GuiLoader.getDef("playlist_push")?.dynamic
            val dynItems = pageList.map { pl ->
                val displayName = PlayerSettings.getRename(player, pl.platform, pl.id, pl.name)
                val ownerLine = when {
                    pl.isOwn -> "&a[个人]"
                    pl.isFavorite -> "&e[收藏]"
                    else -> "&b[全服]"
                }
                val name = GuiLoader.applyPlaceholders(dynDef?.templateName ?: "&f{name}", mapOf("name" to displayName))
                val lore = (dynDef?.templateLore ?: emptyList()).map { line ->
                    GuiLoader.applyPlaceholders(line, mapOf(
                        "count" to pl.songCount.toString(),
                        "platform" to platformName(pl.platform),
                        "owner_line" to ownerLine,
                        "scope" to state.scope.display,
                        "scope_icon" to state.scope.icon,
                        "name" to displayName
                    ))
                }
                GuiLoader.DynamicItem(name, lore, Material.MUSIC_DISC_CAT, glow = false)
            }
            GuiLoader.fillDynamic(inv, "playlist_push", dynItems)
        }

        player.openInventory(inv)
    }

    override fun open(player: Player) {
        if (states.containsKey(player)) render(player) else QuickPlayGui.open(player)
    }

    override fun handleClick(player: Player, slot: Int) {
        val state = states[player] ?: return
        val handler = GuiLoader.getClickHandler("playlist_push", slot)
        when (handler) {
            "prev-page" -> { state.page = (state.page - 1).coerceAtLeast(0); render(player) }
            "back" -> QuickPlayGui.open(player)
            "next-page" -> {
                val maxPage = ((state.playlists.size + PER_PAGE - 1) / PER_PAGE) - 1
                state.page = (state.page + 1).coerceAtMost(maxOf(0, maxPage))
                render(player)
            }
            else -> {
                // 动态槽位点击 (选择歌单推送)
                val dynSlots = GuiLoader.getDynamicSlots("playlist_push")
                val idx = dynSlots.indexOf(slot)
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
        val displayName = PlayerSettings.getRename(player, playlist.platform, playlist.id, playlist.name)
        player.sendMessage(Items.color("${Messages.prefix()} &a已推送歌单: &f$displayName &a到 ${scope.display}"))

        val targets = ScopeManager.getTargets(player, scope).filter { it.isOnline }

        for (target in targets) {
            sendPlaylistPushMessage(target, player, playlist)
        }

        if (targets.none { it.uniqueId == player.uniqueId }) {
            sendPlaylistPushMessage(player, player, playlist)
        }

        player.sendMessage(Items.color("${Messages.prefix()} &7共 &f${targets.size} &7名玩家收到推送"))
    }

    /** 给目标玩家发送可点击的歌单推送消息 (歌单名按接收者本地 rename 覆盖显示) */
    private fun sendPlaylistPushMessage(target: Player, pusher: Player, playlist: AggregatedPlaylist) {
        val prefix = Messages.prefix()
        val displayName = PlayerSettings.getRename(target, playlist.platform, playlist.id, playlist.name)
        target.sendMessage(Items.color("$prefix &e${pusher.name} &7向你推送了歌单: &f$displayName &7(${playlist.songCount} 首)"))

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
            val firstSong = songs[0]
            val firstDetail = try { SearchService.getSongDetailBySource(firstSong.id, source, player) } catch (_: Throwable) { null }

            if (firstDetail == null || firstDetail.url.isEmpty()) {
                SchedulerUtil.runSync(ZMusicGUI.plugin, Runnable {
                    player.sendMessage(Items.color("${Messages.prefix()} &c无法播放: &f${firstSong.name}"))
                })
                return@Runnable
            }

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
