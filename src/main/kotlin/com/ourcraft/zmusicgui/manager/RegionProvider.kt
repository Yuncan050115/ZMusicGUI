package com.ourcraft.zmusicgui.manager

import com.ourcraft.zmusicgui.ZMusicGUI
import com.ourcraft.zmusicgui.util.Debug
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.UUID

/**
 * 领地插件统一接口 — 屏蔽 Residence / Lands / Dominion 三种底层实现差异
 *
 * 由 [ScopeManager] 根据 config.yml 的 scope.region-plugin 选择具体实现,
 * RESIDENCE scope 的领地检测/玩家枚举/主人查询全部委托给当前 provider。
 *
 * 所有实现均使用反射, 避免硬依赖任一领地插件 (按需加载, 缺失时降级)。
 *
 * v3.0.2 修正:
 *  - Lands: of(plugin) 不是 of(Player); getArea(loc) 替代已移除的 getLand(loc); Area.getLand().getName()/getOwnerUID()
 *  - Dominion: getDominion(Location) 不是 getDominionByLoc; DominionDTO.getName()/getOwner()
 */
interface RegionProvider {
    val typeId: String
    fun isAvailable(): Boolean
    fun setup()
    fun getRegionName(player: Player): String?
    fun getRegionOwner(player: Player): OfflinePlayer?
    fun getPlayersInRegion(requester: Player): List<Player>
}

// ==================== Residence ====================

class ResidenceProvider(private val scopeManager: ScopeManager) : RegionProvider {
    override val typeId: String = "residence"
    private var residencePlugin: Plugin? = null

    override fun isAvailable(): Boolean = residencePlugin != null

    override fun setup() {
        residencePlugin = Bukkit.getPluginManager().getPlugin("Residence")
        if (residencePlugin != null) {
            Debug.info(">> Residence 已检测到 (版本: ${residencePlugin!!.description.version}), 支持领地范围播放")
        }
    }

    override fun getRegionName(player: Player): String? = scopeManager.getResidenceNameImpl(player)
    override fun getRegionOwner(player: Player): OfflinePlayer? = scopeManager.getResidenceOwnerImpl(player)
    override fun getPlayersInRegion(requester: Player): List<Player> = scopeManager.getPlayersInResidenceImpl(requester)
}

// ==================== Lands ====================
//
// API 流程 (基于 LandsAPI 7.27.1 实际 jar 逆向确认):
//   LandsIntegration.of(plugin)          → LandsIntegration 实例 (静态方法, 参数为我们的 Plugin)
//   api.getArea(Location)                → Area (子区域) 或 null
//   area.getLand()                       → Land (父领地)
//   land.getName()                       → String
//   land.getOwnerUID()                   → UUID
//   area.getOwnerUID()                   → UUID (区域级所有者, 可能与 Land 不同)
//
// 注意: 6.26.0 起 getLand(Location) 已被移除, 必须通过 getArea → getLand 两步走

class LandsProvider : RegionProvider {
    override val typeId: String = "lands"
    private var landsPlugin: Plugin? = null
    private var api: Any? = null  // LandsIntegration 实例
    private var initialized = false

    override fun isAvailable(): Boolean = landsPlugin != null

    override fun setup() {
        landsPlugin = Bukkit.getPluginManager().getPlugin("Lands")
        if (landsPlugin == null) {
            Debug.warn(">> 配置选择了 Lands 作为领地插件, 但 Lands 未加载")
            return
        }
        Debug.info(">> Lands 已检测到 (版本: ${landsPlugin!!.description.version}), 正在初始化 API...")
        initApi()
    }

    private fun initApi() {
        try {
            val cls = Class.forName("me.angeschossen.lands.api.LandsIntegration")
            // 静态方法 of(Plugin) → LandsIntegration
            val ofMethod = cls.getMethod("of", Plugin::class.java)
            api = ofMethod.invoke(null, ZMusicGUI.plugin)
            if (api != null) {
                Debug.info(">> Lands API 初始化成功 (LandsIntegration.of(plugin) = $api)")
            } else {
                Debug.warn(">> Lands API 返回 null — 可能是 Lands 尚未完全启用")
            }
        } catch (e: NoClassDefFoundError) {
            Debug.warn(">> Lands API 类未找到 (me.angeschossen.lands.api.LandsIntegration): ${e.message}")
        } catch (e: Throwable) {
            Debug.warn(">> Lands API 初始化失败: ${e.javaClass.simpleName}: ${e.message}")
        }
        initialized = true
    }

