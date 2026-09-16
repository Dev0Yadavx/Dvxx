package com.allsocial.sealclone

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SealApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            EngineInitState.ensureInitialized(this)
            Log.d("SealApp", "Engine initialization completed")
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("SealApp", "Failed to initialize YoutubeDL engine", e)
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
