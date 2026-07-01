package com.ourcraft.zmusicgui.gui

import com.ourcraft.zmusicgui.ZMusicGUI
import com.ourcraft.zmusicgui.listener.ChatListener
import com.ourcraft.zmusicgui.manager.Config
import com.ourcraft.zmusicgui.manager.EconomyManager
import com.ourcraft.zmusicgui.manager.Messages
import com.ourcraft.zmusicgui.manager.PlayerSettings
import com.ourcraft.zmusicgui.manager.ScopeManager
import com.ourcraft.zmusicgui.manager.ScopeManager.Scope
import com.ourcraft.zmusicgui.manager.SearchService
import com.ourcraft.zmusicgui.music.OurMusicApi
import com.ourcraft.zmusicgui.music.MusicPlayer
import com.ourcraft.zmusicgui.util.Debug
import com.ourcraft.zmusicgui.util.Items
import com.ourcraft.zmusicgui.util.SchedulerUtil
import org.bukkit.Material
import org.bukkit.entity.Player

/**
 * 快捷点歌 v3.0.1 — TrMenu 风格 YAML 自定义
 *
 * 两个视图, 均由 YAML 定义:
 *  - MAIN: GUI/quick_play.yml (搜索/范围/推送/返回)
 *  - RESULTS: GUI/quick_play_results.yml (搜索结果列表 + 翻页)
 *
 * 代码负责: 占位符填充, 范围材质切换, 点击路由, 搜索/播放逻辑
 */
object QuickPlayGui : ZGui {

    private enum class View { MAIN, RESULTS }

    private data class SearchState(
        var results: List<OurMusicApi.Song> = emptyList(),
        var page: Int = 0,
        var scope: Scope = Scope.SELF,
        var view: View = View.MAIN
    )

    private val states = java.util.concurrent.ConcurrentHashMap<Player, SearchState>()
    private const val RESULTS_PER_PAGE = 36

    override fun open(player: Player) {
        ChatListener.cancel(player)
        val state = states.getOrPut(player) { SearchState() }
        if (!ScopeManager.isAvailable(player, state.scope)) {
            state.scope = Scope.SELF
        }
        renderMain(player)
    }

    private fun renderMain(player: Player) {
        val state = states.getOrPut(player) { SearchState() }
        state.view = View.MAIN
        val source = PlayerSettings.getCurrentSource(player)
        val sourceName = SearchService.sourceName(source)
        val holder = GuiHolder(this)

        val scopeDesc = ScopeManager.describeScope(player, state.scope) ?: ""
        val scopeAvail = ScopeManager.isAvailable(player, state.scope)
        val placeholders = mutableMapOf(
            "source" to sourceName,
            "scope" to state.scope.display,
            "scope_icon" to state.scope.icon,
            "scope_desc" to (if (!scopeAvail) "&c当前范围不可用, 点击切换" else scopeDesc)
        )

        val inv = GuiLoader.render("quick_play", holder, placeholders) ?: run {
            player.sendMessage(Items.color("${Messages.prefix()} &cGUI 配置 quick_play.yml 缺失"))
            return
        }

        // 范围图标材质按当前 scope 切换 (保留 YAML 的 name/lore, 仅替换材质)
        val scopeIcon = GuiLoader.getIconAt("quick_play", 13)
        if (scopeIcon != null && scopeIcon.clickHandler == "scope") {
            val name = GuiLoader.applyPlaceholders(scopeIcon.name, placeholders)
            val lore = scopeIcon.lore.map { GuiLoader.applyPlaceholders(it, placeholders) }
            inv.setItem(13, Items.build(scopeMaterial(state.scope), name, lore, scopeIcon.glow))
        }

        // credits 按配置显示/隐藏
        if (!Config.showCredits()) {
            GuiLoader.getIconAt("quick_play", 26)?.let {
                if (it.clickHandler == "credits") inv.setItem(26, Items.border())
            }
        }

        player.openInventory(inv)
        Debug.debug("快捷点歌: ${player.name} scope=${state.scope.id} source=$source")
    }

    override fun handleClick(player: Player, slot: Int) {
        handleClickWithEvent(player, slot, false, false)
    }

