package com.ourcraft.zmusicgui.util

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.plugin.Plugin
import java.util.concurrent.TimeUnit

/**
 * Folia/Paper 统一调度器抽象层
 *
 * 自动检测服务器是否为 Folia，并提供统一的调度接口。
 * - Folia: 使用 RegionScheduler / GlobalRegionScheduler / AsyncScheduler
 * - Paper/Bukkit: 使用 BukkitScheduler
 *
 * 一套代码兼容两种平台。
 */
object SchedulerUtil {

    private val foliaSupported: Boolean by lazy {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    /** 检测当前服务器是否支持 Folia 调度 API */
    fun isFolia(): Boolean = foliaSupported

    // ==================== 同步任务（主线程 / 区域线程） ====================

    /**
     * 在主线程（Paper）或全局区域线程（Folia）执行任务。
     * 用于需要安全访问 Bukkit API 的操作。
     */
    fun runSync(plugin: Plugin, runnable: Runnable) {
        if (foliaSupported) {
            Bukkit.getGlobalRegionScheduler().run(plugin) { runnable.run() }
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable)
        }
    }

    /**
     * 延迟执行同步任务。
     *
     * @param delayTicks 延迟（tick）
     */
    fun runSyncLater(plugin: Plugin, runnable: Runnable, delayTicks: Long) {
        if (foliaSupported) {
            // Folia GlobalRegionScheduler 的 runDelayed 第二参数是 tick
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, { runnable.run() }, delayTicks)
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, runnable, delayTicks)
        }
    }

    /**
     * 在指定位置的区域线程执行任务（Folia 专用，Paper 回退到主线程）。
     */
    fun runAtLocation(plugin: Plugin, location: Location, runnable: Runnable) {
        if (foliaSupported) {
            Bukkit.getRegionScheduler().run(plugin, location) { runnable.run() }
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable)
        }
    }

    // ==================== 异步任务 ====================

    /**
     * 在异步线程执行任务。
     */
    fun runAsync(plugin: Plugin, runnable: Runnable) {
        if (foliaSupported) {
            Bukkit.getAsyncScheduler().runNow(plugin) { runnable.run() }
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable)
        }
    }

    /**
     * 定时重复执行同步任务。
     *
     * @param delayTicks  延迟（tick）
     * @param periodTicks 间隔（tick）
     * @return 任务对象，可用于取消
     */
    fun runSyncTimer(plugin: Plugin, runnable: Runnable, delayTicks: Long, periodTicks: Long): Any {
        return if (foliaSupported) {
            // Folia GlobalRegionScheduler.runAtFixedRate 以 tick 为单位
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, { runnable.run() }, delayTicks, periodTicks)
        } else {
            Bukkit.getScheduler().runTaskTimer(plugin, runnable, delayTicks, periodTicks)
        }
    }

    /**
     * 定时重复执行异步任务。
     */
    fun runAsyncTimer(plugin: Plugin, runnable: Runnable, delayTicks: Long, periodTicks: Long): Any {
        return if (foliaSupported) {
            val delayMs = delayTicks * 50L
            val periodMs = periodTicks * 50L
            Bukkit.getAsyncScheduler().runAtFixedRate(plugin, { runnable.run() }, delayMs, periodMs, TimeUnit.MILLISECONDS)
        } else {
            Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, runnable, delayTicks, periodTicks)
        }
    }

    /** 取消任务 */
    fun cancelTask(task: Any) {
        when (task) {
            is org.bukkit.scheduler.BukkitTask -> task.cancel()
            is io.papermc.paper.threadedregions.scheduler.ScheduledTask -> task.cancel()
        }
    }

    /** 取消所有由本插件注册的任务 */
    fun cancelAll(plugin: Plugin) {
        if (foliaSupported) {
            Bukkit.getAsyncScheduler().cancelTasks(plugin)
            Bukkit.getGlobalRegionScheduler().cancelTasks(plugin)
        } else {
            Bukkit.getScheduler().cancelTasks(plugin)
        }
    }
}
