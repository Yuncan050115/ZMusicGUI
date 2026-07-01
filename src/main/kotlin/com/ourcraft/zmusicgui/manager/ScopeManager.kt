package com.ourcraft.zmusicgui.manager

import com.ourcraft.zmusicgui.ZMusicGUI
import com.ourcraft.zmusicgui.music.OurMusicApi
import com.ourcraft.zmusicgui.music.MusicPlayer
import com.ourcraft.zmusicgui.util.Debug
import com.ourcraft.zmusicgui.util.Items
import com.ourcraft.zmusicgui.util.SchedulerUtil
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 播放范围管理器 v2.1.0
 *
 * 五种播放范围:
 *  - SELF     仅自己听到 (直接播放)
 *  - RESIDENCE 玩家脚下 Residence 领地内所有人
 *  - PLOT     玩家脚下 PlotSquared 地皮内所有人
 *  - WORLD    玩家当前世界内所有人
 *  - SERVER   全服玩家
 *
 * 同意机制 (v2.1.0 改版):
 *  - 个人范围 → 直接播放 (无其他人)
 *  - 其他范围 → 请求者立即播放, 范围内其他玩家收到可点击的同意/拒绝消息
 *    * 点击同意 → 给该玩家播放
 *    * 点击拒绝 → 不播放给该玩家
 *    * 超时不点击 → 不播放给该玩家
 *  绝不直接给其他人播放, 必须征得接收者同意!
 */
object ScopeManager {

    enum class Scope(val id: String, val display: String, val icon: String) {
        SELF("self", "个人", "&a"),
        RESIDENCE("residence", "领地", "&6"),
        PLOT("plot", "地皮", "&d"),
        WORLD("world", "世界", "&b"),
        SERVER("server", "全服", "&e");

        companion object {
            fun from(id: String): Scope = entries.firstOrNull { it.id == id } ?: SELF
        }
    }

    /** 同意请求: 每个接收者独立决定是否接收 */
    data class ConsentRequest(
        val id: UUID,
        val requester: Player,
        val scope: Scope,
        val song: OurMusicApi.Song,
        val source: String,
        val payee: OfflinePlayer?,
        val cost: Double,
        val expireAt: Long,
        val consented: MutableSet<UUID> = mutableSetOf(),
        val rejected: MutableSet<UUID> = mutableSetOf()
    )

    private val consents = ConcurrentHashMap<UUID, ConsentRequest>()
    private val consentByPlayer = ConcurrentHashMap<UUID, MutableList<UUID>>()

    private var residencePlugin: Plugin? = null
    private var plotSquaredPlugin: Plugin? = null

    /** 当前领地插件 provider (由 config.yml scope.region-plugin 决定) */
    private var regionProvider: RegionProvider = NoRegionProvider

    fun setup() {
        // 根据配置选择领地插件 provider
        val pluginType = Config.regionPlugin()
        regionProvider = when (pluginType) {
            "lands" -> LandsProvider()
            "dominion" -> DominionProvider()
            else -> ResidenceProvider(this)
        }
        Debug.info(">> 领地插件类型: ${regionProvider.typeId} (来自 config.yml scope.region-plugin)")

        // Residence 特殊: 需要先设置 residencePlugin 字段供文件读取/API 反射使用
        if (regionProvider is ResidenceProvider) {
            residencePlugin = Bukkit.getPluginManager().getPlugin("Residence")
            if (residencePlugin != null) {
                Debug.info(">> Residence 已检测到 (版本: ${residencePlugin!!.description.version}, 类: ${residencePlugin!!.javaClass.name})，支持领地范围播放")
                testResidenceApi()
            }
        } else {
            // Lands / Dominion 由 provider 自行初始化
            regionProvider.setup()
        }

        plotSquaredPlugin = Bukkit.getPluginManager().getPlugin("PlotSquared")
        if (plotSquaredPlugin != null) Debug.info(">> PlotSquared 已检测到，支持地皮范围播放")
    }

