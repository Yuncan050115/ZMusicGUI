package com.ourcraft.zmusicgui.gui

import com.ourcraft.zmusicgui.ZMusicGUI
import com.ourcraft.zmusicgui.listener.ChatListener
import com.ourcraft.zmusicgui.manager.Config
import com.ourcraft.zmusicgui.manager.Messages
import com.ourcraft.zmusicgui.manager.PlaylistManager
import com.ourcraft.zmusicgui.manager.PlaylistManager.AggregatedPlaylist
import com.ourcraft.zmusicgui.manager.PlayerSettings
import com.ourcraft.zmusicgui.manager.SearchService
import com.ourcraft.zmusicgui.music.MusicPlayer
import com.ourcraft.zmusicgui.music.OurMusicApi
import com.ourcraft.zmusicgui.util.Debug
import com.ourcraft.zmusicgui.util.Items
import com.ourcraft.zmusicgui.util.SchedulerUtil
import org.bukkit.Material
import org.bukkit.entity.Player

/**
 * 歌单详情 — 歌曲列表
 *
 * 展示歌单内所有歌曲, 点击播放单曲, 或播放整个歌单。
 * 直接使用 MusicPlayer.play, 不调用 zm play 命令。
 *
 * 新增: 铁砧 (ANVIL) 按钮用于本地重命名歌单 (不修改外部平台)。
 */
object PlaylistDetailGui : ZGui {

    private data class DetailState(val playlist: AggregatedPlaylist, var page: Int = 0)
    private val states = mutableMapOf<Player, DetailState>()
    private val SONG_SLOT_ROWS = listOf(10..16, 19..25, 28..34)

    /** 重命名按钮槽位 */
    private const val SLOT_RENAME = 39

    fun open(player: Player, playlist: AggregatedPlaylist) {
        states[player] = DetailState(playlist)
        render(player)
    }

    /** 获取歌单显示名 (应用本地重命名覆盖) */
    private fun displayPlaylistName(player: Player, pl: AggregatedPlaylist): String {
        return PlayerSettings.getRename(player, pl.platform, pl.id, pl.name)
    }

