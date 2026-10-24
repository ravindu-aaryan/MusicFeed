package com.musicfeed.app.playback

import android.app.PendingIntent
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.webkit.WebSettings
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.musicfeed.app.MainActivity
import com.musicfeed.app.instagram.NotLoggedInException
import com.musicfeed.app.instagram.ReelsApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Plays the reels feed as audio only, in the background, with a media
 * notification (play/pause, next, previous) and lock-screen controls. More reels
 * load automatically as the queue runs low.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    private var session: MediaSession? = null
    private lateinit var api: ReelsApi
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var loadJob: Job? = null

    /** Progressive MP4 fallbacks for items whose DASH manifest fails to play. */
    private val fallbacks = HashMap<String, String>()

    override fun onCreate() {
        super.onCreate()
        val userAgent = runCatching { WebSettings.getDefaultUserAgent(this) }
            .getOrDefault("Mozilla/5.0 (Linux; Android) MusicFeed")
        api = ReelsApi(userAgent)

        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setAllowCrossProtocolRedirects(true)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this, httpFactory)))
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        // Audio only: never fetch or decode video, and always take the best audio stream.
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
            .setForceHighestSupportedBitrate(true)
            .build()

        val audioSessionId = (getSystemService(AUDIO_SERVICE) as AudioManager).generateAudioSessionId()
        player.setAudioSessionId(audioSessionId)
        AudioEffects.attach(this, audioSessionId)

        player.addListener(PlayerListener())

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setCallback(SessionCallback())
            .setSessionActivity(openApp)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        AudioEffects.release()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    /** Throws away the current queue and starts a fresh feed. */
    private fun refresh() {
        loadJob?.cancel()
        loadJob = null
        api.reset()
        fallbacks.clear()
        loadMore(replace = true)
    }

    private fun loadMore(replace: Boolean = false) {
        if (loadJob?.isActive == true) return
        FeedStatus.set(FeedStatus.State.Loading)
        loadJob = scope.launch {
            try {
                val (reels, items) = withContext(Dispatchers.IO) {
                    if (replace) clearDashCache(this@PlaybackService)
                    val reels = api.nextPage()
                    reels to reels.map { it.toMediaItem(this@PlaybackService) }
                }
                reels.forEach { reel ->
                    if (reel.dashManifest != null && reel.videoUrl != null) fallbacks[reel.id] = reel.videoUrl
                }
                if (replace || player.mediaItemCount == 0) {
                    player.setMediaItems(items)
                    player.prepare()
                    player.play()
                } else {
                    val ended = player.playbackState == Player.STATE_ENDED
                    player.addMediaItems(items)
                    if (ended) {
                        player.seekToNextMediaItem()
                        player.prepare()
                    }
                }
                FeedStatus.set(FeedStatus.State.Idle)
            } catch (e: NotLoggedInException) {
                FeedStatus.set(FeedStatus.State.Error(e.message.orEmpty(), needsLogin = true))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                FeedStatus.set(FeedStatus.State.Error("Couldn't load reels: ${e.message}"))
            }
        }
    }

    private fun maybeLoadMore() {
        if (player.mediaItemCount == 0) return
        if (player.mediaItemCount - player.currentMediaItemIndex <= PREFETCH_THRESHOLD) loadMore()
    }

    private inner class PlayerListener : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = maybeLoadMore()

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) maybeLoadMore()
        }

        override fun onPlayerError(error: PlaybackException) {
            val index = player.currentMediaItemIndex
            val item = player.currentMediaItem
            val fallback = item?.let { fallbacks.remove(it.mediaId) }
            when {
                item != null && fallback != null -> {
                    player.replaceMediaItem(index, item.buildUpon().setUri(fallback).setMimeType(null).build())
                    player.prepare()
                }
                player.hasNextMediaItem() -> {
                    // Signed CDN links expire; skip what can't be played.
                    player.seekToNextMediaItem()
                    player.prepare()
                }
                else -> {
                    FeedStatus.set(FeedStatus.State.Error("Playback failed: ${error.errorCodeName}"))
                    loadMore()
                }
            }
        }
    }

    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(COMMAND_REFRESH, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == COMMAND_REFRESH) {
                refresh()
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    companion object {
        const val COMMAND_REFRESH = "com.musicfeed.app.REFRESH"
        private const val PREFETCH_THRESHOLD = 3
    }
}