    /**
     * 启动时测试 Residence API (诊断用)
     * 输出所有可用的 Residence 检测方式到控制台
     */
    private fun testResidenceApi() {
        try {
            val residence = residencePlugin!!
            Debug.info(">> [Residence诊断] 开始测试 API...")

            // 方式0: ResidenceAPI 静态类
            try {
                val apiClass = Class.forName("com.bekvon.bukkit.residence.api.ResidenceAPI")
                val resManager = apiClass.getMethod("getResidenceManager").invoke(null)
                if (resManager != null) {
                    Debug.info(">> [Residence诊断] ResidenceAPI 静态类: 找到, 类=${resManager.javaClass.name}")
                    val methods = resManager.javaClass.methods
                        .filter { it.name.contains("getBy") || it.name.contains("Loc") || it.name.contains("get") }
                        .map { "${it.name}(${it.parameterTypes.map { p -> p.simpleName }.joinToString(",")})" }
                    Debug.info(">> [Residence诊断] ResidenceAPI 方法: ${methods.joinToString(" | ")}")
                } else {
                    Debug.info(">> [Residence诊断] ResidenceAPI.getResidenceManager() 返回 null")
                }
            } catch (e: ClassNotFoundException) {
                Debug.info(">> [Residence诊断] ResidenceAPI 静态类不存在 (旧版)")
            } catch (e: Throwable) {
                Debug.info(">> [Residence诊断] ResidenceAPI 测试异常: ${e.message}")
            }

            // 方式1: Residence 主类
            try {
                val resManager = residence.javaClass.getMethod("getResidenceManager").invoke(residence)
                if (resManager != null) {
                    Debug.info(">> [Residence诊断] Residence主类.getResidenceManager(): 找到, 类=${resManager.javaClass.name}")
                    val methods = resManager.javaClass.methods
                        .filter { it.name.contains("getBy") || it.name.contains("Loc") }
                        .map { "${it.name}(${it.parameterTypes.map { p -> p.simpleName }.joinToString(",")})" }
                    Debug.info(">> [Residence诊断] ResidenceManager 方法: ${methods.joinToString(" | ")}")
                } else {
                    Debug.info(">> [Residence诊断] Residence主类.getResidenceManager() 返回 null")
                }
            } catch (e: Throwable) {
                Debug.info(">> [Residence诊断] Residence主类测试异常: ${e.message}")
            }

            Debug.info(">> [Residence诊断] 测试完成")
        } catch (e: Throwable) {
            Debug.info(">> [Residence诊断] 整体异常: ${e.message}")
        }
    }

    fun hasResidence(): Boolean = regionProvider.isAvailable()
    fun hasPlotSquared(): Boolean = plotSquaredPlugin != null

    /** Residence provider 是否就绪 (供 ResidenceProvider.isAvailable 委托) */
    fun hasResidenceImpl(): Boolean = residencePlugin != null

    fun fromId(id: String): Scope = Scope.from(id)

    fun isAvailable(player: Player, scope: Scope): Boolean = when (scope) {
        Scope.SELF -> true
        Scope.RESIDENCE -> {
            if (!regionProvider.isAvailable()) {
                player.sendMessage(Items.color("${Messages.prefix()} &c领地插件未加载 (${regionProvider.typeId})"))
                false
            } else {
                val name = regionProvider.getRegionName(player)
                if (name == null) {
                    // 领地检测失败, 发送诊断信息给玩家 (仅 Residence 有详细诊断, Lands/Dominion 简单提示)
                    if (regionProvider is ResidenceProvider) {
                        val diag = diagnoseResidence(player)
                        for (line in diag.split("\n")) {
                            if (line.isNotBlank()) player.sendMessage(Items.color(line))
                        }
                    } else {
                        player.sendMessage(Items.color("${Messages.prefix()} &7你不在任何领地内 (${regionProvider.typeId})"))
                    }
                    false
                } else {
                    true
                }
            }
        }
        Scope.PLOT -> plotSquaredPlugin != null && getCurrentPlot(player) != null
        Scope.WORLD -> true
        Scope.SERVER -> Config.publicRequestEnabled()
    }

    /**
     * 获取范围内所有玩家 (含请求者自身)
     */
    fun getTargets(player: Player, scope: Scope): List<Player> = when (scope) {
        Scope.SELF -> listOf(player)
        Scope.RESIDENCE -> regionProvider.getPlayersInRegion(player)
        Scope.PLOT -> getPlayersInPlot(player)
        Scope.WORLD -> player.world.players.toList()
        Scope.SERVER -> Bukkit.getOnlinePlayers().toList()
    }

