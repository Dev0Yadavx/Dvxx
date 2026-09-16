package com.allsocial.sealclone

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class QuickDownloadOption(
    val id: String,
    val title: String,
    val label: String,
    val format: String,
    val isAudio: Boolean,
    val estSize: String,
    val bitrate: String? = null,
    val height: Int? = null
)

class QuickDownloadActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rawText = intent?.getStringExtra(Intent.EXTRA_TEXT) ?: intent?.dataString ?: ""
        val url = Regex("""(https?://[^\s]+)""").find(rawText)?.value?.trim()

        if (url.isNullOrEmpty()) {
            Toast.makeText(this, "No valid link found to download", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val prefs = getSharedPreferences("seal_app_prefs", Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("pref_dark_mode", true)

        setContent {
            val colorScheme = if (isDarkMode) darkColorScheme() else lightColorScheme()

            MaterialTheme(colorScheme = colorScheme) {
                QuickDownloadPopupDialog(
                    initialUrl = url,
                    onDismiss = { finish() },
                    onStartDownload = { chosenOption, mediaTitle, mediaThumb ->
                        val taskId = System.currentTimeMillis().toString()
                        DownloadForegroundService.enqueueDownload(
                            context = this@QuickDownloadActivity,
                            taskId = taskId,
                            url = url,
                            format = chosenOption.format,
                            isAudio = chosenOption.isAudio,
                            qualityLabel = chosenOption.label,
                            title = mediaTitle,
                            thumb = mediaThumb
                        )
                        Toast.makeText(
                            this@QuickDownloadActivity,
                            "Download started: ${chosenOption.label}",
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun QuickDownloadPopupDialog(
    initialUrl: String,
    onDismiss: () -> Unit,
    onStartDownload: (QuickDownloadOption, String, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isLoadingInfo by remember { mutableStateOf(true) }
    var parsedMedia by remember { mutableStateOf<ParsedMediaData?>(null) }

    // Standard 5 Bitrates
    val audioOptions = remember {
        listOf(
            QuickDownloadOption("a_320", "320 kbps", "320 kbps MP3", "bestaudio/best", true, "~9.5 MB", bitrate = "320K"),
            QuickDownloadOption("a_256", "256 kbps", "256 kbps MP3", "bestaudio/best", true, "~7.8 MB", bitrate = "256K"),
            QuickDownloadOption("a_192", "192 kbps", "192 kbps MP3", "bestaudio/best", true, "~5.8 MB", bitrate = "192K"),
            QuickDownloadOption("a_128", "128 kbps", "128 kbps MP3", "bestaudio/best", true, "~3.9 MB", bitrate = "128K"),
            QuickDownloadOption("a_64", "64 kbps", "64 kbps MP3", "bestaudio/best", true, "~2.1 MB", bitrate = "64K")
        )
    }

    // Dynamic Video Heights extracted by DownloaderEngine.inspectUrl
    val videoOptions = remember(parsedMedia) {
        val heights = parsedMedia?.availableVideoHeights ?: listOf(2160, 1440, 1080, 720, 480, 360)
        heights.map { h ->
            val label = when {
                h >= 2160 -> "4K ($h p)"
                h >= 1440 -> "2K ($h p)"
                h >= 1080 -> "1080p FHD"
                h >= 720 -> "720p HD"
                h >= 480 -> "480p SD"
                else -> "${h}p"
            }
            val est = when {
                h >= 2160 -> "~250 MB"
                h >= 1440 -> "~150 MB"
                h >= 1080 -> "~85 MB"
                h >= 720 -> "~45 MB"
                h >= 480 -> "~25 MB"
                else -> "~12 MB"
            }
            QuickDownloadOption(
                id = "v_$h",
                title = label,
                label = "$label MP4",
                format = "bestvideo[height<=$h]+bestaudio/best[height<=$h]/best",
                isAudio = false,
                estSize = est,
                height = h
            )
        }
    }

    var selectedOption by remember {
        mutableStateOf(
            QuickDownloadOption("v_720", "720p HD", "720p HD MP4", "bestvideo[height<=720]+bestaudio/best[height<=720]/best", false, "~45 MB", height = 720)
        )
    }

    LaunchedEffect(initialUrl) {
        scope.launch(Dispatchers.IO) {
            try {
                val media = DownloaderEngine.inspectUrl(initialUrl)
                withContext(Dispatchers.Main) {
                    parsedMedia = media
                    val defaultVideo = videoOptions.find { it.height == 720 || it.height == 1080 } ?: videoOptions.firstOrNull()
                    if (defaultVideo != null) {
                        selectedOption = defaultVideo
                    }
                    isLoadingInfo = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoadingInfo = false
                }
            }
        }
    }

    // Outer full-screen dim background (tapping dismisses)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
            .padding(horizontal = 16.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn() + scaleIn(initialScale = 0.92f)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { /* Prevent clicks from closing modal */ }
                    )
                    .testTag("card_quick_download_popup"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // Header with Close 'X' button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Quick Download",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("btn_close_quick_download")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Media Info Card with Art Cover
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val thumb = parsedMedia?.thumbnail ?: ""
                            if (thumb.isNotBlank()) {
                                AsyncImage(
                                    model = thumb,
                                    contentDescription = "Thumbnail",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isLoadingInfo) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = parsedMedia?.title ?: "Shared Media",
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                if (isLoadingInfo) {
                                    Text(
                                        text = "Analyzing dynamic formats...",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Text(
                                        text = "${parsedMedia?.uploader ?: "Social Media"} • Duration: ${parsedMedia?.duration ?: "00:00"}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Audio Qualities Section (All 5 bitrates)
                    Text(
                        text = "Audio Qualities (MP3 with Art & Tags)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        audioOptions.forEach { opt ->
                            val isSelected = selectedOption.id == opt.id
                            Surface(
                                onClick = { selectedOption = opt },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier
                                    .width(96.dp)
                                    .testTag("btn_opt_${opt.id}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = opt.title,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 11.sp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = opt.estSize,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Video Qualities Section (Dynamic extracted heights)
                    Text(
                        text = "Video Qualities (MP4 Dynamic Resolutions)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        videoOptions.forEach { opt ->
                            val isSelected = selectedOption.id == opt.id
                            Surface(
                                onClick = { selectedOption = opt },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier
                                    .width(100.dp)
                                    .testTag("btn_opt_${opt.id}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = opt.title,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 11.sp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = opt.estSize,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Primary Download Action Button
                    Button(
                        onClick = {
                            val title = parsedMedia?.title ?: "Downloaded Media"
                            val thumb = parsedMedia?.thumbnail ?: ""
                            onStartDownload(
                                selectedOption,
                                title,
                                thumb
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("btn_start_quick_download"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Download ${selectedOption.label}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
