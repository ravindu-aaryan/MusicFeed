package com.musicfeed.app.instagram

import android.webkit.CookieManager

/**
 * The Instagram login lives in the WebView cookie jar: the user signs in on the
 * real instagram.com page, and the feed client reuses those cookies. No password
 * ever passes through this app.
 */
object InstagramSession {
    const val BASE_URL = "https://www.instagram.com"

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
