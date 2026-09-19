package com.allsocial.sealclone

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniversalDownloadSheet(
    title: String,
    uploader: String,
    targetUrl: String,
    onDismiss: () -> Unit,
    onWatchClick: (String) -> Unit,
    onStartDownload: (height: Int?, isAudio: Boolean, bitrate: String?) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E24),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = uploader,
                color = Color(0xFF00ADB5),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
            )

            // Watch / Preview
            OutlinedButton(
                onClick = { onWatchClick(targetUrl) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF00ADB5))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Watch / Play Stream Preview", color = Color.White)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // AUDIO QUALITIES
            Text(
                text = "Audio Qualities (MP3)",
                color = Color(0xFF00ADB5),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            val audioRates = listOf(
                "320K" to "320 kbps",
                "256K" to "256 kbps",
                "192K" to "192 kbps",
                "128K" to "128 kbps",
                "64K" to "64 kbps"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                audioRates.forEach { (key, label) ->
                    FilledTonalButton(
                        onClick = { 
                            onStartDownload(null, true, key)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // VIDEO QUALITIES
            Text(
                text = "Video Qualities (MP4)",
                color = Color(0xFF00ADB5),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            val videoQualities = listOf(
                "4K Ultra HD (2160p)" to 2160,
                "2K Quad HD (1440p)" to 1440,
                "1080p Full HD" to 1080,
                "720p HD" to 720,
                "480p SD" to 480,
                "360p Low" to 360
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                videoQualities.forEach { (label, height) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFF2A2A32))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Button(
                            onClick = { 
                                onStartDownload(height, false, null)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00ADB5)),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Overload for ParsedMediaData to integrate seamlessly with MainActivity.
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
    UniversalDownloadSheet(
        title = media.title,
        uploader = media.uploader,
        targetUrl = media.webUrl,
        onDismiss = onDismiss,
        onWatchClick = onWatchClick,
        onStartDownload = onStartDownload
    )
}