    /** 处理带点击类型的事件 (由 GuiListener 调用) */
    fun handleClickWithEvent(player: Player, slot: Int, isShift: Boolean, isRight: Boolean) {
        val state = states.getOrPut(player) { SearchState() }
        if (state.view == View.RESULTS) {
            handleResultClick(player, slot)
            return
        }
        val handler = GuiLoader.getClickHandler("quick_play", slot) ?: return
        when (handler) {
            "search" -> startSearch(player)
            "scope" -> cycleScope(player)
            "push" -> {
                if (state.scope == Scope.SELF) {
                    player.sendMessage(Items.color("${Messages.prefix()} &7个人范围无需推送, 请直接在歌单详情中播放"))
                    return
                }
                if (!ScopeManager.isAvailable(player, state.scope)) {
                    player.sendMessage(Items.color("${Messages.prefix()} &c当前范围不可用"))
                    return
                }
                PlaylistPushGui.open(player, state.scope)
            }
            "back" -> MainGui.open(player)
            "credits" -> MainGui.openWebsite(player)
        }
    }

    /** 开始搜索 — 聊天输入 */
    fun startSearch(player: Player) {
        val state = states.getOrPut(player) { SearchState() }
        if (!ScopeManager.isAvailable(player, state.scope)) {
            player.sendMessage(Items.color("${Messages.prefix()} &c当前范围不可用, 请切换范围"))
            return
        }
        player.closeInventory()
        ChatListener.awaitInput(player, PlayerSettings.getCurrentSource(player), "quickplay_search", "song-play-prompt")
        Debug.debug("等待搜索输入: ${player.name}")
    }

    /** 处理聊天输入 (由 ChatListener 调用) */
    fun handleSearchInput(player: Player, keyword: String) {
        val source = PlayerSettings.getCurrentSource(player)
        player.sendMessage(Items.color("${Messages.prefix()} ${Messages.player("search-started", "keyword" to keyword)}"))

        SchedulerUtil.runAsync(ZMusicGUI.plugin, Runnable {
            val results = try {
                SearchService.search(keyword, source)
            } catch (e: Throwable) {
                SchedulerUtil.runSync(ZMusicGUI.plugin, Runnable {
                    player.sendMessage(Items.color("${Messages.prefix()} ${Messages.player("search-failed", "error" to (e.message ?: "未知"))}"))
                })
                return@Runnable
            }
            if (results.isEmpty()) {
                SchedulerUtil.runSync(ZMusicGUI.plugin, Runnable {
                    player.sendMessage(Items.color("${Messages.prefix()} &c${Messages.player("search-no-result")}"))
                })
                return@Runnable
            }
            val state = states.getOrPut(player) { SearchState() }
            state.results = results
            state.page = 0
            SchedulerUtil.runSync(ZMusicGUI.plugin, Runnable { renderResults(player) })
        })
    }

    /** 渲染搜索结果列表 */
    private fun renderResults(player: Player) {
        val state = states[player] ?: return
        state.view = View.RESULTS
        val holder = GuiHolder(this)

        val placeholders = mapOf(
            "scope" to state.scope.display,
            "scope_icon" to state.scope.icon
        )

        val inv = GuiLoader.render("quick_play_results", holder, placeholders) ?: run {
            player.sendMessage(Items.color("${Messages.prefix()} &cGUI 配置 quick_play_results.yml 缺失"))
            return
        }

        // 动态填充搜索结果
        val start = state.page * RESULTS_PER_PAGE
        val pageResults = state.results.drop(start).take(RESULTS_PER_PAGE)
        val dynamicItems = pageResults.mapIndexed { i, song ->
            GuiLoader.DynamicItem(
                name = GuiLoader.applyPlaceholders(
                    GuiLoader.getDef("quick_play_results")?.dynamic?.templateName ?: "&f{index}. {name}",
                    mapOf("index" to (start + i + 1).toString(), "name" to song.name)
                ),
                lore = (GuiLoader.getDef("quick_play_results")?.dynamic?.templateLore ?: emptyList()).map { line ->
                    GuiLoader.applyPlaceholders(line, mapOf(
                        "singer" to song.singer, "id" to song.id,
                        "scope" to state.scope.display, "scope_icon" to state.scope.icon
                    ))
                }
            )
        }
        GuiLoader.fillDynamic(inv, "quick_play_results", dynamicItems)

        // 翻页按钮显隐: 无上一页时移除 prev-page, 无下一页时移除 next-page
        if (state.page == 0) {
            GuiLoader.getIconAt("quick_play_results", 38)?.let {
                if (it.clickHandler == "prev-page") inv.setItem(38, null)
            }
        }
        if (state.results.size <= start + RESULTS_PER_PAGE) {
            GuiLoader.getIconAt("quick_play_results", 42)?.let {
                if (it.clickHandler == "next-page") inv.setItem(42, null)
            }
        }

        player.openInventory(inv)
        Debug.debug("搜索结果: ${player.name} ${pageResults.size}首 page=${state.page}")
    }

