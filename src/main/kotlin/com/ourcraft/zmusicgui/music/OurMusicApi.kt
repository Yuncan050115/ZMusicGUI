package com.ourcraft.zmusicgui.music

import com.google.gson.JsonParser
import com.ourcraft.zmusicgui.manager.Config
import com.ourcraft.zmusicgui.util.Debug
import com.ourcraft.zmusicgui.util.HttpUtil
import java.net.URLEncoder

/**
 * 统一音乐 API 客户端 v3.0.0 — 调用 ourcraft-music-api 服务端
 *
 * 设计目标:
 *  - 单一文件替代旧的 NeteaseApi/KugouMusicApi/KuwoMusicApi/QQMusicApi 4 个文件
 *  - 使用服务端 song_full 端点 (1 次请求拿 name+url+lyric+time), 替代旧的 3-5 次串行请求
 *  - 网易云歌单搜索/详情仍走 enhanced 模式 (服务端兼容)
 *
 * 支持源: netease / kugou / kuwo
 *  - netease  网易云 (enhanced 模式, 免登录)
 *  - kugou    酷狗   (公开接口, 免登录)
 *  - kuwo     酷我   (公开接口, 免登录)
 *
 * v3.0.0: 移除账号登录模块 (QQ/网易云 VIP 登录已删除), 仅播放免费歌曲。
 *
 * 接口形态:
 *   {api}/api?server={source}&type={type}&id={id}
 *   type=search      搜索歌曲
 *   type=song_full   完整单曲 (name+url+lyric+time, 服务端并发获取)
 *   type=url          播放 URL (302 重定向)
 *   type=lrc          歌词文本
 *   type=playlist     网易云歌单 (MetingJS 格式)
 *
 * action=enhanced&path=... 网易云 enhanced 模式 (歌单搜索/详情)
 */
object OurMusicApi {

    /** 统一歌曲 (搜索结果) */
    data class Song(
        val id: String,
        val name: String,
        val singer: String,
        val source: String
    )

    /** 统一歌曲详情 (含播放 URL 和歌词) */
    data class SongDetail(
        val id: String,
        val name: String,
        val singer: String,
        val url: String,
        val lyric: String,
        val time: Int,
        val source: String
    )

    /** 网易云歌单元信息 (搜索/详情) */
    data class PlaylistSearch(
        val id: String,
        val name: String,
        val creator: String,
        val trackCount: Int,
        val playCount: Long
    )

    /** 网易云歌单 (含歌曲列表) */
    data class Playlist(
        val id: String,
        val name: String,
        val songCount: Int,
        val songs: List<Song>
    )

    private fun apiBase(): String = Config.ourcraftApi().trimEnd('/')

    // ==================== 多平台通用接口 ====================

    /** 搜索歌曲 (按源路由) */
    fun search(keyword: String, source: String, limit: Int = 10): List<Song> {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val url = "${apiBase()}/api?server=$source&type=search&id=$encoded&limit=$limit"
        Debug.debug("[搜索] $source/$keyword → $url")
        val body = HttpUtil.httpGet(url)
        if (body.isEmpty()) {
            Debug.warn("[搜索] 响应为空: $source/$keyword")
            return emptyList()
        }
        return try {
            val root = JsonParser.parseString(body).asJsonObject
            if (!root.get("ok")?.asBoolean!!) {
                Debug.warn("[搜索] 服务端返回失败: ${body.take(200)}")
                return emptyList()
            }
            val arr = root.getAsJsonArray("songs") ?: return emptyList()
            arr.mapNotNull { el ->
                val obj = el.asJsonObject
                val id = obj.get("id")?.asString ?: return@mapNotNull null
                Song(
                    id = id,
                    name = obj.get("name")?.asString ?: "未知",
                    singer = obj.get("singer")?.asString ?: "未知",
                    source = source
                )
            }
        } catch (e: Throwable) {
            Debug.warn("[搜索] 解析失败 [$source/$keyword]: ${e.message}")
            emptyList()
        }
    }

