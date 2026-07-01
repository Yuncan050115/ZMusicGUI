package com.ourcraft.zmusicgui.gui

import com.ourcraft.zmusicgui.ZMusicGUI
import com.ourcraft.zmusicgui.util.Debug
import com.ourcraft.zmusicgui.util.Items
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

/**
 * GUI 加载器 v3.0.1 — TrMenu 风格的 YAML GUI 自定义系统
 *
 * 设计:
 *  - 每个 GUI 对应 GUI/ 文件夹下的一个 .yml 文件 (如 main.yml, control.yml)
 *  - YAML 定义: 标题, 尺寸, 边框, 图标 (槽位/材质/名称/描述/发光/点击处理器)
 *  - 代码负责: 点击路由 (按 handler id 分发), 动态内容填充 (搜索结果/歌单列表等)
 *  - 支持 {placeholder} 占位符替换 (代码传入 Map)
 *
 * YAML 格式示例:
 * ```yaml
 * title: "&6&l标题"
 * size: 45
 * border:
 *   enabled: true
 *   material: LIGHT_BLUE_STAINED_GLASS_PANE
 * icons:
 *   welcome:
 *     slot: 4
 *     material: NETHER_STAR
 *     name: "&6&l欢迎"
 *     lore:
 *       - "&7第一行"
 *       - "&7第二行"
 *     glow: false
 *     click: none
 *   play:
 *     slot: 20
 *     material: MUSIC_DISC_CAT
 *     name: "&a播放"
 *     glow: true
 *     click: quick-play
 * dynamic:
 *   slots: [10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25]
 *   template:
 *     material: PAPER
 *     name: "&f{name}"
 *     lore:
 *       - "&7歌手: &f{singer}"
 * ```
 *
 * 点击处理: 代码通过 getClickHandler(guiId, slot) 获取 handler id, 自行分发。
 * 动态内容: 代码通过 fillDynamic(inv, guiId, items) 填充动态槽位。
 */
object GuiLoader {

    data class IconDef(
        val id: String,
        val slot: Int,
        val material: Material,
        val name: String,
        val lore: List<String>,
        val glow: Boolean,
        val clickHandler: String,
        val customModelData: Int? = null
    )

    data class BorderDef(
        val enabled: Boolean,
        val material: Material
    )

    data class DynamicDef(
        val slots: List<Int>,
        val templateMaterial: Material,
        val templateName: String,
        val templateLore: List<String>,
        val templateGlow: Boolean
    )

    data class GuiDef(
        val id: String,
        val title: String,
        val size: Int,
        val border: BorderDef?,
        val icons: Map<String, IconDef>,
        val dynamic: DynamicDef?
    ) {
        /** 槽位 → 图标 的快速映射 (用于点击路由) */
        val slotToIcon: Map<Int, IconDef> by lazy { icons.values.associateBy { it.slot } }
    }

    private val guis = mutableMapOf<String, GuiDef>()
    private lateinit var guiFolder: File

    fun load(plugin: ZMusicGUI) {
        guiFolder = File(plugin.dataFolder, "GUI")
        if (!guiFolder.exists()) guiFolder.mkdirs()

        // 从 resources/GUI/ 释放默认文件 (不覆盖已存在的)
        for (resName in DEFAULT_GUI_FILES) {
            val target = File(guiFolder, resName)
            if (!target.exists()) {
                try {
                    plugin.saveResource("GUI/$resName", false)
                } catch (_: Throwable) {
                    // saveResource 对子目录可能失败, 手动复制
                    try {
                        val stream = plugin.javaClass.getResourceAsStream("/GUI/$resName")
                        if (stream != null) {
                            target.parentFile.mkdirs()
                            target.writeBytes(stream.readAllBytes())
                            stream.close()
                        }
                    } catch (e: Throwable) {
                        Debug.warn("无法释放 GUI 默认文件: $resName — ${e.message}")
                    }
                }
            }
        }

        reload()
    }

    fun reload() {
        guis.clear()
        if (!guiFolder.exists()) return
        for (file in guiFolder.listFiles { f -> f.extension.equals("yml", true) } ?: emptyArray()) {
            val id = file.nameWithoutExtension
            try {
                val def = parseGui(id, YamlConfiguration.loadConfiguration(file))
                guis[id] = def
                Debug.debug("GUI 已加载: $id (icons=${def.icons.size}, size=${def.size})")
            } catch (e: Throwable) {
                Debug.warn("GUI 解析失败: $id — ${e.message}")
            }
        }
        Debug.info(">> GUI 已加载 ${guis.size} 个文件 (来自 ${guiFolder.name}/)")
    }

    /** 解析单个 GUI YAML */
    private fun parseGui(id: String, yaml: YamlConfiguration): GuiDef {
        val title = yaml.getString("title", "&8GUI")!!
        val size = (yaml.getInt("size", 27) / 9 * 9).coerceIn(9, 54)

        val border = if (yaml.getBoolean("border.enabled", false)) {
            val matName = yaml.getString("border.material", "LIGHT_BLUE_STAINED_GLASS_PANE")!!
            BorderDef(true, parseMaterial(matName, Material.LIGHT_BLUE_STAINED_GLASS_PANE))
        } else null

        val icons = mutableMapOf<String, IconDef>()
        val iconsSection = yaml.getConfigurationSection("icons")
        if (iconsSection != null) {
            for (iconId in iconsSection.getKeys(false)) {
                val sec = iconsSection.getConfigurationSection(iconId) ?: continue
                val slot = sec.getInt("slot", -1)
                if (slot < 0 || slot >= size) continue
                val matName = sec.getString("material", "STONE")!!
                val name = sec.getString("name", "&f")!!
                val lore = sec.getStringList("lore")
                val glow = sec.getBoolean("glow", false)
                val click = sec.getString("click", "none")!!
                val cmd = sec.getInt("custom-model-data", -1).let { if (it < 0) null else it }
                icons[iconId] = IconDef(iconId, slot, parseMaterial(matName, Material.STONE), name, lore, glow, click, cmd)
            }
        }

        val dynamic = parseDynamic(yaml)

        return GuiDef(id, title, size, border, icons, dynamic)
    }

