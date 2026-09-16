package com.allsocial.sealclone

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SealApp : Application() {
    companion object {
        lateinit var instance: SealApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        EngineInitState.setApplicationContext(this)

        // Initialize binaries in background to keep UI startup instant and avoid audit log flood
        CoroutineScope(Dispatchers.IO).launch {
            try {
                EngineInitState.ensureInitialized(this@SealApp)
                Log.d("SealApp", "Engine initialization completed in background")
            } catch (e: Exception) {
                Log.e("SealApp", "Failed to initialize YoutubeDL engine: ${e.message}")
            }
        }
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                DownloadForegroundService.CHANNEL_ID,
                DownloadForegroundService.CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time status and progress for media downloads"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