    /**
     * 获取单曲详情 (含播放 URL 和歌词) — 调用服务端 song_full 端点
     *
     * 服务端并发获取 name+url+lyric+time, 客户端只需 1 次请求 (替代旧的 3-5 次串行)
     *
     * v3.0.0: 移除 userId/token/cookie 参数 (账号登录模块已删除), 仅播放免费歌曲。
     */
    fun getSongDetail(
        id: String,
        source: String
    ): SongDetail? {
        val url = "${apiBase()}/api?server=$source&type=song_full&id=${URLEncoder.encode(id, "UTF-8")}"
        Debug.debug("[详情] $source/$id → ${url.take(120)}")
        val body = HttpUtil.httpGet(url)
        if (body.isEmpty()) {
            Debug.warn("[详情] 响应为空: $source/$id (可能 Vercel 超时或接口不可达)")
            return null
        }
        // v2.4.2: 检查是否为有效 JSON (Vercel 502 会返回 HTML 错误页)
        val trimmed = body.trim()
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            Debug.warn("[详情] 非 JSON 响应: $source/$id — ${trimmed.take(100)}")
            return null
        }
        return try {
            val root = JsonParser.parseString(body).asJsonObject
            if (!root.get("ok")?.asBoolean!!) {
                Debug.warn("[详情] 服务端返回失败: ${body.take(200)}")
                return null
            }
            val song = root.getAsJsonObject("song") ?: return null
            val playUrl = song.get("url")?.asString ?: ""
            if (playUrl.isEmpty()) {
                Debug.warn("[详情] 无播放 URL: $source/$id (可能为 VIP 歌曲)")
                return null
            }
            SongDetail(
                id = id,
                name = song.get("name")?.asString ?: "未知",
                singer = song.get("singer")?.asString ?: "未知",
                url = playUrl,
                lyric = song.get("lyric")?.asString ?: "",
                time = song.get("time")?.asInt ?: 0,
                source = source
            )
        } catch (e: Throwable) {
            Debug.warn("[详情] 解析失败 [$source/$id]: ${e.message}")
            null
        }
    }

    // ==================== 网易云歌单特性 (enhanced 模式) ====================

    /** 关键词搜索歌单 (使用 enhanced 模式访问底层网易云 /search?type=1000) */
    fun searchPlaylists(keyword: String, limit: Int = 10): List<PlaylistSearch> {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val url = "${apiBase()}/api?action=enhanced&path=%2Fsearch&keywords=$encoded&type=1000&limit=$limit"
        Debug.debug("[搜索歌单] $url")
        val body = HttpUtil.httpGet(url)
        if (body.isEmpty()) {
            Debug.warn("[搜索歌单] 响应为空: $keyword")
            return emptyList()
        }
        return try {
            val root = JsonParser.parseString(body).asJsonObject
            val playlistsArr = root.getAsJsonObject("body")?.getAsJsonObject("result")?.getAsJsonArray("playlists")
                ?: run {
                    Debug.warn("[搜索歌单] 无 body.result.playlists 字段")
                    return emptyList()
                }
            playlistsArr.mapNotNull { el ->
                val obj = el.asJsonObject
                val id = obj.get("id")?.asLong ?: return@mapNotNull null
                PlaylistSearch(
                    id = id.toString(),
                    name = obj.get("name")?.asString ?: "未知歌单",
                    creator = obj.getAsJsonObject("creator")?.get("nickname")?.asString ?: "未知",
                    trackCount = obj.get("trackCount")?.asInt ?: 0,
                    playCount = obj.get("playCount")?.asLong ?: 0L
                )
            }
        } catch (e: Throwable) {
            Debug.warn("[搜索歌单] 解析失败: ${e.message}")
            emptyList()
        }
    }

    /** 获取歌单元信息 (使用 enhanced 模式) */
    fun getPlaylistDetail(playlistId: String): PlaylistSearch? {
        val url = "${apiBase()}/api?action=enhanced&path=%2Fplaylist%2Fdetail&id=$playlistId"
        Debug.debug("[歌单详情] $url")
        val body = HttpUtil.httpGet(url)
        if (body.isEmpty()) return null
        return try {
            val root = JsonParser.parseString(body).asJsonObject
            val playlist = root.getAsJsonObject("body")?.getAsJsonObject("playlist") ?: return null
            PlaylistSearch(
                id = playlistId,
                name = playlist.get("name")?.asString ?: "未知歌单",
                creator = playlist.getAsJsonObject("creator")?.get("nickname")?.asString ?: "未知",
                trackCount = playlist.get("trackCount")?.asInt ?: 0,
                playCount = playlist.get("playCount")?.asLong ?: 0L
            )
        } catch (e: Throwable) {
            Debug.warn("[歌单详情] 解析失败: ${e.message}")
            null
        }
    }

    /** 获取网易云歌单 (含歌曲列表, 使用 type=playlist MetingJS 兼容格式) */
    fun getPlaylist(playlistId: String, limit: Int = 100): Playlist? {
        // 1. 获取歌单元信息
        val detail = getPlaylistDetail(playlistId)
        val playlistName = detail?.name ?: "网易云歌单 $playlistId"
        Debug.debug("[歌单] 元信息: name=$playlistName tracks=${detail?.trackCount}")

        // 2. 获取歌曲列表 (MetingJS 格式)
        val url = "${apiBase()}/api?server=netease&type=playlist&id=$playlistId&limit=$limit"
        Debug.debug("[歌单] $url")
        val body = HttpUtil.httpGet(url)
        if (body.isEmpty()) {
            Debug.warn("[歌单] 响应为空: $playlistId")
            return null
        }
        return try {
            val arr = JsonParser.parseString(body).asJsonArray
            val songs = arr.mapNotNull { el ->
                val obj = el.asJsonObject
                val lrcUrl = obj.get("lrc")?.asString ?: ""
                val id = extractIdFromUrl(lrcUrl)
                if (id.isEmpty()) null else Song(
                    id = id,
                    name = obj.get("title")?.asString ?: "未知",
                    singer = obj.get("author")?.asString ?: "未知",
                    source = "netease"
                )
            }
            Playlist(playlistId, playlistName, songs.size, songs)
        } catch (e: Throwable) {
            Debug.warn("[歌单] 解析失败: ${e.message}")
            null
        }
    }

    /** 从 MetingJS 返回的 url/lrc 字段中提取歌曲 ID */
    private fun extractIdFromUrl(url: String): String {
        if (url.isEmpty()) return ""
        val match = Regex("[?&]id=(\\w+)").find(url)
        return match?.groupValues?.get(1) ?: ""
    }

    // ==================== 多平台歌单搜索 (v0.17.0+ 服务端) ====================

    /** 多平台搜索歌单 (netease/kugou) */
    fun searchPlaylistsBySource(keyword: String, source: String, limit: Int = 30): List<PlaylistSearch> {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val url = "${apiBase()}/api?server=$source&type=playlist_search&id=$encoded&limit=$limit"
        Debug.debug("[多平台搜索歌单] $url")
        val body = HttpUtil.httpGet(url)
        if (body.isEmpty()) {
            Debug.warn("[多平台搜索歌单] 响应为空: $source/$keyword")
            return emptyList()
        }
        return try {
            val root = JsonParser.parseString(body).asJsonObject
            if (root.get("ok")?.asBoolean != true) {
                Debug.warn("[多平台搜索歌单] 服务端返回 ok=false: ${root.get("message")}")
                return emptyList()
            }
            val arr = root.getAsJsonArray("playlists") ?: return emptyList()
            arr.mapNotNull { el ->
                val obj = el.asJsonObject
                val id = obj.get("id")?.asString ?: return@mapNotNull null
                PlaylistSearch(
                    id = id,
                    name = obj.get("name")?.asString ?: "未知歌单",
                    creator = obj.get("creator")?.asString ?: "未知",
                    trackCount = obj.get("songCount")?.asInt ?: 0,
                    playCount = 0L
                )
            }
        } catch (e: Throwable) {
            Debug.warn("[多平台搜索歌单] 解析失败: ${e.message}")
            emptyList()
        }
    }

    /** 多平台获取歌单详情 (含歌曲列表) */
    fun getPlaylistBySource(playlistId: String, source: String, limit: Int = 100): Playlist? {
        val url = "${apiBase()}/api?server=$source&type=playlist_detail&id=$playlistId&limit=$limit"
        Debug.debug("[多平台歌单详情] $url")
        val body = HttpUtil.httpGet(url)
        if (body.isEmpty()) {
            Debug.warn("[多平台歌单详情] 响应为空: $source/$playlistId")
            return null
        }
        return try {
            val root = JsonParser.parseString(body).asJsonObject
            if (root.get("ok")?.asBoolean != true) {
                Debug.warn("[多平台歌单详情] 服务端返回 ok=false: ${root.get("message")}")
                return null
            }
            val songsArr = root.getAsJsonArray("songs") ?: return null
            val songs = songsArr.mapNotNull { el ->
                val obj = el.asJsonObject
                val id = obj.get("id")?.asString ?: return@mapNotNull null
                Song(
                    id = id,
                    name = obj.get("name")?.asString ?: "未知",
                    singer = obj.get("singer")?.asString ?: "未知",
                    source = source
                )
            }
            Playlist(playlistId, "$source 歌单 $playlistId", songs.size, songs)
        } catch (e: Throwable) {
            Debug.warn("[多平台歌单详情] 解析失败: ${e.message}")
            null
        }
    }

}

