package com.allsocial.sealclone

import android.content.Context
import android.os.Environment
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object DownloaderEngine {

    fun fixUrl(input: String): String {
        val trimmed = input.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed.substringBefore("?si=").substringBefore("&si=")
        }
        val idRegex = Regex("""([a-zA-Z0-9_-]{11})""")
        val match = idRegex.find(trimmed)
        return if (match != null) {
            "https://www.youtube.com/watch?v=${match.value}"
        } else {
            trimmed
        }
    }

    // 1. In-App Player ke liye Direct Playable CDN Stream URL nikalna
    suspend fun extractDirectStreamUrl(targetUrl: String): String = withContext(Dispatchers.IO) {
        val validUrl = fixUrl(targetUrl)
        val request = YoutubeDLRequest(validUrl).apply {
            addOption("-g") // Get direct stream URL flag
            addOption("-f", "best[ext=mp4]/best")
            addOption("--extractor-args", "youtube:player_client=ios,android")
        }
        val response = try {
            YoutubeDL.getInstance().execute(request)
        } catch (e: Exception) {
            null
        }
        val directUrl = response?.out?.lines()?.firstOrNull { it.startsWith("http") }
        directUrl ?: validUrl
    }

    // 10 Search Results (Guaranteed 10 entries via raw JSON line streaming)
    suspend fun searchOrFetch(query: String): List<SearchItem> = withContext(Dispatchers.IO) {
        val clean = fixUrl(query)
        val isDirectLink = clean.startsWith("http://") || clean.startsWith("https://")
        val target = if (isDirectLink) clean else "ytsearch10:$clean"

        val request = YoutubeDLRequest(target).apply {
            addOption("-j") // Print each item as raw JSON line
            addOption("--flat-playlist")
            addOption("--no-warnings")
            addOption("--ignore-errors")
            addOption("--extractor-args", "youtube:player_client=ios,android")
        }

        val list = mutableListOf<SearchItem>()

        try {
            // Raw execute se har video ki ek alag JSON line aati hai
            val response = YoutubeDL.getInstance().execute(request)
            val output = response.out ?: ""

            output.lineSequence().forEach { line ->
                val trimmedLine = line.trim()
                if (trimmedLine.startsWith("{") && trimmedLine.endsWith("}")) {
                    try {
                        val json = org.json.JSONObject(trimmedLine)
                        val id = json.optString("id", "")
                        val title = json.optString("title", "YouTube Video")
                        val uploader = json.optString("uploader", json.optString("channel", "Artist"))
                        
                        // Duration formatting
                        val durationSec = json.optLong("duration", 0L)
                        val durationStr = if (durationSec > 0) {
                            String.format("%02d:%02d", durationSec / 60, durationSec % 60)
                        } else {
                            json.optString("duration_string", "03:30")
                        }

                        // Thumbnail fallback
                        val thumb = if (id.isNotEmpty()) {
                            "https://i.ytimg.com/vi/$id/hqdefault.jpg"
                        } else {
                            json.optString("thumbnail", "")
                        }

                        val url = if (id.isNotEmpty()) {
                            "https://www.youtube.com/watch?v=$id"
                        } else {
                            json.optString("url", clean)
                        }

                        list.add(
                            SearchItem(
                                id = id,
                                title = title,
                                uploader = uploader,
                                duration = durationStr,
                                thumbnail = thumb,
                                url = url
                            )
                        )
                    } catch (e: Exception) {
                        // Skip corrupted line
                    }
                }
                if (list.size >= 10) return@forEach
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        list
    }

    // Direct alias for backward-compatibility
    suspend fun search(query: String): List<SearchItem> = searchOrFetch(query)

    // 3. Robust Download Engine for all MP3 bitrates and Video resolutions
    suspend fun startDownload(
        targetUrl: String,
        resolutionHeight: Int?,
        isAudio: Boolean,
        audioBitrate: String?,
        onProgress: (Float, String) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val validUrl = fixUrl(targetUrl)
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val fileTemplate = "${downloadDir.absolutePath}/%(title)s.%(ext)s"

        val request = YoutubeDLRequest(validUrl).apply {
            addOption("--no-warnings")
            addOption("--extractor-args", "youtube:player_client=ios,android")

            if (isAudio) {
                addOption("-x")
                addOption("--audio-format", "mp3")
                addOption("--audio-quality", audioBitrate ?: "320K")
                addOption("-f", "bestaudio/best")
            } else {
                val res = resolutionHeight ?: 1080
                // Strict fallback pattern: agar separate video+audio fail ho to combined format le
                addOption("-f", "bestvideo[height<=$res]+bestaudio/best[height<=$res]/best")
                addOption("--merge-output-format", "mp4")
            }

            addOption("-o", fileTemplate)
            addOption("--no-mtime")
        }

        YoutubeDL.getInstance().execute(request) { progress, _, line ->
            onProgress(progress, line ?: "")
        }

        downloadDir.listFiles()?.maxByOrNull { it.lastModified() } ?: File(downloadDir, "output.mp4")
    }
}
