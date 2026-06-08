package com.example.model

data class ScannedDevice(
    val macAddress: String,
    val name: String,
    val rawRssi: Int,
    val smoothedRssi: Double,
    val deviceType: String, // "BLUETOOTH" or "WIFI"
    val timestamp: Long = System.currentTimeMillis()
)
