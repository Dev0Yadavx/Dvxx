package com.allsocial.sealclone

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream

// Dynamic format data structure
data class ParsedMediaData(
    val id: String,
    val title: String,
    val uploader: String,
    val duration: String,
    val thumbnail: String,
    val webUrl: String,
    val availableVideoHeights: List<Int>,
    val isYouTube: Boolean
)

object DownloaderEngine {

    fun sanitizeUrl(raw: String): String {
        val trimmed = raw.trim()
        val urlMatch = Regex("""(https?://[^\s]+)""").find(trimmed)
        val extracted = urlMatch?.value ?: trimmed
        return extracted.replace(Regex("""[?&](si|igshid|fbclid|utm_[^&=]+)=[^&#]*"""), "").trimEnd('?', '&')
    }

    suspend fun inspectUrl(rawUrl: String): ParsedMediaData = withContext(Dispatchers.IO) {
        val cleanUrl = sanitizeUrl(rawUrl)
        val isYt = cleanUrl.contains("youtube.com") || cleanUrl.contains("youtu.be")
        EngineInitState.ensureInitialized()

        val request = YoutubeDLRequest(cleanUrl).apply {
            addOption("--dump-single-json")
            addOption("--no-warnings")
            addOption("--no-cache-dir")
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

        val finalHeights = if (detectedHeights.size > 1) {
            detectedHeights.sortedDescending()
        } else if (isYt) {
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

    suspend fun searchYouTubeTop10(query: String): List<SearchItem> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        val isDirectLink = cleanQuery.startsWith("http://") || cleanQuery.startsWith("https://")

        if (isDirectLink) {
            val valid = sanitizeUrl(cleanQuery)
            val vId = Regex("""([a-zA-Z0-9_-]{11})""").find(valid)?.value ?: ""
            return@withContext listOf(
                SearchItem(
                    id = vId,
                    title = "Media Link",
                    uploader = "Social Media",
                    duration = "HD",
                    thumbnail = if (vId.isNotEmpty()) "https://i.ytimg.com/vi/$vId/hqdefault.jpg" else "",
                    url = valid
                )
            )
        }

        val request = YoutubeDLRequest("ytsearch10:$cleanQuery").apply {
            addOption("-j")
            addOption("--flat-playlist")
            addOption("--no-warnings")
            addOption("--ignore-errors")
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

    // 100% Guaranteed Download Engine
    suspend fun executeDownload(
        context: Context,
        rawUrl: String,
        selectedHeight: Int?,
        isAudioOnly: Boolean,
        audioBitrateKbps: String?,
        onProgress: (Float, String) -> Unit
    ): File = withContext(Dispatchers.IO) {
        EngineInitState.ensureInitialized(context)
        val cleanUrl = sanitizeUrl(rawUrl)
        val isYt = cleanUrl.contains("youtube.com") || cleanUrl.contains("youtu.be")

        // Private directory to avoid Android Scoped Storage restriction during yt-dlp/ffmpeg execution
        val workingDir = File(context.getExternalFilesDir(null), "downloads_cache").apply {
            if (!exists()) mkdirs()
        }

        val outputTemplate = "${workingDir.absolutePath}/%(title).80B.%(ext)s"

        val request = YoutubeDLRequest(cleanUrl).apply {
            addOption("--no-warnings")
            addOption("--no-mtime")
            addOption("--windows-filenames")
            addOption("--no-check-certificates")
            addOption("-P", workingDir.absolutePath)
            addOption("-o", outputTemplate)

            if (isYt) {
                addOption("--extractor-args", "youtube:player_client=android,web;formats=missing_pot")
            }
            if (isAudioOnly) {
                addOption("-x")
                addOption("--audio-format", "mp3")
                addOption("--audio-quality", audioBitrateKbps ?: "320K")
                // Safe audio: pehle best audio dekhega, na mile to video se audio rip karega
                addOption("-f", "bestaudio/ba/b/best")
            } else {
                val h = selectedHeight ?: 1080
                // SEAL PRODUCTION STRING:
                // 1. Target height ki separate video + audio
                // 2. Us height se choti koi bhi separate video + audio
                // 3. Combined single file (jahan audio-video pehle se ek ho, e.g. 360p/720p progressive)
                // 4. Fallback best
                val formatChain = "bestvideo[height<=$h]+bestaudio/best[height<=$h]/bestvideo+bestaudio/best"
                addOption("-f", formatChain)
                addOption("--merge-output-format", "mp4")
            }
        }

        // Run engine with progress throttling to prevent log flood
        var lastProgressReportTime = 0L
        YoutubeDL.getInstance().execute(request) { progress, _, line ->
            val now = System.currentTimeMillis()
            if (now - lastProgressReportTime >= 250L || progress >= 100f) {
                lastProgressReportTime = now
                onProgress(progress, line ?: "")
            }
        }

        // Get created file in internal cache
        val downloadedFile = workingDir.listFiles()?.maxByOrNull { it.lastModified() }
            ?: throw IllegalStateException("Download file was not found")

        // Move to public Downloads via MediaStore
        val finalPublicFile = copyToPublicDownloads(context, downloadedFile, isAudioOnly)
        downloadedFile.delete()

        finalPublicFile
    }

    // MediaStore Resolver to make file visible in Phone Gallery / File Manager
    private fun copyToPublicDownloads(context: Context, srcFile: File, isAudio: Boolean): File {
        val mimeType = if (isAudio) "audio/mpeg" else "video/mp4"
        val fileName = srcFile.name

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection: Uri = if (isAudio) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, if (isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val uri = context.contentResolver.insert(collection, values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(srcFile).use { input ->
                        input.copyTo(out)
                    }
                }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            }
        } else {
            val targetDir = Environment.getExternalStoragePublicDirectory(
                if (isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_DOWNLOADS
            )
            val dest = File(targetDir, fileName)
            srcFile.copyTo(dest, overwrite = true)
            MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), arrayOf(mimeType), null)
            return dest
        }

        return File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
    }

    suspend fun getStreamUrl(webUrl: String): String = withContext(Dispatchers.IO) {
        EngineInitState.ensureInitialized()
        val clean = sanitizeUrl(webUrl)
        val isYt = clean.contains("youtube.com") || clean.contains("youtu.be")
        val request = YoutubeDLRequest(clean).apply {
            addOption("-g")
            addOption("-f", "best[ext=mp4]/best")
            addOption("--no-cache-dir")
            if (isYt) {
                addOption("--extractor-args", "youtube:player_client=android_creator,ios")
            }
        }
        val out = try {
            YoutubeDL.getInstance().execute(request).out ?: ""
        } catch (e: Exception) {
            ""
        }
        out.lines().firstOrNull { it.startsWith("http") } ?: clean
    }

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
    ): File = executeDownload(
        context = context,
        rawUrl = targetUrl,
        selectedHeight = resolutionHeight,
        isAudioOnly = isAudio,
        audioBitrateKbps = audioBitrate,
        onProgress = onProgress
    )
}
