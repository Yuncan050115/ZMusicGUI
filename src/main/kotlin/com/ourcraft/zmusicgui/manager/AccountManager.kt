package com.ourcraft.zmusicgui.manager

import com.ourcraft.zmusicgui.ZMusicGUI
import com.ourcraft.zmusicgui.gui.AccountGui
import com.ourcraft.zmusicgui.music.OurMusicApi
import com.ourcraft.zmusicgui.util.Debug
import com.ourcraft.zmusicgui.util.Items
import com.ourcraft.zmusicgui.util.SchedulerUtil
import org.bukkit.entity.Player
import java.text.SimpleDateFormat
import java.util.Date

/**
 * 多平台账号管理器 v2.5.0
 *
 * v2.5.0: 移除酷狗/酷我登录 (服务器 IP 无法获取 VIP URL, 改用网易云兜底)
 *
 * 支持的平台:
 *  - qq      QQ音乐   (F12 复制 document.cookie, 内含 uin 和 qqmusic_key, 30 天有效)
 *  - netease 网易云   (网页扫码登录, 服务端返回登录态, VIP 歌曲可兜底播放)
 *
 * 绑定流程:
 *  - QQ音乐: 浏览器登录 y.qq.com → F12 控制台执行 copy(document.cookie) → 粘贴到绑定页 →
 *            网页显示 6 位绑定码 → 回游戏输入绑定码
 *            (或直接在游戏聊天栏粘贴 uin|qqmusic_key 格式的 Cookie)
 *  - 网易云:  打开绑定页 → 点击生成二维码 → 网易云 APP 扫码 → 网页显示 6 位绑定码 →
 *            回游戏输入绑定码
 *
 * 注: 酷狗/酷我的 VIP 歌曲播放已下线 (服务器 IP 无法获取 VIP URL, token 绑浏览器 IP)。
 *     酷狗/酷我仍可搜索和播放免费歌曲, VIP 歌曲请使用网易云兜底。
 */
object AccountManager {

    /** 平台元数据 */
    data class PlatformMeta(
        val id: String,
        val display: String,
        val icon: String,
        val tokenFormat: String,
        val tokenExample: String,
        val expireDays: Int,
        val loginMethod: String,
        val siteUrl: String,
        val jsCode: String
    )

    val PLATFORMS = listOf(
        PlatformMeta(
            id = "qq",
            display = "&dQQ音乐",
            icon = "MUSIC_DISC_13",
            tokenFormat = "uin|qqmusic_key (用 | 分隔)",
            tokenExample = "123456789|Q_H_L_XXXX",
            expireDays = 30,
            loginMethod = "浏览器F12控制台一键复制",
            siteUrl = "https://y.qq.com/",
            // v2.4.1: 只提取 uin 和 qqmusic_key, 生成短字符串 (避免 Minecraft 256 字符限制)
            jsCode = "javascript:(function(){var c=document.cookie;var u=(c.match(/\\buin=o?(\\d+)/)||[])[1]||'';var k=(c.match(/qqmusic_key=([^;\\s]+)/)||[])[1]||'';if(u&&k){copy(u+'|'+k);console.log('已复制 QQ 登录信息:\\nuin='+u+'\\nqqmusic_key='+k.slice(0,20)+'...')}else{console.log('未找到 uin 或 qqmusic_key, 请确认已在 y.qq.com 登录')}})()"
        ),
        PlatformMeta(
            id = "netease",
            display = "&c网易云",
            icon = "MUSIC_DISC_BLOCKS",
            tokenFormat = "网页扫码登录 (无需 F12)",
            tokenExample = "6位绑定码",
            expireDays = 30,
            loginMethod = "网页扫码登录",
            siteUrl = "https://music.163.com/",
            // 网易云走网页扫码, 不需要 F12 控制台脚本
            jsCode = ""
        )
    )

    fun getPlatform(id: String): PlatformMeta? = PLATFORMS.firstOrNull { it.id == id }

    // ==================== 扫码登录绑定码验证 ====================

