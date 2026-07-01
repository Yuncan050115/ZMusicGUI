package com.ourcraft.zmusicgui.gui

import com.ourcraft.zmusicgui.ZMusicGUI
import com.ourcraft.zmusicgui.listener.ChatListener
import com.ourcraft.zmusicgui.manager.Config
import com.ourcraft.zmusicgui.manager.Messages
import com.ourcraft.zmusicgui.manager.PlaylistManager
import com.ourcraft.zmusicgui.manager.PlaylistManager.AggregatedPlaylist
import com.ourcraft.zmusicgui.manager.PlayerSettings
import com.ourcraft.zmusicgui.manager.SearchService
import com.ourcraft.zmusicgui.music.OurMusicApi
import com.ourcraft.zmusicgui.util.Debug
import com.ourcraft.zmusicgui.util.Items
import com.ourcraft.zmusicgui.util.SchedulerUtil
import org.bukkit.Material
import org.bukkit.entity.Player

/**
 * 歌单浏览器 v3.0.1 — TrMenu 风格 YAML 自定义
 *
 * 两个视图, 均由 YAML 定义:
 *  - MAIN: GUI/playlist_browser.yml (歌单列表 + 导入/搜索/翻页/刷新)
 *  - SEARCH: GUI/playlist_search.yml (搜索结果列表 + 翻页)
 *
 * 代码负责: 占位符填充, 分类材质/前缀, 点击路由 (含 Shift/右键)
 */
object PlaylistBrowserGui : ZGui {

    private data class State(
        var all: List<AggregatedPlaylist> = emptyList(),
        var page: Int = 0,
        var loading: Boolean = false,
        var searchResults: List<OurMusicApi.PlaylistSearch> = emptyList(),
        var searchPage: Int = 0
    )

    private val states = mutableMapOf<Player, State>()
    private const val PER_PAGE = 28
    private const val SEARCH_PER_PAGE = 28

    override fun open(player: Player) {
        ChatListener.cancel(player)
        val state = states.getOrPut(player) { State() }
        state.page = 0
        showLoading(player)
        PlaylistManager.loadAsync(player) { all ->
            state.all = all
            state.loading = false
            render(player)
        }
    }

    private fun showLoading(player: Player) {
        val holder = GuiHolder(this)
        val inv = holder.create(27, Items.deserialize("${Messages.gui("playlist.title")} &7(加载中...)"))
        for (i in 0..26) inv.setItem(i, Items.border())
        inv.setItem(13, Items.build(Material.CLOCK, Messages.gui("common.loading"), Messages.gui("common.loading-hint")))
        player.openInventory(inv)
    }

    private fun render(player: Player) {
        val state = states[player] ?: return
        val sorted = PlaylistManager.sortForDisplay(player, state.all)
        val holder = GuiHolder(this)

        val mineCount = sorted.count { it.isOwn }
        val favCount = sorted.count { it.isFavorite }
        val totalCount = sorted.size
        val totalPages = maxOf(1, ((sorted.size + PER_PAGE - 1) / PER_PAGE))
        val sourceName = SearchService.sourceName(PlayerSettings.getCurrentSource(player))

        val placeholders = mapOf(
            "total" to totalCount.toString(),
            "mine" to mineCount.toString(),
            "fav" to favCount.toString(),
            "other" to (totalCount - mineCount - favCount).toString(),
            "source" to sourceName,
            "current" to (state.page + 1).toString(),
            "total_pages" to totalPages.toString()
        )

        val inv = GuiLoader.render("playlist_browser", holder, placeholders) ?: run {
            player.sendMessage(Items.color("${Messages.prefix()} &cGUI 配置 playlist_browser.yml 缺失"))
            return
        }

        // 动态填充歌单列表
        val dynSlots = GuiLoader.getDynamicSlots("playlist_browser")
        if (sorted.isEmpty()) {
            inv.setItem(22, Items.build(Material.BARRIER, "&c暂无歌单",
                "&7你还没有任何歌单",
                "&7点击下方 &e➕ 导入歌单 &7添加"))
        } else {
            val startIdx = state.page * PER_PAGE
            val pageList = sorted.drop(startIdx).take(PER_PAGE)
            val dynItems = pageList.map { pl -> buildPlaylistDynamicItem(player, pl) }
            GuiLoader.fillDynamic(inv, "playlist_browser", dynItems)
        }

        // credits 按配置显示/隐藏
        if (!Config.showCredits()) {
            GuiLoader.getIconAt("playlist_browser", 48)?.let {
                if (it.clickHandler == "credits") inv.setItem(48, Items.border())
            }
        }

        player.openInventory(inv)
        Debug.debug("歌单浏览: ${player.name} total=${sorted.size} page=${state.page}")
    }

