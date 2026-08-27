package com.anmei.warehouseputawayrecorder.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.anmei.warehouseputawayrecorder.R

class WarehouseRecorderService : Service() {

    companion object {

        private const val TAG =
            "WarehouseBackgroundService"

        private const val CHANNEL_ID =
            "warehouse_background_service"

        private const val NOTIFICATION_ID =
            1001
    }

    override fun onCreate() {

        super.onCreate()

        Log.i(
            TAG,
            "=========================================="
        )

        Log.i(
            TAG,
            "WarehouseBackgroundService created"
        )

        Log.i(
            TAG,
            "=========================================="
        )

        createNotificationChannel()

        startForeground(
            NOTIFICATION_ID,
            createNotification()
        )
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        Log.i(
            TAG,
            "WarehouseBackgroundService onStartCommand"
        )

        /*
         * START_STICKY：
         *
         * 如果 Android 因为内存等原因杀掉 Service，
         * 系统有机会重新创建 Service。
         */
        return START_STICKY
    }

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT <
            Build.VERSION_CODES.O
        ) {
            return
        }

        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "WarehousePutawayRecorder",
                NotificationManager.IMPORTANCE_LOW
            )

        channel.description =
            "Warehouse background service"

        channel.setShowBadge(false)

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        manager.createNotificationChannel(
            channel
        )
    }

    private fun createNotification(): Notification {

        return NotificationCompat
            .Builder(
                this,
                CHANNEL_ID
            )
            .setContentTitle(
                "WarehousePutawayRecorder"
            )
            .setContentText(
                "Warehouse service is running"
            )
            .setSmallIcon(
                R.mipmap.ic_launcher
            )
            .setOngoing(true)
            .setSilent(true)
            .setCategory(
                NotificationCompat.CATEGORY_SERVICE
            )
            .build()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? {

        return null
    }

    override fun onDestroy() {

        Log.i(
            TAG,
            "WarehouseBackgroundService destroyed"
        )

        super.onDestroy()
    }
}