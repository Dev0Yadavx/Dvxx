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
            var dynamicColor by remember { mutableStateOf<Color?>(null) }
            val colorScheme = remember(isDarkMode, dynamicColor) {
                if (dynamicColor != null) {
                    DynamicThemeEngine.buildDynamicColorScheme(dynamicColor!!, isDarkMode)
                } else if (isDarkMode) {
                    darkColorScheme()
                } else {
                    lightColorScheme()
                }
            }

            MaterialTheme(colorScheme = colorScheme) {
                DownloadPopupDialog(
                    initialUrl = url,
                    onColorExtracted = { dynamicColor = it },
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
fun DownloadPopupDialog(
    initialUrl: String,
    onColorExtracted: (Color) -> Unit = {},
    onDismiss: () -> Unit,
    onStartDownload: (QuickDownloadOption, String, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isLoadingInfo by remember { mutableStateOf(true) }
    var parsedMedia by remember { mutableStateOf<ParsedMediaData?>(null) }

    val audioOptions = remember {
        listOf(
            QuickDownloadOption("a_128", "Fast (128k)", "128k MP3", "bestaudio/best", true, "3.3 MB", bitrate = "128K"),
            QuickDownloadOption("a_192", "Classic MP3 (192k)", "192k MP3", "bestaudio/best", true, "4.8 MB", bitrate = "192K"),
            QuickDownloadOption("a_320", "HQ Audio (320k)", "320k MP3", "bestaudio/best", true, "9.2 MB", bitrate = "320K")
        )
    }

    val standardVideoOptions = remember(parsedMedia) {
        val heights = parsedMedia?.availableVideoHeights ?: listOf(1080, 720, 480, 360)
        val defaultList = listOf(
            Triple(360, "Fast (360p)", "15.6 MB"),
            Triple(480, "Standard (480p)", "28.4 MB"),
            Triple(720, "High quality (720p)", "85.3 MB"),
            Triple(1080, "Full HD (1080p)", "142.0 MB")
        )
        defaultList.map { (h, name, est) ->
            QuickDownloadOption(
                id = "v_$h",
                title = name,
                label = "$h p MP4",
                format = "bestvideo[height<=$h]+bestaudio/best[height<=$h]/best",
                isAudio = false,
                estSize = est,
                height = h
            )
        }
    }

    var selectedOption by remember {
        mutableStateOf(
            QuickDownloadOption("v_1080", "Full HD (1080p)", "1080p MP4", "bestvideo[height<=1080]+bestaudio/best[height<=1080]/best", false, "142.0 MB", height = 1080)
        )
    }

    LaunchedEffect(initialUrl) {
        scope.launch(Dispatchers.IO) {
            try {
                val media = DownloaderEngine.inspectUrl(initialUrl)
                if (media.thumbnail.isNotBlank()) {
                    val extracted = DynamicThemeEngine.extractThemeFromThumbnail(context, media.thumbnail)
                    if (extracted != null) {
                        withContext(Dispatchers.Main) {
                            onColorExtracted(extracted)
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    parsedMedia = media
                    val defaultVideo = standardVideoOptions.find { it.height == 1080 || it.height == 720 } ?: standardVideoOptions.lastOrNull()
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
            .padding(horizontal = 20.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn() + scaleIn(initialScale = 0.92f)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { /* Do nothing */ }
                    )
                    .testTag("card_download_popup"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Header: "Quick Download" & Close
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Quick Download",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(28.dp)
                                .testTag("btn_close_download_popup")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Media Info Card (Compact)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val thumb = parsedMedia?.thumbnail ?: ""
                            if (thumb.isNotBlank()) {
                                AsyncImage(
                                    model = thumb,
                                    contentDescription = "Thumbnail",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isLoadingInfo) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = parsedMedia?.title ?: "Inspecting Media...",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.5.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(1.dp))
                                Text(
                                    text = "${parsedMedia?.uploader ?: "Media"} • Duration: ${parsedMedia?.duration ?: "--:--"}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Square Mode Toggle: Video vs Audio (Shows only 1 section at a time, drastically reducing height)
                    var isAudioSelected by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!isAudioSelected) {
                            Button(
                                onClick = {
                                    isAudioSelected = false
                                    val vid = standardVideoOptions.firstOrNull()
                                    if (vid != null) selectedOption = vid
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .testTag("btn_quick_toggle_video"),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("Video (MP4)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    isAudioSelected = false
                                    val vid = standardVideoOptions.firstOrNull()
                                    if (vid != null) selectedOption = vid
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .testTag("btn_quick_toggle_video"),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("Video (MP4)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                        }

                        if (isAudioSelected) {
                            Button(
                                onClick = {
                                    isAudioSelected = true
                                    val aud = audioOptions.firstOrNull()
                                    if (aud != null) selectedOption = aud
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .testTag("btn_quick_toggle_audio"),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("Music (MP3)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    isAudioSelected = true
                                    val aud = audioOptions.firstOrNull()
                                    if (aud != null) selectedOption = aud
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .testTag("btn_quick_toggle_audio"),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("Music (MP3)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                        }
                    }

                    // Quality Options (Only shows the active category to keep height compact)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        val currentOptions = if (isAudioSelected) audioOptions else standardVideoOptions
                        currentOptions.forEach { opt ->
                            val isSelected = selectedOption.id == opt.id
                            PopupQualityRow(
                                icon = if (isAudioSelected) Icons.Default.MusicNote else Icons.Default.PlayArrow,
                                title = opt.title,
                                size = opt.estSize,
                                isSelected = isSelected,
                                onClick = { selectedOption = opt }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Download Action Button (Compact & Square)
                    var isDownloading by remember { mutableStateOf(false) }

                    Button(
                        onClick = {
                            if (!isDownloading) {
                                isDownloading = true
                                val title = parsedMedia?.title ?: "Downloaded Media"
                                val thumb = parsedMedia?.thumbnail ?: ""
                                onStartDownload(
                                    selectedOption,
                                    title,
                                    thumb
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("btn_start_download_popup"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        if (isDownloading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.2.dp,
                                trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Starting...",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Download",
                                modifier = Modifier.size(18.dp)
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
}

@Composable
private fun PopupQualityRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    size: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
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
                .padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = title,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.5.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = size,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(RoundedCornerShape(4.dp))
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
                            modifier = Modifier.size(11.dp)
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.outlineVariant),
                            color = Color.Transparent,
                            modifier = Modifier.size(14.dp)
                        ) {}
                    }
                }
            }
        }
    }
}
