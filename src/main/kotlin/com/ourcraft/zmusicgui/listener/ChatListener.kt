package com.ourcraft.zmusicgui.listener

import com.ourcraft.zmusicgui.ZMusicGUI
import com.ourcraft.zmusicgui.gui.PlaylistBrowserGui
import com.ourcraft.zmusicgui.gui.QuickPlayGui
import com.ourcraft.zmusicgui.manager.Messages
import com.ourcraft.zmusicgui.manager.PlaylistManager
import com.ourcraft.zmusicgui.util.Debug
import com.ourcraft.zmusicgui.util.SchedulerUtil
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.concurrent.ConcurrentHashMap

/**
 * 聊天输入监听 — 用于搜索歌曲 / 导入歌单 / 歌单重命名
 *
 * 不再处理 zm 命令分发, 所有播放均通过 MusicPlayer。
 */
object ChatListener : Listener {

    /**
     * @param source  音乐平台 id (netease)
     * @param scope   操作类型:
     *                - "quickplay_search"  快捷点歌搜索输入
     *                - "import_personal"   个人歌单导入
     *                - "rename_playlist"   歌单重命名输入
     * @param extra   额外数据 (rename_playlist 用: "平台:歌单ID")
     */
    private data class Ctx(
        val source: String,
        val scope: String,
        val timeoutTask: Any?,
        val extra: String? = null
    )
    private val waiting = ConcurrentHashMap<Player, Ctx>()

    fun awaitInput(player: Player, source: String, scope: String, promptKey: String = "song-play-prompt") {
        waiting.remove(player)?.timeoutTask?.let { SchedulerUtil.cancelTask(it) }

        val timeoutTask = SchedulerUtil.runSyncLater(ZMusicGUI.plugin, Runnable {
            val removed = waiting.remove(player)
            if (removed != null) {
                player.sendMessage(color("${Messages.prefix()} &c输入超时, 已取消"))
                Debug.debug("输入超时: ${player.name}")
            }
        }, 1800L)

        waiting[player] = Ctx(source, scope, timeoutTask)
        val prompt = Messages.player(promptKey)
        player.sendMessage(color("${Messages.prefix()} ${Messages.player("song-prompt", "prompt" to prompt)}"))
        Debug.debug("等待输入: ${player.name} scope=$scope source=$source")
    }

    /**
     * 通用提示输入 — 用于歌单重命名等需要自定义提示的场景
     *
     * @param scope     操作类型 (例如 "rename_playlist")
     * @param promptKey messages.yml 中的 player.<key> 提示文本键
     * @param extra     附带数据 (例如 "平台:歌单ID"), 在 onChat 中传给处理函数
     */
    fun awaitInputWithPrompt(player: Player, scope: String, promptKey: String, extra: String?) {
        waiting.remove(player)?.timeoutTask?.let { SchedulerUtil.cancelTask(it) }

        val timeoutTask = SchedulerUtil.runSyncLater(ZMusicGUI.plugin, Runnable {
            val removed = waiting.remove(player)
            if (removed != null) {
                player.sendMessage(color("${Messages.prefix()} &c输入超时, 已取消"))
                Debug.debug("输入超时: ${player.name}")
            }
        }, 1800L)

        waiting[player] = Ctx(source = "", scope = scope, timeoutTask = timeoutTask, extra = extra)
        val prompt = Messages.player(promptKey)
        player.sendMessage(color("${Messages.prefix()} ${Messages.player("song-prompt", "prompt" to prompt)}"))
        Debug.debug("等待输入: ${player.name} scope=$scope extra=$extra")
    }

    fun cancel(player: Player) {
        waiting.remove(player)?.timeoutTask?.let { SchedulerUtil.cancelTask(it) }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onChat(event: AsyncPlayerChatEvent) {
        val player = event.player
        val ctx = waiting.remove(player) ?: return

        ctx.timeoutTask?.let { SchedulerUtil.cancelTask(it) }
        event.isCancelled = true

        if (event.message.equals("cancel", ignoreCase = true)) {
            player.sendMessage(color("${Messages.prefix()} &c${Messages.player("song-cancelled")}"))
            Debug.debug("输入取消: ${player.name}")
            return
        }

        val input = event.message.trim()
        Debug.debug("输入: ${player.name} -> \"$input\" (scope=${ctx.scope})")

        when (ctx.scope) {
            // 快捷点歌搜索输入 → QuickPlayGui 处理
            "quickplay_search" -> {
                SchedulerUtil.runSync(ZMusicGUI.plugin, Runnable {
                    QuickPlayGui.handleSearchInput(player, input)
                })
            }
            // 个人歌单导入 → PlaylistBrowserGui 处理
            "import_personal" -> {
                SchedulerUtil.runSync(ZMusicGUI.plugin, Runnable {
                    PlaylistBrowserGui.handleImportInput(player, input)
                })
            }
            // 搜索歌单 → PlaylistBrowserGui 处理
            "search_playlist" -> {
                SchedulerUtil.runSync(ZMusicGUI.plugin, Runnable {
                    PlaylistBrowserGui.handleSearchInput(player, input)
                })
            }
            // 歌单重命名输入 → PlaylistManager 处理 (ctx.extra="平台:歌单ID")
            "rename_playlist" -> {
                SchedulerUtil.runSync(ZMusicGUI.plugin, Runnable {
                    PlaylistManager.handleRenameInput(player, ctx.extra ?: "", input)
                })
            }
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        cancel(event.player)
        com.ourcraft.zmusicgui.manager.ScopeManager.cleanup(event.player)
    }

    private fun color(text: String) = text.replace('&', '§')
}
