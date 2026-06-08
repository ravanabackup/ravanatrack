package com.example.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.manager.DeviceScanManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class SignalLocatorService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var scanManager: DeviceScanManager

    companion object {
        const val CHANNEL_ID = "signal_locator_foreground_service"
        const val NOTIFICATION_ID = 4567
        
        const val ACTION_START = "ACTION_START_SIGNAL_LOCATOR"
        const val ACTION_STOP = "ACTION_STOP_SIGNAL_LOCATOR"
    }

    override fun onCreate() {
        super.onCreate()
        scanManager = DeviceScanManager.getInstance(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startForegroundService()
            ACTION_STOP -> stopForegroundService()
        }
        return START_STICKY
    }

    @SuppressLint("ForegroundServiceType")
    private fun startForegroundService() {
        val notification = buildNotification("Signal Tracking Active", "Preparing hardware sensors...")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, 
                notification, 
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        scanManager.startScanning()
        observeSignals()
    }

    private fun observeSignals() {
        serviceScope.launch {
            combine(scanManager.targetMacAddress, scanManager.scannedDevices) { targetMac, devices ->
                Pair(targetMac, devices)
            }.collect { (targetMac, devices) ->
                val targetDevice = devices.find { it.macAddress == targetMac }
                val contentTitle = if (targetDevice != null) {
                    "Tracking Target: ${targetDevice.name}"
                } else {
                    "Signal Locator Active"
                }
                
                val contentText = if (targetDevice != null) {
                    "Calibrated Signal: %.1f dBm (Raw: %d)".format(targetDevice.smoothedRssi, targetDevice.rawRssi)
                } else {
                    "Scanning ${devices.size} nearby wireless networks..."
                }

                updateNotification(contentTitle, contentText)
            }
        }
    }

    private fun updateNotification(title: String, text: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = buildNotification(title, text)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(title: String, text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Using standard Android icon for signal/cellular activity
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        return builder.build()
    }

    private fun stopForegroundService() {
        scanManager.stopScanning()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Signal Tracking Services",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Displays real-time signal calculations for tracked Bluetooth and Wi-Fi devices"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
