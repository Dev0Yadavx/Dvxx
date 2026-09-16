package com.allsocial.sealclone

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
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

    data class QualityChoice(
        val label: String,
        val isAudio: Boolean,
        val height: Int? = null,
        val bitrate: String? = null,
        val sizeEstimate: String = ""
    )

    var selectedChoice by remember {
        mutableStateOf(QualityChoice(label = "1080p Full HD", isAudio = false, height = 1080, sizeEstimate = "~85 MB"))
    }

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

        // Play stream preview button
        OutlinedButton(
            onClick = { onPlayStream(video.url) },
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Play Stream Preview",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ================= 1. AUDIO QUALITIES (UPER AUDIO) - LINE BY LINE =================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Headphones,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Audio Qualities (MP3)",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        val audioQualities = listOf(
            Triple("320K", "320 kbps (Studio Quality)", "~9.5 MB"),
            Triple("256K", "256 kbps (High Quality)", "~7.5 MB"),
            Triple("192K", "192 kbps (Standard Quality)", "~5.8 MB"),
            Triple("128K", "128 kbps (Compact)", "~3.9 MB"),
            Triple("64K", "64 kbps (Low Data Saver)", "~2.1 MB")
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            audioQualities.forEach { (qualityKey, label, sizeEst) ->
                val isSelected = selectedChoice.isAudio && selectedChoice.bitrate == qualityKey
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedChoice = QualityChoice(
                                label = "$label MP3",
                                isAudio = true,
                                bitrate = qualityKey,
                                sizeEstimate = sizeEst
                            )
                        }
                        .testTag("btn_audio_$qualityKey"),
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                        Text(
                            text = sizeEst,
                            fontSize = 11.sp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ================= 2. VIDEO QUALITIES (NICHE VIDEO) - LINE BY LINE =================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Videocam,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Video Qualities (MP4)",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        val videoQualities = listOf(
            Triple("4K Ultra HD (2160p)", 2160, "~350 MB"),
            Triple("2K Quad HD (1440p)", 1440, "~180 MB"),
            Triple("1080p Full HD", 1080, "~85 MB"),
            Triple("720p HD", 720, "~42 MB"),
            Triple("480p SD", 480, "~26 MB"),
            Triple("360p Low", 360, "~18 MB")
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            videoQualities.forEach { (title, height, sizeEst) ->
                val isSelected = !selectedChoice.isAudio && selectedChoice.height == height
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedChoice = QualityChoice(
                                label = title,
                                isAudio = false,
                                height = height,
                                sizeEstimate = sizeEst
                            )
                        }
                        .testTag("video_quality_row_$height"),
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = title,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }

                        Text(
                            text = sizeEst,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ================= 3. TAP TO DOWNLOAD NOW (FULL ROUNDED M3 BUTTON) =================
        Button(
            onClick = {
                onStartDownload(
                    video.url,
                    selectedChoice.height,
                    selectedChoice.isAudio,
                    selectedChoice.bitrate,
                    video.title,
                    video.thumbnail
                )
                onDismissRequest()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("btn_download_now_bottom_sheet"),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp)
        ) {
            Icon(
                Icons.Default.Download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Tap To Download Now • ${selectedChoice.label} (${selectedChoice.sizeEstimate})",
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
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
