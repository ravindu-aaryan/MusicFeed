package com.musicfeed.app.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import com.musicfeed.app.instagram.Reel
import java.io.File

private fun dashDir(context: Context) = File(context.cacheDir, "dash")

fun clearDashCache(context: Context) {
    dashDir(context).deleteRecursively()
}

/**
 * Prefers the DASH manifest: its audio is a separate stream, so with video
 * disabled the player downloads only the audio. Writes to disk, so call off the
 * main thread.
 */
fun Reel.toMediaItem(context: Context): MediaItem {
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setArtworkUri(artworkUrl?.let(Uri::parse))
        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
        .setIsPlayable(true)
        .setIsBrowsable(false)
        .build()
    val builder = MediaItem.Builder().setMediaId(id).setMediaMetadata(metadata)

    if (dashManifest != null) {
        val file = File(dashDir(context).apply { mkdirs() }, "$id.mpd")
        file.writeText(dashManifest)
        builder.setUri(Uri.fromFile(file)).setMimeType(MimeTypes.APPLICATION_MPD)
    } else {
        builder.setUri(videoUrl)
    }
    return builder.build()
}