    /** 构建歌单动态项 (含分类前缀/材质/状态行) */
    private fun buildPlaylistDynamicItem(player: Player, pl: AggregatedPlaylist): GuiLoader.DynamicItem {
        val displayName = PlayerSettings.getRename(player, pl.platform, pl.id, pl.name)
        val (prefix, mat) = when {
            pl.isHistory -> "&d[历史] " to Material.MUSIC_DISC_PIGSTEP
            pl.isOwn -> "&a[个人] " to Material.MUSIC_DISC_CAT
            pl.isFavorite -> "&e[收藏] " to Material.MUSIC_DISC_MALL
            else -> "&b[全服] " to Material.MUSIC_DISC_CHIRP
        }

        val ownerLine = if (pl.isGlobal) "&7来源: &b全服" else Messages.gui("playlist.owner", "owner" to pl.owner)
        val statusLine = when {
            pl.isHistory -> {
                val pub = if (pl.isPublic) Messages.gui("playlist.public") else Messages.gui("playlist.private")
                "$pub &7| ${Messages.gui("playlist.public-toggle-hint")}"
            }
            pl.isOwn -> {
                val pub = if (pl.isPublic) Messages.gui("playlist.public") else Messages.gui("playlist.private")
                "$pub &7| ${Messages.gui("playlist.public-toggle-hint")}"
            }
            pl.isFavorite -> Messages.gui("playlist.favorite-marked")
            else -> Messages.gui("playlist.favorite-add-hint")
        }

        val dynDef = GuiLoader.getDef("playlist_browser")?.dynamic
        val name = GuiLoader.applyPlaceholders(dynDef?.templateName ?: "&f{name}",
            mapOf("name" to "$prefix&f$displayName"))
        val lore = (dynDef?.templateLore ?: emptyList()).map { line ->
            GuiLoader.applyPlaceholders(line, mapOf(
                "count" to pl.songCount.toString(),
                "platform" to platformName(pl.platform),
                "owner_line" to ownerLine,
                "status_line" to statusLine,
                "name" to displayName
            ))
        }

        return GuiLoader.DynamicItem(name, lore, mat, glow = false)
    }

    override fun handleClick(player: Player, slot: Int) {
        handleClickWithEvent(player, slot, false, false)
    }

