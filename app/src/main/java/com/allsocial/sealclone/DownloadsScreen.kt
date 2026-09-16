package com.allsocial.sealclone

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class MediaCategoryFilter {
    ALL, VIDEOS, AUDIO
}

enum class MediaSortOrder(val label: String) {
    NEWEST("Newest"),
    OLDEST("Oldest"),
    LARGEST("Largest"),
    NAME("Name (A-Z)")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    vm: SealViewModel,
    onNavigateHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val downloadedHistory by vm.downloadedHistory.collectAsState()
    var mediaList by remember { mutableStateOf<List<DownloadedMedia>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedCategory by remember { mutableStateOf(MediaCategoryFilter.ALL) }
    var selectedSort by remember { mutableStateOf(MediaSortOrder.NEWEST) }
    var itemToDelete by remember { mutableStateOf<DownloadedMedia?>(null) }

    // Active player state
    var activeMedia by remember { mutableStateOf<DownloadedMedia?>(null) }
    var isPlayerMinimized by remember { mutableStateOf(false) }

    // Load ONLY downloaded content from Xtube (tracked history & app downloads)
    fun loadAppDownloadedContent() {
        isLoading = true
        scope.launch(Dispatchers.IO) {
            val list = mutableListOf<DownloadedMedia>()
            val seenPaths = mutableSetOf<String>()

            // 1. Process tracked records from ViewModel history
            downloadedHistory.forEach { record ->
                val file = File(record.filePath)
                if (file.exists() && seenPaths.add(file.absolutePath)) {
                    val isVideo = record.ext.equals("mp4", ignoreCase = true) ||
                            record.ext.equals("mkv", ignoreCase = true) ||
                            record.ext.equals("webm", ignoreCase = true)

                    val uri = try {
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.provider",
                            file
                        )
                    } catch (e: Exception) {
                        Uri.fromFile(file)
                    }

                    list.add(
                        DownloadedMedia(
                            id = file.absolutePath.hashCode().toLong(),
                            uri = uri,
                            filePath = file.absolutePath,
                            displayName = file.name,
                            title = record.title.ifBlank { file.nameWithoutExtension },
                            artist = "Xtube",
                            sizeBytes = file.length(),
                            durationMs = 0L,
                            dateModified = file.lastModified() / 1000L,
                            mimeType = if (isVideo) "video/${record.ext}" else "audio/${record.ext}",
                            isVideo = isVideo,
                            thumbnailUri = if (record.thumbnail.isNotBlank()) Uri.parse(record.thumbnail) else null
                        )
                    )
                }
            }

            // 2. Scan downloads directory for files downloaded by Xtube/Seal
            try {
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val sealDir = File(downloadsDir, "Seal")
                val xtubeDir = File(downloadsDir, "Xtube")
                val searchDirs = listOfNotNull(downloadsDir, sealDir, xtubeDir).filter { it.exists() }

                searchDirs.forEach { dir ->
                    dir.listFiles { f ->
                        f.isFile && (f.name.endsWith(".mp4", ignoreCase = true) ||
                                f.name.endsWith(".mkv", ignoreCase = true) ||
                                f.name.endsWith(".webm", ignoreCase = true) ||
                                f.name.endsWith(".mp3", ignoreCase = true) ||
                                f.name.endsWith(".m4a", ignoreCase = true) ||
                                f.name.endsWith(".opus", ignoreCase = true))
                    }?.forEach { file ->
                        if (seenPaths.add(file.absolutePath)) {
                            val ext = file.extension.lowercase()
                            val isVideo = ext == "mp4" || ext == "mkv" || ext == "webm"
                            val uri = try {
                                FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.provider",
                                    file
                                )
                            } catch (e: Exception) {
                                Uri.fromFile(file)
                            }
                            list.add(
                                DownloadedMedia(
                                    id = file.absolutePath.hashCode().toLong(),
                                    uri = uri,
                                    filePath = file.absolutePath,
                                    displayName = file.name,
                                    title = file.nameWithoutExtension,
                                    artist = "Xtube Download",
                                    sizeBytes = file.length(),
                                    durationMs = 0L,
                                    dateModified = file.lastModified() / 1000L,
                                    mimeType = if (isVideo) "video/$ext" else "audio/$ext",
                                    isVideo = isVideo
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }

            withContext(Dispatchers.Main) {
                mediaList = list
                isLoading = false
            }
        }
    }

    LaunchedEffect(downloadedHistory) {
        loadAppDownloadedContent()
    }

    // Filter and sort items
    val filteredList = remember(mediaList, selectedCategory, selectedSort) {
        var result = mediaList

        // Filter by category
        result = when (selectedCategory) {
            MediaCategoryFilter.ALL -> result
            MediaCategoryFilter.VIDEOS -> result.filter { it.isVideo }
            MediaCategoryFilter.AUDIO -> result.filter { !it.isVideo }
        }

        // Sort items
        when (selectedSort) {
            MediaSortOrder.NEWEST -> result.sortedByDescending { it.dateModified }
            MediaSortOrder.OLDEST -> result.sortedBy { it.dateModified }
            MediaSortOrder.LARGEST -> result.sortedByDescending { it.sizeBytes }
            MediaSortOrder.NAME -> result.sortedBy { it.title.lowercase() }
        }
    }

    // Storage summary calculation
    val totalSizeBytes = remember(mediaList) {
        mediaList.sumOf { it.sizeBytes }
    }
    val totalFormattedSize = remember(totalSizeBytes) {
        val mb = totalSizeBytes / (1024.0 * 1024.0)
        if (mb >= 1024.0) "%.2f GB".format(mb / 1024.0) else "%.1f MB".format(mb)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("downloads_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header Row: Title & Total Info (Refresh icon removed as requested)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Downloads",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${mediaList.size} downloaded items • $totalFormattedSize",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Category Filter & Sort Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category Chips
                FilterChip(
                    selected = selectedCategory == MediaCategoryFilter.ALL,
                    onClick = { selectedCategory = MediaCategoryFilter.ALL },
                    label = { Text("All (${mediaList.size})", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("chip_category_all")
                )

                FilterChip(
                    selected = selectedCategory == MediaCategoryFilter.VIDEOS,
                    onClick = { selectedCategory = MediaCategoryFilter.VIDEOS },
                    label = { Text("Videos (${mediaList.count { it.isVideo }})", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("chip_category_videos")
                )

                FilterChip(
                    selected = selectedCategory == MediaCategoryFilter.AUDIO,
                    onClick = { selectedCategory = MediaCategoryFilter.AUDIO },
                    label = { Text("Audio (${mediaList.count { !it.isVideo }})", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("chip_category_audio")
                )
            }

            // Content Area: Loading / Empty State / List
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            modifier = Modifier.size(80.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.DownloadDone,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No downloaded media yet",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Downloaded videos and audios from Xtube will appear here",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = onNavigateHome,
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.testTag("btn_downloads_go_home")
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Download Media", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 90.dp)
                ) {
                    items(filteredList, key = { it.id }) { item ->
                        DownloadedMediaCard(
                            media = item,
                            onClick = {
                                activeMedia = item
                                isPlayerMinimized = false
                            },
                            onDelete = { itemToDelete = item },
                            onShare = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = item.mimeType
                                    putExtra(Intent.EXTRA_STREAM, item.uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Media"))
                            }
                        )
                    }
                }
            }
        }

        // In-App Player Overlay
        activeMedia?.let { media ->
            IntegratedMediaPlayerSheet(
                media = media,
                isMinimized = isPlayerMinimized,
                onToggleMinimize = { isPlayerMinimized = !isPlayerMinimized },
                onClose = { activeMedia = null }
            )
        }

        // Delete Confirmation Dialog
        itemToDelete?.let { item ->
            AlertDialog(
                onDismissRequest = { itemToDelete = null },
                icon = {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                title = { Text("Delete Download?") },
                text = {
                    Text(
                        "Are you sure you want to delete \"${item.title}\"? This will permanently remove the file from storage.",
                        fontSize = 13.5.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val targetFile = File(item.filePath)
                            if (targetFile.exists()) {
                                targetFile.delete()
                            }
                            try {
                                context.contentResolver.delete(item.uri, null, null)
                            } catch (_: Exception) {}

                            // Update ViewModel history
                            vm.removeRecord(
                                DownloadedRecord(
                                    title = item.title,
                                    filePath = item.filePath,
                                    thumbnail = "",
                                    quality = "",
                                    ext = item.extension,
                                    fileSize = item.formattedSize
                                )
                            )

                            mediaList = mediaList.filter { it.id != item.id }
                            if (activeMedia?.id == item.id) {
                                activeMedia = null
                            }
                            itemToDelete = null
                            Toast.makeText(context, "Deleted successfully", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { itemToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

/**
 * Material 3 Downloaded Media Item Card
 */
@Composable
fun DownloadedMediaCard(
    media: DownloadedMedia,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("media_card_${media.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail / Icon Box
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                if (media.thumbnailUri != null) {
                    AsyncImage(
                        model = media.thumbnailUri,
                        contentDescription = media.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = if (media.isVideo) Icons.Default.PlayArrow else Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = media.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = media.extension,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                    Text(
                        text = media.formattedSize,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "•",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = media.formattedDate,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Share button
            IconButton(
                onClick = onShare,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Delete button
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
