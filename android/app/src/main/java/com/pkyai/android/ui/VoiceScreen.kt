package com.pkyai.android.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pkyai.android.ui.theme.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VoiceScreen(
    isConnected: Boolean,
    hasPermission: Boolean,
    statusText: String,
    isRecordingActive: Boolean,
    selectedModel: String,
    availableModels: List<String>,
    onModelSelected: (String) -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onShowConsole: () -> Unit = {},
    micAmplitude: Float = 0f,          // Live mic amplitude [0..1] for waveform
    onCameraCapture: () -> Unit = {}   // Multimodal camera trigger
) {
    val modelColor = remember(selectedModel) {
        when (selectedModel.lowercase()) {
            "build" -> BrainBuild
            "plan" -> BrainPlan
            "adaptive" -> BrainAdaptive
            "extended" -> BrainExtended
            else -> BrainGeneral
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(SurfaceDim),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = !isConnected,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 110.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.Red.copy(alpha = 0.8f))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text("Connection Lost", color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }

        AuroraBackground()

        Column(
            modifier = Modifier.fillMaxSize().padding(top = 48.dp, bottom = 120.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            BrainSelectorRow(
                models = availableModels,
                selected = selectedModel,
                onSelect = onModelSelected
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isRecordingActive) "RECORDING" else "SYSTEM ACTIVE",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isRecordingActive) modelColor else PkyAiTertiaryFixed,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = if (isRecordingActive) "Listening..." else "Tap to Wake",
                    style = MaterialTheme.typography.headlineLarge,
                    color = TextPrimary
                )
            }

            RecordingOrb(
                isRecording = isRecordingActive,
                modelColor = modelColor,
                onStart = onStartRecording,
                onStop = onStopRecording
            )

            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )

            // Live mic amplitude waveform — visible only while recording
            AnimatedVisibility(
                visible = isRecordingActive,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                AudioWaveform(
                    amplitude = micAmplitude,
                    barColor = modelColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 32.dp)
                )
            }

            VoiceActionControls(
                isRecording = isRecordingActive,
                onStart = onStartRecording,
                onStop = onStopRecording,
                onShowConsole = onShowConsole,
                onCameraCapture = onCameraCapture
            )
        }
    }
}

@Composable
private fun AuroraBackground() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawCircle(
            color = PkyAiPrimaryDim.copy(alpha = 0.3f),
            radius = size.minDimension * 0.8f,
            center = androidx.compose.ui.geometry.Offset(size.width * 1.2f, -size.height * 0.2f)
        )
        drawCircle(
            color = PkyAiSecondaryContainer.copy(alpha = 0.3f),
            radius = size.minDimension * 0.9f,
            center = androidx.compose.ui.geometry.Offset(-size.width * 0.2f, size.height * 1.1f)
        )
    }
}

@Composable
private fun BrainSelectorRow(
    models: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(9999.dp))
            .background(SurfaceContainerLow.copy(alpha = 0.5f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        models.forEach { model ->
            val isSelected = model == selected
            val brainColor = when (model.lowercase()) {
                "build" -> BrainBuild
                "plan" -> BrainPlan
                "adaptive" -> BrainAdaptive
                "extended" -> BrainExtended
                else -> BrainGeneral
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(9999.dp))
                    .background(if (isSelected) brainColor else Color.Transparent)
                    .border(1.dp, if (isSelected) Color.White.copy(alpha = 0.2f) else Color.Transparent, RoundedCornerShape(9999.dp))
                    .clickable { onSelect(model) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = model.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) SurfaceDim else TextSecondary
                )
            }
        }
    }
}

@Composable
private fun RecordingOrb(
    isRecording: Boolean,
    modelColor: Color,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(280.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (isRecording) onStop() else onStart() }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isRecording) {
            val infiniteTransition = rememberInfiniteTransition()
            val scale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.4f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.6f,
                targetValue = 0.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(color = modelColor, radius = size.minDimension / 2 * scale, alpha = alpha)
                drawCircle(color = modelColor, radius = size.minDimension / 2 * (1f + (scale - 1f) * 0.5f), alpha = alpha * 1.5f)
            }
        }

        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(if (isRecording) Color.White else modelColor)
                .padding(2.dp)
                .clip(CircleShape)
                .background(if (isRecording) Color.White else SurfaceDim),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = "Voice Action",
                tint = if (isRecording) Color.Red else modelColor,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VoiceActionControls(
    isRecording: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onShowConsole: () -> Unit,
    onCameraCapture: () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(SurfaceContainerHighest.copy(alpha = 0.4f))
                .border(1.dp, Color.White.copy(alpha = 0.05f), CircleShape)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* Volume logic */ },
                    onLongClick = { onShowConsole() }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = Color.LightGray,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(24.dp))

        IconButton(
            onClick = { if (!isRecording) onStart() else onStop() },
            modifier = Modifier
                .size(80.dp)
                .background(
                    brush = Brush.linearGradient(colors = listOf(PkyAiPrimaryDim, PkyAiSecondaryDim)),
                    shape = CircleShape
                )
                .shadow(30.dp, CircleShape, spotColor = PkyAiPrimaryDim.copy(alpha = 0.4f))
        ) {
            Icon(Icons.Default.Mic, contentDescription = "Mic", tint = Color.White, modifier = Modifier.size(36.dp))
        }

        Spacer(modifier = Modifier.width(24.dp))

        // Camera capture button — triggers multimodal vision input
        IconButton(
            onClick = { onCameraCapture() },
            modifier = Modifier
                .size(56.dp)
                .background(SurfaceContainerHighest.copy(alpha = 0.4f), CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.05f), CircleShape)
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = "Capture for AI", tint = Color.LightGray)
        }
    }
}

/**
 * AudioWaveform — real-time bar-chart visualizer reacting to microphone amplitude.
 *
 * @param amplitude Normalized RMS amplitude [0..1] from VoiceService.onAmplitudeChanged.
 * @param barColor  Color of bars (matches brain/model color).
 */
@Composable
private fun AudioWaveform(
    amplitude: Float,
    barColor: Color,
    modifier: Modifier = Modifier
) {
    val barCount = 32
    val amplitudeHistory = remember { ArrayDeque<Float>(barCount) }

    // Shift history left and append latest amplitude
    LaunchedEffect(amplitude) {
        if (amplitudeHistory.size >= barCount) amplitudeHistory.removeFirst()
        amplitudeHistory.addLast(amplitude)
    }

    Canvas(modifier = modifier) {
        val barWidth = size.width / barCount
        val centerY = size.height / 2f
        val maxBarHeight = size.height * 0.9f

        amplitudeHistory.forEachIndexed { index, amp ->
            val barHeight = (amp * maxBarHeight).coerceAtLeast(4f)
            val x = index * barWidth + barWidth / 2f
            val alpha = 0.4f + (index.toFloat() / barCount) * 0.6f  // fade in older bars

            drawLine(
                color = barColor.copy(alpha = alpha),
                start = Offset(x, centerY - barHeight / 2f),
                end = Offset(x, centerY + barHeight / 2f),
                strokeWidth = barWidth * 0.6f,
                cap = StrokeCap.Round
            )
        }
    }
}