    fun describeScope(player: Player, scope: Scope): String? = when (scope) {
        Scope.RESIDENCE -> regionProvider.getRegionName(player)?.let { "领地: $it" }
        Scope.PLOT -> getCurrentPlotId(player)?.let { "地皮: $it" }
        Scope.WORLD -> player.world.name
        Scope.SERVER -> "全服"
        else -> null
    }

    /**
     * 请求范围播放
     *
     * 流程:
     *  1. 检查范围可用性
     *  2. 计算费用和收款人
     *  3. 扣费
     *  4. 请求者立即播放
     *  5. 给范围内其他玩家发送可点击的同意/拒绝消息
     *  6. 其他玩家点击同意 → 给该玩家播放
     *  7. 超时不点击 → 不播放给该玩家
     */
    fun requestPlay(requester: Player, scope: Scope, source: String, song: OurMusicApi.Song) {
        // 二次校验
        if (!isAvailable(requester, scope)) {
            requester.sendMessage(Items.color("${Messages.prefix()} &c当前范围不可用"))
            return
        }

        val targets = getTargets(requester, scope)
        if (targets.isEmpty()) {
            requester.sendMessage(Items.color("${Messages.prefix()} &c范围内没有玩家"))
            return
        }

        // 范围内其他玩家 (排除请求者)
        val others = targets.filter { it.uniqueId != requester.uniqueId && it.isOnline }

        // 费用
        val cost = Config.scopeCost(scope.id)
        val payee = getPayee(requester, scope)

        // 检查余额
        if (cost > 0 && EconomyManager.isAvailable) {
            if (!EconomyManager.has(requester, cost)) {
                requester.sendMessage(Items.color("${Messages.prefix()} &c余额不足, 需要 &f${EconomyManager.format(cost)}"))
                return
            }
        }

        // 扣费
        if (cost > 0 && EconomyManager.isAvailable) {
            val ok = EconomyManager.withdraw(requester, cost)
            if (!ok) {
                requester.sendMessage(Items.color("${Messages.prefix()} &c扣费失败, 播放取消"))
                return
            }
            requester.sendMessage(Items.color("${Messages.prefix()} &7已扣费: &f${EconomyManager.format(cost)}"))
            if (payee != null) EconomyManager.deposit(payee, cost)
        }

        // 异步获取歌曲详情
        SchedulerUtil.runAsync(ZMusicGUI.plugin, Runnable {
            val detail = SearchService.getSongDetail(song, requester) ?: run {
                SchedulerUtil.runSync(ZMusicGUI.plugin, Runnable {
                    requester.sendMessage(Items.color("${Messages.prefix()} &c获取歌曲详情失败"))
                    // 退还费用
                    if (cost > 0 && EconomyManager.isAvailable && payee == null) {
                        EconomyManager.deposit(requester, cost)
                        requester.sendMessage(Items.color("${Messages.prefix()} &7费用已退还"))
                    }
                })
                return@Runnable
            }

            SchedulerUtil.runSync(ZMusicGUI.plugin, Runnable {
                // 1. 请求者立即播放
                MusicPlayer.play(requester, detail)
                val desc = describeScope(requester, scope) ?: scope.display
                requester.sendMessage(Items.color("${Messages.prefix()} &a正在为你播放: &f${detail.name} &7- &f${detail.singer}"))
                Debug.debug("范围播放(请求者): ${requester.name} scope=${scope.id} song=${detail.name}")

                // 2. 个人范围 → 无需他人同意, 结束
                if (others.isEmpty()) {
                    requester.sendMessage(Items.color("${Messages.prefix()} &7范围内无其他玩家"))
                    return@Runnable
                }

                // 3. 创建同意请求
                val reqId = UUID.randomUUID()
                val req = ConsentRequest(
                    id = reqId,
                    requester = requester,
                    scope = scope,
                    song = song,
                    source = source,
                    payee = payee,
                    cost = cost,
                    expireAt = System.currentTimeMillis() + Config.scopeApprovalTimeout() * 1000
                )
                consents[reqId] = req

                // 4. 给范围内其他玩家发送可点击消息
                for (target in others) {
                    if (!target.isOnline) continue
                    consentByPlayer.getOrPut(target.uniqueId) { mutableListOf() }.add(reqId)
                    sendConsentMessage(target, requester, detail.name, detail.singer, scope, reqId)
                }

                requester.sendMessage(Items.color("${Messages.prefix()} &7已通知 &f${others.size} &7名范围内玩家, 等待同意..."))
                requester.sendMessage(Items.color("${Messages.prefix()} &7超时 &f${Config.scopeApprovalTimeout()} &7秒未同意的玩家不会收到播放"))

                // 5. 超时处理
                val timeoutTicks = Config.scopeApprovalTimeout() * 20L
                SchedulerUtil.runSyncLater(ZMusicGUI.plugin, Runnable {
                    val req = consents.remove(reqId) ?: return@Runnable
                    for (list in consentByPlayer.values) list.removeIf { it == reqId }
                    val notResponded = others.size - req.consented.size - req.rejected.size
                    if (notResponded > 0) {
                        requester.sendMessage(Items.color("${Messages.prefix()} &7${notResponded} 名玩家未在超时前选择, 未为其播放"))
                    }
                    if (req.consented.isNotEmpty()) {
                        requester.sendMessage(Items.color("${Messages.prefix()} &a${req.consented.size} 名玩家已同意接收"))
                    }
                    Debug.debug("同意请求超时: ${requester.name} 同意=${req.consented.size} 拒绝=${req.rejected.size} 未响应=$notResponded")
                }, timeoutTicks)
            })
        })
    }