    /** 处理带点击类型的事件 (由 GuiListener 调用) */
    fun handleClickWithEvent(player: Player, slot: Int, isShift: Boolean, isRight: Boolean) {
        val state = states[player] ?: return

        // 搜索结果视图优先处理
        if (state.searchResults.isNotEmpty()) {
            val searchHandler = GuiLoader.getClickHandler("playlist_search", slot)
            when (searchHandler) {
                "prev-page" -> { state.searchPage = (state.searchPage - 1).coerceAtLeast(0); renderSearchResults(player); return }
                "back" -> { state.searchResults = emptyList(); state.searchPage = 0; render(player); return }
                "next-page" -> {
                    val maxPage = ((state.searchResults.size + SEARCH_PER_PAGE - 1) / SEARCH_PER_PAGE) - 1
                    state.searchPage = (state.searchPage + 1).coerceAtMost(maxOf(0, maxPage))
                    renderSearchResults(player); return
                }
            }
            // 搜索结果动态槽位点击 (导入该歌单)
            val searchSlots = GuiLoader.getDynamicSlots("playlist_search")
            val sIdx = searchSlots.indexOf(slot)
            if (sIdx >= 0) {
                val actualIdx = state.searchPage * SEARCH_PER_PAGE + sIdx
                val pl = state.searchResults.getOrNull(actualIdx)
                if (pl != null) {
                    state.searchResults = emptyList()
                    state.searchPage = 0
                    handleImportInput(player, pl.id)
                    return
                }
            }
        }

        // 主视图按钮
        val handler = GuiLoader.getClickHandler("playlist_browser", slot)
        when (handler) {
            "import" -> { openImportMenu(player); return }
            "search-playlist" -> { openSearchPrompt(player); return }
            "prev-page" -> { state.page = (state.page - 1).coerceAtLeast(0); render(player); return }
            "back" -> { MainGui.open(player); return }
            "refresh" -> { PlaylistManager.refresh(player); open(player); return }
            "next-page" -> {
                val sorted = PlaylistManager.sortForDisplay(player, state.all)
                val maxPage = ((sorted.size + PER_PAGE - 1) / PER_PAGE) - 1
                state.page = (state.page + 1).coerceAtMost(maxOf(0, maxPage))
                render(player); return
            }
            "credits" -> { MainGui.openWebsite(player); return }
        }

        // 歌单动态槽位点击
        val dynSlots = GuiLoader.getDynamicSlots("playlist_browser")
        val idx = dynSlots.indexOf(slot)
        if (idx < 0) return

        val sorted = PlaylistManager.sortForDisplay(player, state.all)
        val actualIdx = state.page * PER_PAGE + idx
        val pl = sorted.getOrNull(actualIdx) ?: return

        when {
            isRight && (pl.isOwn || pl.isHistory) -> {
                val made = PlayerSettings.togglePublic(player, pl.platform, pl.id)
                player.sendMessage(Items.color("${Messages.prefix()} ${if (made) Messages.player("public-toggled-on") else Messages.player("public-toggled-off")}"))
                PlaylistManager.refresh(player)
                open(player)
            }
            isShift -> {
                if (pl.isOwn || pl.isHistory) {
                    player.sendMessage(Items.color("${Messages.prefix()} ${Messages.player("cant-favorite-own")}"))
                    return
                }
                val added = PlayerSettings.toggleFavorite(player, pl.platform, pl.id)
                player.sendMessage(Items.color("${Messages.prefix()} ${if (added) Messages.player("favorite-added") else Messages.player("favorite-removed")}"))
                PlaylistManager.refresh(player)
                open(player)
            }
            else -> PlaylistDetailGui.open(player, pl)
        }
    }

    private fun openImportMenu(player: Player) {
        player.closeInventory()
        val source = PlayerSettings.getCurrentSource(player)
        if (source != "netease") {
            player.sendMessage(Items.color("${Messages.prefix()} &c当前平台 &f${SearchService.sourceName(source)} &c暂不支持歌单导入"))
            player.sendMessage(Items.color("${Messages.prefix()} &7请切换到 &c网易云 &7源后再试 (设置 → 音乐源)"))
            return
        }
        ChatListener.awaitInput(player, source, "import_personal", "import-prompt")
    }

    /** 打开搜索歌单提示 (聊天输入歌单名) */
    private fun openSearchPrompt(player: Player) {
        player.closeInventory()
        val source = PlayerSettings.getCurrentSource(player)
        if (source != "netease") {
            player.sendMessage(Items.color("${Messages.prefix()} &c当前平台 &f${SearchService.sourceName(source)} &c暂不支持歌单搜索"))
            player.sendMessage(Items.color("${Messages.prefix()} &7请切换到 &c网易云 &7源后再试 (设置 → 音乐源)"))
            return
        }
        ChatListener.awaitInput(player, source, "search_playlist", "playlist-search-prompt")
    }

    /** 渲染搜索结果 GUI */
    private fun renderSearchResults(player: Player) {
        val state = states[player] ?: return
        val results = state.searchResults
        if (results.isEmpty()) {
            render(player)
            return
        }
        val holder = GuiHolder(this)

        val totalPages = maxOf(1, ((results.size + SEARCH_PER_PAGE - 1) / SEARCH_PER_PAGE))
        val placeholders = mapOf(
            "count" to results.size.toString(),
            "current" to (state.searchPage + 1).toString(),
            "total_pages" to totalPages.toString()
        )

        val inv = GuiLoader.render("playlist_search", holder, placeholders) ?: run {
            player.sendMessage(Items.color("${Messages.prefix()} &cGUI 配置 playlist_search.yml 缺失"))
            return
        }

        // 动态填充搜索结果
        val startIdx = state.searchPage * SEARCH_PER_PAGE
        val pageList = results.drop(startIdx).take(SEARCH_PER_PAGE)
        val dynDef = GuiLoader.getDef("playlist_search")?.dynamic
        val dynItems = pageList.map { pl ->
            val playCountStr = if (pl.playCount >= 10000) "${pl.playCount / 10000}万" else pl.playCount.toString()
            val name = GuiLoader.applyPlaceholders(dynDef?.templateName ?: "&f{name}", mapOf("name" to pl.name))
            val lore = (dynDef?.templateLore ?: emptyList()).map { line ->
                GuiLoader.applyPlaceholders(line, mapOf(
                    "creator" to pl.creator,
                    "count" to pl.trackCount.toString(),
                    "plays" to playCountStr,
                    "name" to pl.name
                ))
            }
            GuiLoader.DynamicItem(name, lore, Material.MUSIC_DISC_CAT, glow = false)
        }
        GuiLoader.fillDynamic(inv, "playlist_search", dynItems)

        player.openInventory(inv)
        Debug.debug("歌单搜索结果: ${player.name} 共 ${results.size} 个, 第 ${state.searchPage + 1} 页")
    }

    /** 处理搜索歌单输入 (由 ChatListener 调用) */
    fun handleSearchInput(player: Player, keyword: String) {
        val state = states.getOrPut(player) { State() }
        player.sendMessage(Items.color("${Messages.prefix()} &7正在搜索歌单: &f$keyword &7..."))

        SchedulerUtil.runAsync(ZMusicGUI.plugin, Runnable {
            val results = try {
                com.ourcraft.zmusicgui.manager.SearchService.searchPlaylists(keyword, 30)
            } catch (e: Throwable) {
                Debug.warn("搜索歌单失败: ${e.message}")
                emptyList()
            }

            SchedulerUtil.runSync(ZMusicGUI.plugin, Runnable {
                if (results.isEmpty()) {
                    player.sendMessage(Items.color("${Messages.prefix()} &c未找到匹配的歌单"))
                    render(player)
                } else {
                    state.searchResults = results
                    state.searchPage = 0
                    player.sendMessage(Items.color("${Messages.prefix()} &a找到 &f${results.size} &a个歌单, 点击选择导入"))
                    renderSearchResults(player)
                }
            })
        })
    }

    /** 处理歌单导入输入 (由 ChatListener 调用) */
    fun handleImportInput(player: Player, input: String) {
        val playlistId = extractPlaylistId(input)
        if (playlistId.isEmpty()) {
            player.sendMessage(Items.color("${Messages.prefix()} &c无法解析歌单 ID"))
            return
        }

        player.sendMessage(Items.color("${Messages.prefix()} &7正在获取歌单..."))

        SchedulerUtil.runAsync(ZMusicGUI.plugin, Runnable {
            val playlist = try {
                com.ourcraft.zmusicgui.manager.SearchService.getPlaylist(playlistId)
            } catch (e: Throwable) {
                SchedulerUtil.runSync(ZMusicGUI.plugin, Runnable {
                    player.sendMessage(Items.color("${Messages.prefix()} ${Messages.player("playlist-import-failed", "error" to (e.message ?: "未知"))}"))
                })
                return@Runnable
            }

            if (playlist == null) {
                SchedulerUtil.runSync(ZMusicGUI.plugin, Runnable {
                    player.sendMessage(Items.color("${Messages.prefix()} ${Messages.player("playlist-not-found")}"))
                })
                return@Runnable
            }

            val isPrivate = Config.playlistDefaultPrivate()
            try {
                PlaylistManager.saveImportedPlaylist(player, playlist, isPrivate)
                SchedulerUtil.runSync(ZMusicGUI.plugin, Runnable {
                    player.sendMessage(Items.color("${Messages.prefix()} ${Messages.player("playlist-imported",
                        "name" to playlist.name, "count" to playlist.songs.size.toString())}"))
                    if (isPrivate) {
                        player.sendMessage(Items.color("${Messages.prefix()} &7(已设为隐私, 右键切换公开)"))
                    }
                    PlaylistManager.refresh(player)
                    open(player)
                })
            } catch (e: Throwable) {
                SchedulerUtil.runSync(ZMusicGUI.plugin, Runnable {
                    player.sendMessage(Items.color("${Messages.prefix()} ${Messages.player("playlist-import-failed", "error" to (e.message ?: "未知"))}"))
                })
            }
        })
    }

    /** 从输入中提取歌单 ID (支持纯数字 / https://music.163.com/playlist/123456 / ...id=123456) */
    private fun extractPlaylistId(input: String): String {
        val trimmed = input.trim()
        if (trimmed.matches(Regex("^\\d+$"))) return trimmed
        val urlMatch = Regex("""(?:playlist/|id=)(\d+)""").find(trimmed)
        return urlMatch?.groupValues?.get(1) ?: ""
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