    /** 获取 Area 对象 (子区域) */
    private fun getArea(player: Player): Any? {
        val inst = api ?: run {
            if (!initialized) initApi()
            api ?: return null
        }
        val loc = player.location
        return try {
            val cls = Class.forName("me.angeschossen.lands.api.LandsIntegration")
            val getAreaMethod = cls.getMethod("getArea", org.bukkit.Location::class.java)
            val area = getAreaMethod.invoke(inst, loc)
            if (area == null) {
                Debug.debug("Lands: ${player.name} 不在任何领地内 (getArea 返回 null)")
            }
            area
        } catch (e: Throwable) {
            Debug.debug("Lands getArea 反射失败: ${e.message}")
            null
        }
    }

    override fun getRegionName(player: Player): String? {
        val area = getArea(player) ?: return null
        return try {
            // Area.getLand() → Land, 然后 Land.getName()
            val land = area.javaClass.getMethod("getLand").invoke(area)
            if (land == null) {
                Debug.debug("Lands: Area.getLand() 返回 null (${player.name})")
                return null
            }
            val name = land.javaClass.getMethod("getName").invoke(land) as? String
            Debug.debug("Lands: ${player.name} 所在领地 = $name")
            name
        } catch (e: Throwable) {
            Debug.debug("Lands getRegionName 失败: ${e.message}")
            null
        }
    }

    override fun getRegionOwner(player: Player): OfflinePlayer? {
        val area = getArea(player) ?: return null
        return try {
            val land = area.javaClass.getMethod("getLand").invoke(area) ?: return null
            val uuid = land.javaClass.getMethod("getOwnerUID").invoke(land) as? UUID
            uuid?.let { Bukkit.getOfflinePlayer(it) }
        } catch (e: Throwable) {
            Debug.debug("Lands getRegionOwner 失败: ${e.message}")
            null
        }
    }

    override fun getPlayersInRegion(requester: Player): List<Player> {
        val reqArea = getArea(requester) ?: return listOf(requester)
        // 用 Area 对象比较 (equals) — 同一 Land 下的玩家
        val reqLand = try {
            reqArea.javaClass.getMethod("getLand").invoke(reqArea)
        } catch (_: Throwable) { null } ?: return listOf(requester)

        val targets = mutableListOf(requester)
        for (p in Bukkit.getOnlinePlayers()) {
            if (p.uniqueId == requester.uniqueId) continue
            try {
                val pArea = getArea(p) ?: continue
                val pLand = pArea.javaClass.getMethod("getLand").invoke(pArea) ?: continue
                if (pLand == reqLand) targets.add(p)
            } catch (_: Throwable) {}
        }
        return targets
    }
}

// ==================== Dominion ====================
//
// API 流程 (基于 DominionAPI 4.8.x 源码确认):
//   DominionAPI.getInstance()              → DominionAPI 实例 (静态, 可能返回 null)
//   api.getDominion(Location)              → DominionDTO 或 null
//   api.getPlayerCurrentDominion(Player)   → DominionDTO 或 null (带缓存的当前领地)
//   dto.getName()                          → String
//   dto.getOwner()                         → UUID

class DominionProvider : RegionProvider {
    override val typeId: String = "dominion"
    private var dominionPlugin: Plugin? = null
    private var api: Any? = null  // DominionAPI 实例
    private var initialized = false

    override fun isAvailable(): Boolean = dominionPlugin != null

    override fun setup() {
        dominionPlugin = Bukkit.getPluginManager().getPlugin("Dominion")
        if (dominionPlugin == null) {
            Debug.warn(">> 配置选择了 Dominion 作为领地插件, 但 Dominion 未加载")
            return
        }
        Debug.info(">> Dominion 已检测到 (版本: ${dominionPlugin!!.description.version}), 正在初始化 API...")
        initApi()
    }