    /** 接收者同意接收 */
    fun consentReceive(reqId: UUID, player: Player): Boolean {
        val req = consents[reqId] ?: return false
        if (player.uniqueId in req.consented) return false
        if (player.uniqueId in req.rejected) return false

        req.consented.add(player.uniqueId)
        player.sendMessage(Items.color("${Messages.prefix()} &a已同意接收 &f${req.requester.name} &a的音乐"))

        // 异步获取歌曲详情并播放
        SchedulerUtil.runAsync(ZMusicGUI.plugin, Runnable {
            val detail = SearchService.getSongDetail(req.song, player) ?: return@Runnable
            SchedulerUtil.runSync(ZMusicGUI.plugin, Runnable {
                if (player.isOnline) {
                    MusicPlayer.play(player, detail)
                    player.sendMessage(Items.color("${Messages.prefix()} &a正在播放: &f${detail.name} &7- &f${detail.singer}"))
                    Debug.debug("接收者同意: ${player.name} 接收 ${req.requester.name} 的音乐 ${detail.name}")
                }
            })
        })
        return true
    }

    /** 接收者拒绝接收 */
    fun rejectReceive(reqId: UUID, player: Player): Boolean {
        val req = consents[reqId] ?: return false
        if (player.uniqueId in req.rejected) return false
        if (player.uniqueId in req.consented) return false

        req.rejected.add(player.uniqueId)
        player.sendMessage(Items.color("${Messages.prefix()} &7已拒绝接收音乐"))
        Debug.debug("接收者拒绝: ${player.name} 拒绝 ${req.requester.name} 的音乐")
        return true
    }

    /** 获取玩家待响应的请求 */
    fun getPendingConsents(player: Player): List<ConsentRequest> {
        val ids = consentByPlayer[player.uniqueId] ?: return emptyList()
        return ids.mapNotNull { consents[it] }.filter { System.currentTimeMillis() < it.expireAt }
    }

    /** 获取收款人 */
    private fun getPayee(player: Player, scope: Scope): OfflinePlayer? = when (scope) {
        Scope.RESIDENCE -> regionProvider.getRegionOwner(player)
        Scope.PLOT -> getPlotOwner(player)
        Scope.WORLD -> {
            val account = Config.worldPayeeAccount()
            if (account.isNotEmpty()) Bukkit.getOfflinePlayer(account) else null
        }
        else -> null
    }

