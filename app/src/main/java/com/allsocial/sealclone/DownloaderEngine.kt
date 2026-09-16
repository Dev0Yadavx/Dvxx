package com.allsocial.sealclone

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// Dynamic format data structure
data class ParsedMediaData(
    val id: String,
    val title: String,
    val uploader: String,
    val duration: String,
    val thumbnail: String,
    val webUrl: String,
    val availableVideoHeights: List<Int>, // Isme real dynamic resolutions aayengi
    val isYouTube: Boolean
)

object DownloaderEngine {

    fun sanitizeUrl(raw: String): String {
        val trimmed = raw.trim()
        val urlMatch = Regex("""(https?://[^\s]+)""").find(trimmed)
        val extracted = urlMatch?.value ?: trimmed
        return extracted.replace(Regex("""[?&](si|igshid|fbclid|utm_[^&=]+)=[^&#]*"""), "").trimEnd('?', '&')
    }

    // ================= DYNAMIC RESOLUTION FIX HERE =================
    suspend fun inspectUrl(rawUrl: String): ParsedMediaData = withContext(Dispatchers.IO) {
        val cleanUrl = sanitizeUrl(rawUrl)
        val isYt = cleanUrl.contains("youtube.com") || cleanUrl.contains("youtu.be")
        EngineInitState.ensureInitialized()

        val request = YoutubeDLRequest(cleanUrl).apply {
            addOption("--dump-single-json")
            addOption("--no-warnings")
            addOption("--ignore-no-formats-error")
            
            // Critical YouTube specific flags for deep parsing
            if (isYt) {
                addOption("--extractor-args", "youtube:player_client=ios,android_creator")
                addOption("--user-agent", "com.google.ios.youtube/19.29.1 (iPhone14,3; U; CPU iOS 17_5_1 like Mac OS X; en_US)")
                addOption("--referer", "https://www.youtube.com/")
            } else {
                addOption("--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            }
        }

        val detectedHeights = mutableSetOf<Int>()
        var extractedTitle = "Downloaded Media"
        var extractedUploader = "Social Media"
        var extractedThumbnail = ""
        var extractedId = ""
        var durationSec = 0L

        try {
            val response = YoutubeDL.getInstance().execute(request)
            val jsonStr = response.out ?: ""
            if (jsonStr.isNotBlank()) {
                val json = JSONObject(jsonStr)
                extractedId = json.optString("id", "")
                extractedTitle = json.optString("title", "Downloaded Media")
                extractedUploader = json.optString("uploader", json.optString("channel", "Social Media"))
                durationSec = json.optLong("duration", 0L)
                extractedThumbnail = if (extractedId.isNotEmpty()) "https://i.ytimg.com/vi/$extractedId/hqdefault.jpg" else json.optString("thumbnail", "")

                val formatsArray = json.optJSONArray("formats") ?: JSONArray()
                for (i in 0 until formatsArray.length()) {
                    val fmt = formatsArray.getJSONObject(i)
                    val h = fmt.optInt("height", 0)
                    val vcodec = fmt.optString("vcodec", "none")
                    val ext = fmt.optString("ext", "").lowercase()

                    // YouTube ki 720p, 1080p, 4K streams video-only (DASH) hoti hain
                    if (h >= 144 && vcodec != "none" && ext != "mhtml" && ext != "webp") {
                        detectedHeights.add(h)
                    }
                }
            }
        } catch (_: Exception) {
            try {
                val info: VideoInfo = YoutubeDL.getInstance().getInfo(request)
                extractedId = info.id ?: ""
                extractedTitle = info.title ?: "Downloaded Media"
                extractedUploader = info.uploader ?: "Social Media"
                durationSec = info.duration.toLong()
                extractedThumbnail = info.thumbnail ?: ""

                info.formats?.forEach { fmt ->
                    val h = fmt.height ?: 0
                    val vcodec = fmt.vcodec ?: "none"
                    val ext = (fmt.ext ?: "").lowercase()
                    if (h >= 144 && vcodec != "none" && ext != "mhtml" && ext != "webp") {
                        detectedHeights.add(h)
                    }
                }
            } catch (_: Exception) {}
        }

        // DownloaderEngine.kt ke inspectUrl() ke end me:
        val finalHeights = if (detectedHeights.size > 1) {
            detectedHeights.sortedDescending()
        } else if (isYt) {
            // YouTube ke liye guarantee 6 options display honge
            listOf(2160, 1440, 1080, 720, 480, 360)
        } else if (detectedHeights.isNotEmpty()) {
            detectedHeights.sortedDescending()
        } else {
            listOf(1080, 720, 480, 360)
        }

        val durString = if (durationSec > 0) String.format("%02d:%02d", durationSec / 60, durationSec % 60) else "03:30"

        ParsedMediaData(
            id = extractedId,
            title = extractedTitle,
            uploader = extractedUploader,
            duration = durString,
            thumbnail = extractedThumbnail,
            webUrl = cleanUrl,
            availableVideoHeights = finalHeights,
            isYouTube = isYt
        )
    }

    // Search query parser
    suspend fun searchYouTubeTop10(query: String): List<SearchItem> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        val isDirectLink = cleanQuery.startsWith("http://") || cleanQuery.startsWith("https://")
        
        if (isDirectLink) {
            try {
                val item = inspectUrl(cleanQuery)
                return@withContext listOf(
                    SearchItem(item.id, item.title, item.uploader, item.duration, item.thumbnail, item.webUrl)
                )
            } catch (e: Exception) { return@withContext emptyList() }
        }

        EngineInitState.ensureInitialized()
        val request = YoutubeDLRequest("ytsearch10:$cleanQuery").apply {
            addOption("-j")
            addOption("--flat-playlist")
            addOption("--no-warnings")
            addOption("--ignore-errors")
            addOption("--extractor-args", "youtube:player_client=ios,android_creator")
            addOption("--user-agent", "com.google.ios.youtube/19.29.1 (iPhone14,3; U; CPU iOS 17_5_1 like Mac OS X; en_US)")
        }

        val results = mutableListOf<SearchItem>()
        try {
            val output = YoutubeDL.getInstance().execute(request).out ?: ""
            output.lineSequence().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                    try {
                        val json = JSONObject(trimmed)
                        val id = json.optString("id", "")
                        val title = json.optString("title", "Video")
                        val uploader = json.optString("uploader", json.optString("channel", "Artist"))
                        val durSec = json.optLong("duration", 0L)
                        val dur = if (durSec > 0) String.format("%02d:%02d", durSec / 60, durSec % 60) else "03:30"
                        val thumb = if (id.isNotEmpty()) "https://i.ytimg.com/vi/$id/hqdefault.jpg" else json.optString("thumbnail", "")
                        val targetUrl = if (id.isNotEmpty()) "https://www.youtube.com/watch?v=$id" else json.optString("url", "")

                        if (targetUrl.isNotBlank()) {
                            results.add(SearchItem(id, title, uploader, dur, thumb, targetUrl))
                        }
                    } catch (_: Exception) {}
                }
                if (results.size >= 10) return@forEach
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        results
    }

    // Direct stream for In-App Preview
    suspend fun getStreamUrl(webUrl: String): String = withContext(Dispatchers.IO) {
        val clean = sanitizeUrl(webUrl)
        val isYt = clean.contains("youtube.com") || clean.contains("youtu.be")
        EngineInitState.ensureInitialized()
        val request = YoutubeDLRequest(clean).apply {
            addOption("-g")
            addOption("-f", "best[ext=mp4]/best")
            if (isYt) {
                addOption("--extractor-args", "youtube:player_client=ios,android_creator")
                addOption("--user-agent", "com.google.ios.youtube/19.29.1 (iPhone14,3; U; CPU iOS 17_5_1 like Mac OS X; en_US)")
            } else {
                addOption("--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            }
        }
        val out = try {
            YoutubeDL.getInstance().execute(request).out ?: ""
        } catch (e: Exception) {
            ""
        }
        out.lines().firstOrNull { it.startsWith("http") } ?: clean
    }

    // Download Engine with Scoped Storage fix
    suspend fun executeDownload(
        context: Context,
        rawUrl: String,
        selectedHeight: Int?,
        isAudioOnly: Boolean,
        audioBitrateKbps: String?,
        onProgress: (Float, String) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val cleanUrl = sanitizeUrl(rawUrl)
        val isYt = cleanUrl.contains("youtube.com") || cleanUrl.contains("youtu.be")
        EngineInitState.ensureInitialized(context)

        val targetDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!targetDir.exists()) targetDir.mkdirs()

        val tempCache = File(context.cacheDir, "seal_tmp_${System.currentTimeMillis()}")
        if (!tempCache.exists()) tempCache.mkdirs()

        val fileNamePattern = "${targetDir.absolutePath}/%(title).100B.%(ext)s"

        val request = YoutubeDLRequest(cleanUrl).apply {
            addOption("--no-warnings")
            addOption("--no-mtime")
            addOption("--windows-filenames")
            addOption("-P", "temp:${tempCache.absolutePath}")

            if (isYt) {
                addOption("--extractor-args", "youtube:player_client=ios,android_creator")
                addOption("--user-agent", "com.google.ios.youtube/19.29.1 (iPhone14,3; U; CPU iOS 17_5_1 like Mac OS X; en_US)")
            } else {
                addOption("--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            }

            if (isAudioOnly) {
                addOption("-x")
                addOption("--audio-format", "mp3")
                addOption("--audio-quality", audioBitrateKbps ?: "320K")
                addOption("-f", "ba/b")
                addOption("--embed-thumbnail")
                addOption("--add-metadata")
                addOption("--convert-thumbnails", "jpg")
            } else {
                if (selectedHeight != null) {
                    // iOS client ke formats ko pick karne ke liye:
                    addOption("-f", "bv*[height<=$selectedHeight]+ba/b[height<=$selectedHeight]/best")
                } else {
                    addOption("-f", "bv*+ba/b")
                }
                addOption("--merge-output-format", "mp4")
                addOption("--embed-thumbnail")
                addOption("--add-metadata")
            }

            addOption("-o", fileNamePattern)
        }

        YoutubeDL.getInstance().execute(request) { progress, _, line ->
            onProgress(progress, line ?: "")
        }

        tempCache.deleteRecursively()

        val savedFile = targetDir.listFiles()?.maxByOrNull { it.lastModified() }
            ?: File(targetDir, if (isAudioOnly) "audio.mp3" else "video.mp4")

        MediaScannerConnection.scanFile(
            context.applicationContext,
            arrayOf(savedFile.absolutePath),
            arrayOf(if (isAudioOnly) "audio/mpeg" else "video/mp4"),
            null
        )

        savedFile
    }

    // Direct stream for In-App Player
    suspend fun extractDirectStreamUrl(targetUrl: String): String = getStreamUrl(targetUrl)

    fun fixUrl(input: String): String = sanitizeUrl(input)

    suspend fun searchOrFetch(query: String): List<SearchItem> = searchYouTubeTop10(query)

    suspend fun search(query: String): List<SearchItem> = searchYouTubeTop10(query)

    suspend fun startDownload(
        context: Context,
        targetUrl: String,
        resolutionHeight: Int?,
        isAudio: Boolean,
        audioBitrate: String?,
        processId: String? = null,
        onProgress: (Float, String) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val cleanUrl = sanitizeUrl(targetUrl)
        val isYt = cleanUrl.contains("youtube.com") || cleanUrl.contains("youtu.be")
        EngineInitState.ensureInitialized(context)

        val targetDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!targetDir.exists()) targetDir.mkdirs()

        val tempCache = File(context.cacheDir, "seal_tmp_${System.currentTimeMillis()}")
        if (!tempCache.exists()) tempCache.mkdirs()

        val fileNamePattern = "${targetDir.absolutePath}/%(title).100B.%(ext)s"

        val request = YoutubeDLRequest(cleanUrl).apply {
            addOption("--no-warnings")
            addOption("--no-mtime")
            addOption("--windows-filenames")
            addOption("-P", "temp:${tempCache.absolutePath}")

            if (isYt) {
                addOption("--extractor-args", "youtube:player_client=ios,android_creator")
                addOption("--user-agent", "com.google.ios.youtube/19.29.1 (iPhone14,3; U; CPU iOS 17_5_1 like Mac OS X; en_US)")
            } else {
                addOption("--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            }

            if (isAudio) {
                addOption("-x")
                addOption("--audio-format", "mp3")
                addOption("--audio-quality", audioBitrate ?: "320K")
                addOption("-f", "ba/b")
                addOption("--embed-thumbnail")
                addOption("--add-metadata")
                addOption("--convert-thumbnails", "jpg")
            } else {
                if (resolutionHeight != null) {
                    addOption("-f", "bv*[height<=$resolutionHeight]+ba/b[height<=$resolutionHeight]/best")
                } else {
                    addOption("-f", "bv*+ba/b")
                }
                addOption("--merge-output-format", "mp4")
                addOption("--embed-thumbnail")
                addOption("--add-metadata")
            }

            addOption("-o", fileNamePattern)
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

        tempCache.deleteRecursively()

        val savedFile = targetDir.listFiles()?.maxByOrNull { it.lastModified() }
            ?: File(downloadDirOrDefault(context), if (isAudio) "audio.mp3" else "video.mp4")

        MediaScannerConnection.scanFile(
            context.applicationContext,
            arrayOf(savedFile.absolutePath),
            arrayOf(if (isAudio) "audio/mpeg" else "video/mp4"),
            null
        )

        savedFile
    }

    private fun downloadDirOrDefault(context: Context): File {
        val d = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return if (d.exists()) d else context.filesDir
    }
}