    private fun initApi() {
        try {
            val cls = Class.forName("cn.lunadeer.dominion.api.DominionAPI")
            // 静态方法 getInstance() → DominionAPI
            val getInstance = cls.getMethod("getInstance")
            api = getInstance.invoke(null)
            if (api != null) {
                Debug.info(">> Dominion API 初始化成功 (DominionAPI.getInstance() = $api)")
            } else {
                Debug.warn(">> Dominion API 返回 null — 可能是 Dominion 尚未完全启用 (instance 未设置)")
            }
        } catch (e: NoClassDefFoundError) {
            Debug.warn(">> Dominion API 类未找到 (cn.lunadeer.dominion.api.DominionAPI): ${e.message}")
        } catch (e: Throwable) {
            Debug.warn(">> Dominion API 初始化失败: ${e.javaClass.simpleName}: ${e.message}")
        }
        initialized = true
    }

    /** 获取 DominionDTO (优先用 getPlayerCurrentDominion, 回退到 getDominion(Location)) */
    private fun getDominion(player: Player): Any? {
        val inst = api ?: run {
            if (!initialized) initApi()
            api ?: return null
        }
        return try {
            val cls = Class.forName("cn.lunadeer.dominion.api.DominionAPI")
            // 路径1: getPlayerCurrentDominion(Player) — 带缓存, 更快
            try {
                val m = cls.getMethod("getPlayerCurrentDominion", Player::class.java)
                val dto = m.invoke(inst, player)
                if (dto != null) return dto
            } catch (_: NoSuchMethodException) {} catch (e: Throwable) {
                Debug.debug("Dominion getPlayerCurrentDominion 失败: ${e.message}")
            }
            // 路径2: getDominion(Location) — 直接按坐标查
            val loc = player.location
            val m = cls.getMethod("getDominion", org.bukkit.Location::class.java)
            val dto = m.invoke(inst, loc)
            if (dto == null) {
                Debug.debug("Dominion: ${player.name} 不在任何领地内 (getDominion(loc) 返回 null)")
            }
            dto
        } catch (e: Throwable) {
            Debug.debug("Dominion getDominion 反射失败: ${e.message}")
            null
        }
    }

    override fun getRegionName(player: Player): String? {
        val dto = getDominion(player) ?: return null
        return try {
            val name = dto.javaClass.getMethod("getName").invoke(dto) as? String
            Debug.debug("Dominion: ${player.name} 所在领地 = $name")
            name
        } catch (e: Throwable) {
            Debug.debug("Dominion getRegionName 失败: ${e.message}")
            null
        }
    }

    override fun getRegionOwner(player: Player): OfflinePlayer? {
        val dto = getDominion(player) ?: return null
        return try {
            val uuid = dto.javaClass.getMethod("getOwner").invoke(dto) as? UUID
            uuid?.let { Bukkit.getOfflinePlayer(it) }
        } catch (e: Throwable) {
            Debug.debug("Dominion getRegionOwner 失败: ${e.message}")
            null
        }
    }

    override fun getPlayersInRegion(requester: Player): List<Player> {
        val reqDto = getDominion(requester) ?: return listOf(requester)
        val reqId = try {
            reqDto.javaClass.getMethod("getId").invoke(reqDto) as? Int
        } catch (_: Throwable) { null } ?: return listOf(requester)

        val cls = Class.forName("cn.lunadeer.dominion.api.DominionAPI")
        val inst = api ?: return listOf(requester)
        val targets = mutableListOf(requester)
        for (p in Bukkit.getOnlinePlayers()) {
            if (p.uniqueId == requester.uniqueId) continue
            try {
                // 优先用 getPlayerCurrentDominion (带缓存)
                val m = cls.getMethod("getPlayerCurrentDominion", Player::class.java)
                val pDto = m.invoke(inst, p)
                if (pDto != null) {
                    val pId = pDto.javaClass.getMethod("getId").invoke(pDto) as? Int
                    if (pId == reqId) targets.add(p)
                }
            } catch (_: Throwable) {}
        }
        return targets
    }
}

// ==================== 空实现 (插件未加载时降级) ====================

object NoRegionProvider : RegionProvider {
    override val typeId: String = "none"
    override fun isAvailable(): Boolean = false
    override fun setup() {}
    override fun getRegionName(player: Player): String? = null
    override fun getRegionOwner(player: Player): OfflinePlayer? = null
    override fun getPlayersInRegion(requester: Player): List<Player> = listOf(requester)
}
