package com.example.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.DeviceRepository
import com.example.manager.DeviceScanManager
import com.example.model.PinnedDevice
import com.example.model.ScannedDevice
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SignalLocatorViewModel(
    private val repository: DeviceRepository,
    private val scanManager: DeviceScanManager
) : ViewModel() {

    // Filtering inputs
    val filterMacQuery = MutableStateFlow("")

    // Hot streams from scanned hardware
    val scannedDevices: StateFlow<List<ScannedDevice>> = scanManager.scannedDevices
    val currentCompassAzimuth: StateFlow<Float> = scanManager.currentCompassAzimuth
    val targetMacAddress: StateFlow<String?> = scanManager.targetMacAddress
    val isCalibrating: StateFlow<Boolean> = scanManager.isCalibrating
    val maxCalibratedRssi: StateFlow<Double> = scanManager.maxCalibratedRssi
    val calibratedSignalAzimuth: StateFlow<Float?> = scanManager.calibratedSignalAzimuth
    val calibrationRadarData: StateFlow<Map<Int, Double>> = scanManager.calibrationRadarData

    // Live list of devices filtered by MAC address query
    val filteredScannedDevices: StateFlow<List<ScannedDevice>> = combine(
        scannedDevices,
        filterMacQuery
    ) { devices, query ->
        if (query.isBlank()) {
            devices
        } else {
            devices.filter { it.macAddress.contains(query, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Saved database targets
    val pinnedDevices: StateFlow<List<PinnedDevice>> = repository.allPinnedDevices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Start scan sessions
     */
    fun startScanning() {
        scanManager.startScanning()
    }

    /**
     * Stop scan sessions safely
     */
    fun stopScanning() {
        scanManager.stopScanning()
    }

    /**
     * Set target to locate
     */
    fun selectTargetDevice(macAddress: String?) {
        scanManager.selectTargetDevice(macAddress)
    }

    /**
     * Pin target to local Room DB
     */
    fun pinDevice(device: ScannedDevice, customAlias: String) {
        viewModelScope.launch {
            repository.pinDevice(
                PinnedDevice(
                    macAddress = device.macAddress,
                    name = device.name,
                    alias = customAlias,
                    deviceType = device.deviceType
                )
            )
        }
    }

    /**
     * Direct database pinning for predefined entries
     */
    fun pinDatabaseDevice(device: PinnedDevice) {
        viewModelScope.launch {
            repository.pinDevice(device)
        }
    }

    /**
     * Unpin target from local DB
     */
    fun unpinDevice(macAddress: String) {
        viewModelScope.launch {
            repository.unpinDevice(macAddress)
            if (scanManager.targetMacAddress.value == macAddress) {
                scanManager.selectTargetDevice(null)
            }
        }
    }

    fun isPinned(macAddress: String): Flow<Boolean> {
        return repository.isPinned(macAddress)
    }

    /**
     * Active Wi-Fi Scan (throttling-aware)
     */
    fun triggerWifiScan(): Boolean {
        return scanManager.triggerWifiScan()
    }

    /**
     * Calibrate rotation 360-degree orientation signal
     */
    fun toggleCalibration() {
        scanManager.toggleCalibration()
    }

    fun resetCalibration() {
        scanManager.resetCalibration()
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val database = AppDatabase.getDatabase(context)
            val repository = DeviceRepository(database.deviceDao())
            val scanManager = DeviceScanManager.getInstance(context)
            @Suppress("UNCHECKED_CAST")
            return SignalLocatorViewModel(repository, scanManager) as T
        }
    }
}
