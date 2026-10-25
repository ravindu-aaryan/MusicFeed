package com.musicfeed.app.instagram

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings

/**
 * The Instagram login lives in the WebView cookie jar: the user signs in on the
 * real instagram.com page, and the feed client reuses those cookies. No password
 * ever passes through this app.
 */
object InstagramSession {
    const val BASE_URL = "https://www.instagram.com"

    private const val FALLBACK_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/130.0.0.0 Mobile Safari/537.36"

    /**
     * The device's WebView user agent with the WebView markers ("; wv", "Version/4.0",
     * "Build/...") removed, so Instagram serves the regular mobile-browser login
     * instead of its embedded-browser flow. The feed client sends the same value so
     * the session stays consistent.
     */
    fun userAgent(context: Context): String {
        val webView = runCatching { WebSettings.getDefaultUserAgent(context) }.getOrNull()
            ?: return FALLBACK_USER_AGENT
        return webView
            .replace("; wv", "")
            .replace(Regex(" Build/[^;)]+"), "")
            .replace(Regex("Version/\\S+ "), "")
    }

    fun cookieHeader(): String? = CookieManager.getInstance().getCookie(BASE_URL)

    fun cookie(name: String): String? = cookieHeader()
        ?.split(";")
        ?.map { it.trim() }
        ?.firstOrNull { it.startsWith("$name=") }
        ?.substringAfter("=")
        ?.takeIf { it.isNotEmpty() }

    fun isLoggedIn(): Boolean = cookie("sessionid") != null

    fun logout() {
        CookieManager.getInstance().run {
            removeAllCookies(null)
            flush()
        }
    }
}

class NotLoggedInException : Exception("Your Instagram session has expired. Please log in again.")
