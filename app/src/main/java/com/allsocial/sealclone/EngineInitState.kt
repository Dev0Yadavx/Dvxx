package com.allsocial.sealclone

import android.content.Context
import android.util.Log
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Singleton state manager for the yt-dlp native extraction engine.
 * Prevents redundant process spawning and SELinux audit rate-limiting.
 */
object EngineInitState {
    @Volatile
    var isInitialized: Boolean = false
        private set

    @Volatile
    var cachedVersion: String = "2025.01.26"
        private set

    private val initLock = Any()

    fun ensureInitialized(context: Context): Boolean {
        if (isInitialized) return true
        synchronized(initLock) {
            if (isInitialized) return true
            return try {
                val appCtx = context.applicationContext
                YoutubeDL.getInstance().init(appCtx)
                FFmpeg.getInstance().init(appCtx)
                Aria2c.getInstance().init(appCtx)
                isInitialized = true
                Log.d("EngineInitState", "Native binaries successfully initialized")
                true
            } catch (e: Exception) {
                Log.e("EngineInitState", "Failed to initialize native binaries", e)
                false
            }
        }
    }

    suspend fun getOrFetchVersion(context: Context, forceRefresh: Boolean = false): String = withContext(Dispatchers.IO) {
        if (!ensureInitialized(context)) {
            return@withContext "Init Failed"
        }
        if (!forceRefresh && cachedVersion != "2025.01.26" && cachedVersion.isNotBlank()) {
            return@withContext cachedVersion
        }
        return@withContext try {
            val fetched = YoutubeDL.getInstance().version(context.applicationContext)
            if (!fetched.isNullOrBlank()) {
                cachedVersion = fetched
            }
            cachedVersion
        } catch (e: Exception) {
            Log.w("EngineInitState", "Could not fetch version: ${e.message}")
            cachedVersion
        }
    }

    fun setUpdatedVersion(newVersion: String) {
        if (newVersion.isNotBlank()) {
            cachedVersion = newVersion
        }
    }
}