    private fun render(player: Player) {
        val state = states[player] ?: return
        val pl = state.playlist
        val displayName = displayPlaylistName(player, pl)
        val holder = GuiHolder(this)
        val inv = holder.create(54, Items.deserialize("&6&l$displayName"))

        // 边框
        for (i in 0..8) inv.setItem(i, Items.border())
        for (i in 45..53) inv.setItem(i, Items.border())
        for (r in 0..5) { inv.setItem(r * 9, Items.border()); inv.setItem(r * 9 + 8, Items.border()) }

        // 标题信息 (显示重命名后的名称, 若有覆盖则附加原始名)
        val renameOpt = PlayerSettings.getRenameOpt(player, pl.platform, pl.id)
        val titleLore = mutableListOf(
            Messages.gui("playlist.song-count", "count" to pl.songCount.toString()),
            Messages.gui("playlist.platform", "platform" to platformName(pl.platform)),
            if (pl.isGlobal) "&7来源: &b全服" else Messages.gui("playlist.owner", "owner" to pl.owner)
        )
        if (renameOpt != null && pl.name != renameOpt) {
            titleLore.add("&7原始名: &f${pl.name}")
        }
        titleLore.add("")
        titleLore.add(Messages.gui("detail.play-song"))
        inv.setItem(4, Items.build(Material.NETHER_STAR, "&6&l$displayName",
            *titleLore.toTypedArray()))

        // 歌曲列表
        val songs = pl.songs
        val perPage = Config.playlistSongsPerPage()
        val startIdx = state.page * perPage
        val pageSongs = songs.drop(startIdx).take(perPage)

        val slots = SONG_SLOT_ROWS.flatten()
        for (i in pageSongs.indices) {
            val song = pageSongs[i]
            val slot = slots.getOrNull(i) ?: break
            val num = startIdx + i + 1

            val songDisplayName = Messages.gui("detail.song-name", "name" to "$num. ${song.name}")
            val lore = mutableListOf(
                Messages.gui("detail.song-singer", "singer" to song.singer),
                Messages.gui("detail.song-id", "id" to song.id),
                ""
            )
            lore.add(Messages.gui("detail.play-song"))
            // 历史歌单: Shift+右键删除提示
            if (pl.isHistory) {
                lore.add("&cShift+右键 &7删除此歌")
            }
            inv.setItem(slot, Items.build(Material.PAPER, songDisplayName, *lore.toTypedArray()))
        }

        // 操作按钮
        val totalPages = maxOf(1, (songs.size + perPage - 1) / perPage)
        inv.setItem(37, Items.buildGlowing(Material.JUKEBOX, Messages.gui("detail.play-all"),
            "&7从第一首开始顺序播放"))
        // 重命名按钮 (铁砧图标, 仅个人歌单可重命名)
        if (pl.isOwn || pl.isFavorite) {
            val hasRename = renameOpt != null
            inv.setItem(SLOT_RENAME, Items.build(Material.ANVIL,
                if (hasRename) "&e&l✎ 重命名 (已自定义)" else "&e&l✎ 重命名歌单",
                "&7本地重命名此歌单 (不影响外部平台)",
                if (hasRename) "&7当前: &f$displayName" else "",
                if (hasRename) "&7原始: &f${pl.name}" else "",
                "",
                "&a▸ 点击输入新名称",
                "&c输入空 &7恢复原名"))
        }
        inv.setItem(40, Items.build(Material.ARROW, Messages.gui("playlist.prev-page"),
            Messages.gui("playlist.page-info", "current" to (state.page + 1).toString(), "total" to totalPages.toString())))
        inv.setItem(42, Items.build(Material.ARROW, Messages.gui("playlist.next-page"),
            Messages.gui("playlist.page-info", "current" to (state.page + 1).toString(), "total" to totalPages.toString())))

        // 收藏按钮 (仅非自己的歌单)
        if (!pl.owner.equals(player.name, true) || pl.isGlobal) {
            val isFav = PlayerSettings.isFavorite(player, pl.platform, pl.id)
            inv.setItem(44, Items.build(
                if (isFav) Material.GOLD_INGOT else Material.GOLD_NUGGET,
                if (isFav) Messages.gui("detail.favorite-remove") else Messages.gui("detail.favorite-add"),
                if (isFav) "&7点击取消收藏" else "&7点击收藏此歌单"))
        }

        inv.setItem(49, Items.build(Material.ARROW, "&a← 返回歌单列表"))
        if (Config.showCredits()) inv.setItem(53, Items.credits())

        player.openInventory(inv)
        Debug.debug("歌单详情: ${player.name} playlist=${pl.name} songs=${songs.size} page=${state.page}")
    }

    override fun open(player: Player) {
        if (states.containsKey(player)) render(player) else PlaylistBrowserGui.open(player)
    }

    override fun handleClick(player: Player, slot: Int) {
        handleClickWithEvent(player, slot, false, false)
    }

    /** 处理带点击类型的事件 (Shift+右键删除歌曲, 普通左键播放) */
    fun handleClickWithEvent(player: Player, slot: Int, isShift: Boolean, isRight: Boolean) {
        val state = states[player] ?: return

        // 歌曲槽位
        val songSlots = SONG_SLOT_ROWS.flatten()
        val songIdx = songSlots.indexOf(slot)
        if (songIdx >= 0) {
            val perPage = Config.playlistSongsPerPage()
            val actualIdx = state.page * perPage + songIdx
            val song = state.playlist.songs.getOrNull(actualIdx) ?: return

            // 历史歌单: Shift+右键删除歌曲
            if (isShift && isRight && state.playlist.isHistory) {
                player.closeInventory()
                player.sendMessage(Items.color("${Messages.prefix()} &7正在删除: &f${song.name} &7- &f${song.singer}"))
                PlaylistManager.removeSongFromHistory(player, song.id, song.source) {
                    player.sendMessage(Items.color("${Messages.prefix()} &a已从历史歌单删除: &f${song.name}"))
                    // 重新打开详情页 (会刷新缓存)
                    val refreshed = PlaylistManager.sortForDisplay(player, emptyList())
                    Debug.debug("历史删除完成: ${player.name} (refreshed=${refreshed.size})")
                    PlaylistBrowserGui.open(player)
                }
                return
            }

            // 普通左键: 整个歌单队列播放 (从点击位置开始)
            if (!isShift && !isRight) {
                playPlaylistFromIndex(player, state.playlist, actualIdx)
                return
            }
            return
        }

        // 仅普通左键处理功能按钮 (Shift/右键不触发翻页/收藏等)
        if (isShift || isRight) return

        when (slot) {
            37 -> playPlaylistFromIndex(player, state.playlist, 0)
            SLOT_RENAME -> startRename(player, state.playlist)
            40 -> { state.page = (state.page - 1).coerceAtLeast(0); render(player) }
            42 -> {
                val perPage = Config.playlistSongsPerPage()
                val maxPage = (state.playlist.songs.size + perPage - 1) / perPage - 1
                state.page = (state.page + 1).coerceAtMost(maxOf(0, maxPage))
                render(player)
            }
            44 -> {
                val added = PlayerSettings.toggleFavorite(player, state.playlist.platform, state.playlist.id)
                player.sendMessage(Items.color("${Messages.prefix()} ${if (added) Messages.player("favorite-added") else Messages.player("favorite-removed")}"))
                render(player)
            }
            49 -> PlaylistBrowserGui.open(player)
            53 -> MainGui.openWebsite(player)
        }
    }