    /**
     * 处理玩家输入的 6 位绑定码 (扫码登录 / 网页 Cookie 提交)
     *
     * 玩家在网页扫码成功或提交 Cookie 后, 网页显示 6 位绑定码
     * 玩家回游戏输入绑定码, 客户端调用服务端验证并获取账号信息
     */
    fun handleBindCode(player: Player, code: String) {
        val trimmed = code.trim()

        // 验证格式: 6 位数字
        if (!trimmed.matches(Regex("\\d{6}"))) {
            player.sendMessage(Items.color("${Messages.prefix()} &c绑定码格式错误, 应为 6 位数字"))
            player.sendMessage(Items.color("${Messages.prefix()} &7请在网页扫码成功后, 输入显示的 6 位绑定码"))
            AccountGui.open(player)
            return
        }

        player.sendMessage(Items.color("${Messages.prefix()} &7正在验证绑定码..."))

        SchedulerUtil.runAsync(ZMusicGUI.plugin, Runnable {
            val result = OurMusicApi.verifyBindCode(trimmed)

            SchedulerUtil.runSync(ZMusicGUI.plugin, Runnable {
                if (!result.ok) {
                    player.sendMessage(Items.color("${Messages.prefix()} &c绑定失败: ${result.message}"))
                    player.sendMessage(Items.color("${Messages.prefix()} &7请重新扫码获取新的绑定码"))
                    AccountGui.open(player)
                    return@Runnable
                }

                // 验证成功, 保存账号信息
                val platform = getPlatform(result.platform)
                val expireDays = platform?.expireDays ?: 30
                val accountInfo = PlayerSettings.AccountInfo(
                    platform = result.platform,
                    userId = result.userId,
                    token = result.token,
                    cookie = result.cookie,
                    nickname = result.nickname,
                    expireAt = System.currentTimeMillis() + expireDays.toLong() * 24 * 60 * 60 * 1000
                )
                PlayerSettings.setAccount(player, accountInfo)

                val display = platform?.display ?: result.platform
                player.sendMessage(Items.color("${Messages.prefix()} &a${display} &a账号绑定成功!"))
                player.sendMessage(Items.color("${Messages.prefix()} &7昵称: &f${result.nickname}"))
                val df = SimpleDateFormat("yyyy-MM-dd HH:mm")
                val days = expireDays.toLong()
                player.sendMessage(Items.color("${Messages.prefix()} &7过期: &f${df.format(Date(accountInfo.expireAt))} (剩余 ${days} 天)"))
                Debug.info("扫码绑定成功: ${player.name} - ${result.platform} (${result.nickname})")
                AccountGui.open(player)
            })
        })
    }

    // ==================== F12 Cookie 输入 (仅 QQ) ====================

    /** 处理玩家输入的 Token / Cookie 字符串 (仅 QQ 支持 F12 Cookie 直接输入) */
    fun handleTokenInput(player: Player, platformId: String, tokenInput: String) {
        val platform = getPlatform(platformId)
        if (platform == null) {
            player.sendMessage(Items.color("${Messages.prefix()} &c未知平台: $platformId"))
            return
        }

        // v2.5.0: 仅 QQ 支持 F12 Cookie 直接输入, 网易云必须走网页扫码
        if (platformId != "qq") {
            player.sendMessage(Items.color("${Messages.prefix()} &c${platform.display} &c仅支持网页扫码登录"))
            player.sendMessage(Items.color("${Messages.prefix()} &7请前往绑定页面扫码获取绑定码"))
            AccountGui.open(player)
            return
        }

        player.sendMessage(Items.color("${Messages.prefix()} &7正在验证 ${platform.display} &7Cookie..."))

        SchedulerUtil.runAsync(ZMusicGUI.plugin, Runnable {
            val result = parseQqCookie(tokenInput)

            SchedulerUtil.runSync(ZMusicGUI.plugin, Runnable {
                if (result == null) {
                    player.sendMessage(Items.color("${Messages.prefix()} &cCookie 为空或格式错误"))
                    player.sendMessage(Items.color("${Messages.prefix()} &7请按教程在 F12 控制台执行代码后复制"))
                    AccountGui.open(player)
                    return@Runnable
                }

                PlayerSettings.setAccount(player, result)
                player.sendMessage(Items.color("${Messages.prefix()} &a${platform.display} &a账号绑定成功!"))
                player.sendMessage(Items.color("${Messages.prefix()} &7昵称: &f${result.nickname}"))
                val df = SimpleDateFormat("yyyy-MM-dd HH:mm")
                val expireText = if (result.expireAt > 0) {
                    val days = ((result.expireAt - System.currentTimeMillis()) / (24 * 60 * 60 * 1000)).coerceAtLeast(0)
                    "${df.format(Date(result.expireAt))} (剩余 ${days} 天)"
                } else "永久"
                player.sendMessage(Items.color("${Messages.prefix()} &7过期: &f$expireText"))
                Debug.info("Cookie 绑定成功: ${player.name} - $platformId (${result.nickname}) cookie长度=${result.cookie.length}")
                AccountGui.open(player)
            })
        })
    }