    /** 发送可点击的同意/拒绝消息 */
    private fun sendConsentMessage(target: Player, requester: Player, songName: String, singer: String, scope: Scope, reqId: UUID) {
        val idShort = reqId.toString().take(8)

        // 第一行: 请求信息
        target.sendMessage(Items.color("${Messages.prefix()} &6${requester.name} &7想在 ${scope.icon}${scope.display} &7内播放音乐:"))
        // 第二行: 歌曲信息
        target.sendMessage(Items.color("${Messages.prefix()} &f${songName} &7- &f${singer}"))

        // 第三行: 可点击的同意/拒绝按钮 (使用 Adventure API)
        val prefix = Items.color(Messages.prefix())
        val acceptBtn = Component.text("[✔ 同意接收]")
            .color(NamedTextColor.GREEN)
            .decorate(TextDecoration.BOLD)
            .clickEvent(ClickEvent.runCommand("/zmg consent $idShort"))
            .hoverEvent(HoverEvent.showText(Component.text("§a点击同意接收这首音乐")))
        val rejectBtn = Component.text("[✘ 拒绝接收]")
            .color(NamedTextColor.RED)
            .decorate(TextDecoration.BOLD)
            .clickEvent(ClickEvent.runCommand("/zmg reject $idShort"))
            .hoverEvent(HoverEvent.showText(Component.text("§c点击拒绝接收这首音乐")))

        target.sendMessage(Component.text(prefix + " ")
            .append(acceptBtn)
            .append(Component.text("  "))
            .append(rejectBtn))

        Debug.debug("发送同意请求: target=${target.name} requester=${requester.name} song=$songName reqId=$idShort")
    }

    // ==================== Residence (反射) — 原实现, 供 ResidenceProvider 委托 ====================

    /** 公开 API: 获取玩家当前领地名 (委托给 regionProvider) */
    fun getCurrentResidenceName(player: Player): String? = regionProvider.getRegionName(player)

    /** Residence 原实现 (文件读取 + API 反射), 供 ResidenceProvider 委托调用 */
    fun getResidenceNameImpl(player: Player): String? {
        if (residencePlugin == null) return null

        // 方式A (主): 直接读取 Residence save 文件 (不依赖 WorldGuard, 不依赖 API 反射)
        val nameFromFile = getResidenceNameFromFile(player)
        if (nameFromFile != null) return nameFromFile

        // 方式B (备): 通过 Residence API 反射 (可能因 WorldGuard 缺失而失败)
        return getResidenceNameByApi(player)
    }

    /**
     * 方式A: 直接读取 plugins/Residence/Save/Worlds/res_<world>.yml
     * 领地数据格式: Residences.<name>.Areas.<area>: x1:y1:z1:x2:y2:z2
     * 完全不依赖 WorldGuard 或 Residence API
     */
    private fun getResidenceNameFromFile(player: Player): String? {
        return try {
            val loc = player.location
            val worldName = loc.world.name
            val resFile = java.io.File(
                ZMusicGUI.plugin.dataFolder.parentFile,  // plugins/
                "Residence/Save/Worlds/res_${worldName}.yml"
            )
            if (!resFile.exists()) {
                Debug.debug("Residence save 文件不存在: ${resFile.absolutePath}")
                return null
            }

            // 缓存: 文件最后修改时间 + 解析结果 (避免每次都解析 YAML)
            val lastModified = resFile.lastModified()
            val cacheKey = worldName
            val cached = residenceFileCache[cacheKey]
            val residences: List<Triple<String, Int, IntArray>>  // (name, areaCount, [x1,y1,z1,x2,y2,z2] x N)
            if (cached != null && cached.first == lastModified) {
                residences = cached.second
            } else {
                // 解析 YAML
                val yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(resFile)
                val resSection = yaml.getConfigurationSection("Residences") ?: return null
                val list = mutableListOf<Triple<String, Int, IntArray>>()
                for (name in resSection.getKeys(false)) {
                    val areas = resSection.getConfigurationSection("$name.Areas") ?: continue
                    for (areaName in areas.getKeys(false)) {
                        val coords = areas.getString(areaName) ?: continue
                        // 格式: x1:y1:z1:x2:y2:z2
                        val parts = coords.split(":")
                        if (parts.size != 6) continue
                        try {
                            val x1 = parts[0].toInt()
                            val y1 = parts[1].toInt()
                            val z1 = parts[2].toInt()
                            val x2 = parts[3].toInt()
                            val y2 = parts[4].toInt()
                            val z2 = parts[5].toInt()
                            // 合并为 min/max
                            val minX = minOf(x1, x2)
                            val maxX = maxOf(x1, x2)
                            val minY = minOf(y1, y2)
                            val maxY = maxOf(y1, y2)
                            val minZ = minOf(z1, z2)
                            val maxZ = maxOf(z1, z2)
                            list.add(Triple(name, 1, intArrayOf(minX, minY, minZ, maxX, maxY, maxZ)))
                        } catch (_: NumberFormatException) {
                            continue
                        }
                    }
                }
                residences = list
                residenceFileCache[cacheKey] = Pair(lastModified, list)
                Debug.info(">> [Residence] 已加载 ${list.size} 个领地区域 (世界: $worldName)")
            }

            // 检查玩家是否在某个领地内
            val px = loc.blockX
            val py = loc.blockY
            val pz = loc.blockZ
            for ((name, _, coords) in residences) {
                // coords: [minX, minY, minZ, maxX, maxY, maxZ]
                if (px >= coords[0] && px <= coords[3] &&
                    py >= coords[1] && py <= coords[4] &&
                    pz >= coords[2] && pz <= coords[5]) {
                    Debug.debug("Residence(文件): ${player.name} 在领地 $name 内")
                    return name
                }
            }
            Debug.debug("Residence(文件): ${player.name} 不在任何领地内 (位置: $px,$py,$pz)")
            null
        } catch (e: Throwable) {
            Debug.debug("Residence 文件读取失败: ${e.message}")
            null
        }
    }

