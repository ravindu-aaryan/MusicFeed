package com.musicfeed.app.instagram

import org.json.JSONArray
import org.json.JSONObject

/**
 * Instagram's feed responses differ by endpoint and change over time, so rather
 * than binding to one exact shape we walk the JSON and pick up every media object
 * that carries a playable video.
 */
object MediaParser {

    fun findReels(root: JSONObject): List<Reel> {
        val out = ArrayList<Reel>()
        walk(root, out)
        return out.distinctBy { it.id }
    }

    private fun walk(node: Any?, out: MutableList<Reel>) {
        when (node) {
            is JSONObject -> {
                if (node.has("video_versions") || node.has("video_dash_manifest")) {
                    toReel(node)?.let(out::add)
                    return
                }
                for (key in node.keys()) walk(node.opt(key), out)
            }
            is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i), out)
        }
    }

    private fun toReel(media: JSONObject): Reel? {
        if (media.has("ad_id") || media.str("product_type") == "ad") return null
        if (media.has("has_audio") && !media.optBoolean("has_audio", true)) return null

        val id = media.str("pk") ?: media.str("id") ?: return null
        val dash = media.str("video_dash_manifest")
        val videoUrl = media.optJSONArray("video_versions")?.optJSONObject(0)?.str("url")
        if (dash == null && videoUrl == null) return null

        val clips = media.optJSONObject("clips_metadata")
        val music = clips?.optJSONObject("music_info")?.optJSONObject("music_asset_info")
        val original = clips?.optJSONObject("original_sound_info")
        val username = media.optJSONObject("user")?.str("username")
        val caption = media.optJSONObject("caption")?.str("text")
            ?.lineSequence()?.firstOrNull { it.isNotBlank() }?.take(60)

        val title = music?.str("title")
            ?: original?.str("original_audio_title")
            ?: caption
            ?: username?.let { "Reel by @$it" }
            ?: "Instagram reel"
        val artist = music?.str("display_artist")
            ?: original?.optJSONObject("ig_artist")?.str("username")
            ?: username
            ?: "Instagram"
        val artwork = music?.str("cover_artwork_uri")
            ?: media.optJSONObject("image_versions2")
                ?.optJSONArray("candidates")?.optJSONObject(0)?.str("url")

        return Reel(
            id = id,
            title = title,
            artist = artist,
            artworkUrl = artwork,
            dashManifest = dash,
            videoUrl = videoUrl,
            durationMs = (media.optDouble("video_duration", 0.0) * 1000).toLong(),
        )
    }
}

/** Like optString, but returns null for missing, JSON null, or empty values. */
internal fun JSONObject.str(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }
