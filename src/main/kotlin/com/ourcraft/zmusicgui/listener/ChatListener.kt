package com.ourcraft.zmusicgui.listener

import com.ourcraft.zmusicgui.ZMusicGUI
import com.ourcraft.zmusicgui.manager.Messages
import com.ourcraft.zmusicgui.util.Debug
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.scheduler.BukkitRunnable
import java.util.concurrent.ConcurrentHashMap

object ChatListener : Listener {

    private data class Ctx(val source: String, val mode: String, val timeoutTask: BukkitRunnable)
    private val waiting = ConcurrentHashMap<Player, Ctx>()

    fun awaitInput(player: Player, source: String, mode: String, promptKey: String = "song-play-prompt") {
        // Cancel previous request if any
        waiting.remove(player)?.timeoutTask?.cancel()

        val timeoutTask = object : BukkitRunnable() {
            override fun run() {
                val removed = waiting.remove(player)
                if (removed != null) {
                    player.sendMessage("${Messages.prefix()} &c输入超时，已取消".color())
                    Debug.debug("输入超时: ${player.name}")
                }
            }
        }
        timeoutTask.runTaskLater(ZMusicGUI.plugin, 600L) // 30 seconds timeout

        waiting[player] = Ctx(source, mode, timeoutTask)
        val prompt = Messages.player(promptKey)
        player.sendMessage("${Messages.prefix()} ${Messages.player("song-prompt", "prompt" to prompt)}")
        Debug.debug("等待输入: ${player.name} mode=$mode source=$source")
    }

    fun cancel(player: Player) {
        waiting.remove(player)?.timeoutTask?.cancel()
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onChat(event: AsyncPlayerChatEvent) {
        val player = event.player
        val ctx = waiting.remove(player) ?: return  // Not waiting, let normal chat through

        // Cancel timeout
        ctx.timeoutTask.cancel()

        // Only cancel chat for the waiting player's message
        event.isCancelled = true

        if (event.message.equals("cancel", ignoreCase = true)) {
            player.sendMessage("${Messages.prefix()} &c${Messages.player("song-cancelled")}".color())
            Debug.debug("输入取消: ${player.name}")
            return
        }

        val input = event.message.trim()
        Debug.debug("输入: ${player.name} -> \"$input\" (mode=${ctx.mode})")

        val cmd = when (ctx.mode) {
            "play"    -> "zm play ${ctx.source} $input"
            "music"   -> "zm music ${ctx.source} $input"
            "playall" -> "zm playall ${ctx.source} $input"
            "import_personal" -> "zm playlist ${ctx.source} import $input"
            "import_global"   -> "zm playlist global ${ctx.source} import $input"
            else -> "zm play ${ctx.source} $input"
        }

        Debug.debug("执行: $cmd")
        Bukkit.getScheduler().runTask(ZMusicGUI.plugin, Runnable { player.performCommand(cmd) })
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        cancel(event.player)
    }

    private fun String.color() = replace('&', '§')
}
