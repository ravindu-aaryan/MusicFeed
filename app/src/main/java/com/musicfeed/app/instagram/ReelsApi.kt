package com.musicfeed.app.instagram

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Pages through the logged-in user's personalised reels feed using the same
 * private web endpoints instagram.com uses. These are unofficial: if Instagram
 * changes them, add or adjust entries in [endpoints].
 */
class ReelsApi(private val userAgent: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Tried in order; the first that yields reels is kept for later pages. */
    private val endpoints = listOf(
        "clips/connected/", // Reels tab: based on your interests and watch history
        "clips/discover/",  // Explore reels
        "feed/timeline/",   // Home feed, videos only
    )

    private var endpointIndex = 0
    private var maxId: String? = null
    private val seen = HashSet<String>()

    fun reset() {
        endpointIndex = 0
        maxId = null
        seen.clear()
    }

    /** Blocking network call: run it off the main thread. */
    fun nextPage(): List<Reel> {
        var lastError: Exception? = null
        while (endpointIndex < endpoints.size) {
            val path = endpoints[endpointIndex]
            try {
                val json = request(path)
                val reels = MediaParser.findReels(json).filter { seen.add(it.id) }
                maxId = json.str("max_id")
                    ?: json.optJSONObject("paging_info")?.str("max_id")
                    ?: json.str("next_max_id")
                if (reels.isNotEmpty()) return reels
                lastError = IOException("$path returned no playable reels")
            } catch (e: NotLoggedInException) {
                throw e
            } catch (e: Exception) {
                lastError = e
            }
            endpointIndex++
            maxId = null
        }
        // Everything failed: start from the top again on the next attempt.
        endpointIndex = 0
        throw lastError ?: IOException("No reels available")
    }

    private fun request(path: String): JSONObject {
        val cookies = InstagramSession.cookieHeader()
        if (cookies.isNullOrEmpty() || !InstagramSession.isLoggedIn()) throw NotLoggedInException()

        val form = FormBody.Builder()
            .add("container_module", "clips_viewer_clips_tab")
            .add("page_size", "12")
            .add("include_feed_video", "true")
        maxId?.let { form.add("max_id", it) }

        val request = Request.Builder()
            .url("${InstagramSession.BASE_URL}/api/v1/$path")
            .post(form.build())
            .header("User-Agent", userAgent)
            .header("Cookie", cookies)
            .header("X-CSRFToken", InstagramSession.cookie("csrftoken").orEmpty())
            .header("X-IG-App-ID", WEB_APP_ID)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Origin", InstagramSession.BASE_URL)
            .header("Referer", "${InstagramSession.BASE_URL}/reels/")
            .header("Accept", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (body.contains("\"login_required\"") || response.code == 401) {
                throw NotLoggedInException()
            }
            if (!response.isSuccessful) throw IOException("$path: HTTP ${response.code}")
            if (!body.trimStart().startsWith("{")) throw IOException("$path: unexpected response")
            return JSONObject(body)
        }
    }

    private companion object {
        /** Public app id the instagram.com web client sends with every API call. */
        const val WEB_APP_ID = "936619743392459"
    }
}
