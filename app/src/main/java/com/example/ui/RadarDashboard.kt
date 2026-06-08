package com.example.ui

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ScannedDevice
import com.example.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

/**
 * A highly polished tactical Radar scope painted on Canvas.
 * Renders concentric radar boundaries, current physical compass headings,
 * daily calibration data history as heatmap sectors, and a target pointing arrow.
 */
@Composable
fun SignalRadarScope(
    currentHeading: Float,
    targetDevice: ScannedDevice?,
    calibratedSignalAzimuth: Float?,
    isCalibrating: Boolean,
    radarData: Map<Int, Double>, // sector index [0-15] -> maximum RSSI smooth value detected
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    
    // Animate sweep rotation continuously
    val infiniteTransition = rememberInfiniteTransition(label = "sweep")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep"
    )

    // Needle scale pulsation for locking locks
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Compass physical azimuth smoothing
    val smoothHeading by animateFloatAsState(
        targetValue = currentHeading,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "heading"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .border(1.dp, RadarSecondary.copy(alpha = 0.25f), RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "TACTICAL SIGNAL TRACKER",
                        color = RadarMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = if (targetDevice != null) "LOCK: ${targetDevice.macAddress}" else "TARGET: SCANNING FOR LOCK",
                        color = if (targetDevice != null) RadarPrimary else RadarMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }
                
                // Calibration indicator
                val indicatorColor = if (isCalibrating) RadarAccent else if (calibratedSignalAzimuth != null) RadarPrimary else RadarMuted
                val indicatorText = if (isCalibrating) "CAL STAT: SWEEPS" else if (calibratedSignalAzimuth != null) "CAL STAT: LOCKED" else "CAL STAT: EMPTY"
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(indicatorColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = indicatorText,
                        color = indicatorColor,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main Radar Scope box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f))
                    .border(1.5.dp, RadarSecondary.copy(alpha = 0.2f), CircleShape)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("radar_canvas")
                ) {
                    val center = Offset(size.width / 2, size.height / 2)
                    val maxRadius = size.width / 2.1f
                    
                    // 1. Draw Concentric Circles (Tactical Metric Grids)
                    drawCircle(
                        color = RadarSecondary.copy(alpha = 0.1f),
                        radius = maxRadius,
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )
                    drawCircle(
                        color = RadarSecondary.copy(alpha = 0.08f),
                        radius = maxRadius * 0.66f,
                        center = center,
                        style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
                    )
                    drawCircle(
                        color = RadarSecondary.copy(alpha = 0.05f),
                        radius = maxRadius * 0.33f,
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )

                    // 2. Draw Crosshairs
                    drawLine(
                        color = RadarSecondary.copy(alpha = 0.15f),
                        start = Offset(center.x - maxRadius, center.y),
                        end = Offset(center.x + maxRadius, center.y),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = RadarSecondary.copy(alpha = 0.15f),
                        start = Offset(center.x, center.y - maxRadius),
                        end = Offset(center.x, center.y + maxRadius),
                        strokeWidth = 1.dp.toPx()
                    )

                    // 3. Draw North, South, East, West texts relative to physical device coordinates
                    // Top is phone front, which matches 0° rotation relative to phone context
                    val headingTextAngle = -smoothHeading // Counter-rotate so North points north
                    val cardinalDirs = listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f)
                    
                    cardinalDirs.forEach { (dir, angle) ->
                        val targetAngleRad = Math.toRadians((angle - 90f + headingTextAngle).toDouble())
                        val textX = center.x + (maxRadius + 18.dp.toPx()) * cos(targetAngleRad).toFloat()
                        val textY = center.y + (maxRadius + 18.dp.toPx()) * sin(targetAngleRad).toFloat()
                        
                        val textLayoutResult = textMeasurer.measure(
                            text = dir,
                            style = TextStyle(
                                color = if (dir == "N") RadarPrimary else RadarMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                        drawText(
                            textLayoutResult = textLayoutResult,
                            topLeft = Offset(textX - textLayoutResult.size.width / 2, textY - textLayoutResult.size.height / 2)
                        )
                    }

                    // 4. Draw Radial Heatmap Wedges of Scan Data
                    // Map 360 degrees to 16 sectors
                    val numSectors = 16
                    val sectorDegrees = 360f / numSectors
                    
                    radarData.forEach { (sectorIndex, rssi) ->
                        // sector 0 points North (which is -90° inside Canvas polar coordinate system)
                        // Sector points relative to our counter-rotated heading
                        val startAngle = (sectorIndex * sectorDegrees) - 90f - 90f + headingTextAngle
                        
                        // Stronger signals (closer to 0 dBm, e.g. -50 dBm) are wider and brighter
                        // Normalize RSSI range from -100 dBm (0f) to -40 dBm (1f)
                        val strengthScale = ((rssi + 100.0) / 60.0).coerceIn(0.1, 1.0).toFloat()
                        val sectorColor = if (isCalibrating) {
                            RadarAccent.copy(alpha = 0.25f * strengthScale)
                        } else {
                            RadarPrimary.copy(alpha = 0.35f * strengthScale)
                        }

                        // Arc sizing based on normalized signal strength
                        val arcRadius = maxRadius * strengthScale
                        drawArc(
                            color = sectorColor,
                            startAngle = startAngle,
                            sweepAngle = sectorDegrees,
                            useCenter = true,
                            size = Size(arcRadius * 2, arcRadius * 2),
                            topLeft = Offset(center.x - arcRadius, center.y - arcRadius)
                        )
                    }

                    // 5. Draw Dynamic Scan Sweep Beam
                    val sweepBrush = Brush.sweepGradient(
                        colors = listOf(
                            Color.Transparent,
                            RadarPrimary.copy(alpha = 0.01f),
                            RadarPrimary.copy(alpha = 0.2f),
                            Color.Transparent
                        ),
                        center = center
                    )
                    rotate(degrees = sweepAngle, pivot = center) {
                        drawCircle(
                            brush = sweepBrush,
                            radius = maxRadius,
                            center = center
                        )
                        // Leading laser sweep line
                        drawLine(
                            color = RadarPrimary.copy(alpha = 0.4f),
                            start = center,
                            end = Offset(center.x, center.y - maxRadius),
                            strokeWidth = 1.5.dp.toPx()
                        )
                    }

                    // 6. Draw Signal Tracker Needle (Locked Position Arrow)
                    if (targetDevice != null && calibratedSignalAzimuth != null) {
                        // Relative target angle: calibrated azimuth - current physical layout azimuth
                        val targetRelativeDegrees = calibratedSignalAzimuth - smoothHeading
                        
                        rotate(degrees = targetRelativeDegrees, pivot = center) {
                            // High-tech Arrow / Pinhead pointer
                            val arrowPath = Path().apply {
                                moveTo(center.x, center.y - maxRadius * 0.15f)
                                lineTo(center.x - 12.dp.toPx(), center.y - maxRadius * 0.3f)
                                lineTo(center.x - 4.dp.toPx(), center.y - maxRadius * 0.85f * pulseScale)
                                lineTo(center.x, center.y - maxRadius * 0.92f * pulseScale) // Sharp tip
                                lineTo(center.x + 4.dp.toPx(), center.y - maxRadius * 0.85f * pulseScale)
                                lineTo(center.x + 12.dp.toPx(), center.y - maxRadius * 0.3f)
                                close()
                            }
                            
                            // Glowing inner triangle path
                            val glowBrush = Brush.verticalGradient(
                                colors = listOf(RadarPrimary, RadarSecondary.copy(alpha = 0.3f)),
                                startY = center.y - maxRadius * 0.95f,
                                endY = center.y
                            )
                            
                            drawPath(
                                path = arrowPath,
                                brush = glowBrush
                            )
                            
                            drawPath(
                                path = arrowPath,
                                color = RadarPrimary,
                                style = Stroke(width = 2.dp.toPx())
                            )
                            
                            // Draw an outer glowing dot at target sector edge
                            drawCircle(
                                color = RadarPrimary,
                                radius = 5.dp.toPx(),
                                center = Offset(center.x, center.y - maxRadius)
                            )
                            drawCircle(
                                color = RadarPrimary.copy(alpha = 0.4f),
                                radius = 9.dp.toPx() * pulseScale,
                                center = Offset(center.x, center.y - maxRadius),
                                style = Stroke(width = 1.5.dp.toPx())
                            )
                        }
                    } else if (targetDevice != null) {
                        // Drawing search sweeps when locked but waiting calibration
                        val rotAngle = sweepAngle * 1.5f
                        rotate(degrees = rotAngle, pivot = center) {
                            drawCircle(
                                color = RadarAccent,
                                radius = maxRadius * 0.75f,
                                center = center,
                                style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 30f), 0f))
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Calibration indicators & hints
            if (targetDevice != null) {
                if (calibratedSignalAzimuth == null) {
                    Text(
                        text = "🚨 CALIBRATION REQUIRED",
                        color = RadarAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = "Rotate your device slowly\nto recalibrate signal direction.",
                        color = RadarOnBg.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.GpsFixed,
                            contentDescription = "Target locked icon",
                            tint = RadarPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "TARGET LOCK CONFIRMED AT %.1f°".format(calibratedSignalAzimuth),
                            color = RadarPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            } else {
                Text(
                    text = "SELECT A BEACON TARGET BELOW",
                    color = RadarMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Custom live scan list item card displaying live hardware parameters and locks
 */
@Composable
fun ScannedDeviceRow(
    device: ScannedDevice,
    isPinned: Boolean,
    isTarget: Boolean,
    onTrackToggle: () -> Unit,
    onPinToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isTarget) RadarPrimary.copy(alpha = 0.08f) else RadarSurface
        ),
        border = BorderStroke(
            1.dp,
            if (isTarget) RadarPrimary.copy(alpha = 0.4f) else Color.Transparent
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Signal Strength Radial Icon or Dot
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (device.deviceType == "BLUETOOTH") RadarSecondary.copy(alpha = 0.12f) else RadarPrimary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (device.deviceType == "BLUETOOTH") Icons.Default.Bluetooth else Icons.Default.Wifi,
                    contentDescription = "Device Type",
                    tint = if (device.deviceType == "BLUETOOTH") RadarSecondary else RadarPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Text Info: Name, MAC Address, Smoothed/Raw RSSI
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = device.name,
                        color = RadarOnSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }
                Text(
                    text = device.macAddress,
                    color = RadarMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // RSSI Meter
                    Text(
                        text = "RSSI: %.1f dBm".format(device.smoothedRssi),
                        color = if (device.smoothedRssi > -65) RadarPrimary else if (device.smoothedRssi > -85) RadarSecondary else RadarAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = " (Raw: %d)".format(device.rawRssi),
                        color = RadarMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Action Buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Pin / Favorite Icon
                IconButton(
                    onClick = onPinToggle,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = if (isPinned) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = "Pin Device",
                        tint = if (isPinned) RadarAccent else RadarMuted,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Track (Radar Lock Command) Button
                Button(
                    onClick = onTrackToggle,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isTarget) RadarSecondary else RadarPrimary.copy(alpha = 0.2f),
                        contentColor = if (isTarget) Color.Black else RadarPrimary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("track_button_${device.macAddress}")
                ) {
                    Text(
                        text = if (isTarget) "UNLOCKED" else "TRACK",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

/**
 * Pinned Device Card - Displayed on the radar pinned tab
 */
@Composable
fun PinnedDeviceCard(
    mac: String,
    name: String,
    alias: String,
    type: String,
    isTarget: Boolean,
    liveDevice: ScannedDevice?,
    onTrackToggle: () -> Unit,
    onUnpin: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp, horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = RadarSurface),
        border = BorderStroke(
            1.dp,
            if (isTarget) RadarSecondary.copy(alpha = 0.5f) else RadarMuted.copy(alpha = 0.15f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (type == "BLUETOOTH") Icons.Default.Bluetooth else Icons.Default.Wifi,
                    contentDescription = "Device Type Icon",
                    tint = if (type == "BLUETOOTH") RadarSecondary else RadarPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = alias,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = RadarOnSurface,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )
                // Unpin
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Unpin database item",
                    tint = Color.Red.copy(alpha = 0.8f),
                    modifier = Modifier
                        .size(22.dp)
                        .clickable { onUnpin() }
                )
            }
            Text(
                text = "Device SSID/Name: $name",
                style = TextStyle(color = RadarMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                modifier = Modifier.padding(top = 2.dp)
            )
            Text(
                text = "MAC: $mac",
                style = TextStyle(color = RadarMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            )

            HorizontalDivider(color = RadarMuted.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (liveDevice != null) {
                    Text(
                        text = "LIVE RSSI: %.1f dBm".format(liveDevice.smoothedRssi),
                        color = if (liveDevice.smoothedRssi > -65) RadarPrimary else RadarSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                } else {
                    Text(
                        text = "OFFLINE or OUT OF RANGE",
                        color = RadarMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Button(
                    onClick = onTrackToggle,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isTarget) RadarSecondary else RadarPrimary.copy(alpha = 0.15f),
                        contentColor = if (isTarget) Color.Black else RadarPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = if (isTarget) "UNTRACK" else "TRACK TARGET",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
