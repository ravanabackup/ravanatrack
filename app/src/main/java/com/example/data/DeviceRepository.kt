package com.example.data

import com.example.model.PinnedDevice
import kotlinx.coroutines.flow.Flow

class DeviceRepository(private val deviceDao: DeviceDao) {
    val allPinnedDevices: Flow<List<PinnedDevice>> = deviceDao.getAllPinnedDevices()

    suspend fun pinDevice(device: PinnedDevice) {
        deviceDao.insertDevice(device)
    }

    suspend fun unpinDevice(macAddress: String) {
        deviceDao.deleteDeviceByMac(macAddress)
    }

    fun isPinned(macAddress: String): Flow<Boolean> {
        return deviceDao.isPinned(macAddress)
    }
}