    /**
     * 解析 QQ cookie 输入
     *
     * 兼容两种输入:
     *  1. 旧格式 "123456789|XXXXXX" (uin|qqmusic_key, 用 | 分隔) — 旧版本用户
     *  2. 完整 cookie 字符串 "uin=o012345678; qqmusic_key=XXXX; ..." — v2.4.0 新格式
     *
     * 服务端 qq.js 期待: userId=uin (QQ号), token=qqmusic_key
     * 为兼容服务端, 我们解析出 uin 和 qqmusic_key, 但同时把完整 cookie 也保存
     */
    private fun parseQqCookie(input: String): PlayerSettings.AccountInfo? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        // 情况1: 旧格式 "uin|qqmusic_key" (用 | 分隔, 不含 = 号)
        if (!trimmed.contains('=') && trimmed.contains('|')) {
            val idx = trimmed.indexOf('|')
            val uin = trimmed.substring(0, idx).trim()
            val qqmusicKey = trimmed.substring(idx + 1).trim()
            if (uin.isEmpty() || qqmusicKey.isEmpty()) return null
            return PlayerSettings.AccountInfo(
                platform = "qq",
                userId = uin,
                token = qqmusicKey,
                cookie = "uin=o0${uin}; qqmusic_key=$qqmusicKey",
                nickname = "QQ用户$uin",
                expireAt = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000
            )
        }

        // 情况2: 完整 cookie 字符串 — 从中提取 uin 和 qqmusic_key
        val uinMatch = Regex("uin=o?(\\d+)").find(trimmed)
        val keyMatch = Regex("qqmusic_key=([^;\\s]+)").find(trimmed)
        val uin = uinMatch?.groupValues?.get(1) ?: return null
        val qqmusicKey = keyMatch?.groupValues?.get(1) ?: return null

        return PlayerSettings.AccountInfo(
            platform = "qq",
            userId = uin,
            token = qqmusicKey,
            cookie = trimmed,  // 保存完整 cookie, 服务端可直接用作 Cookie 头
            nickname = "QQ用户$uin",
            expireAt = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000
        )
    }

    // ==================== 解绑 ====================

    /** 解绑玩家在指定平台的账号 */
    fun unbind(player: Player, platformId: String) {
        val platform = getPlatform(platformId) ?: return
        val removed = PlayerSettings.removeAccount(player, platformId)
        if (removed) {
            player.sendMessage(Items.color("${Messages.prefix()} &a已解绑 ${platform.display} &a账号"))
            Debug.info("账号解绑: ${player.name} - $platformId")
        } else {
            player.sendMessage(Items.color("${Messages.prefix()} &7该平台未绑定"))
        }
        AccountGui.open(player)
    }

    /** 获取玩家在各平台的绑定状态 */
    fun getBindStatus(player: Player): Map<String, PlayerSettings.AccountInfo?> {
        return PLATFORMS.associate { it.id to PlayerSettings.getAccount(player, it.id) }
    }
}
