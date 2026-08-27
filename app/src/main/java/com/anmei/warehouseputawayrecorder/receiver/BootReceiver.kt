package com.anmei.warehouseputawayrecorder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.anmei.warehouseputawayrecorder.service.WarehouseRecorderService

class BootReceiver : BroadcastReceiver() {

    companion object {

        private const val TAG = "BootReceiver"
    }

    override fun onReceive(
        context: Context,
        intent: Intent?
    ) {

        Log.i(TAG, "==========================================")
        Log.i(TAG, "BootReceiver received")
        Log.i(TAG, "action=${intent?.action}")
        Log.i(TAG, "==========================================")

        when (intent?.action) {

            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> {

                Log.i(
                    TAG,
                    "Device boot completed"
                )

                startBackgroundService(context)
            }
        }
    }

    private fun startBackgroundService(
        context: Context
    ) {

        try {

            val serviceIntent =
                Intent(
                    context,
                    WarehouseRecorderService::class.java
                )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                context.startForegroundService(
                    serviceIntent
                )

                Log.i(
                    TAG,
                    "WarehouseBackgroundService started as foreground service"
                )

            } else {

                context.startService(
                    serviceIntent
                )

                Log.i(
                    TAG,
                    "WarehouseBackgroundService started"
                )
            }

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to start WarehouseBackgroundService",
                e
            )
        }
    }
}