    /** 处理结果列表点击 */
    private fun handleResultClick(player: Player, slot: Int) {
        val state = states[player] ?: return
        val handler = GuiLoader.getClickHandler("quick_play_results", slot)
        when (handler) {
            "re-search" -> { startSearch(player); return }
            "back" -> { renderMain(player); return }
            "prev-page" -> { state.page = (state.page - 1).coerceAtLeast(0); renderResults(player); return }
            "next-page" -> { state.page++; renderResults(player); return }
        }
        // 动态槽位点击 (搜索结果项)
        val dynSlots = GuiLoader.getDynamicSlots("quick_play_results")
        val idx = dynSlots.indexOf(slot)
        if (idx >= 0) {
            val start = state.page * RESULTS_PER_PAGE
            val songIdx = start + idx
            val song = state.results.getOrNull(songIdx) ?: return
            playWithScope(player, song)
            player.closeInventory()
        }
    }

    /** 根据范围播放 */
    private fun playWithScope(player: Player, song: OurMusicApi.Song) {
        val state = states[player] ?: return
        val scope = state.scope

        when (scope) {
            Scope.SELF -> playDirect(player, song)
            Scope.SERVER -> playToAll(player, song)
            Scope.RESIDENCE, Scope.PLOT, Scope.WORLD -> {
                ScopeManager.requestPlay(player, scope, PlayerSettings.getCurrentSource(player), song)
            }
        }
    }

    /** 个人直接播放 (异步获取歌曲 URL → 调用 MusicPlayer) */
    private fun playDirect(player: Player, song: OurMusicApi.Song) {
        if (!player.hasPermission("zmusicgui.bypass")) {
            val remaining = CooldownManager.remaining(player)
            if (remaining > 0) {
                player.sendMessage(Items.color("${Messages.prefix()} ${Messages.player("cooldown", "seconds" to remaining.toString())}"))
                return
            }
        }
        val cost = Config.musicCost()
        if (cost > 0 && EconomyManager.isAvailable && !player.hasPermission("zmusicgui.bypass")) {
            if (!EconomyManager.has(player, cost)) {
                player.sendMessage(Items.color("${Messages.prefix()} ${Messages.player("cost-insufficient", "cost" to EconomyManager.format(cost))}"))
                return
            }
            EconomyManager.withdraw(player, cost)
            player.sendMessage(Items.color("${Messages.prefix()} ${Messages.player("cost-charged", "cost" to EconomyManager.format(cost))}"))
        }

        CooldownManager.set(player, Config.cooldownSeconds())

        SchedulerUtil.runAsync(ZMusicGUI.plugin, Runnable {
            val detail = try { SearchService.getSongDetail(song, player) } catch (e: Throwable) {
                SchedulerUtil.runSync(ZMusicGUI.plugin, Runnable {
                    player.sendMessage(Items.color("${Messages.prefix()} ${Messages.player("search-failed", "error" to (e.message ?: "未知"))}"))
                })
                return@Runnable
            }
            if (detail == null || detail.url.isEmpty()) {
                SchedulerUtil.runSync(ZMusicGUI.plugin, Runnable {
                    player.sendMessage(Items.color("${Messages.prefix()} &c歌曲暂时无法播放, 请尝试其他源或歌曲"))
                })
                return@Runnable
            }
            SchedulerUtil.runSync(ZMusicGUI.plugin, Runnable {
                MusicPlayer.play(player, detail)
                player.sendMessage(Items.color("${Messages.prefix()} ${Messages.player("playing", "name" to song.name, "singer" to song.singer)}"))
            })
        })
    }

