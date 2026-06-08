package com.example.manager

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.wifi.WifiManager
import android.util.Log
import com.example.filter.KalmanFilter
import com.example.model.ScannedDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

@SuppressLint("MissingPermission")
class DeviceScanManager private constructor(private val context: Context) : SensorEventListener {

    // Device context
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var bluetoothLeScanner: BluetoothLeScanner? = null

    // Kalman filters for each MAC address
    private val kalmanFilters = ConcurrentHashMap<String, KalmanFilter>()

    // Live state streams
    private val _scannedDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val scannedDevices: StateFlow<List<ScannedDevice>> = _scannedDevices.asStateFlow()

    private val _currentCompassAzimuth = MutableStateFlow(0f) // 0 to 360 degrees
    val currentCompassAzimuth: StateFlow<Float> = _currentCompassAzimuth.asStateFlow()

    // Tracking target details
    private val _targetMacAddress = MutableStateFlow<String?>(null)
    val targetMacAddress: StateFlow<String?> = _targetMacAddress.asStateFlow()

    // Calibration states
    private val _isCalibrating = MutableStateFlow(false)
    val isCalibrating: StateFlow<Boolean> = _isCalibrating.asStateFlow()

    private val _maxCalibratedRssi = MutableStateFlow(-120.0)
    val maxCalibratedRssi: StateFlow<Double> = _maxCalibratedRssi.asStateFlow()

    private val _calibratedSignalAzimuth = MutableStateFlow<Float?>(null)
    val calibratedSignalAzimuth: StateFlow<Float?> = _calibratedSignalAzimuth.asStateFlow()

    // Circular radar map of signal strength: Sector Int (0..15) -> Max RSSI observed
    private val _calibrationRadarData = MutableStateFlow<Map<Int, Double>>(emptyMap())
    val calibrationRadarData: StateFlow<Map<Int, Double>> = _calibrationRadarData.asStateFlow()

    // Sensor storage arrays
    private val gravityValues = FloatArray(3)
    private val magneticValues = FloatArray(3)
    private var hasGravity = false
    private var hasMagnetic = false

