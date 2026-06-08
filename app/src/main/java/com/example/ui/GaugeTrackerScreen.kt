package com.example.ui

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ScannedDevice
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.sin

/**
 * A highly immersive tactile proximity panel based precisely on the user's reference image.
 * Combines an analog 3-color arc segment gauge (Far/Closer/Near) with a physical needle sensor,
 * active wireless dBm telemetry, haptic pulses, and sound beep guidance.
 */
@Composable
fun GaugeTrackerScreen(
    device: ScannedDevice,
    isPaired: Boolean,
    onBack: () -> Unit,
    onFoundIt: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var soundEnabled by rememberSaveable { mutableStateOf(false) }
    var vibrationEnabled by rememberSaveable { mutableStateOf(true) } // Vibration is ON by default in the image!
    var showHelpDialog by remember { mutableStateOf(false) }

    val deviceRssi = device.smoothedRssi
    val isConnected = System.currentTimeMillis() - device.timestamp < 10000

    // Sound alert generator thread
    if (soundEnabled) {
        val currentRssiState = rememberUpdatedState(deviceRssi)
        LaunchedEffect(Unit) {
            val toneGenerator = try {
                ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            } catch (e: Exception) {
                null
            }
            try {
                while (isActive) {
                    val rssiVal = currentRssiState.value
                    val delayInterval = when {
                        rssiVal >= -65.0 -> 240L  // Very close: ultra rapid search pips
                        rssiVal >= -80.0 -> 650L  // Medium: normal periodic telemetry pips
                        else -> 1400L             // Far: slow searching telemetry
                    }
                    val beepType = when {
                        rssiVal >= -65.0 -> ToneGenerator.TONE_PROP_BEEP2
                        rssiVal >= -80.0 -> ToneGenerator.TONE_PROP_BEEP
                        else -> ToneGenerator.TONE_CDMA_PIP
                    }
                    toneGenerator?.startTone(beepType, 70)
                    delay(delayInterval)
                }
            } catch (e: Exception) {
                // Ignore errors
            } finally {
                toneGenerator?.release()
            }
        }
    }

    // Vibrator pulsation generator thread
    if (vibrationEnabled) {
        val currentRssiState = rememberUpdatedState(deviceRssi)
        LaunchedEffect(Unit) {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            try {
                while (isActive) {
                    val rssiVal = currentRssiState.value
                    val delayInterval = when {
                        rssiVal >= -65.0 -> 240L  // Rapid vibration pulses
                        rssiVal >= -80.0 -> 650L  // Intricate pacing
                        else -> 1400L             // Grounded searching pulses
                    }
                    val vibrationLength = when {
                        rssiVal >= -65.0 -> 80L
                        rssiVal >= -80.0 -> 60L
                        else -> 40L
                    }
                    vibrator?.let {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            it.vibrate(VibrationEffect.createOneShot(vibrationLength, VibrationEffect.DEFAULT_AMPLITUDE))
                        } else {
                            @Suppress("DEPRECATION")
                            it.vibrate(vibrationLength)
                        }
                    }
                    delay(delayInterval)
                }
            } catch (e: Exception) {
                // Ignore exceptions
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F11)) // Dark black/charcoal background matching reference
    ) {
        // Core Top App Bar from image
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("gauge_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back back Arrow",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Text(
                text = device.name,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = { showHelpDialog = true },
                modifier = Modifier.testTag("gauge_help_button")
            ) {
                Icon(
                    imageVector = Icons.Default.HelpOutline,
                    contentDescription = "Help dialog icon",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Main content in a vertical scrollable column to fit beautifully everywhere
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 1. Proximity Meter Gauge in the center
            ProximityGauge(
                rssi = deviceRssi,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
            )

            // Current dBm reading text styled centered
            Text(
                text = "%.1f dBm".format(deviceRssi),
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 20.dp, top = 4.dp)
            )

            // 2. Status message box from image: "Very close! Look around carefully"
            val statusMessage = when {
                deviceRssi >= -65.0 -> "Very close! Look around carefully"
                deviceRssi >= -80.0 -> "Signal is getting stronger! Move closer"
                else -> "Far - scan surrounding rooms"
            }
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1C1C1E))
                    .padding(horizontal = 20.dp, vertical = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = statusMessage,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.SansSerif,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Status info element",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Smiling pill-shaped button: "I found it"
            Button(
                onClick = onFoundIt,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(24.dp), // Pill
                contentPadding = PaddingValues(vertical = 12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(54.dp)
                    .testTag("found_it_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SentimentVerySatisfied, // Smiley Face
                        contentDescription = "Happy emotion icon",
                        tint = Color.Black,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "I found it",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Detail Table card: MAC address, Paired, Connected
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF1C1C1E))
                    .padding(20.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "MAC Address:",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 14.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                        Text(
                            text = device.macAddress,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Paired:",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 14.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                        Text(
                            text = if (isPaired) "Yes" else "No",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.SansSerif
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Connected:",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 14.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                        Text(
                            text = if (isConnected) "Yes" else "No",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 5. Sound & Vibration toggle controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Sound Toggle Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Sound speaker",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Sound",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                    Switch(
                        checked = soundEnabled,
                        onCheckedChange = { soundEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF4CAF50),
                            uncheckedThumbColor = Color.White.copy(alpha = 0.7f),
                            uncheckedTrackColor = Color(0xFF3A3A3C),
                            uncheckedBorderColor = Color.Transparent
                        ),
                        modifier = Modifier.testTag("sound_toggle")
                    )
                }

                // Vibration Toggle Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Vibration,
                            contentDescription = "Vibration trigger icon",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Vibration",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                    Switch(
                        checked = vibrationEnabled,
                        onCheckedChange = { vibrationEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF4CAF50),
                            uncheckedThumbColor = Color.White.copy(alpha = 0.7f),
                            uncheckedTrackColor = Color(0xFF3A3A3C),
                            uncheckedBorderColor = Color.Transparent
                        ),
                        modifier = Modifier.testTag("vibration_toggle")
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Interactive Instructions Dialog
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = {
                Text(
                    text = "SIGNAL FINDER GUIDANCE",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "1. Walk around slowly while monitoring the analog pointer needle.\n\n" +
                           "2. If the indicator swings left towards the Orange sector ('Far'), you are moving away.\n\n" +
                           "3. When the indicator glides right into the Green sector ('Near'), the wireless beacon is extremely close. Check under tables, pockets, or cushions!\n\n" +
                           "4. Enable 'Sound' or 'Vibration' switches to hear/feel adaptive pulses that beep/pulse faster as you get closer.",
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("UNDERSTOOD", fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF1C1C1E),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.82f)
        )
    }
}

