package com.allsocial.sealclone

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

/**
 * DownloadOptionsSheet composable that displays UI elements for:
 * 1. Watch In-App Preview Button
 * 2. MP3 Audio Qualities (320K, 192K, 64K)
 * 3. MP4 Video Qualities (360p, 720p, 1080p, 2K, 4K)
 * Hosted directly inside the ModalBottomSheet.
 */
@Composable
fun DownloadOptionsSheet(
    item: SearchItem,
    onDismissRequest: () -> Unit,
    onPlayStream: (url: String) -> Unit,
    onDownloadAudio: (url: String, bitrate: String, title: String, thumbnail: String) -> Unit,
    onDownloadVideo: (url: String, resolution: String, formatSelector: String, title: String, thumbnail: String) -> Unit,
    modifier: Modifier = Modifier,
    vm: SealViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val video = item

    // Observe real-time download state from ViewModel
    val activeTasks by vm.activeTasks.collectAsState()
    val activeTask = activeTasks.values.firstOrNull {
        it.title == video.title || it.id == video.id || (video.title.isNotBlank() && it.title.contains(video.title))
    } ?: activeTasks.values.lastOrNull()

    val isDownloading = activeTask != null
    val progressFloat = ((activeTask?.progress ?: 0f) / 100f).coerceIn(0f, 1f)

    fun onStartDownload(
        targetUrl: String,
        resolutionHeight: Int?,
        isAudio: Boolean,
        audioBitrate: String?,
        title: String,
        thumbnail: String
    ) {
        if (isAudio) {
            onDownloadAudio(targetUrl, audioBitrate ?: "320K", title, thumbnail)
        } else {
            val h = resolutionHeight ?: 1080
            onDownloadVideo(targetUrl, "${h}p", h.toString(), title, thumbnail)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Media Header Info Card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (video.thumbnail.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .size(width = 110.dp, height = 70.dp)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    AsyncImage(
                        model = video.thumbnail,
                        contentDescription = video.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (video.duration.isNotBlank()) {
                        Text(
                            text = video.duration,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                                .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = video.uploader,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 1. WATCH / PLAY BUTTON (YouTube App par jane ke bajaye in-app chalega)
        OutlinedButton(
            onClick = {
                scope.launch {
                    Toast.makeText(context, "Extracting player stream...", Toast.LENGTH_SHORT).show()
                    val streamUrl = DownloaderEngine.extractDirectStreamUrl(video.url)
                    onPlayStream(streamUrl)
                    onDismissRequest()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("btn_watch_stream"),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Watch In-App Preview", color = MaterialTheme.colorScheme.onSurface)
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. MP3 AUDIO BUTTONS (320, 192, 64)
        Text(
            "Audio Qualities (MP3)",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("320K", "192K", "64K").forEach { bitrate ->
                Button(
                    onClick = {
                        onStartDownload(video.url, null, true, bitrate, video.title, video.thumbnail)
                        onDismissRequest()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_audio_$bitrate"),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    Text(
                        bitrate.replace("K", " kbps"),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. VIDEO BUTTONS (360p, 720p, 1080p, 2K, 4K)
        Text(
            "Video Qualities (MP4)",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        val videoQualities = listOf(
            "360p SD" to 360,
            "720p HD" to 720,
            "1080p Full HD" to 1080,
            "2K Quad HD" to 1440,
            "4K Ultra HD" to 2160
        )

        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            videoQualities.forEach { (label, height) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .testTag("video_quality_row_$height"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        label,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Button(
                        onClick = {
                            onStartDownload(video.url, height, false, null, video.title, video.thumbnail)
                            onDismissRequest()
                        },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
                        modifier = Modifier.testTag("btn_video_save_$height")
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (isDownloading) {
            Spacer(modifier = Modifier.height(16.dp))

            // Real-Time Download Progress (observing ViewModel activeTasks state)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .padding(14.dp)
                    .testTag("download_progress_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Downloading in Progress...",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = "${(progressFloat * 100).toInt()}%",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("download_progress_text")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { progressFloat },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .testTag("download_progress_indicator"),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surface
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = activeTask?.speed?.ifBlank { "Processing stream..." } ?: "Downloading...",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        modifier = Modifier.testTag("download_speed_text")
                    )
                    Text(
                        text = "Real-time sync",
                        color = Color.Gray,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

/**
 * Material 3 ModalBottomSheet hosting the DownloadOptionsSheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaDownloadBottomSheet(
    item: SearchItem,
    onDismissRequest: () -> Unit,
    onPlayStream: (url: String) -> Unit,
    onDownloadAudio: (url: String, bitrate: String, title: String, thumbnail: String) -> Unit,
    onDownloadVideo: (url: String, resolution: String, formatSelector: String, title: String, thumbnail: String) -> Unit,
    modifier: Modifier = Modifier,
    vm: SealViewModel = viewModel()
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        },
        modifier = modifier.testTag("media_download_bottom_sheet")
    ) {
        DownloadOptionsSheet(
            item = item,
            onDismissRequest = onDismissRequest,
            onPlayStream = onPlayStream,
            onDownloadAudio = onDownloadAudio,
            onDownloadVideo = onDownloadVideo,
            modifier = Modifier,
            vm = vm
        )
    }
}
