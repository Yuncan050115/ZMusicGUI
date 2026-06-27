package com.ourcraft.zmusicgui.manager

import com.ourcraft.zmusicgui.util.Debug
import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.plugin.RegisteredServiceProvider

/**
 * Vault 经济系统钩子
 *
 * 用于范围点歌付费:
 *  - 扣费点歌者
 *  - 支付给领地/地皮主人
 *
 * 如果 Vault 未安装, 所有方法返回 false (不阻塞功能, 但不扣费)
 */
object EconomyManager {

    private var econ: Economy? = null

    val isAvailable: Boolean get() = econ != null

    fun setup(): Boolean {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            Debug.info(">> Vault 未安装, 范围点歌不扣费")
            return false
        }
        return try {
            val rsp: RegisteredServiceProvider<Economy>? = Bukkit.getServicesManager().getRegistration(Economy::class.java)
            if (rsp?.provider != null) {
                econ = rsp.provider
                Debug.info(">> Vault 经济系统已挂载: ${econ?.name}")
                true
            } else {
                Debug.warn(">> Vault 已安装但未找到经济服务")
                false
            }
        } catch (e: Throwable) {
            Debug.warn(">> Vault 经济系统挂载失败: ${e.message}")
            false
        }
    }

    /** 检查玩家余额是否足够 */
    fun has(player: OfflinePlayer, amount: Double): Boolean {
        if (amount <= 0) return true
        val e = econ ?: return true
        return try { e.has(player, amount) } catch (_: Throwable) { false }
    }

    /** 扣费 */
    fun withdraw(player: OfflinePlayer, amount: Double): Boolean {
        if (amount <= 0) return true
        val e = econ ?: return true
        return try {
            val result = e.withdrawPlayer(player, amount)
            result.transactionSuccess()
        } catch (_: Throwable) { false }
    }

    /** 支付给收款人 */
    fun deposit(player: OfflinePlayer, amount: Double): Boolean {
        if (amount <= 0) return true
        val e = econ ?: return true
        return try {
            val result = e.depositPlayer(player, amount)
            result.transactionSuccess()
        } catch (_: Throwable) { false }
    }

    /** 格式化金额 */
    fun format(amount: Double): String {
        val e = econ
        return if (e != null) try { e.format(amount) } catch (_: Throwable) { "$amount" } else "$amount"
    }
}