    /** 全服播放 (对每个在线玩家调用 MusicPlayer.play) */
    private fun playToAll(requester: Player, song: OurMusicApi.Song) {
        val cost = Config.scopeCost("server")
        if (cost > 0 && EconomyManager.isAvailable && !requester.hasPermission("zmusicgui.bypass")) {
            if (!EconomyManager.has(requester, cost)) {
                requester.sendMessage(Items.color("${Messages.prefix()} ${Messages.player("cost-insufficient", "cost" to EconomyManager.format(cost))}"))
                return
            }
            EconomyManager.withdraw(requester, cost)
            requester.sendMessage(Items.color("${Messages.prefix()} ${Messages.player("cost-charged", "cost" to EconomyManager.format(cost))}"))
        }

        SchedulerUtil.runAsync(ZMusicGUI.plugin, Runnable {
            val detail = try { SearchService.getSongDetail(song, requester) } catch (e: Throwable) {
                SchedulerUtil.runSync(ZMusicGUI.plugin, Runnable {
                    requester.sendMessage(Items.color("${Messages.prefix()} ${Messages.player("search-failed", "error" to (e.message ?: "未知"))}"))
                })
                return@Runnable
            }
            if (detail == null || detail.url.isEmpty()) {
                SchedulerUtil.runSync(ZMusicGUI.plugin, Runnable {
                    requester.sendMessage(Items.color("${Messages.prefix()} &c获取歌曲播放地址失败"))
                })
                return@Runnable
            }
            SchedulerUtil.runSync(ZMusicGUI.plugin, Runnable {
                val targets = requester.server.onlinePlayers.toList()
                for (target in targets) {
                    MusicPlayer.play(target, detail)
                }
                requester.sendMessage(Items.color("${Messages.prefix()} ${Messages.player("playing-with-scope",
                    "scope" to Scope.SERVER.display, "count" to targets.size.toString(), "name" to song.name)}"))
            })
        })
    }

    /** 循环切换范围 */
    private fun cycleScope(player: Player) {
        val state = states.getOrPut(player) { SearchState() }
        val order = listOf(Scope.SELF, Scope.RESIDENCE, Scope.PLOT, Scope.WORLD, Scope.SERVER)
        val startIdx = order.indexOf(state.scope)
        for (i in 1..order.size) {
            val next = order[(startIdx + i) % order.size]
            if (ScopeManager.isAvailable(player, next)) {
                state.scope = next
                val desc = ScopeManager.describeScope(player, next) ?: next.display
                player.sendMessage(Items.color("${Messages.prefix()} &a范围切换为: &f${next.display} &7($desc)"))
                renderMain(player)
                return
            }
        }
        player.sendMessage(Items.color("${Messages.prefix()} &7当前无可切换的范围"))
    }

    private fun scopeMaterial(scope: Scope): Material = when (scope) {
        Scope.SELF -> Material.GREEN_CONCRETE
        Scope.RESIDENCE -> Material.ORANGE_CONCRETE
        Scope.PLOT -> Material.MAGENTA_CONCRETE
        Scope.WORLD -> Material.LIGHT_BLUE_CONCRETE
        Scope.SERVER -> Material.YELLOW_CONCRETE
    }

    fun cleanup(player: Player) {
        states.remove(player)
    }
}

/** 点歌冷却管理 */
object CooldownManager {
    private val cooldowns = java.util.concurrent.ConcurrentHashMap<Player, Long>()

    fun remaining(player: Player): Int {
        val until = cooldowns[player] ?: return 0
        val now = System.currentTimeMillis() / 1000
        return ((until - now).coerceAtLeast(0)).toInt()
    }

    fun set(player: Player, seconds: Int) {
        if (seconds <= 0) return
        cooldowns[player] = System.currentTimeMillis() / 1000 + seconds
    }

    fun clear(player: Player) {
        cooldowns.remove(player)
    }
}
