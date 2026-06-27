package com.ourcraft.zmusicgui.manager

import com.ourcraft.zmusicgui.music.OurMusicApi
import com.ourcraft.zmusicgui.util.Debug
import org.bukkit.entity.Player

/**
 * 统一搜索服务 v2.2.1 — 简化版
 *
 * 直接调用 OurMusicApi, 不再做 when 分发。
 *
 * 支持源:
 *  - netease   网易云 (走 enhanced 模式, 自带解灰)
 *  - kugou     酷狗   (公开接口, 无需登录)
 *  - kuwo      酷我   (公开接口, 无需登录)
 *  - qq        QQ音乐 (vkey 接口 + VIP 跨平台解灰)
 *
 * 歌曲类型直接使用 OurMusicApi.Song / OurMusicApi.SongDetail。
 */
object SearchService {

    /** 兼容别名: 旧数据可能用 163 / 1 表示网易云 */
    private fun normalizeSource(source: String): String = when (source) {
        "netease", "163", "1" -> "netease"
        "kugou", "2" -> "kugou"
        "kuwo", "3" -> "kuwo"
        "qq", "tencent", "4" -> "qq"
        "bilibili", "bili", "5" -> "netease" // B站已移除, 兼容旧数据回退到网易云
        else -> source
    }

    /** 搜索歌曲 (按源路由) */
    fun search(keyword: String, source: String): List<OurMusicApi.Song> {
        val src = normalizeSource(source)
        Debug.debug("[SearchService] 搜索: keyword=$keyword source=$src")
        return try {
            OurMusicApi.search(keyword, src, Config.searchLimit())
        } catch (e: Throwable) {
            Debug.warn("[SearchService] 搜索失败 [$src]: ${e.message}")
            emptyList()
        }
    }

    /** 获取歌曲详情 (根据 Song 对象, 携带玩家账号 Token) */
    fun getSongDetail(song: OurMusicApi.Song, player: Player? = null): OurMusicApi.SongDetail? {
        return getSongDetailBySource(song.id, song.source, player)
    }

    /** 按 ID + 来源获取详情 (携带玩家账号登录态用于 VIP 歌曲) */
    fun getSongDetailBySource(id: String, source: String, player: Player? = null): OurMusicApi.SongDetail? {
        val src = normalizeSource(source)
        Debug.debug("[SearchService] 获取详情: id=$id source=$src player=${player?.name}")
        // v2.5.0: 仅 QQ/网易云 支持账号登录 (酷狗/酷我 VIP 已下线)
        // - QQ: 既传 uin+qqmusic_key (兼容旧服务端), 也传完整 cookie
        // - 网易云: 传 cookie (服务端用 cookie 获取 VIP 歌曲)
        // - 酷狗/酷我: 不传 cookie (VIP 已下线, 仅免费歌曲)
        val acc = if (player != null) PlayerSettings.getAccount(player, src) else null
        val (userId, token, cookie) = when {
            acc == null -> Triple("", "", "")
            src == "qq" -> Triple(acc.userId, acc.token, acc.cookie)
            src == "netease" -> Triple(acc.userId, acc.token, acc.cookie)
            else -> Triple("", "", "")
        }
        return try {
            OurMusicApi.getSongDetail(id, src, userId, token, cookie)
        } catch (e: Throwable) {
            Debug.warn("[SearchService] 获取详情失败 [$src/$id]: ${e.message}")
            null
        }
    }

    /** 按歌单 ID 获取网易云歌单 (含歌曲列表和真实歌单名) */
    fun getPlaylist(id: String): OurMusicApi.Playlist? {
        return OurMusicApi.getPlaylist(id)
    }

    /** 搜索网易云歌单 (按歌单名搜索) */
    fun searchPlaylists(keyword: String, limit: Int = 10): List<OurMusicApi.PlaylistSearch> {
        return OurMusicApi.searchPlaylists(keyword, limit)
    }

    /** 多平台搜索歌单 (netease/kugou) */
    fun searchPlaylistsBySource(keyword: String, source: String, limit: Int = 30): List<OurMusicApi.PlaylistSearch> {
        val src = normalizeSource(source)
        return try {
            OurMusicApi.searchPlaylistsBySource(keyword, src, limit)
        } catch (e: Throwable) {
            Debug.warn("[SearchService] 多平台搜索歌单失败 [$src]: ${e.message}")
            emptyList()
        }
    }

    /** 多平台获取歌单详情 (含歌曲列表) */
    fun getPlaylistBySource(id: String, source: String, limit: Int = 100): OurMusicApi.Playlist? {
        val src = normalizeSource(source)
        return try {
            OurMusicApi.getPlaylistBySource(id, src, limit)
        } catch (e: Throwable) {
            Debug.warn("[SearchService] 多平台歌单详情失败 [$src/$id]: ${e.message}")
            null
        }
    }

    /** 平台显示名 (含颜色码) */
    fun sourceName(source: String): String = when (normalizeSource(source)) {
        "netease" -> "&c网易云"
        "kugou" -> "&a酷狗"
        "kuwo" -> "&6酷我"
        "qq" -> "&dQQ音乐"
        else -> source
    }

    /** 平台纯文本名 (无颜色码) */
    fun sourcePlainName(source: String): String = when (normalizeSource(source)) {
        "netease" -> "网易云"
        "kugou" -> "酷狗"
        "kuwo" -> "酷我"
        "qq" -> "QQ音乐"
        else -> source
    }

    /** 所有可用的平台 */
    val SUPPORTED_SOURCES = listOf("netease", "kugou", "kuwo", "qq")

    /** 支持歌单搜索的平台 */
    val PLAYLIST_SEARCH_SOURCES = listOf("netease", "kugou")
}
