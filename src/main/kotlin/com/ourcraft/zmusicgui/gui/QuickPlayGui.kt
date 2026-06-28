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
 * 快捷点歌 v2.2.1
 *
 * 流程: 输入歌名 → 搜索 → 结果列表 → 选歌 → 选范围 → 播放
 * 个人范围直接播放, 其他范围需同意 + 扣费
 */
object QuickPlayGui : ZGui {

    private const val SLOT_SEARCH = 11
    private const val SLOT_SCOPE = 13
    private const val SLOT_PUSH = 15
    private const val SLOT_BACK = 22

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
        val inv = holder.create(27, Items.deserialize(Config.quickPlayTitle()))

        // 边框
        for (i in 0..8) { inv.setItem(i, Items.border()); inv.setItem(i + 18, Items.border()) }
        inv.setItem(9, Items.border()); inv.setItem(17, Items.border())

        // 标题
        inv.setItem(4, Items.build(Material.NETHER_STAR, "&6&l🎵 快捷点歌",
            "&7输入歌名 → 展示搜索结果 → 选歌播放",
            "",
            "&f当前源: &a$sourceName &7(在设置中切换)",
            "${state.scope.icon}&f当前范围: ${state.scope.display}",
            "",
            "&a▸ 点击下方搜索按钮开始"))

        // 搜索输入
        inv.setItem(SLOT_SEARCH, Items.buildGlowing(Material.OAK_SIGN,
            Messages.gui("quick-play.search"),
            "&7点击后在聊天栏输入歌名",
            "&7将展示搜索结果列表选歌播放",
            "",
            "&a▸ 点击开始点歌"))

        // 范围切换
        val scopeAvail = ScopeManager.isAvailable(player, state.scope)
        val scopeDesc = ScopeManager.describeScope(player, state.scope)
        val scopeLore = mutableListOf(
            "&7点击循环切换范围",
            "",
            "${state.scope.icon}&f当前: ${state.scope.display}"
        )
        if (scopeDesc != null) scopeLore.add("&7$scopeDesc")
        if (!scopeAvail) scopeLore.add("&c当前范围不可用, 点击切换")
        scopeLore.add("")
        scopeLore.add("&b▸ 点击切换")
        inv.setItem(SLOT_SCOPE, Items.build(scopeMaterial(state.scope),
            Messages.gui("quick-play.scope"), *scopeLore.toTypedArray()))

        // 歌单推送
        val scopeAvail2 = ScopeManager.isAvailable(player, state.scope)
        inv.setItem(SLOT_PUSH, Items.buildGlowing(Material.BOOKSHELF, "&d&l📚 歌单推送",
            "&7将整个歌单推给范围内的人",
            "${state.scope.icon}&f目标: ${state.scope.display}",
            "&7范围内玩家可点 [顺序] 或 [随机]",
            "",
            if (scopeAvail2 && state.scope != Scope.SELF) "&d▸ 点击选择歌单" else "&7(个人范围无需推送)"))

        // 返回
        inv.setItem(SLOT_BACK, Items.back())
        if (Config.showCredits()) inv.setItem(26, Items.credits())

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
        when (slot) {
            SLOT_SEARCH -> startSearch(player)
            SLOT_SCOPE -> cycleScope(player)
            SLOT_PUSH -> {
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
            SLOT_BACK -> MainGui.open(player)
            26 -> MainGui.openWebsite(player)
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

        // 异步搜索
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
        val sourceName = SearchService.sourceName(PlayerSettings.getCurrentSource(player))

        val holder = GuiHolder(this)
        val inv = holder.create(45, Items.deserialize(Messages.gui("quick-play.search-result", "count" to state.results.size.toString())))

        // 底部边框
        for (i in 36..44) inv.setItem(i, Items.border())

        val start = state.page * RESULTS_PER_PAGE
        val pageResults = state.results.drop(start).take(RESULTS_PER_PAGE)

        for (i in pageResults.indices) {
            val song = pageResults[i]
            val num = start + i + 1
            inv.setItem(i, Items.build(Material.PAPER,
                "&f$num. ${song.name}",
                "&7歌手: &f${song.singer}",
                "&7ID: &8${song.id}",
                "",
                "${state.scope.icon}${Messages.gui("quick-play.play-this")} (${state.scope.display})"))
        }

        // 底部操作
        inv.setItem(36, Items.build(Material.ARROW, "&a← 重新搜索", "&7点击输入新关键词"))
        inv.setItem(40, Items.build(Material.ARROW, "&a← 返回", "&7返回快捷点歌"))
        if (state.page > 0) {
            inv.setItem(38, Items.build(Material.ARROW, "&a⬆ 上一页"))
        }
        if (state.results.size > start + RESULTS_PER_PAGE) {
            inv.setItem(42, Items.build(Material.ARROW, "&a⬇ 下一页"))
        }

        player.openInventory(inv)
        Debug.debug("搜索结果: ${player.name} ${pageResults.size}首 page=${state.page}")
    }

    /** 处理结果列表点击 */
    private fun handleResultClick(player: Player, slot: Int) {
        val state = states[player] ?: return
        when (slot) {
            36 -> { startSearch(player); return }
            40 -> { renderMain(player); return }
            38 -> { state.page = (state.page - 1).coerceAtLeast(0); renderResults(player); return }
            42 -> { state.page++; renderResults(player); return }
        }
        if (slot in 0..35) {
            val start = state.page * RESULTS_PER_PAGE
            val idx = start + slot
            val song = state.results.getOrNull(idx) ?: return
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
        // 检查冷却 (个人范围免扣费)
        if (!player.hasPermission("zmusicgui.bypass")) {
            val remaining = CooldownManager.remaining(player)
            if (remaining > 0) {
                player.sendMessage(Items.color("${Messages.prefix()} ${Messages.player("cooldown", "seconds" to remaining.toString())}"))
                return
            }
        }
        // 扣费 (个人范围也扣点歌费)
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

        // 异步获取歌曲详情并播放
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
        // 全服播放扣更高费用 (server scope 的 cost)
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
