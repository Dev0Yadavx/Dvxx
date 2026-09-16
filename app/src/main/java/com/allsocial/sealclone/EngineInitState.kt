package com.allsocial.sealclone

import android.content.Context
import android.util.Log
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

    @Volatile
    var appContext: Context? = null

    private val initLock = Any()

    fun setApplicationContext(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    fun ensureInitialized(context: Context? = null): Boolean {
        if (isInitialized) return true
        val ctx = context?.applicationContext ?: appContext ?: return false
        setApplicationContext(ctx)
        synchronized(initLock) {
            if (isInitialized) return true
            return try {
                YoutubeDL.getInstance().init(ctx)
                FFmpeg.getInstance().init(ctx)
                isInitialized = true
                Log.d("EngineInitState", "Native binaries successfully initialized")
                true
            } catch (e: Exception) {
                Log.e("EngineInitState", "Failed to initialize native binaries: ${e.message}")
                false
            }
        }
    }

    suspend fun getOrFetchVersion(context: Context? = null, forceRefresh: Boolean = false): String = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedVersion.isNotBlank()) {
            return@withContext cachedVersion
        }
        val ctx = context?.applicationContext ?: appContext
        if (ctx == null || !ensureInitialized(ctx)) {
            return@withContext cachedVersion
        }
        return@withContext try {
            val fetched = YoutubeDL.getInstance().version(ctx)
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