    /** Residence save 文件缓存: worldName -> (lastModified, residences) */
    private val residenceFileCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<Triple<String, Int, IntArray>>>>()

    /**
     * 方式B: 通过 Residence API 反射获取领地名 (备用, 可能因 WorldGuard 缺失而失败)
     */
    private fun getResidenceNameByApi(player: Player): String? {
        return try {
            val residence = residencePlugin!!
            val loc = player.location

            // 方式0: 通过 ResidenceAPI 静态类 (4.x+ 支持)
            try {
                val apiClass = Class.forName("com.bekvon.bukkit.residence.api.ResidenceAPI")
                val resManager = apiClass.getMethod("getResidenceManager").invoke(null)
                if (resManager != null) {
                    val currentRes = safeInvoke(resManager, "getByLoc", loc)
                        ?: safeInvoke(resManager, "getByLocation", loc)
                    if (currentRes != null) {
                        val name = tryMethodChain(currentRes, listOf("getName", "getResidenceName"))
                        if (name is String && name.isNotEmpty()) return name
                    }
                }
            } catch (_: ClassNotFoundException) {
                // ResidenceAPI 静态类不存在 (旧版)
            }

            // 方式1: Residence 主类.getResidenceManager().getByLoc(Location)
            val resManager = residence.javaClass.getMethod("getResidenceManager").invoke(residence)
            if (resManager != null) {
                val currentRes = safeInvoke(resManager, "getByLoc", loc)
                    ?: safeInvoke(resManager, "getByLocation", loc)
                if (currentRes != null) {
                    val name = tryMethodChain(currentRes, listOf("getName", "getResidenceName"))
                    if (name is String && name.isNotEmpty()) return name
                }
            }

            // 方式2: PlayerManager.getResidencePlayer (备用)
            val playerManager = residence.javaClass.getMethod("getPlayerManager").invoke(residence)
                ?: return null
            val resPlayer = playerManager.javaClass
                .getMethod("getResidencePlayer", Player::class.java)
                .invoke(playerManager, player)
                ?: return null
            val currentArea = tryMethodChain(resPlayer, listOf("getCurrentResidence", "getCurrentLocation"))
                ?: return null
            val name = tryMethodChain(currentArea, listOf("getName", "getResidenceName"))
            if (name is String && name.isNotEmpty()) name else null
        } catch (e: Throwable) {
            Debug.debug("Residence API 调用失败: ${e.message}")
            null
        }
    }

