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
                        onClick = { /* Do nothing */ }
                    )
                    .testTag("card_download_popup"),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Header: "Download video as" & Close
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Download video as",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("btn_close_download_popup")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Media Info Card
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
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
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isLoadingInfo) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(22.dp),
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
                                    text = parsedMedia?.title ?: "Inspecting Media...",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.5.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${parsedMedia?.uploader ?: "Media"} • Duration: ${parsedMedia?.duration ?: "--:--"}",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 1. Music Section
                    Text(
                        text = "Music",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        audioOptions.forEach { opt ->
                            val isSelected = selectedOption.id == opt.id
                            PopupQualityRow(
                                icon = Icons.Default.MusicNote,
                                title = opt.title,
                                size = opt.estSize,
                                isSelected = isSelected,
                                onClick = { selectedOption = opt }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 2. Video Section
                    Text(
                        text = "Video",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        standardVideoOptions.forEach { opt ->
                            val isSelected = selectedOption.id == opt.id
                            PopupQualityRow(
                                icon = Icons.Default.PlayArrow,
                                title = opt.title,
                                size = opt.estSize,
                                isSelected = isSelected,
                                onClick = { selectedOption = opt }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Download Action Button
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
                            .height(54.dp)
                            .testTag("btn_start_download_popup"),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                    ) {
                        if (isDownloading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.8.dp,
                                trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Starting...",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp
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
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp
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
        shape = RoundedCornerShape(14.dp),
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
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
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
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = title,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.5.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = size,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.width(10.dp))

                Box(
                    modifier = Modifier
                        .size(18.dp)
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
                            modifier = Modifier.size(12.dp)
                        )
                    } else {
                        Surface(
                            shape = CircleShape,
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
                            color = Color.Transparent,
                            modifier = Modifier.size(16.dp)
                        ) {}
                    }
                }
            }
        }
    }
}
