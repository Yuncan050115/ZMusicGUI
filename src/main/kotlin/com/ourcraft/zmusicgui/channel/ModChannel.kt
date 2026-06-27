package com.ourcraft.zmusicgui.channel

import com.ourcraft.zmusicgui.ZMusicGUI
import com.ourcraft.zmusicgui.util.Debug
import org.bukkit.entity.Player
import java.nio.charset.StandardCharsets

/**
 * Mod 通信 — ZMusic Mod 通信协议
 *
 * 注册两个 outgoing channel:
 *  - allmusic:channel (兼容旧 Mod)
 *  - zmusic:channel (新 Mod)
 *
 * 消息格式: 1 字节魔数 0x9A + UTF-8 文本负载
 * 指令: [Play]url / [Stop] / [Lyric]text / [Info]text
 */
object ModChannel {
    private const val ALLMUSIC = "allmusic:channel"
    private const val ZMUSIC = "zmusic:channel"
    private val MAGIC: Byte = 0x9A.toByte() // 154, ZMusic Mod 协议魔数

    fun register() {
        val plugin = ZMusicGUI.plugin
        plugin.server.messenger.apply {
            registerOutgoingPluginChannel(plugin, ALLMUSIC)
            registerOutgoingPluginChannel(plugin, ZMUSIC)
        }
        Debug.info("Mod 通信频道已注册 (allmusic + zmusic)")
    }

    fun unregister() {
        val plugin = ZMusicGUI.plugin
        plugin.server.messenger.apply {
            unregisterOutgoingPluginChannel(plugin, ALLMUSIC)
            unregisterOutgoingPluginChannel(plugin, ZMUSIC)
        }
    }

    /** 发送原始消息到客户端 Mod (带魔数前缀) */
    private fun send(player: Player, message: String) {
        try {
            val textBytes = message.toByteArray(StandardCharsets.UTF_8)
            val payload = ByteArray(textBytes.size + 1)
            payload[0] = MAGIC
            System.arraycopy(textBytes, 0, payload, 1, textBytes.size)
            // 同时发送到两个 channel, 确保新旧 Mod 都能收到
            player.sendPluginMessage(ZMusicGUI.plugin, ALLMUSIC, payload)
            player.sendPluginMessage(ZMusicGUI.plugin, ZMUSIC, payload)
        } catch (e: Throwable) {
            Debug.debug("Mod 通信发送失败: ${e.message}")
        }
    }

    /** 播放音乐 */
    fun play(player: Player, url: String) = send(player, "[Play]$url")

    /** 停止播放 */
    fun stop(player: Player) = send(player, "[Stop]")

    /** 发送歌词 */
    fun sendLyric(player: Player, lyric: String) = send(player, "[Lyric]$lyric")

    /** 发送信息 HUD */
    fun sendInfo(player: Player, info: String) = send(player, "[Info]$info")

    /** 清空歌词 */
    fun clearLyric(player: Player) = send(player, "[Lyric]")

    /** 清空信息 */
    fun clearInfo(player: Player) = send(player, "[Info]")
}
