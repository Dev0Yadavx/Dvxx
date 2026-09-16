package com.allsocial.sealclone

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
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
 * Modal bottom sheet for stream preview and multi-format selection.
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
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(44.dp)
                    .height(4.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            ) {}
        }
    ) {
        DownloadOptionsSheet(
            item = item,
            onDismissRequest = onDismissRequest,
            onPlayStream = onPlayStream,
            onDownloadAudio = onDownloadAudio,
            onDownloadVideo = onDownloadVideo,
            modifier = modifier,
            vm = vm
        )
    }
}

/**
 * Premium Material 3 expensive format selection sheet
 * structured with "Download video as", "Music", "Video", and full-width "Download" action.
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

    var isAudio by remember { mutableStateOf(false) }
    var selectedBitrate by remember { mutableStateOf("320K") }
    var selectedResolution by remember { mutableStateOf(1080) }
    var showAllFormats by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Top Header title: "Download video as"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Download video as",
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp
            )

            Surface(
                onClick = { onPlayStream(video.url) },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.height(32.dp).testTag("btn_sheet_play_preview")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Preview",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // Media Info Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (video.thumbnail.isNotBlank()) {
                    AsyncImage(
                        model = video.thumbnail,
                        contentDescription = video.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = video.title,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${video.uploader.ifBlank { "Media" }} • ${video.duration}",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // ================= 1. MUSIC SECTION =================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Music",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        val musicList = listOf(
            Triple("128K", "Fast (128k)", "3.3 MB"),
            Triple("192K", "Classic MP3 (192k)", "4.8 MB"),
            Triple("320K", "HQ Audio (320k)", "9.2 MB")
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            musicList.forEach { (bitrate, label, size) ->
                val isSelected = isAudio && selectedBitrate == bitrate
                SheetQualityOptionRow(
                    icon = Icons.Default.MusicNote,
                    title = label,
                    size = size,
                    isSelected = isSelected,
                    onClick = {
                        isAudio = true
                        selectedBitrate = bitrate
                    },
                    testTag = "btn_sheet_audio_$bitrate"
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ================= 2. VIDEO SECTION =================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Video",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        val standardVideoList = listOf(
            Triple(360, "Fast (360p)", "15.6 MB"),
            Triple(480, "Standard (480p)", "28.4 MB"),
            Triple(720, "High quality (720p)", "85.3 MB"),
            Triple(1080, "Full HD (1080p)", "142.0 MB")
        )

        val extendedVideoList = listOf(
            Triple(1440, "2K Quad HD (1440p)", "240.0 MB"),
            Triple(2160, "4K Ultra HD (2160p)", "450.0 MB")
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            standardVideoList.forEach { (height, label, size) ->
                val isSelected = !isAudio && selectedResolution == height
                SheetQualityOptionRow(
                    icon = Icons.Default.PlayArrow,
                    title = label,
                    size = size,
                    isSelected = isSelected,
                    onClick = {
                        isAudio = false
                        selectedResolution = height
                    },
                    testTag = "btn_sheet_video_$height"
                )
            }

            AnimatedVisibility(
                visible = showAllFormats,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    extendedVideoList.forEach { (height, label, size) ->
                        val isSelected = !isAudio && selectedResolution == height
                        SheetQualityOptionRow(
                            icon = Icons.Default.PlayArrow,
                            title = label,
                            size = size,
                            isSelected = isSelected,
                            onClick = {
                                isAudio = false
                                selectedResolution = height
                            },
                            testTag = "btn_sheet_video_$height"
                        )
                    }
                }
            }

            // More formats row toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showAllFormats = !showAllFormats }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "More formats",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (showAllFormats) "Collapse" else "All",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = if (showAllFormats) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ================= 3. DOWNLOAD BUTTON =================
        val activeTasks by vm.activeTasks.collectAsState()
        val activeTask = activeTasks.values.firstOrNull { it.title == video.title || it.id == video.id }
        val currentProgress = activeTask?.progress
        val isDownloading = currentProgress != null && currentProgress in 0.1f..99.9f

        Button(
            onClick = {
                if (!isDownloading) {
                    onDismissRequest()
                    if (isAudio) {
                        onDownloadAudio(video.url, selectedBitrate, video.title, video.thumbnail)
                    } else {
                        onDownloadVideo(video.url, "${selectedResolution}p", selectedResolution.toString(), video.title, video.thumbnail)
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("btn_sheet_download_confirm"),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) {
            if (isDownloading) {
                val p = (currentProgress ?: 0f) / 100f
                CircularProgressIndicator(
                    progress = { p },
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 3.dp,
                    trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Downloading ${(currentProgress ?: 0f).toInt()}%",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Download",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Download",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
private fun SheetQualityOptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    size: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        },
        border = BorderStroke(
            width = if (isSelected) 1.8.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = title,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = size,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.width(10.dp))

                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(13.dp)
                        )
                    } else {
                        Surface(
                            shape = CircleShape,
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
                            color = Color.Transparent,
                            modifier = Modifier.size(18.dp)
                        ) {}
                    }
                }
            }
        }
    }
}
