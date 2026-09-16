package com.allsocial.sealclone

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * Compact, lightweight Material 3 download quality selection modal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniversalDownloadSheet(
    media: ParsedMediaData,
    onDismiss: () -> Unit,
    onWatchClick: (String) -> Unit,
    onStartDownload: (height: Int?, isAudio: Boolean, bitrate: String?) -> Unit,
    downloadProgress: Float? = null
) {
    // Default selection: Video 1080p or highest available
    var selectedIsAudio by remember { mutableStateOf(false) }
    var selectedAudioBitrate by remember { mutableStateOf("320K") }
    var selectedVideoHeight by remember {
        mutableStateOf(media.availableVideoHeights.firstOrNull { it == 1080 } ?: media.availableVideoHeights.firstOrNull() ?: 1080)
    }

    val videoResolutions = listOf(
        "4K Ultra HD (2160p)" to 2160,
        "2K Quad HD (1440p)" to 1440,
        "1080p Full HD" to 1080,
        "720p HD" to 720,
        "480p SD" to 480,
        "360p Low" to 360
    )

    val sizeEstimates = mapOf(
        2160 to "450 MB",
        1440 to "240 MB",
        1080 to "142 MB",
        720 to "85 MB",
        480 to "28 MB",
        360 to "15 MB"
    )

    val musicOptions = listOf(
        Triple("320K", "320 kbps (HQ Audio)", "9.2 MB"),
        Triple("192K", "192 kbps (Standard)", "4.8 MB"),
        Triple("128K", "128 kbps (Fast)", "3.3 MB")
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .width(36.dp)
                    .height(4.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Media Info Small Header Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (media.thumbnail.isNotBlank()) {
                        AsyncImage(
                            model = media.thumbnail,
                            contentDescription = "Cover",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = media.title,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${media.uploader} • ${media.duration}",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Surface(
                        onClick = { onWatchClick(media.webUrl) },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier
                            .height(28.dp)
                            .testTag("btn_preview_stream")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                "Preview",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // Compact Mode Selector: Video vs Audio
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    onClick = { selectedIsAudio = false },
                    modifier = Modifier.weight(1f).height(38.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = if (!selectedIsAudio) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, if (!selectedIsAudio) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Videocam,
                            contentDescription = null,
                            tint = if (!selectedIsAudio) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Video",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (!selectedIsAudio) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Surface(
                    onClick = { selectedIsAudio = true },
                    modifier = Modifier.weight(1f).height(38.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = if (selectedIsAudio) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, if (selectedIsAudio) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Headphones,
                            contentDescription = null,
                            tint = if (selectedIsAudio) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Audio (MP3)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (selectedIsAudio) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Quality Cards List
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (!selectedIsAudio) {
                    videoResolutions.forEach { (label, height) ->
                        val isSelected = selectedVideoHeight == height
                        CompactQualityCard(
                            title = label,
                            size = sizeEstimates[height] ?: "HD",
                            isSelected = isSelected,
                            onClick = { selectedVideoHeight = height },
                            tag = "btn_video_$height"
                        )
                    }
                } else {
                    musicOptions.forEach { (bitrate, name, size) ->
                        val isSelected = selectedAudioBitrate == bitrate
                        CompactQualityCard(
                            title = name,
                            size = size,
                            isSelected = isSelected,
                            onClick = { selectedAudioBitrate = bitrate },
                            tag = "btn_music_$bitrate"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Download Action Button
            val isDownloading = downloadProgress != null && downloadProgress in 0.1f..99.9f
            Button(
                onClick = {
                    if (!isDownloading) {
                        if (selectedIsAudio) {
                            onStartDownload(null, true, selectedAudioBitrate)
                        } else {
                            onStartDownload(selectedVideoHeight, false, null)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_sheet_download_confirm"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (isDownloading) {
                    val progressVal = (downloadProgress ?: 0f) / 100f
                    CircularProgressIndicator(
                        progress = { progressVal },
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.5.dp,
                        trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Downloading ${(downloadProgress ?: 0f).toInt()}%",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    val qualityText = if (selectedIsAudio) "Audio (${selectedAudioBitrate})" else "${selectedVideoHeight}p"
                    Text(
                        text = "Download $qualityText",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Compact, lightweight quality row card.
 */
@Composable
private fun CompactQualityCard(
    title: String,
    size: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag(tag),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        },
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                        )
                        .then(
                            if (!isSelected) {
                                Modifier.background(
                                    Color.Transparent,
                                    CircleShape
                                )
                            } else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(11.dp)
                        )
                    } else {
                        Surface(
                            shape = CircleShape,
                            border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.outlineVariant),
                            color = Color.Transparent,
                            modifier = Modifier.size(14.dp)
                        ) {}
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = title,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }

            Text(
                text = size,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
