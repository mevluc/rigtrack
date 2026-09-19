package com.rigtrack.ui

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.rigtrack.R
import com.rigtrack.recording.ReferenceVideoRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ReferenceVideoThumbnail(directory: File, modifier: Modifier = Modifier) {
    val video = remember(directory) { File(directory, ReferenceVideoRecorder.FILENAME) }
    val bitmap by produceState<Bitmap?>(null, video.absolutePath, video.lastModified()) {
        value = withContext(Dispatchers.IO) {
            if (!video.isFile) null else runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(video.absolutePath)
                    retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } finally {
                    retriever.release()
                }
            }.getOrNull()
        }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Default.Movie, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
fun ReferenceVideoPlayerScreen(a: MainActivity) {
    val directory = a.selected ?: return
    val video = remember(directory) { File(directory, ReferenceVideoRecorder.FILENAME) }
    if (!video.isFile) {
        Page { EmptyState(s(R.string.reference_video), s(R.string.reference_video_unavailable)) }
        return
    }
    val context = LocalContext.current
    val player = remember(video.absolutePath) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(video)))
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    Page {
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            AndroidView(
                factory = { PlayerView(it).apply {
                    this.player = player; useController = true; keepScreenOn = true
                    setShowPreviousButton(false); setShowNextButton(false)
                    setShowFastForwardButton(false); setShowRewindButton(false)
                } },
                update = { it.player = player },
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).testTag("reference_video_player"),
            )
        }
        Text(s(R.string.reference_video_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
