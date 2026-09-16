package com.allsocial.sealclone

import android.content.Context
import android.content.Intent
import android.os.Build
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
    val estSize: String
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
    var mediaItem by remember {
        mutableStateOf(
            SearchItem(
                id = "shared",
                title = "Shared Media",
                uploader = "",
                duration = "",
                thumbnail = "",
                url = initialUrl
            )
        )
    }

    val audioOptions = remember {
        listOf(
            QuickDownloadOption(
                id = "audio_320",
                title = "320 kbps",
                label = "MP3 (320kbps)",
                format = "bestaudio/best",
                isAudio = true,
                estSize = "~9.5 MB"
            ),
            QuickDownloadOption(
                id = "audio_192",
                title = "192 kbps",
                label = "MP3 (192kbps)",
                format = "bestaudio/best",
                isAudio = true,
                estSize = "~5.8 MB"
            ),
            QuickDownloadOption(
                id = "audio_64",
                title = "64 kbps",
                label = "MP3 (64kbps)",
                format = "worstaudio/worst",
                isAudio = true,
                estSize = "~2.1 MB"
            )
        )
    }

    val videoOptions = remember {
        listOf(
            QuickDownloadOption(
                id = "video_1080",
                title = "1080p FHD",
                label = "1080p MP4",
                format = "bestvideo[height<=1080]+bestaudio/best[height<=1080]/best",
                isAudio = false,
                estSize = "~85 MB"
            ),
            QuickDownloadOption(
                id = "video_720",
                title = "720p HD",
                label = "720p MP4",
                format = "bestvideo[height<=720]+bestaudio/best[height<=720]/best",
                isAudio = false,
                estSize = "~45 MB"
            ),
            QuickDownloadOption(
                id = "video_480",
                title = "480p SD",
                label = "480p MP4",
                format = "bestvideo[height<=480]+bestaudio/best[height<=480]/best",
                isAudio = false,
                estSize = "~25 MB"
            ),
            QuickDownloadOption(
                id = "video_360",
                title = "360p Low",
                label = "360p MP4",
                format = "bestvideo[height<=360]+bestaudio/best[height<=360]/best",
                isAudio = false,
                estSize = "~12 MB"
            )
        )
    }

    var selectedOption by remember { mutableStateOf(videoOptions[1]) } // Default: 720p HD

    LaunchedEffect(initialUrl) {
        scope.launch(Dispatchers.IO) {
            try {
                val results = DownloaderEngine.searchOrFetch(initialUrl)
                val first = results.firstOrNull()
                if (first != null) {
                    withContext(Dispatchers.Main) {
                        mediaItem = first
                        isLoadingInfo = false
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        isLoadingInfo = false
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoadingInfo = false
                }
            }
        }
    }

    // Outer full-screen dim background (tappings dismisses without opening app)
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
        // Modal Card Popup
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
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
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
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
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

                    // Media Info Card
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (mediaItem.thumbnail.isNotEmpty()) {
                                AsyncImage(
                                    model = mediaItem.thumbnail,
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
                                    text = mediaItem.title,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                if (isLoadingInfo) {
                                    Text(
                                        text = "Fetching media details...",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else if (mediaItem.duration.isNotEmpty()) {
                                    Text(
                                        text = "Duration: ${mediaItem.duration}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Text(
                                        text = "Ready to download",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Audio Qualities Section
                    Text(
                        text = "Audio Qualities (MP3)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        audioOptions.forEach { opt ->
                            val isSelected = selectedOption.id == opt.id
                            Surface(
                                onClick = { selectedOption = opt },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_opt_${opt.id}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
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

                    // Video Qualities Section
                    Text(
                        text = "Video Qualities (MP4)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        videoOptions.forEach { opt ->
                            val isSelected = selectedOption.id == opt.id
                            Surface(
                                onClick = { selectedOption = opt },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_opt_${opt.id}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
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
                            onStartDownload(
                                selectedOption,
                                mediaItem.title,
                                mediaItem.thumbnail
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
