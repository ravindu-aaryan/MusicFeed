package com.musicfeed.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import coil.compose.AsyncImage
import com.musicfeed.app.instagram.InstagramSession
import com.musicfeed.app.playback.FeedStatus
import com.musicfeed.app.playback.PlaybackService
import kotlinx.coroutines.delay

private data class QueueEntry(val index: Int, val metadata: MediaMetadata)

private data class PlayerUi(
    val metadata: MediaMetadata?,
    val isPlaying: Boolean,
    val isBuffering: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val upNext: List<QueueEntry>,
)

private fun MediaController.snapshot(): PlayerUi {
    val current = currentMediaItemIndex
    val upNext = (current + 1 until mediaItemCount).map { QueueEntry(it, getMediaItemAt(it).mediaMetadata) }
    return PlayerUi(
        metadata = currentMediaItem?.mediaMetadata,
        isPlaying = isPlaying,
        isBuffering = playbackState == Player.STATE_BUFFERING,
        positionMs = currentPosition.coerceAtLeast(0),
        durationMs = duration.takeIf { it > 0 } ?: 0,
        upNext = upNext,
    )
}

private fun MediaController.refreshFeed() {
    sendCustomCommand(SessionCommand(PlaybackService.COMMAND_REFRESH, Bundle.EMPTY), Bundle.EMPTY)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(onOpenEqualizer: () -> Unit, onLoggedOut: () -> Unit) {
    val context = LocalContext.current
    val controller = rememberMediaController()
    val status by FeedStatus.state.collectAsState()

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Re-read the controller on every player event, and twice a second for the progress bar.
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(controller) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                tick++
            }
        }
        controller?.addListener(listener)
        onDispose { controller?.removeListener(listener) }
    }
    LaunchedEffect(controller) {
        val c = controller ?: return@LaunchedEffect
        if (c.mediaItemCount == 0) c.refreshFeed()
        while (true) {
            delay(500)
            tick++
        }
    }
    val ui = remember(tick, controller) { controller?.snapshot() }

    fun logout() {
        controller?.run {
            stop()
            clearMediaItems()
        }
        InstagramSession.logout()
        FeedStatus.set(FeedStatus.State.Idle)
        onLoggedOut()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MusicFeed", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { controller?.refreshFeed() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Fresh feed")
                    }
                    IconButton(onClick = onOpenEqualizer) {
                        Icon(Icons.Default.Equalizer, contentDescription = "Equalizer")
                    }
                    IconButton(onClick = ::logout) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Log out")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                StatusBanner(
                    status = status,
                    onRetry = { controller?.refreshFeed() },
                    onLogin = ::logout,
                )
            }
            item {
                NowPlaying(
                    ui = ui,
                    onPlayPause = { controller?.run { if (isPlaying) pause() else play() } },
                    onPrevious = { controller?.seekToPrevious() },
                    onNext = { controller?.seekToNext() },
                    onSeek = { controller?.seekTo(it) },
                )
            }
            val upNext = ui?.upNext.orEmpty()
            if (upNext.isNotEmpty()) {
                item {
                    Text(
                        "Up next",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, top = 24.dp, bottom = 8.dp),
                    )
                }
                items(upNext, key = { it.index }) { entry ->
                    QueueRow(entry.metadata) { controller?.seekToDefaultPosition(entry.index) }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun StatusBanner(status: FeedStatus.State, onRetry: () -> Unit, onLogin: () -> Unit) {
    when (status) {
        FeedStatus.State.Idle -> Unit
        FeedStatus.State.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
        is FeedStatus.State.Error -> Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(status.message, color = MaterialTheme.colorScheme.onErrorContainer)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (status.needsLogin) {
                        TextButton(onClick = onLogin) { Text("Log in again") }
                    } else {
                        TextButton(onClick = onRetry) { Text("Retry") }
                    }
                }
            }
        }
    }
}

@Composable
private fun NowPlaying(
    ui: PlayerUi?,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    val metadata = ui?.metadata
    Column(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val placeholder = rememberVectorPainter(Icons.Default.MusicNote)
            AsyncImage(
                model = metadata?.artworkUri,
                contentDescription = null,
                placeholder = placeholder,
                error = placeholder,
                fallback = placeholder,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (ui?.isBuffering == true) CircularProgressIndicator()
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = metadata?.title?.toString() ?: "Loading your reels…",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = metadata?.artist?.toString().orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(16.dp))
        SeekBar(ui?.positionMs ?: 0, ui?.durationMs ?: 0, onSeek)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Default.SkipPrevious, "Previous", Modifier.size(40.dp))
            }
            FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(80.dp)) {
                Icon(
                    if (ui?.isPlaying == true) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (ui?.isPlaying == true) "Pause" else "Play",
                    modifier = Modifier.size(44.dp),
                )
            }
            IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Default.SkipNext, "Next", Modifier.size(40.dp))
            }
        }
    }
}

@Composable
private fun SeekBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val fraction = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f

    Column {
        Slider(
            value = if (dragging) dragValue else fraction.coerceIn(0f, 1f),
            onValueChange = {
                dragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                onSeek((dragValue * durationMs).toLong())
                dragging = false
            },
            enabled = durationMs > 0,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val shown = if (dragging) (dragValue * durationMs).toLong() else positionMs
            Text(formatTime(shown), style = MaterialTheme.typography.labelMedium)
            Text(formatTime(durationMs), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun QueueRow(metadata: MediaMetadata, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val placeholder = rememberVectorPainter(Icons.Default.MusicNote)
        AsyncImage(
            model = metadata.artworkUri,
            contentDescription = null,
            placeholder = placeholder,
            error = placeholder,
            fallback = placeholder,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                metadata.title?.toString().orEmpty(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                metadata.artist?.toString().orEmpty(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