    /**
     * 安全反射调用 (捕获 NoClassDefFoundError, 如 WorldGuard 缺失)
     */
    private fun safeInvoke(target: Any, methodName: String, vararg args: Any): Any? {
        return try {
            val argTypes = args.map { it::class.java }.toTypedArray()
            // 尝试精确匹配
            try {
                val method = target.javaClass.getMethod(methodName, *argTypes)
                method.invoke(target, *args)
            } catch (_: NoSuchMethodException) {
                // 尝试父类参数 (如 Location -> Location)
                val method = target.javaClass.methods.firstOrNull {
                    it.name == methodName && it.parameterTypes.size == args.size &&
                        args.indices.all { i -> it.parameterTypes[i].isAssignableFrom(argTypes[i]) }
                }
                method?.invoke(target, *args)
            }
        } catch (e: NoClassDefFoundError) {
            // 依赖缺失 (如 WorldGuard), 记录但不崩溃
            Debug.debug("Residence ${methodName} 调用失败 (依赖缺失): ${e.message}")
            null
        } catch (e: Throwable) {
            Debug.debug("Residence ${methodName} 调用异常: ${e.message}")
            null
        }
    }

    /**
     * 诊断 Residence 领地检测 (发送给玩家, 不需要 debug 模式)
     * 返回诊断信息字符串, 供玩家查看
     */
    fun diagnoseResidence(player: Player): String {
        if (residencePlugin == null) return "Residence 插件未加载"
        val sb = StringBuilder()
        val residence = residencePlugin!!
        val loc = player.location
        sb.append("§7位置: ${loc.world.name} ${loc.blockX},${loc.blockY},${loc.blockZ}\n")

        // 方式0: ResidenceAPI 静态类
        try {
            val apiClass = Class.forName("com.bekvon.bukkit.residence.api.ResidenceAPI")
            val resManager = apiClass.getMethod("getResidenceManager").invoke(null)
            if (resManager != null) {
                sb.append("§aResidenceAPI 静态类: 找到\n")
                val currentRes = try {
                    resManager.javaClass
                        .getMethod("getByLoc", org.bukkit.Location::class.java)
                        .invoke(resManager, loc)
                } catch (e: Throwable) {
                    sb.append("§cgetByLoc 异常: ${e.message}\n")
                    null
                }
                if (currentRes != null) {
                    val name = tryMethodChain(currentRes, listOf("getName", "getResidenceName"))
                    sb.append("§a方式0 成功: 领地=$name 类=${currentRes.javaClass.simpleName}\n")
                    return sb.toString()
                } else {
                    sb.append("§e方式0: getByLoc 返回 null (不在领地内)\n")
                }
            } else {
                sb.append("§eResidenceAPI.getResidenceManager() 返回 null\n")
            }
        } catch (_: ClassNotFoundException) {
            sb.append("§eResidenceAPI 静态类不存在 (旧版)\n")
        }

        // 方式1: Residence 主类
        try {
            val resManager = residence.javaClass.getMethod("getResidenceManager").invoke(residence)
            if (resManager != null) {
                sb.append("§aResidenceManager: 找到 类=${resManager.javaClass.name}\n")
                // 打印 getBy/Loc 相关方法
                val methods = resManager.javaClass.methods
                    .filter { it.name.contains("getBy") || it.name.contains("Loc") }
                    .map { "${it.name}(${it.parameterTypes.map { p -> p.simpleName }.joinToString(",")})" }
                sb.append("§7相关方法: ${methods.joinToString(" | ")}\n")

                val currentRes = try {
                    resManager.javaClass
                        .getMethod("getByLoc", org.bukkit.Location::class.java)
                        .invoke(resManager, loc)
                } catch (e: NoSuchMethodException) {
                    sb.append("§egetByLoc 方法不存在\n")
                    try {
                        resManager.javaClass
                            .getMethod("getByLocation", org.bukkit.Location::class.java)
                            .invoke(resManager, loc)
                    } catch (e2: Throwable) {
                        sb.append("§cgetByLocation 失败: ${e2.message}\n")
                        null
                    }
                } catch (e: Throwable) {
                    sb.append("§cgetByLoc 异常: ${e.message}\n")
                    null
                }
                if (currentRes != null) {
                    val name = tryMethodChain(currentRes, listOf("getName", "getResidenceName"))
                    sb.append("§a方式1 成功: 领地=$name\n")
                    return sb.toString()
                } else {
                    sb.append("§e方式1: getByLoc 返回 null (不在领地内)\n")
                }
            }
        } catch (e: Throwable) {
            sb.append("§c方式1 异常: ${e.message}\n")
        }

        return sb.toString()
    }