    /** 启动歌单重命名 — 关闭 GUI, 监听玩家下一条聊天输入 */
    private fun startRename(player: Player, playlist: AggregatedPlaylist) {
        player.closeInventory()
        val key = "${playlist.platform}:${playlist.id}"
        val currentName = PlayerSettings.getRename(player, playlist.platform, playlist.id, playlist.name)
        player.sendMessage(Items.color("${Messages.prefix()} &6━━━ 歌单重命名 ━━━"))
        player.sendMessage(Items.color("${Messages.prefix()} &7歌单: &f${playlist.name}"))
        if (currentName != playlist.name) {
            player.sendMessage(Items.color("${Messages.prefix()} &7当前自定义: &f$currentName"))
        }
        player.sendMessage(Items.color("${Messages.prefix()} &7请在聊天栏输入新名称, 输入 &ccancel &7取消, 输入空 &7恢复原名"))
        ChatListener.awaitInputWithPrompt(player, "rename_playlist", "rename-prompt", key)
    }

    /**
     * 从指定位置开始播放整个歌单 (异步加载歌曲详情 → MusicPlayer.playPlaylist)
     *
     * 点歌单内的歌 = 整个歌单队列播放, 即使被搜索点歌截停, 下一首仍播放歌单内歌曲,
     * 除非设置了单曲循环或用户手动选择其他歌单。
     */
    private fun playPlaylistFromIndex(player: Player, playlist: AggregatedPlaylist, startIndex: Int) {
        if (playlist.songs.isEmpty()) {
            player.sendMessage(Items.color("${Messages.prefix()} &c歌单为空"))
            return
        }
        player.closeInventory()
        // 显示名称应用本地重命名覆盖
        val displayName = PlayerSettings.getRename(player, playlist.platform, playlist.id, playlist.name)
        player.sendMessage(Items.color("${Messages.prefix()} &7正在加载: &f${playlist.songs[startIndex].name}..."))

        val source = playlist.platform
        val songs = playlist.songs

        // 懒加载: 只加载点击的歌曲, 后续歌曲在切歌时按需加载 (避免大量并发请求压垮服务端)
        SchedulerUtil.runAsync(ZMusicGUI.plugin, Runnable {
            val startSong = songs[startIndex]
            val startDetail = try { SearchService.getSongDetailBySource(startSong.id, source, player) } catch (_: Throwable) { null }

            if (startDetail == null || startDetail.url.isEmpty()) {
                SchedulerUtil.runSync(ZMusicGUI.plugin, Runnable {
                    player.sendMessage(Items.color("${Messages.prefix()} &c无法播放: &f${startSong.name}"))
                })
                return@Runnable
            }

            // 只把后续歌曲的元信息(id/name/singer)放进队列, 播放时才按需获取 url
            val lazyQueue = songs.mapIndexed { idx, s ->
                if (idx == startIndex) startDetail
                else OurMusicApi.SongDetail(
                    id = s.id, name = s.name, singer = s.singer,
                    url = "", lyric = "", time = s.time.toInt(),
                    source = source
                )
            }
            SchedulerUtil.runSync(ZMusicGUI.plugin, Runnable {
                MusicPlayer.playPlaylist(player, lazyQueue.toMutableList(), startIndex)
                player.sendMessage(Items.color("${Messages.prefix()} &a正在播放歌单: &f$displayName"))
                player.sendMessage(Items.color("${Messages.prefix()} &7从第 &f${startIndex + 1} &7首开始 (共 ${songs.size} 首)"))
                player.sendMessage(Items.color("${Messages.prefix()} &7后续歌曲切歌时自动加载"))
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
