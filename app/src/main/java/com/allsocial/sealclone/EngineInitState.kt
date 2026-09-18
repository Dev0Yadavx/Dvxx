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
        val ctx = context?.applicationContext
            ?: appContext
            ?: try { SealApp.instance.applicationContext } catch (_: Throwable) { null }
            ?: return false
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

    @Volatile
    private var isUpdatingEngine: Boolean = false

    suspend fun checkAutoUpdateDaily(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (isUpdatingEngine) return@withContext false
        val ctx = context.applicationContext
        val prefs = ctx.getSharedPreferences("seal_auto_update_prefs", Context.MODE_PRIVATE)
        val lastUpdate = prefs.getLong("last_auto_update_time", 0L)
        val now = System.currentTimeMillis()
        val oneDayMillis = 24 * 60 * 60 * 1000L // 24 hours daily check

        // First launch: initialize timestamp to avoid heavy network/exec operations on first boot
        if (lastUpdate == 0L) {
            prefs.edit().putLong("last_auto_update_time", now).apply()
            return@withContext false
        }

        if (now - lastUpdate >= oneDayMillis) {
            isUpdatingEngine = true
            try {
                if (ensureInitialized(ctx)) {
                    YoutubeDL.getInstance().updateYoutubeDL(ctx)
                    val newVer = getOrFetchVersion(ctx, forceRefresh = true)
                    setUpdatedVersion(newVer)
                    prefs.edit().putLong("last_auto_update_time", now).apply()
                    Log.d("EngineInitState", "Daily auto update completed: $newVer")
                    return@withContext true
                }
            } catch (e: Exception) {
                Log.w("EngineInitState", "Daily auto update skipped or failed: ${e.message}")
            } finally {
                isUpdatingEngine = false
            }
        }
        return@withContext false
    }
}