    /** 尝试多个方法名, 返回第一个非 null 结果 */
    private fun tryMethodChain(obj: Any, methodNames: List<String>): Any? {
        for (name in methodNames) {
            try {
                val method = obj.javaClass.getMethod(name)
                val result = method.invoke(obj)
                if (result != null) return result
            } catch (_: NoSuchMethodException) {
            } catch (_: Throwable) {
            }
        }
        return null
    }

    /** Residence 原实现: 获取领地拥有者 (供 ResidenceProvider 委托) */
    fun getResidenceOwnerImpl(player: Player): OfflinePlayer? {
        val resName = getCurrentResidenceName(player) ?: return null
        return try {
            val residence = residencePlugin!!
            val resManager = residence.javaClass.getMethod("getResidenceManager").invoke(residence)
            val resInfo = resManager?.javaClass?.getMethod("getByName", String::class.java)?.invoke(resManager, resName)
            if (resInfo != null) {
                val ownerName = resInfo.javaClass.getMethod("getOwner").invoke(resInfo) as String
                Bukkit.getOfflinePlayer(ownerName)
            } else null
        } catch (e: Throwable) {
            Debug.debug("Residence owner 获取失败: ${e.message}")
            null
        }
    }

    /** Residence 原实现: 获取同领地玩家 (供 ResidenceProvider 委托) */
    fun getPlayersInResidenceImpl(requester: Player): List<Player> {
        val resName = getCurrentResidenceName(requester) ?: return listOf(requester)
        val targets = mutableListOf<Player>()
        for (p in Bukkit.getOnlinePlayers()) {
            val pRes = getCurrentResidenceName(p)
            if (pRes != null && pRes.equals(resName, ignoreCase = true)) {
                targets.add(p)
            }
        }
        return targets
    }

    // ==================== PlotSquared (反射) ====================

    fun getCurrentPlot(player: Player): Any? {
        if (plotSquaredPlugin == null) return null
        return try {
            val bukkitUtilClass = Class.forName("com.plotsquared.bukkit.util.BukkitUtil")
            val adaptMethod = bukkitUtilClass.getMethod("adapt", Player::class.java)
            val plotPlayer = adaptMethod.invoke(null, player) ?: return null
            val getCurrentPlot = plotPlayer.javaClass.getMethod("getCurrentPlot")
            getCurrentPlot.invoke(plotPlayer)
        } catch (e: Throwable) {
            Debug.debug("PlotSquared API 调用失败: ${e.message}")
            null
        }
    }

    fun getCurrentPlotId(player: Player): String? {
        val plot = getCurrentPlot(player) ?: return null
        return try {
            val getId = plot.javaClass.getMethod("getId")
            getId.invoke(plot)?.toString()
        } catch (_: Throwable) {
            try {
                val toString = plot.javaClass.getMethod("toString")
                toString.invoke(plot) as? String
            } catch (_: Throwable) { null }
        }
    }

    private fun getPlotOwner(player: Player): OfflinePlayer? {
        val plot = getCurrentPlot(player) ?: return null
        return try {
            val hasOwner = plot.javaClass.getMethod("hasOwner")
            if (hasOwner.invoke(plot) as Boolean) {
                val getOwner = plot.javaClass.getMethod("getOwner")
                val ownerUUID = getOwner.invoke(plot) as? java.util.UUID
                ownerUUID?.let { Bukkit.getOfflinePlayer(it) }
            } else null
        } catch (e: Throwable) {
            Debug.debug("Plot owner 获取失败: ${e.message}")
            null
        }
    }

    private fun getPlayersInPlot(requester: Player): List<Player> {
        val requesterPlot = getCurrentPlot(requester) ?: return listOf(requester)
        val targets = mutableListOf<Player>()
        for (p in Bukkit.getOnlinePlayers()) {
            val pPlot = getCurrentPlot(p)
            if (pPlot != null && requesterPlot == pPlot) {
                targets.add(p)
            }
        }
        return targets
    }

    /** 清理玩家数据 (退出时调用) */
    fun cleanup(player: Player) {
        consentByPlayer.remove(player.uniqueId)
        // 移除该玩家作为请求者的待处理请求
        val toRemove = consents.values.filter { it.requester.uniqueId == player.uniqueId }
        for (req in toRemove) {
            consents.remove(req.id)
            for (list in consentByPlayer.values) list.removeIf { it == req.id }
        }
    }
}
