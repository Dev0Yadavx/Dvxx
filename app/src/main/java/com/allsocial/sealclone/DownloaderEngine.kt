package com.allsocial.sealclone

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

fun scanMediaFile(context: Context, file: File, mimeType: String) {
    MediaScannerConnection.scanFile(
        context.applicationContext,
        arrayOf(file.absolutePath),
        arrayOf(mimeType)
    ) { path, uri ->
        // Media indexed successfully by Android OS
    }
}

object DownloaderEngine {

    fun fixUrl(input: String): String {
        val trimmed = input.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed.substringBefore("?si=").substringBefore("&si=")
        }
        val idRegex = Regex("""([a-zA-Z0-9_-]{11})""")
        val match = idRegex.find(trimmed)
        return if (match != null) "https://www.youtube.com/watch?v=${match.value}" else trimmed
    }

    // Direct stream for In-App Player
    suspend fun extractDirectStreamUrl(targetUrl: String): String = withContext(Dispatchers.IO) {
        val validUrl = fixUrl(targetUrl)
        EngineInitState.ensureInitialized()
        val request = YoutubeDLRequest(validUrl).apply {
            addOption("-g")
            addOption("-f", "best[ext=mp4]/best")
            addOption("--no-cache-dir")
            addOption("--no-warnings")
            addOption("--socket-timeout", "15")
            addOption("--extractor-args", "youtube:player_client=android,web")
        }
        val response = try {
            YoutubeDL.getInstance().execute(request)
        } catch (e: Exception) {
            null
        }
        response?.out?.lines()?.firstOrNull { it.startsWith("http") } ?: validUrl
    }

    // 10 Search Results (Guaranteed 10 entries via raw JSON line streaming)
    suspend fun searchOrFetch(query: String): List<SearchItem> = withContext(Dispatchers.IO) {
        val clean = fixUrl(query)
        val isDirectLink = clean.startsWith("http://") || clean.startsWith("https://")
        val target = if (isDirectLink) clean else "ytsearch20:$clean"

        EngineInitState.ensureInitialized()
        val request = YoutubeDLRequest(target).apply {
            addOption("-j") // Print each item as raw JSON line
            addOption("--flat-playlist")
            addOption("--no-warnings")
            addOption("--ignore-errors")
            addOption("--no-cache-dir")
            addOption("--socket-timeout", "15")
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
                if (list.size >= 20) return@forEach
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (list.isEmpty() && isDirectLink) {
            val ytRegex = Regex("""(?:v=|/v/|youtu\.be/|/embed/|/shorts/)([a-zA-Z0-9_-]{11})""")
            val match = ytRegex.find(clean)
            val ytId = match?.groupValues?.get(1)
            val fallbackThumb = if (ytId != null) "https://i.ytimg.com/vi/$ytId/hqdefault.jpg" else ""
            list.add(
                SearchItem(
                    id = ytId ?: System.currentTimeMillis().toString(),
                    title = if (ytId != null) "YouTube Video ($ytId)" else clean,
                    uploader = "Direct Media",
                    duration = "HD",
                    thumbnail = fallbackThumb,
                    url = clean
                )
            )
        }

        list
    }

    // Direct alias for backward-compatibility
    suspend fun search(query: String): List<SearchItem> = searchOrFetch(query)

    // Download Engine with Cover Art, Tags & Real Multi-Quality
    suspend fun startDownload(
        context: Context,
        targetUrl: String,
        resolutionHeight: Int?,
        isAudio: Boolean,
        audioBitrate: String?, // "320K", "192K", "64K"
        processId: String? = null,
        onProgress: (Float, String) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val validUrl = fixUrl(targetUrl)
        EngineInitState.ensureInitialized(context)

        // Android Public Standard Directory
        val downloadDir = if (isAudio) {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        } else {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        }
        if (!downloadDir.exists()) downloadDir.mkdirs()

        val fileTemplate = "${downloadDir.absolutePath}/%(title)s.%(ext)s"

        val request = YoutubeDLRequest(validUrl).apply {
            addOption("--no-cache-dir")
            addOption("--no-warnings")
            addOption("--no-mtime")
            addOption("--socket-timeout", "20")
            addOption("--extractor-args", "youtube:player_client=android,web")

            if (isAudio) {
                // 1. Audio Extraction & Quality
                addOption("-x")
                addOption("--audio-format", "mp3")
                addOption("--audio-quality", audioBitrate ?: "320K")
                addOption("-f", "bestaudio/best")

                // 2. Cover Art + ID3 Tags (Artist, Title, Album)
                addOption("--embed-thumbnail")
                addOption("--add-metadata")
                addOption("--convert-thumbnails", "jpg")
            } else {
                val res = resolutionHeight ?: 1080
                // Exact height selection: pehle target resolution dhundega
                addOption("-f", "bestvideo[height=$res]+bestaudio/bestvideo[height<=$res]+bestaudio/best[height<=$res]/best")
                addOption("--merge-output-format", "mp4")
                addOption("--embed-thumbnail")
                addOption("--add-metadata")
            }

            addOption("-o", fileTemplate)
        }

        if (processId != null) {
            YoutubeDL.getInstance().execute(request, processId) { progress, _, line ->
                onProgress(progress, line ?: "")
            }
        } else {
            YoutubeDL.getInstance().execute(request) { progress, _, line ->
                onProgress(progress, line ?: "")
            }
        }

        // Newly created file find karein
        val finalFile = downloadDir.listFiles()?.maxByOrNull { it.lastModified() }
            ?: File(downloadDir, if (isAudio) "audio.mp3" else "video.mp4")

        // 3. System Scanner Trigger (Taaki Phone ke File Manager / Gallery me turant show ho)
        scanMediaFile(
            context = context,
            file = finalFile,
            mimeType = if (isAudio) "audio/mpeg" else "video/mp4"
        )

        finalFile
    }

    // Overload for calls without context (backward compatibility)
    suspend fun startDownload(
        targetUrl: String,
        resolutionHeight: Int?,
        isAudio: Boolean,
        audioBitrate: String?,
        onProgress: (Float, String) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val validUrl = fixUrl(targetUrl)
        EngineInitState.ensureInitialized()
        val downloadDir = if (isAudio) {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        } else {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        }
        if (!downloadDir.exists()) downloadDir.mkdirs()
        val fileTemplate = "${downloadDir.absolutePath}/%(title)s.%(ext)s"

        val request = YoutubeDLRequest(validUrl).apply {
            addOption("--no-cache-dir")
            addOption("--no-warnings")
            addOption("--no-mtime")
            addOption("--socket-timeout", "20")
            addOption("--extractor-args", "youtube:player_client=android,web")

            if (isAudio) {
                addOption("-x")
                addOption("--audio-format", "mp3")
                addOption("--audio-quality", audioBitrate ?: "320K")
                addOption("-f", "bestaudio/best")
                addOption("--embed-thumbnail")
                addOption("--add-metadata")
                addOption("--convert-thumbnails", "jpg")
            } else {
                val res = resolutionHeight ?: 1080
                addOption("-f", "bestvideo[height=$res]+bestaudio/bestvideo[height<=$res]+bestaudio/best[height<=$res]/best")
                addOption("--merge-output-format", "mp4")
                addOption("--embed-thumbnail")
                addOption("--add-metadata")
            }

            addOption("-o", fileTemplate)
        }

        YoutubeDL.getInstance().execute(request) { progress, _, line ->
            onProgress(progress, line ?: "")
        }

        downloadDir.listFiles()?.maxByOrNull { it.lastModified() }
            ?: File(downloadDir, if (isAudio) "audio.mp3" else "video.mp4")
    }
}
