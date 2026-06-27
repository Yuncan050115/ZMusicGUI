package com.ourcraft.zmusicgui.util

import java.net.HttpURLConnection
import java.net.URL

/**
 * 统一 HTTP 工具 — 替代 4 份重复的 httpGet/httpGetLocation 实现
 *
 * 全部使用 java.net.HttpURLConnection, 不引入新依赖。
 * 超时统一: connect=10s, read=15s。
 */
object HttpUtil {

    private const val UA = "ZMusicGUI/2.4.3"
    private const val CONNECT_TIMEOUT = 10000
    private const val READ_TIMEOUT = 15000

    /** GET 请求, 返回响应体字符串; 失败返回空串 */
    fun httpGet(
        url: String,
        referer: String = "",
        cookie: String = "",
        followRedirects: Boolean = true
    ): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
            instanceFollowRedirects = followRedirects
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept", "application/json, text/plain, */*")
            if (referer.isNotEmpty()) setRequestProperty("Referer", referer)
            if (cookie.isNotEmpty()) setRequestProperty("Cookie", cookie)
        }
        try {
            val code = conn.responseCode
            if (code != 200) {
                // 读取错误响应体 (服务端 502 时仍会返回 JSON 错误信息)
                val errBody = try { conn.errorStream?.bufferedReader()?.use { it.readText() } } catch (_: Throwable) { "" }
                Debug.warn("HTTP $code - ${url.take(120)}")
                return errBody ?: ""
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Throwable) {
            Debug.warn("HTTP 请求失败: ${e.message} - ${url.take(120)}")
            return ""
        } finally {
            conn.disconnect()
        }
    }

    /** GET 请求但不跟随重定向, 返回 Location 头 (用于网易云 302 跳转取真实 URL) */
    fun httpGetLocation(url: String, referer: String = "", cookie: String = ""): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
            instanceFollowRedirects = false
            setRequestProperty("User-Agent", UA)
            if (referer.isNotEmpty()) setRequestProperty("Referer", referer)
            if (cookie.isNotEmpty()) setRequestProperty("Cookie", cookie)
        }
        try {
            val code = conn.responseCode
            // 301/302/303/307/308 重定向
            if (code in 300..399) return conn.getHeaderField("Location")
            Debug.warn("URL 接口非重定向: code=$code url=${url.take(120)}")
            return null
        } finally {
            conn.disconnect()
        }
    }

    /** 跟随重定向并返回最终 URL (用于 URL 接口 Location 为空时回退) */
    fun httpGetFinalUrl(url: String, referer: String = "", cookie: String = ""): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", UA)
            if (referer.isNotEmpty()) setRequestProperty("Referer", referer)
            if (cookie.isNotEmpty()) setRequestProperty("Cookie", cookie)
        }
        try {
            val code = conn.responseCode
            if (code == 200) {
                val finalUrl = conn.url.toString()
                if (finalUrl != url && finalUrl.startsWith("http")) return finalUrl
                // 有些响应直接返回 URL 文本
                val body = conn.inputStream.bufferedReader().use { it.readText() }.trim()
                if (body.startsWith("http")) return body.lines().firstOrNull { it.startsWith("http") } ?: body
            }
            return ""
        } finally {
            conn.disconnect()
        }
    }
}
