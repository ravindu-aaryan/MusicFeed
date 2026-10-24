package com.musicfeed.app.instagram

data class Reel(
    val id: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    /** DASH manifest with separate audio streams; lets the player fetch audio only. */
    val dashManifest: String?,
    /** Progressive MP4 (audio + video muxed); used when there is no manifest or it fails. */
    val videoUrl: String?,
    val durationMs: Long,
)
