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
 * 歌单浏览器 v2.1.0 — 统一视图
 *
 * 一个页面展示所有歌单 (个人 + 收藏 + 全服), 收藏和个人置顶。
 *
 * 交互:
 *  - 左键歌单: 查看详情
 *  - Shift+左键: 收藏/取消收藏
 *  - 右键(自己的歌单): 公开/隐私切换
 *  - 底部: 导入(链接) / 搜索 / 翻页 / 刷新 / 返回
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
    private const val PER_PAGE = 36  // 4 行 9 列
    private const val SEARCH_PER_PAGE = 36

    // 底部操作按钮
    private const val SLOT_IMPORT = 45
    private const val SLOT_SEARCH = 46
    private const val SLOT_PREV = 47
    private const val SLOT_BACK = 49
    private const val SLOT_REFRESH = 51
    private const val SLOT_NEXT = 53

    // 搜索结果 GUI 按钮
    private const val SLOT_SEARCH_PREV = 45
    private const val SLOT_SEARCH_BACK = 49
    private const val SLOT_SEARCH_NEXT = 53

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
        val inv = holder.create(27, Items.deserialize("${Config.playlistTitle()} &7(加载中...)"))
        for (i in 0..26) inv.setItem(i, Items.border())
        inv.setItem(13, Items.build(Material.CLOCK, Messages.gui("common.loading"), Messages.gui("common.loading-hint")))
        player.openInventory(inv)
    }

    private fun render(player: Player) {
        val state = states[player] ?: return
        // 统一排序: 个人 → 收藏 → 全服
        val sorted = PlaylistManager.sortForDisplay(player, state.all)

        val holder = GuiHolder(this)
        val inv = holder.create(54, Items.deserialize(Config.playlistTitle()))

        // 边框
        for (i in 0..8) inv.setItem(i, Items.border())
        for (i in 45..53) inv.setItem(i, Items.border())
        for (r in 0..5) { inv.setItem(r * 9, Items.border()); inv.setItem(r * 9 + 8, Items.border()) }

        // 统计
        val mineCount = sorted.count { it.isOwn }
        val favCount = sorted.count { it.isFavorite }
        val totalCount = sorted.size
        inv.setItem(4, Items.build(Material.NETHER_STAR, "&6&l歌单浏览",
            "&7总计: &f$totalCount &7个歌单",
            "&a个人: $mineCount &7| &e收藏: $favCount &7| &b其他: ${totalCount - mineCount - favCount}",
            "",
            "&7左键查看 | Shift+左键收藏 | 右键公开切换"))

        // 歌单列表 (slot 10-43, 排除边框)
        if (sorted.isEmpty()) {
            inv.setItem(22, Items.build(Material.BARRIER, "&c暂无歌单",
                "&7你还没有任何歌单",
                "&7点击下方 &e➕ 导入歌单 &7添加"))
        } else {
            val startIdx = state.page * PER_PAGE
            val pageList = sorted.drop(startIdx).take(PER_PAGE)
            val slots = (10..16) + (19..25) + (28..34) + (37..43)
            var lastCategory = ""
            for (i in pageList.indices) {
                val slot = slots.getOrNull(i) ?: break
                val pl = pageList[i]
                // 分类标签
                val category = when {
                    pl.isOwn && pl.isFavorite -> "个人&收藏"
                    pl.isOwn -> "个人"
                    pl.isFavorite -> "收藏"
                    else -> "全服"
                }
                if (category != lastCategory) {
                    lastCategory = category
                }
                renderPlaylist(inv, slot, pl, player, category)
            }
        }

        // 底部操作
        val totalPages = maxOf(1, ((sorted.size + PER_PAGE - 1) / PER_PAGE))
        val currentSource = PlayerSettings.getCurrentSource(player)
        val sourceName = SearchService.sourceName(currentSource)
        inv.setItem(SLOT_IMPORT, Items.buildGlowing(Material.WRITABLE_BOOK,
            Messages.gui("playlist.import"),
            "&7通过链接或歌单ID导入",
            "&7当前源: $sourceName",
            if (currentSource == "netease") "&7支持网易云分享链接" else "&c该源暂不支持导入",
            "",
            "&a▸ 点击开始导入"))
        inv.setItem(SLOT_SEARCH, Items.buildGlowing(Material.COMPASS,
            "&a🔍 搜索歌单",
            "&7按歌单名搜索歌单",
            "&7当前源: $sourceName",
            if (currentSource == "netease") "&7适合发现热门歌单" else "&c该源暂不支持搜索",
            "",
            "&a▸ 点击开始搜索"))
        inv.setItem(SLOT_PREV, Items.build(Material.ARROW, Messages.gui("playlist.prev-page"),
            Messages.gui("playlist.page-info", "current" to (state.page + 1).toString(), "total" to totalPages.toString())))
        inv.setItem(SLOT_BACK, Items.back())
        inv.setItem(SLOT_REFRESH, Items.build(Material.CLOCK, Messages.gui("playlist.refresh"),
            "&7重新读取歌单数据"))
        inv.setItem(SLOT_NEXT, Items.build(Material.ARROW, Messages.gui("playlist.next-page"),
            Messages.gui("playlist.page-info", "current" to (state.page + 1).toString(), "total" to totalPages.toString())))
        if (Config.showCredits()) inv.setItem(48, Items.credits())

        player.openInventory(inv)
        Debug.debug("歌单浏览: ${player.name} total=${sorted.size} page=${state.page}")
    }

    private fun renderPlaylist(inv: org.bukkit.inventory.Inventory, slot: Int, pl: AggregatedPlaylist, player: Player, category: String) {
        // 应用本地重命名覆盖显示
        val displayName = PlayerSettings.getRename(player, pl.platform, pl.id, pl.name)
        val hasRename = displayName != pl.name
        val lore = mutableListOf(
            Messages.gui("playlist.song-count", "count" to pl.songCount.toString()),
            Messages.gui("playlist.platform", "platform" to platformName(pl.platform)),
            if (pl.isGlobal) "&7来源: &b全服" else Messages.gui("playlist.owner", "owner" to pl.owner)
        )
        // 若有自定义重命名, 显示原始名
        if (hasRename) {
            lore.add("&7原始名: &f${pl.name}")
        }

        // 状态标记
        when {
            pl.isHistory -> {
                lore.add("&7自动记录每次点歌")
                lore.add(if (pl.isPublic) Messages.gui("playlist.public") else Messages.gui("playlist.private"))
                lore.add(Messages.gui("playlist.public-toggle-hint"))
            }
            pl.isOwn -> {
                lore.add(if (pl.isPublic) Messages.gui("playlist.public") else Messages.gui("playlist.private"))
                lore.add(Messages.gui("playlist.public-toggle-hint"))
            }
            pl.isFavorite -> {
                lore.add(Messages.gui("playlist.favorite-marked"))
            }
        }

        if (!pl.isOwn && !pl.isHistory) {
            lore.add(Messages.gui("playlist.favorite-add-hint"))
        }
        lore.add("")
        lore.add(Messages.gui("playlist.click-detail"))

        // 分类前缀和材质
        val (prefix, mat) = when {
            pl.isHistory -> "&d[历史] " to Material.MUSIC_DISC_PIGSTEP
            pl.isOwn -> "&a[个人] " to Material.MUSIC_DISC_CAT
            pl.isFavorite -> "&e[收藏] " to Material.MUSIC_DISC_MALL
            else -> "&b[全服] " to Material.MUSIC_DISC_CHIRP
        }

        inv.setItem(slot, Items.build(mat, "$prefix&f$displayName", *lore.toTypedArray()))
    }

    override fun handleClick(player: Player, slot: Int) {
        handleClickWithEvent(player, slot, false, false)
    }

    /** 处理带点击类型的事件 (由 GuiListener 调用) */
    fun handleClickWithEvent(player: Player, slot: Int, isShift: Boolean, isRight: Boolean) {
        val state = states[player] ?: return

        // 底部操作按钮
        when (slot) {
            SLOT_IMPORT -> { openImportMenu(player); return }
            SLOT_SEARCH -> { openSearchPrompt(player); return }
            SLOT_PREV -> { state.page = (state.page - 1).coerceAtLeast(0); render(player); return }
            SLOT_BACK -> { MainGui.open(player); return }
            SLOT_REFRESH -> { PlaylistManager.refresh(player); open(player); return }
            SLOT_NEXT -> {
                val sorted = PlaylistManager.sortForDisplay(player, state.all)
                val maxPage = ((sorted.size + PER_PAGE - 1) / PER_PAGE) - 1
                state.page = (state.page + 1).coerceAtMost(maxOf(0, maxPage))
                render(player); return
            }
            48 -> { MainGui.openWebsite(player); return }
        }

        // 搜索结果 GUI 的翻页/返回按钮
        if (state.searchResults.isNotEmpty()) {
            when (slot) {
                SLOT_SEARCH_PREV -> { state.searchPage = (state.searchPage - 1).coerceAtLeast(0); renderSearchResults(player); return }
                SLOT_SEARCH_BACK -> { state.searchResults = emptyList(); state.searchPage = 0; render(player); return }
                SLOT_SEARCH_NEXT -> {
                    val maxPage = ((state.searchResults.size + SEARCH_PER_PAGE - 1) / SEARCH_PER_PAGE) - 1
                    state.searchPage = (state.searchPage + 1).coerceAtMost(maxOf(0, maxPage))
                    renderSearchResults(player); return
                }
            }
            // 搜索结果点击 (导入该歌单)
            val slots = (10..16) + (19..25) + (28..34) + (37..43)
            val idx = slots.indexOf(slot)
            if (idx >= 0) {
                val actualIdx = state.searchPage * SEARCH_PER_PAGE + idx
                val pl = state.searchResults.getOrNull(actualIdx)
                if (pl != null) {
                    state.searchResults = emptyList()
                    state.searchPage = 0
                    handleImportInput(player, pl.id)
                    return
                }
            }
        }

        // 歌单点击
        val slots = (10..16) + (19..25) + (28..34) + (37..43)
        val idx = slots.indexOf(slot)
        if (idx < 0) return

        val sorted = PlaylistManager.sortForDisplay(player, state.all)
        val actualIdx = state.page * PER_PAGE + idx
        val pl = sorted.getOrNull(actualIdx) ?: return

        when {
            isRight && (pl.isOwn || pl.isHistory) -> {
                // 公开/隐私切换 (个人歌单 + 历史歌单均支持)
                val made = PlayerSettings.togglePublic(player, pl.platform, pl.id)
                player.sendMessage(Items.color("${Messages.prefix()} ${if (made) Messages.player("public-toggled-on") else Messages.player("public-toggled-off")}"))
                PlaylistManager.refresh(player)
                open(player)
            }
            isShift -> {
                // 收藏/取消收藏 (历史歌单和个人歌单不可收藏)
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
        val inv = holder.create(54, Items.deserialize("&6&l🔍 歌单搜索结果"))

        // 边框
        for (i in 0..8) inv.setItem(i, Items.border())
        for (i in 45..53) inv.setItem(i, Items.border())
        for (r in 0..5) { inv.setItem(r * 9, Items.border()); inv.setItem(r * 9 + 8, Items.border()) }

        inv.setItem(4, Items.build(Material.COMPASS, "&6&l搜索结果",
            "&7共找到 &f${results.size} &7个歌单",
            "&7点击歌单即可导入"))

        // 歌单列表
        val startIdx = state.searchPage * SEARCH_PER_PAGE
        val pageList = results.drop(startIdx).take(SEARCH_PER_PAGE)
        val slots = (10..16) + (19..25) + (28..34) + (37..43)
        for (i in pageList.indices) {
            val slot = slots.getOrNull(i) ?: break
            val pl = pageList[i]
            val playCountStr = if (pl.playCount >= 10000) "${pl.playCount / 10000}万" else pl.playCount.toString()
            inv.setItem(slot, Items.build(Material.MUSIC_DISC_CAT,
                "&f${pl.name}",
                "&7创建者: &f${pl.creator}",
                "&7歌曲数: &f${pl.trackCount}",
                "&7播放量: &f$playCountStr",
                "",
                "&a▸ 点击导入此歌单"))
        }

        // 底部按钮
        val totalPages = maxOf(1, ((results.size + SEARCH_PER_PAGE - 1) / SEARCH_PER_PAGE))
        inv.setItem(SLOT_SEARCH_PREV, Items.build(Material.ARROW, "&f上一页",
            "&7第 ${state.searchPage + 1}/$totalPages 页"))
        inv.setItem(SLOT_SEARCH_BACK, Items.back())
        inv.setItem(SLOT_SEARCH_NEXT, Items.build(Material.ARROW, "&f下一页",
            "&7第 ${state.searchPage + 1}/$totalPages 页"))

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
        // 提取歌单 ID (支持链接或纯数字)
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

            // 保存到玩家歌单目录 (默认隐私)
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
        // 纯数字
        if (trimmed.matches(Regex("^\\d+$"))) return trimmed
        // URL: ...playlist/{id} 或 ...id={id}
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