    private fun parseDynamic(yaml: YamlConfiguration): DynamicDef? {
        val dynSection = yaml.getConfigurationSection("dynamic") ?: return null
        val slots = dynSection.getIntegerList("slots").filter { it in 0..53 }
        if (slots.isEmpty()) return null
        val tmpl = dynSection.getConfigurationSection("template") ?: return null
        val mat = parseMaterial(tmpl.getString("material", "PAPER")!!, Material.PAPER)
        val name = tmpl.getString("name", "&f{name}")!!
        val lore = tmpl.getStringList("lore")
        val glow = tmpl.getBoolean("glow", false)
        return DynamicDef(slots, mat, name, lore, glow)
    }

    /** 渲染 GUI 为 Inventory (应用占位符替换) */
    fun render(guiId: String, holder: GuiHolder, placeholders: Map<String, String> = emptyMap()): Inventory? {
        val def = guis[guiId] ?: return null
        val title = applyPlaceholders(def.title, placeholders)
        val inv = holder.create(def.size, Items.deserialize(title))

        // 边框
        if (def.border != null && def.border.enabled) {
            for (i in 0 until def.size) {
                val row = i / 9
                val col = i % 9
                if (row == 0 || row == def.size / 9 - 1 || col == 0 || col == 8) {
                    inv.setItem(i, Items.build(def.border.material, " "))
                }
            }
        }

        // 图标
        for (icon in def.icons.values) {
            if (icon.slot < 0 || icon.slot >= def.size) continue
            val name = applyPlaceholders(icon.name, placeholders)
            val lore = icon.lore.map { applyPlaceholders(it, placeholders) }
            inv.setItem(icon.slot, Items.build(icon.material, name, lore, icon.glow))
        }

        return inv
    }

    /** 获取某槽位的点击处理器 id (用于点击路由) */
    fun getClickHandler(guiId: String, slot: Int): String? {
        val def = guis[guiId] ?: return null
        return def.slotToIcon[slot]?.clickHandler
    }

    /** 获取某槽位的图标定义 */
    fun getIconAt(guiId: String, slot: Int): IconDef? {
        val def = guis[guiId] ?: return null
        return def.slotToIcon[slot]
    }

    /** 获取 GUI 定义 */
    fun getDef(guiId: String): GuiDef? = guis[guiId]

    /** 获取动态槽位列表 */
    fun getDynamicSlots(guiId: String): List<Int> = guis[guiId]?.dynamic?.slots ?: emptyList()

    /** 填充一个动态槽位 (用模板 + 自定义占位符) */
    fun fillDynamicSlot(inv: Inventory, guiId: String, slotIndex: Int, name: String, lore: List<String>, material: Material? = null, glow: Boolean = false) {
        val def = guis[guiId] ?: return
        val dyn = def.dynamic ?: return
        val slot = dyn.slots.getOrNull(slotIndex) ?: return
        val mat = material ?: dyn.templateMaterial
        val displayName = if (name.isNotEmpty()) name else applyPlaceholders(dyn.templateName, mapOf("name" to name))
        val displayLore = if (lore.isNotEmpty()) lore else dyn.templateLore
        val finalGlow = glow || dyn.templateGlow
        inv.setItem(slot, Items.build(mat, displayName, displayLore, finalGlow))
    }

    /** 批量填充动态槽位 */
    fun fillDynamic(inv: Inventory, guiId: String, items: List<DynamicItem>) {
        val def = guis[guiId] ?: return
        val dyn = def.dynamic ?: return
        for ((i, item) in items.withIndex()) {
            val slot = dyn.slots.getOrNull(i) ?: break
            val mat = item.material ?: dyn.templateMaterial
            val name = item.name.ifEmpty { dyn.templateName }
            val lore = item.lore.ifEmpty { dyn.templateLore }
            inv.setItem(slot, Items.build(mat, name, lore, item.glow || dyn.templateGlow))
        }
    }

    /** 动态内容项 (由代码构建, 传入名称/描述/材质) */
    data class DynamicItem(
        val name: String,
        val lore: List<String>,
        val material: Material? = null,
        val glow: Boolean = false
    )

    /** 占位符替换: {key} → value */
    fun applyPlaceholders(text: String, placeholders: Map<String, String>): String {
        var result = text
        for ((key, value) in placeholders) {
            result = result.replace("{$key}", value)
        }
        return result
    }

    /** 解析材质名 (容错: 无效名称回退到默认) */
    private fun parseMaterial(name: String, fallback: Material): Material {
        return try {
            Material.valueOf(name.uppercase().replace(" ", "_").replace("-", "_"))
        } catch (_: Throwable) {
            Debug.debug("未知材质: $name, 回退到 $fallback")
            fallback
        }
    }

    /** 默认 GUI 文件列表 (从 resources 释放) */
    private val DEFAULT_GUI_FILES = listOf(
        "main.yml", "quick_play.yml", "quick_play_results.yml",
        "control.yml", "settings.yml",
        "playlist_browser.yml", "playlist_search.yml",
        "playlist_detail.yml", "playlist_push.yml", "lyrics.yml"
    )
}
