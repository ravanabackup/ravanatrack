package com.example.data

import androidx.room.*
import com.example.model.PinnedDevice
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Query("SELECT * FROM pinned_devices ORDER BY timestamp DESC")
    fun getAllPinnedDevices(): Flow<List<PinnedDevice>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: PinnedDevice)

    @Query("DELETE FROM pinned_devices WHERE macAddress = :macAddress")
    suspend fun deleteDeviceByMac(macAddress: String)

    @Query("SELECT EXISTS(SELECT 1 FROM pinned_devices WHERE macAddress = :macAddress LIMIT 1)")
    fun isPinned(macAddress: String): Flow<Boolean>
}
