package com.ourcraft.zmusicgui.gui

import com.ourcraft.zmusicgui.listener.ChatListener
import com.ourcraft.zmusicgui.manager.AccountManager
import com.ourcraft.zmusicgui.manager.Config
import com.ourcraft.zmusicgui.manager.Messages
import com.ourcraft.zmusicgui.manager.PlayerSettings
import com.ourcraft.zmusicgui.util.Items
import org.bukkit.Material
import org.bukkit.entity.Player
import java.text.SimpleDateFormat
import java.util.Date

/**
 * 账号管理 GUI v2.5.0 — 2 平台登录
 *
 * v2.5.0: 移除酷狗/酷我 (服务器 IP 无法获取 VIP URL), 仅保留 QQ + 网易云
 *
 * 支持平台 (slot 11/13):
 *  - QQ (slot 11): F12 复制 Cookie (uin|qqmusic_key) 或网页提交 Cookie 获取绑定码
 *  - 网易云 (slot 13): 网页扫码登录, 获取 6 位绑定码
 *
 * 绑定流程:
 *  1. 玩家点击平台图标 → 显示登录教程链接
 *  2. 玩家在浏览器打开链接 → 选择平台 → (QQ 粘贴 Cookie / 网易云 扫码)
 *  3. 网页显示 6 位绑定码 → 玩家回游戏输入绑定码
 *  4. ChatListener 捕获输入 → AccountManager.handleBindCode 验证并保存
 *
 * QQ 也支持直接在游戏聊天栏粘贴 Cookie (uin|qqmusic_key 格式)
 */
object AccountGui : ZGui {

    private const val SLOT_QQ = 11
    private const val SLOT_NETEASE = 13
    private const val SLOT_BACK = 22

    override fun open(player: Player) {
        val holder = GuiHolder(this)
        val inv = holder.create(27, Items.deserialize("&6&l🔗 账号管理"))

        // 边框
        for (i in 0..8) { inv.setItem(i, Items.border()); inv.setItem(i + 18, Items.border()) }
        inv.setItem(9, Items.border()); inv.setItem(17, Items.border())

        // 标题
        inv.setItem(4, Items.build(Material.COMPARATOR, "&6&l🔗 账号管理",
            "&7登录音乐平台账号以播放 VIP 歌曲",
            "",
            "&7QQ: &fF12 Cookie / 网页提交",
            "&7网易云: &f网页扫码登录"))

        // QQ 音乐
        val qqAcc = PlayerSettings.getAccount(player, "qq")
        val qqItem = if (qqAcc != null) {
            buildBoundItem(Material.MUSIC_DISC_13, "&d&lQQ音乐", qqAcc)
        } else {
            Items.build(Material.MUSIC_DISC_13, "&d&lQQ音乐",
                "&c✗ 未绑定",
                "&7绑定后可播放 QQ 音乐 VIP 歌曲",
                "",
                "&a▸ 点击查看绑定教程")
        }
        inv.setItem(SLOT_QQ, qqItem)

        // 网易云
        val neteaseAcc = PlayerSettings.getAccount(player, "netease")
        val neteaseItem = if (neteaseAcc != null) {
            buildBoundItem(Material.MUSIC_DISC_BLOCKS, "&c&l网易云", neteaseAcc)
        } else {
            Items.build(Material.MUSIC_DISC_BLOCKS, "&c&l网易云",
                "&c✗ 未绑定",
                "&7绑定后可播放网易云 VIP 歌曲",
                "&7未绑定也可播放免费歌曲",
                "",
                "&a▸ 点击查看扫码教程")
        }
        inv.setItem(SLOT_NETEASE, neteaseItem)

        // 提示: 酷狗/酷我 VIP 已下线
        inv.setItem(15, Items.build(Material.BARRIER, "&7&n酷狗/酷我 VIP 已下线",
            "&7服务器 IP 无法获取 VIP URL",
            "&7请使用网易云兜底播放 VIP 歌曲",
            "&7酷狗/酷我免费歌曲仍可搜索播放"))

        // 返回按钮
        inv.setItem(SLOT_BACK, Items.back())

        player.openInventory(inv)
    }

    /** 构建已绑定平台的图标 */
    private fun buildBoundItem(material: Material, displayName: String, acc: PlayerSettings.AccountInfo): org.bukkit.inventory.ItemStack {
        val expireText = if (acc.expireAt > 0) {
            val df = SimpleDateFormat("yyyy-MM-dd HH:mm")
            val days = ((acc.expireAt - System.currentTimeMillis()) / (24 * 60 * 60 * 1000)).coerceAtLeast(0)
            "${df.format(Date(acc.expireAt))} (剩余 ${days} 天)"
        } else "永久"
        return Items.buildGlowing(material, displayName,
            "&a✓ 已绑定",
            "&7用户: &f${acc.nickname}",
            "&7过期: &f$expireText",
            "",
            "&c▸ 点击解绑")
    }

    override fun handleClick(player: Player, slot: Int) {
        when (slot) {
            SLOT_QQ -> handlePlatformClick(player, "qq")
            SLOT_NETEASE -> handlePlatformClick(player, "netease")
            SLOT_BACK -> SettingsGui.open(player)
        }
    }

    /** 平台点击处理: 已绑定则解绑, 未绑定则显示登录教程 */
    private fun handlePlatformClick(player: Player, platformId: String) {
        val acc = PlayerSettings.getAccount(player, platformId)
        if (acc != null) {
            AccountManager.unbind(player, platformId)
            return
        }
        showBindTutorial(player, platformId)
    }

    /** 显示登录教程链接 */
    private fun showBindTutorial(player: Player, platform: String) {
        val meta = AccountManager.getPlatform(platform) ?: return

        player.closeInventory()
        val p = Messages.prefix()
        val bindUrl = "${Config.ourcraftApi().trimEnd('/')}/bind"

        player.sendMessage(Items.color("$p &6━━━ ${meta.display} &6账号绑定 ━━━"))
        player.sendMessage(Items.color("$p &7第一步: 点击下方链接打开绑定页面"))
        // 可点击的链接 — 点击后在浏览器打开
        try {
            val comp = net.kyori.adventure.text.Component.text("§a§n$bindUrl")
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.openUrl(bindUrl))
            player.sendMessage(comp)
        } catch (_: Throwable) {
            player.sendMessage(Items.color("$p &f$bindUrl"))
        }

        if (platform == "qq") {
            player.sendMessage(Items.color("$p &7第二步: 选择 &fQQ音乐 &7→ 按 F12 复制 Cookie → 粘贴到网页"))
            player.sendMessage(Items.color("$p &7第三步: 网页显示 &a6 位绑定码 &7→ 回游戏输入绑定码 → 回车"))
            player.sendMessage(Items.color("$p &e提示: 也可直接在聊天栏粘贴 &fuin|qqmusic_key &e格式 Cookie"))
        } else {
            player.sendMessage(Items.color("$p &7第二步: 选择 &f网易云 &7→ 点击生成二维码"))
            player.sendMessage(Items.color("$p &7第三步: 用 &f网易云 APP &7扫码 → 手机确认登录"))
            player.sendMessage(Items.color("$p &7第四步: 网页显示 &a6 位绑定码 &7→ 回游戏输入绑定码 → 回车"))
        }

        // 监听玩家下一条聊天输入作为绑定码或 Cookie
        ChatListener.awaitInputWithPlatform(player, platform, "account_bind", "account-token-prompt", platform)
    }
}
