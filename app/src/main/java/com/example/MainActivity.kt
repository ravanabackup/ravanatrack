package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.PinnedDevice
import com.example.model.ScannedDevice
import com.example.service.SignalLocatorService
import com.example.ui.GaugeTrackerScreen
import com.example.ui.PinnedDeviceCard
import com.example.ui.ScannedDeviceRow
import com.example.ui.SignalRadarScope
import com.example.ui.theme.*
import com.example.viewmodel.SignalLocatorViewModel

enum class AppTab(val title: String) {
    BLUETOOTH("Bluetooth"),
    WIFI("Wi-Fi"),
    SAVED("Saved DB")
}

class MainActivity : ComponentActivity() {

    private val viewModel: SignalLocatorViewModel by viewModels {
        SignalLocatorViewModel.Factory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            MyApplicationTheme {
                val context = LocalContext.current
                var permissionsGranted by remember { mutableStateOf(hasRequiredPermissions(context)) }

                // Runtime permission requesters
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { results ->
                    permissionsGranted = results.values.all { it }
                    if (permissionsGranted) {
                        Toast.makeText(context, "All hardware sensors ready", Toast.LENGTH_SHORT).show()
                        // Auto start scanning once approved
                        viewModel.startScanning()
                    } else {
                        Toast.makeText(context, "Permissions required for tracking", Toast.LENGTH_LONG).show()
                    }
                }

                LaunchedEffect(permissionsGranted) {
                    if (permissionsGranted) {
                        viewModel.startScanning()
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (!permissionsGranted) {
                            PermissionWallScreen(
                                onRequest = {
                                    val requirements = getRequiredPermissionsList()
                                    permissionLauncher.launch(requirements.toTypedArray())
                                }
                            )
                        } else {
                            MainLocatorContent(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasRequiredPermissions(this)) {
            viewModel.startScanning()
        }
    }

    override fun onPause() {
        super.onPause()
    }

    private fun hasRequiredPermissions(context: Context): Boolean {
        return getRequiredPermissionsList().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun getRequiredPermissionsList(): List<String> {
        val list = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list.add(Manifest.permission.BLUETOOTH_SCAN)
            list.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        list.add(Manifest.permission.ACCESS_FINE_LOCATION)
        list.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return list
    }
}

/**
 * Visual landing wall shown until fine-grained Bluetooth/Wi-Fi and notification permissions are cleared.
 */
@Composable
fun PermissionWallScreen(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .background(RadarDarkBg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.CompassCalibration,
            contentDescription = "Radar calibration",
            tint = RadarSecondary,
            modifier = Modifier.size(72.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "HARDWARE SECURE ACCESS",
            color = RadarPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = "To track wireless beacons and calculate physical bearings, this application requires access to the system Bluetooth LE scanner, Wi-Fi hardware state, and direction sensors.",
            color = RadarOnBg.copy(alpha = 0.8f),
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onRequest,
            colors = ButtonDefaults.buttonColors(
                containerColor = RadarPrimary,
                contentColor = Color.Black
            ),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(48.dp)
                .testTag("request_permissions_button")
        ) {
            Text(
                text = "GRANT ACCESS",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/**
 * Primary system console content combining Radar compass and split target scanners.
 */
@Composable
fun MainLocatorContent(viewModel: SignalLocatorViewModel) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(AppTab.BLUETOOTH) }
    
    // Live observations from view-model
    val currentHeading by viewModel.currentCompassAzimuth.collectAsStateWithLifecycle()
    val targetMac by viewModel.targetMacAddress.collectAsStateWithLifecycle()
    val isCalibrating by viewModel.isCalibrating.collectAsStateWithLifecycle()
    val calibratedSignalAzimuth by viewModel.calibratedSignalAzimuth.collectAsStateWithLifecycle()
    val radarData by viewModel.calibrationRadarData.collectAsStateWithLifecycle()
    
    val filteredScannedDevices by viewModel.filteredScannedDevices.collectAsStateWithLifecycle()
    val pinnedDevices by viewModel.pinnedDevices.collectAsStateWithLifecycle()
    
    val currentTargetDevice = filteredScannedDevices.find { it.macAddress == targetMac }
        ?: scannedDeviceFromPinned(pinnedDevices.find { it.macAddress == targetMac })

    // Input state controls
    val macQuery by viewModel.filterMacQuery.collectAsStateWithLifecycle()
    var showPinDialogDevice by remember { mutableStateOf<ScannedDevice?>(null) }
    var pinNicknameText by remember { mutableStateOf("") }
    
    // Background tracking service toggle
    var isServiceRunning by remember { mutableStateOf(false) }

    LaunchedEffect(isServiceRunning) {
        if (isServiceRunning) {
            val intent = Intent(context, SignalLocatorService::class.java).apply {
                action = SignalLocatorService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } else {
            val intent = Intent(context, SignalLocatorService::class.java).apply {
                action = SignalLocatorService.ACTION_STOP
            }
            context.startService(intent)
        }
    }

    if (targetMac != null && currentTargetDevice != null) {
        val isPaired = pinnedDevices.any { it.macAddress == targetMac }
        GaugeTrackerScreen(
            device = currentTargetDevice,
            isPaired = isPaired,
            onBack = { viewModel.selectTargetDevice(null) },
            onFoundIt = {
                viewModel.selectTargetDevice(null)
                Toast.makeText(context, "Nice! You located your device.", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxSize()
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(RadarDarkBg),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // App header with system telemetry
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "RADAR LOCATOR",
                        color = RadarSecondary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "AZIMUTH: %.1f° | SYSTEM ONLINE".format(currentHeading),
                        color = RadarPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                
                // Foreground service toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "BG LOCK",
                        color = if (isServiceRunning) RadarPrimary else RadarMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                    Switch(
                        checked = isServiceRunning,
                        onCheckedChange = { isServiceRunning = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = RadarPrimary,
                            checkedTrackColor = RadarPrimary.copy(alpha = 0.25f),
                            uncheckedThumbColor = RadarMuted,
                            uncheckedTrackColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .scale(0.8f)
                            .testTag("bg_service_switch")
                    )
                }
            }

            // 1. Radar Compass scope
            SignalRadarScope(
                currentHeading = currentHeading,
                targetDevice = currentTargetDevice,
                calibratedSignalAzimuth = calibratedSignalAzimuth,
                isCalibrating = isCalibrating,
                radarData = radarData,
                modifier = Modifier.testTag("signal_radar_scope")
            )

            // Dynamic Action Panel for active lock
            if (targetMac != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { viewModel.toggleCalibration() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isCalibrating) RadarAccent else RadarSecondary.copy(alpha = 0.2f),
                            contentColor = if (isCalibrating) Color.Black else RadarSecondary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .testTag("calibration_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isCalibrating) Icons.Default.PowerSettingsNew else Icons.Default.RotateRight,
                            contentDescription = "Calibration",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isCalibrating) "END SWEEP" else "CALIBRATE SENSORS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Button(
                        onClick = { viewModel.selectTargetDevice(null) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Red.copy(alpha = 0.15f),
                            contentColor = Color.Red
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .height(40.dp)
                            .testTag("break_lock_button")
                    ) {
                        Text(
                            text = "BREAK LOCK",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // 2. Hardware scan split controls
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            HorizontalDivider(color = RadarSecondary.copy(alpha = 0.15f))

            // MAC filtering search panel
            if (activeTab != AppTab.SAVED) {
                OutlinedTextField(
                    value = macQuery,
                    onValueChange = { viewModel.filterMacQuery.value = it },
                    placeholder = {
                        Text(
                            "Filter by MAC/BSSID Address...",
                            color = RadarMuted,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = "Search icon",
                            tint = RadarSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (macQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.filterMacQuery.value = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear search",
                                    tint = RadarMuted
                                )
                            }
                        }
                    },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = RadarOnSurface,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .height(52.dp)
                        .testTag("mac_filter_input"),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RadarSecondary,
                        unfocusedBorderColor = RadarMuted.copy(alpha = 0.3f),
                        unfocusedContainerColor = RadarSurface,
                        focusedContainerColor = RadarSurface
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Wi-Fi Specific Throttling Banner
            if (activeTab == AppTab.WIFI) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(RadarAccent.copy(alpha = 0.1f))
                        .border(1.dp, RadarAccent.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Throttling warning",
                        tint = RadarAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Android limits hardware Wi-Fi scans to once every 2 mins in background. Tap Scan manually to refresh safely.",
                        color = RadarAccent,
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Button(
                        onClick = {
                            val scanTriggered = viewModel.triggerWifiScan()
                            if (scanTriggered) {
                                Toast.makeText(context, "Wi-Fi scan initiated", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Wi-Fi scan throttled or unavailable", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RadarAccent,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier
                            .height(28.dp)
                            .testTag("trigger_wifi_scan_button")
                    ) {
                        Text(
                            "SCAN NOW",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Dynamic Scanning list views
            val displayDevices = filteredScannedDevices.filter {
                if (activeTab == AppTab.BLUETOOTH) it.deviceType == "BLUETOOTH" else it.deviceType == "WIFI"
            }

            if (activeTab == AppTab.SAVED) {
                // SAVED ROOM SQLite PERSISTED TABS
                if (pinnedDevices.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.BookmarkBorder,
                                contentDescription = "No saved devices",
                                tint = RadarMuted,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "SQL DATABASE EMPTY",
                                color = RadarMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Pin frequently tracked devices using the star icon on scanner cards to securely register them to your system library.",
                                color = RadarOnBg.copy(alpha = 0.5f),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp, start = 16.dp, end = 16.dp)
                            )
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(pinnedDevices) { pinned ->
                            // Look up if we currently see this pinned device scan level
                            val liveScan = filteredScannedDevices.find { it.macAddress == pinned.macAddress }
                            PinnedDeviceCard(
                                mac = pinned.macAddress,
                                name = pinned.name,
                                alias = pinned.alias,
                                type = pinned.deviceType,
                                isTarget = targetMac == pinned.macAddress,
                                liveDevice = liveScan,
                                onTrackToggle = {
                                    if (targetMac == pinned.macAddress) {
                                        viewModel.selectTargetDevice(null)
                                    } else {
                                        viewModel.selectTargetDevice(pinned.macAddress)
                                    }
                                },
                                onUnpin = { viewModel.unpinDevice(pinned.macAddress) }
                            )
                        }
                    }
                }
            } else {
                // ACTIVE SCAN TABS (BLUETOOTH / WIFI)
                if (displayDevices.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = if (activeTab == AppTab.BLUETOOTH) RadarSecondary else RadarPrimary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "GATHERING ${activeTab.title.uppercase()} SIGNALS...",
                                color = RadarMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(displayDevices) { device ->
                            var isPinned by remember(pinnedDevices) {
                                mutableStateOf(pinnedDevices.any { it.macAddress == device.macAddress })
                            }
                            
                            ScannedDeviceRow(
                                device = device,
                                isPinned = isPinned,
                                isTarget = targetMac == device.macAddress,
                                onTrackToggle = {
                                    if (targetMac == device.macAddress) {
                                        viewModel.selectTargetDevice(null)
                                    } else {
                                        viewModel.selectTargetDevice(device.macAddress)
                                    }
                                },
                                onPinToggle = {
                                    if (isPinned) {
                                        viewModel.unpinDevice(device.macAddress)
                                    } else {
                                        pinNicknameText = ""
                                        showPinDialogDevice = device
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // 3. Navigation Bar (Tab Switcher)
        NavigationBar(
            containerColor = RadarSurface,
            tonalElevation = 8.dp,
            modifier = Modifier.testTag("app_bottom_nav_bar")
        ) {
            NavigationBarItem(
                selected = activeTab == AppTab.BLUETOOTH,
                onClick = { activeTab = AppTab.BLUETOOTH },
                icon = { Icon(Icons.Default.Bluetooth, contentDescription = "Bluetooth tab") },
                label = { Text("Bluetooth", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.Black,
                    selectedTextColor = RadarSecondary,
                    indicatorColor = RadarSecondary,
                    unselectedIconColor = RadarMuted,
                    unselectedTextColor = RadarMuted
                )
            )

            NavigationBarItem(
                selected = activeTab == AppTab.WIFI,
                onClick = { activeTab = AppTab.WIFI },
                icon = { Icon(Icons.Default.Wifi, contentDescription = "WiFi tab") },
                label = { Text("Wi-Fi", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.Black,
                    selectedTextColor = RadarPrimary,
                    indicatorColor = RadarPrimary,
                    unselectedIconColor = RadarMuted,
                    unselectedTextColor = RadarMuted
                )
            )

            NavigationBarItem(
                selected = activeTab == AppTab.SAVED,
                onClick = { activeTab = AppTab.SAVED },
                icon = { Icon(Icons.Default.Star, contentDescription = "Saved database tab") },
                label = { Text("Saved DB", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.Black,
                    selectedTextColor = RadarAccent,
                    indicatorColor = RadarAccent,
                    unselectedIconColor = RadarMuted,
                    unselectedTextColor = RadarMuted
                )
            )
        }
    }
    }

    // SQLite Pin Register Dialog
    showPinDialogDevice?.let { device ->
        AlertDialog(
            onDismissRequest = { showPinDialogDevice = null },
            title = {
                Text(
                    text = "PIN SIGNAL TO DATABASE",
                    color = RadarSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "Register MAC ${device.macAddress} inside local storage. Enter a friendly label/alias for tracking:",
                        color = RadarOnSurface,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = pinNicknameText,
                        onValueChange = { pinNicknameText = it },
                        placeholder = { Text("e.g. My Smart Watch", color = RadarMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace) },
                        textStyle = androidx.compose.ui.text.TextStyle(color = RadarOnSurface, fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pin_device_alias_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RadarSecondary,
                            unfocusedBorderColor = RadarMuted.copy(alpha = 0.5f),
                            unfocusedContainerColor = RadarSurface,
                            focusedContainerColor = RadarSurface
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalAlias = pinNicknameText.trim().ifEmpty { "Saved Device" }
                        viewModel.pinDevice(device, finalAlias)
                        showPinDialogDevice = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RadarPrimary, contentColor = Color.Black)
                ) {
                    Text("SAVE TO DATABASE", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialogDevice = null }) {
                    Text("CANCEL", color = RadarMuted, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            },
            containerColor = RadarSurface,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

private fun scannedDeviceFromPinned(pinned: PinnedDevice?): ScannedDevice? {
    if (pinned == null) return null
    return ScannedDevice(
        macAddress = pinned.macAddress,
        name = pinned.name,
        rawRssi = -100,
        smoothedRssi = -100.0,
        deviceType = pinned.deviceType
    )
}
