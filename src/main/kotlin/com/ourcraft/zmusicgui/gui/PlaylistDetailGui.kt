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
 * 歌单详情 v3.0.1 — TrMenu 风格 YAML 自定义
 *
 * 布局由 GUI/playlist_detail.yml 定义, 代码负责:
 *  - 占位符填充 (playlist_name/count/platform/owner_line/rename_line/页码)
 *  - 动态歌曲列表填充
 *  - 收藏按钮材质切换 + 重命名按钮显隐
 *  - 点击路由 (含 Shift+右键删除歌曲)
 */
object PlaylistDetailGui : ZGui {

    private data class DetailState(val playlist: AggregatedPlaylist, var page: Int = 0)
    private val states = mutableMapOf<Player, DetailState>()

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

        val songs = pl.songs
        val perPage = Config.playlistSongsPerPage()
        val totalPages = maxOf(1, (songs.size + perPage - 1) / perPage)

        val renameOpt = PlayerSettings.getRenameOpt(player, pl.platform, pl.id)
        val renameLine = if (renameOpt != null && pl.name != renameOpt) "&7原始名: &f${pl.name}" else ""
        val ownerLine = if (pl.isGlobal) "&7来源: &b全服" else Messages.gui("playlist.owner", "owner" to pl.owner)

        val placeholders = mapOf(
            "playlist_name" to displayName,
            "count" to pl.songCount.toString(),
            "platform" to platformName(pl.platform),
            "owner_line" to ownerLine,
            "rename_line" to renameLine,
            "current" to (state.page + 1).toString(),
            "total_pages" to totalPages.toString()
        )

        val inv = GuiLoader.render("playlist_detail", holder, placeholders) ?: run {
            player.sendMessage(Items.color("${Messages.prefix()} &cGUI 配置 playlist_detail.yml 缺失"))
            return
        }

        // 动态填充歌曲列表
        val startIdx = state.page * perPage
        val pageSongs = songs.drop(startIdx).take(perPage)
        val dynDef = GuiLoader.getDef("playlist_detail")?.dynamic
        val dynItems = pageSongs.mapIndexed { i, song ->
            val num = startIdx + i + 1
            val name = GuiLoader.applyPlaceholders(dynDef?.templateName ?: "&f{index}. {name}",
                mapOf("index" to num.toString(), "name" to song.name))
            val lore = (dynDef?.templateLore ?: emptyList()).map { line ->
                GuiLoader.applyPlaceholders(line, mapOf(
                    "singer" to song.singer, "id" to song.id, "name" to song.name
                ))
            }.toMutableList()
            // 历史歌单追加 Shift+右键删除提示
            if (pl.isHistory) lore.add("&cShift+右键 &7删除此歌")
            GuiLoader.DynamicItem(name, lore, Material.PAPER, glow = false)
        }
        GuiLoader.fillDynamic(inv, "playlist_detail", dynItems)

        // 重命名按钮: 仅个人/收藏歌单显示
        if (!pl.isOwn && !pl.isFavorite) {
            GuiLoader.getIconAt("playlist_detail", 39)?.let {
                if (it.clickHandler == "rename") inv.setItem(39, Items.border())
            }
        } else if (renameOpt != null) {
            // 已有重命名: 更新按钮名称提示
            val renameIcon = GuiLoader.getIconAt("playlist_detail", 39)
            if (renameIcon != null && renameIcon.clickHandler == "rename") {
                inv.setItem(39, Items.build(Material.ANVIL, "&e&l✎ 重命名 (已自定义)",
                    "&7本地重命名此歌单 (不影响外部平台)",
                    "&7当前: &f$displayName",
                    "&7原始: &f${pl.name}",
                    "",
                    "&a▸ 点击输入新名称",
                    "&c输入空 &7恢复原名"))
            }
        }

        // 收藏按钮: 仅非自己的歌单显示, 材质按收藏状态切换
        if (pl.owner.equals(player.name, true) && !pl.isGlobal) {
            GuiLoader.getIconAt("playlist_detail", 44)?.let {
                if (it.clickHandler == "favorite") inv.setItem(44, Items.border())
            }
        } else {
            val isFav = PlayerSettings.isFavorite(player, pl.platform, pl.id)
            val favIcon = GuiLoader.getIconAt("playlist_detail", 44)
            if (favIcon != null && favIcon.clickHandler == "favorite") {
                val mat = if (isFav) Material.GOLD_INGOT else Material.GOLD_NUGGET
                val name = if (isFav) Messages.gui("detail.favorite-remove") else Messages.gui("detail.favorite-add")
                val lore = if (isFav) "&7点击取消收藏" else "&7点击收藏此歌单"
                inv.setItem(44, Items.build(mat, name, lore))
            }
        }

        // credits 按配置显示/隐藏
        if (!Config.showCredits()) {
            GuiLoader.getIconAt("playlist_detail", 53)?.let {
                if (it.clickHandler == "credits") inv.setItem(53, Items.border())
            }
        }

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

        // 歌曲动态槽位
        val dynSlots = GuiLoader.getDynamicSlots("playlist_detail")
        val songIdx = dynSlots.indexOf(slot)
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

        // 仅普通左键处理功能按钮
        if (isShift || isRight) return

        val handler = GuiLoader.getClickHandler("playlist_detail", slot) ?: return
        when (handler) {
            "rename" -> startRename(player, state.playlist)
            "prev-page" -> { state.page = (state.page - 1).coerceAtLeast(0); render(player) }
            "next-page" -> {
                val perPage = Config.playlistSongsPerPage()
                val maxPage = (state.playlist.songs.size + perPage - 1) / perPage - 1
                state.page = (state.page + 1).coerceAtMost(maxOf(0, maxPage))
                render(player)
            }
            "favorite" -> {
                val added = PlayerSettings.toggleFavorite(player, state.playlist.platform, state.playlist.id)
                player.sendMessage(Items.color("${Messages.prefix()} ${if (added) Messages.player("favorite-added") else Messages.player("favorite-removed")}"))
                render(player)
            }
            "back" -> PlaylistBrowserGui.open(player)
            "credits" -> MainGui.openWebsite(player)
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
        val displayName = PlayerSettings.getRename(player, playlist.platform, playlist.id, playlist.name)
        player.sendMessage(Items.color("${Messages.prefix()} &7正在加载: &f${playlist.songs[startIndex].name}..."))

        val source = playlist.platform
        val songs = playlist.songs

        // 懒加载: 只加载点击的歌曲, 后续歌曲在切歌时按需加载
        SchedulerUtil.runAsync(ZMusicGUI.plugin, Runnable {
            val startSong = songs[startIndex]
            val startDetail = try { SearchService.getSongDetailBySource(startSong.id, source, player) } catch (_: Throwable) { null }

            if (startDetail == null || startDetail.url.isEmpty()) {
                SchedulerUtil.runSync(ZMusicGUI.plugin, Runnable {
                    player.sendMessage(Items.color("${Messages.prefix()} &c无法播放: &f${startSong.name}"))
                })
                return@Runnable
            }

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
