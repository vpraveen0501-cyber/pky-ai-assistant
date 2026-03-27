package com.pkyai.android.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pkyai.android.ui.theme.PkyAiThemeState
import com.pkyai.android.ui.theme.PkyAiThemeType
import com.pkyai.android.ui.theme.PkyAiPrimary
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun GenerativeVisualizer(
    amplitude: Float, // 0.0 to 1.0 (audio volume)
    frequency: Float, // 0.0 to 1.0 (audio pitch/activity)
    modifier: Modifier = Modifier.fillMaxSize()
) {
    val infiniteTransition = rememberInfiniteTransition()
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    val theme = PkyAiThemeState.currentTheme

    Canvas(modifier = modifier) {
        when (theme) {
            PkyAiThemeType.MIDNIGHT_GALAXY -> drawCosmicPulse(time, amplitude, frequency)
            PkyAiThemeType.OCEAN_DEPTHS -> drawOceanCurrents(time, amplitude, frequency)
            else -> drawDefaultVisualizer(time, amplitude, frequency)
        }
    }
}

private fun DrawScope.drawCosmicPulse(time: Float, amplitude: Float, frequency: Float) {
    val centerX = size.width / 2
    val centerY = size.height / 2
    val baseRadius = size.minDimension / 4
    val pulseRadius = baseRadius + (amplitude * 100f)

    // Draw particle streaks
    for (i in 0 until 50) {
        val angle = (i.toFloat() / 50f) * 2f * Math.PI.toFloat() + time
        val x = centerX + cos(angle) * pulseRadius
        val y = centerY + sin(angle) * pulseRadius
        
        drawCircle(
            color = Color(0xFFA490C2).copy(alpha = 0.6f),
            radius = 4f + (frequency * 10f),
            center = Offset(x, y)
        )
    }

    // Central glow
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFE6E6FA).copy(alpha = 0.8f), Color.Transparent),
            center = Offset(centerX, centerY),
            radius = pulseRadius
        ),
        radius = pulseRadius,
        center = Offset(centerX, centerY)
    )
}

private fun DrawScope.drawOceanCurrents(time: Float, amplitude: Float, frequency: Float) {
    val count = 20
    val spacing = size.height / count

    for (i in 0 until count) {
        val y = i * spacing
        val waveOffset = sin(time + i * 0.5f) * 50f * amplitude
        
        drawPath(
            path = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, y + waveOffset)
                quadraticBezierTo(
                    size.width / 2, y - waveOffset,
                    size.width, y + waveOffset
                )
            },
            color = Color(0xFFA8DADC).copy(alpha = 0.4f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f + frequency * 5f)
        )
    }
}

private fun DrawScope.drawDefaultVisualizer(time: Float, amplitude: Float, frequency: Float) {
    // Glassmorphism 2.0 Visualizer
    drawCircle(
        color = PkyAiPrimary.copy(alpha = 0.5f),
        radius = 100f + (amplitude * 50f),
        center = center
    )
}