    // Scanner callbacks
    private var wifiReceiver: BroadcastReceiver? = null
    private var isScanning = false

    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let { handleBleScanResult(it) }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { handleBleScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE scan failed with error code: $errorCode")
        }
    }

    companion object {
        private const val TAG = "DeviceScanManager"
        
        @Volatile
        private var INSTANCE: DeviceScanManager? = null

        fun getInstance(context: Context): DeviceScanManager {
            return INSTANCE ?: synchronized(this) {
                val instance = DeviceScanManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    /**
     * Set the device we are tracking (via MAC/BSSID) for compass pointing
     */
    fun selectTargetDevice(macAddress: String?) {
        _targetMacAddress.value = macAddress
        // Reset calibration when switching target
        resetCalibration()
    }

    /**
     * Toggles the 360-degree orientation signal calibration mode
     */
    fun toggleCalibration() {
        if (_isCalibrating.value) {
            _isCalibrating.value = false
        } else {
            resetCalibration()
            _isCalibrating.value = true
        }
    }

    fun resetCalibration() {
        _maxCalibratedRssi.value = -120.0
        _calibratedSignalAzimuth.value = null
        _calibrationRadarData.value = emptyMap()
    }

    /**
     * Start scan operations and sensor fusion listeners
     */
    fun startScanning() {
        if (isScanning) return
        isScanning = true

        registerSensors()
        startBleScanning()
        registerWifiScanReceiver()
        triggerWifiScan() // Fetch initially cached results
    }

    /**
     * Stop all sensors and hardware scans safely to protect resources
     */
    fun stopScanning() {
        if (!isScanning) return
        isScanning = false

        unregisterSensors()
        stopBleScanning()
        unregisterWifiReceiver()
    }

    private fun registerSensors() {
        val rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rotationVector != null) {
            sensorManager.registerListener(this, rotationVector, SensorManager.SENSOR_DELAY_UI)
        } else {
            // Fallback to accelerometer + compass
            val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val magnetic = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
            if (accel != null) sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_UI)
            if (magnetic != null) sensorManager.registerListener(this, magnetic, SensorManager.SENSOR_DELAY_UI)
        }
    }

    private fun unregisterSensors() {
        sensorManager.unregisterListener(this)
        hasGravity = false
        hasMagnetic = false
    }

    private fun startBleScanning() {
        try {
            bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            bluetoothLeScanner?.startScan(null, settings, bleScanCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "BLUETOOTH_SCAN permission not granted: ${e.message}")
        }
    }

    private fun stopBleScanning() {
        try {
            bluetoothLeScanner?.stopScan(bleScanCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to stop BLE scan due to missing permission: ${e.message}")
        }
    }

    private fun registerWifiScanReceiver() {
        wifiReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                    processWifiScanResults()
                }
            }
        }
        val intentFilter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        context.registerReceiver(wifiReceiver, intentFilter)
    }

    private fun unregisterWifiReceiver() {
        wifiReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                Log.e(TAG, "Wifi receiver not registered: ${e.message}")
            }
        }
        wifiReceiver = null
    }

    /**
     * Triggers active Wi-Fi hardware scan.
     * Note: Android severely throttles this API. UI must restrict frequent clicks.
     */
    fun triggerWifiScan(): Boolean {
        return try {
            wifiManager.startScan()
        } catch (e: SecurityException) {
            Log.e(TAG, "ACCESS_FINE_LOCATION not granted for Wi-Fi scanning: ${e.message}")
            false
        }
    }

    private fun handleBleScanResult(result: ScanResult) {
        val device = result.device
        val rawRssi = result.rssi
        val mac = device.address ?: "00:00:00:00:00:00"
        val name = result.scanRecord?.deviceName ?: device.name ?: "Unknown BLE"

        updateDeviceRssi(mac, name, rawRssi, "BLUETOOTH")
    }

    private fun processWifiScanResults() {
        try {
            val results = wifiManager.scanResults
            results?.forEach { scan ->
                val mac = scan.BSSID ?: "00:00:00:00:00:00"
                val ssid = if (scan.SSID.isNullOrEmpty()) "Hidden Network" else scan.SSID
                val rawRssi = scan.level

                updateDeviceRssi(mac, ssid, rawRssi, "WIFI")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException collecting Wi-Fi results: ${e.message}")
        }
    }

    private fun updateDeviceRssi(mac: String, name: String, rawRssi: Int, type: String) {
        // Collect or initialize Kalman filter for this device
        val filter = kalmanFilters.getOrPut(mac) {
            KalmanFilter(initialEstimate = rawRssi.toDouble())
        }
        
        val smoothedRssi = filter.update(rawRssi.toDouble())

        // Save scanned details
        val newDevice = ScannedDevice(
            macAddress = mac,
            name = name,
            rawRssi = rawRssi,
            smoothedRssi = smoothedRssi,
            deviceType = type,
            timestamp = System.currentTimeMillis()
        )

        val currentList = _scannedDevices.value.toMutableList()
        val index = currentList.indexOfFirst { it.macAddress == mac }
        if (index != -1) {
            currentList[index] = newDevice
        } else {
            currentList.add(newDevice)
        }

        // Drop stale results (older than 25 seconds) to keep scanning snappy
        val now = System.currentTimeMillis()
        val filteredList = currentList.filter { now - it.timestamp < 25000 }
        _scannedDevices.value = filteredList

        // If this device is the actively tracked target, feed calibration engine
        if (_targetMacAddress.value == mac) {
            processCalibration(smoothedRssi)
        }
    }

    /**
     * Monitors signal peak directions during physical rotations
     */
    private fun processCalibration(rssi: Double) {
        val currentHeading = _currentCompassAzimuth.value
        
        // Map 360 degrees to 16 sectors (22.5 degrees per sector)
        val numSectors = 16
        val sectorDegrees = 360f / numSectors
        val sectorIndex = ((currentHeading + (sectorDegrees / 2f)) % 360f / sectorDegrees).toInt()

        // Update radial signal mesh
        val radarData = _calibrationRadarData.value.toMutableMap()
        val existingRssi = radarData[sectorIndex]
        if (existingRssi == null || rssi > existingRssi) {
            radarData[sectorIndex] = rssi
            _calibrationRadarData.value = radarData
        }

        // Record overall maximum signal vector
        if (_isCalibrating.value && rssi > _maxCalibratedRssi.value) {
            _maxCalibratedRssi.value = rssi
            _calibratedSignalAzimuth.value = currentHeading
        }
    }

    // --- SensorEventListener Implementation ---

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            val orientationValues = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientationValues)
            
            // Azimuth is around Z axis
            var azimuth = Math.toDegrees(orientationValues[0].toDouble()).toFloat()
            if (azimuth < 0) azimuth += 360f
            _currentCompassAzimuth.value = azimuth
        } else {
            // Fallback Accelerometer + Geomagnetic
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    System.arraycopy(event.values, 0, gravityValues, 0, 3)
                    hasGravity = true
                }
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    System.arraycopy(event.values, 0, magneticValues, 0, 3)
                    hasMagnetic = true
                }
            }

            if (hasGravity && hasMagnetic) {
                val rotationMatrix = FloatArray(9)
                val inclinationMatrix = FloatArray(9)
                val success = SensorManager.getRotationMatrix(rotationMatrix, inclinationMatrix, gravityValues, magneticValues)
                if (success) {
                    val orientationValues = FloatArray(3)
                    SensorManager.getOrientation(rotationMatrix, orientationValues)
                    var azimuth = Math.toDegrees(orientationValues[0].toDouble()).toFloat()
                    if (azimuth < 0) azimuth += 360f
                    _currentCompassAzimuth.value = azimuth
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
