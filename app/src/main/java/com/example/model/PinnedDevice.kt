package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pinned_devices")
data class PinnedDevice(
    @PrimaryKey val macAddress: String,
    val name: String,
    val alias: String,
    val deviceType: String, // "BLUETOOTH" or "WIFI"
    val timestamp: Long = System.currentTimeMillis()
)