/**
 * High precision custom analog gauge using polar coordinates and Canvas rotations.
 */
@Composable
fun ProximityGauge(
    rssi: Double,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    
    // Normalize RSSI range from -100 (weakest/Far) to -40 (strongest/Near) with 0..1 scale
    val normalizedSource = ((rssi - (-100.0)) / ((-40.0) - (-100.0))).coerceIn(0.0, 1.0)
    
    // Smooth the pointer rotation
    val targetAngle = 135f + (normalizedSource.toFloat() * 270f)
    val smoothAngle by animateFloatAsState(
        targetValue = targetAngle,
        animationSpec = spring(
            stiffness = Spring.StiffnessLow, 
            dampingRatio = Spring.DampingRatioMediumBouncy
        ),
        label = "gauge_needle"
    )

    // Center pivot breathing scale animation
    val infiniteTransition = rememberInfiniteTransition(label = "pivot_breathing")
    val pivotScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pivot_scale"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().testTag("proximity_gauge")) {
            val center = Offset(size.width / 2f, size.height / 1.55f)
            val maxRadius = size.width / 2.3f
            
            // Draw 3 primary arcs matching reference: Far (Orange), Closer (Yellow/Amber), Near (Green)
            // Arc sweeps 270 degrees total, starting at 135° and ending at 405°
            val gap = 6f
            val arcStrokeWidth = 26.dp.toPx()
            val arcSize = Size(maxRadius * 2, maxRadius * 2)
            val arcTopLeft = Offset(center.x - maxRadius, center.y - maxRadius)
            
            // Orange Arc (Far): 135° to 225°
            drawArc(
                color = Color(0xFFFF5722),
                startAngle = 135f + gap / 2f,
                sweepAngle = 90f - gap,
                useCenter = false,
                style = Stroke(width = arcStrokeWidth, cap = StrokeCap.Round),
                size = arcSize,
                topLeft = arcTopLeft
            )
            
            // Yellow Arc (Closer): 225° to 315°
            drawArc(
                color = Color(0xFFFFB300), // Rich amber/yellow
                startAngle = 225f + gap / 2f,
                sweepAngle = 90f - gap,
                useCenter = false,
                style = Stroke(width = arcStrokeWidth, cap = StrokeCap.Round),
                size = arcSize,
                topLeft = arcTopLeft
            )
            
            // Green Arc (Near): 315° to 405° (which is 45°)
            drawArc(
                color = Color(0xFF4CAF50),
                startAngle = 315f + gap / 2f,
                sweepAngle = 90f - gap,
                useCenter = false,
                style = Stroke(width = arcStrokeWidth, cap = StrokeCap.Round),
                size = arcSize,
                topLeft = arcTopLeft
            )

            // Draw text labels: Far, Closer, Near
            val textDistance = maxRadius - 26.dp.toPx()
            
            // "Far" text drawn vertically on Left (180°)
            val farRad = Math.toRadians(180.0)
            val farX = center.x + textDistance * cos(farRad).toFloat()
            val farY = center.y + textDistance * sin(farRad).toFloat()
            val farLayout = textMeasurer.measure(
                "Far",
                style = TextStyle(color = Color.White.copy(alpha = 0.82f), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
            )
            rotate(degrees = 90f, pivot = Offset(farX, farY)) {
                drawText(
                    textLayoutResult = farLayout,
                    topLeft = Offset(farX - farLayout.size.width / 2, farY - farLayout.size.height / 2)
                )
            }
            
            // "Closer" text drawn horizontally at top (270°)
            val closerRad = Math.toRadians(270.0)
            val closerX = center.x + (maxRadius - 22.dp.toPx()) * cos(closerRad).toFloat()
            val closerY = center.y + (maxRadius - 22.dp.toPx()) * sin(closerRad).toFloat()
            val closerLayout = textMeasurer.measure(
                "Closer",
                style = TextStyle(color = Color.White.copy(alpha = 0.82f), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
            )
            drawText(
                textLayoutResult = closerLayout,
                topLeft = Offset(closerX - closerLayout.size.width / 2, closerY - closerLayout.size.height / 2)
            )
            
            // "Near" text drawn vertically on Right (360° or 0°)
            val nearRad = Math.toRadians(360.0)
            val nearX = center.x + textDistance * cos(nearRad).toFloat()
            val nearY = center.y + textDistance * sin(nearRad).toFloat()
            val nearLayout = textMeasurer.measure(
                "Near",
                style = TextStyle(color = Color.White.copy(alpha = 0.82f), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
            )
            rotate(degrees = -90f, pivot = Offset(nearX, nearY)) {
                drawText(
                    textLayoutResult = nearLayout,
                    topLeft = Offset(nearX - nearLayout.size.width / 2, nearY - nearLayout.size.height / 2)
                )
            }

            // Draw Blue Pointer Needle
            val needleLen = maxRadius * 0.72f
            val needleRad = Math.toRadians(smoothAngle.toDouble())
            val needleEnd = Offset(
                x = center.x + needleLen * cos(needleRad).toFloat(),
                y = center.y + needleLen * sin(needleRad).toFloat()
            )
            
            // Main needle pointer line (Royal Blue)
            drawLine(
                color = Color(0xFF1E88E5), 
                start = center,
                end = needleEnd,
                strokeWidth = 14.dp.toPx(),
                cap = StrokeCap.Round
            )
            
            // Highlight shine center core
            drawLine(
                color = Color(0xFF4FC3F7), 
                start = center,
                end = Offset(
                    x = center.x + (needleLen * 0.9f) * cos(needleRad).toFloat(),
                    y = center.y + (needleLen * 0.9f) * sin(needleRad).toFloat()
                ),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Draw center pivot circle
            drawCircle(
                color = Color(0xFF424242), // Dark grey core
                radius = 24.dp.toPx(),
                center = center
            )
            drawCircle(
                color = Color(0xFF535353), // Highlight ring
                radius = 24.dp.toPx(),
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
            drawCircle(
                color = Color(0xFF1C1C1E),
                radius = 10.dp.toPx() * pivotScale,
                center = center
            )
        }
    }
}
