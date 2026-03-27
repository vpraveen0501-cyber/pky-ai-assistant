package com.pkyai.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pkyai.android.data.model.SystemStats
import com.pkyai.android.ui.theme.*

@Composable
fun DashboardScreen(viewModel: DashboardViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    var stats: SystemStats? = null
    var apiError: String? = null
    var isLoading = false

    when (uiState) {
        is DashboardUiState.Loading -> isLoading = true
        is DashboardUiState.Success -> stats = (uiState as DashboardUiState.Success).stats
        is DashboardUiState.Error -> apiError = (uiState as DashboardUiState.Error).message
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceDim)
            .padding(top = 48.dp, start = 24.dp, end = 24.dp)
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = PkyAiSecondaryContainer.copy(alpha = 0.2f),
                radius = size.minDimension * 0.7f,
                center = androidx.compose.ui.geometry.Offset(size.width, 0f)
            )
            drawCircle(
                color = PkyAiPrimaryDim.copy(alpha = 0.15f),
                radius = size.minDimension * 0.8f,
                center = androidx.compose.ui.geometry.Offset(0f, size.height * 0.6f)
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            item {
                Text(
                    text = "System Status",
                    style = MaterialTheme.typography.labelSmall,
                    color = PkyAiTertiaryFixed
                )
                Text(
                    text = "Greetings, Commander",
                    style = MaterialTheme.typography.headlineLarge,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            if (isLoading) {
                item { ShimmerPriorityCard() }
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ShimmerStatCard(Modifier.weight(1f))
                        ShimmerStatCard(Modifier.weight(1f))
                    }
                }
            } else if (apiError != null) {
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x33FF0000), RoundedCornerShape(16.dp))
                            .border(1.dp, Color.Red.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                            .padding(24.dp)
                    ) {
                        Text("Connection Error: $apiError", color = PkyAiRed, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.loadStats() },
                            colors = ButtonDefaults.buttonColors(containerColor = PkyAiRed.copy(alpha = 0.8f))
                        ) {
                            Text("Retry Connection")
                        }
                    }
                }
            } else {
                item { PriorityCard(stats) }
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        StatCard("AI Brains", stats?.active_brains?.toString() ?: "-", Modifier.weight(1f))
                        StatCard("Knowledge", "${stats?.documents_indexed ?: 0} Docs", Modifier.weight(1f))
                    }
                }
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        StatCard("Backend RAM", stats?.ram_usage ?: "-", Modifier.weight(1f))
                        StatCard("Mode", stats?.llm_mode?.uppercase() ?: "-", Modifier.weight(1f))
                    }
                }
            }

            item {
                Text(
                    text = "Suggested Actions",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                )
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(listOf("Analyze Logs", "Update Models", "System Check")) { action ->
                        ActionChip(action)
                    }
                }
            }

            item {
                Text(
                    text = "Activity Flux",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }

            items(listOf(
                "Summarized research report",
                "Scheduled technical meeting",
                "Updated career upskilling path",
                "Synthesized daily news digest"
            )) { activity ->
                ActivityItem(activity)
            }
        }
    }
}

@Composable
fun PriorityCard(stats: SystemStats?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        PkyAiSecondaryContainer.copy(alpha = 0.4f),
                        SurfaceContainerHigh.copy(alpha = 0.4f)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(32.dp))
            .padding(24.dp)
    ) {
        Column {
            Text(text = "System Ready", style = MaterialTheme.typography.labelSmall, color = PkyAiSecondary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "All neural pathways active. Ready for advanced queries.", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            Spacer(modifier = Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("CPU Load", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text(stats?.cpu_usage ?: "-", style = MaterialTheme.typography.headlineMedium, color = PkyAiPrimary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Uptime", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text(stats?.uptime ?: "-", style = MaterialTheme.typography.headlineMedium, color = PkyAiSecondary)
                }
            }
        }

        androidx.compose.foundation.Canvas(modifier = Modifier.matchParentSize()) {
            drawCircle(
                color = PkyAiTertiaryFixed.copy(alpha = 0.15f),
                radius = 200f,
                center = androidx.compose.ui.geometry.Offset(size.width, size.height)
            )
        }
    }
}

@Composable
fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerHigh.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.Start) {
            Text(text = title, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = value, style = MaterialTheme.typography.headlineMedium, color = PkyAiPrimary)
        }
    }
}

@Composable
fun ActionChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9999.dp))
            .background(PkyAiSecondaryContainer.copy(alpha = 0.3f))
            .border(1.dp, PkyAiSecondary.copy(alpha = 0.3f), RoundedCornerShape(9999.dp))
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = PkyAiSecondary, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ActivityItem(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceContainer.copy(alpha = 0.5f))
            .border(1.dp, Color.White.copy(alpha = 0.02f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val transition = rememberInfiniteTransition()
        val pulse by transition.animateFloat(
            initialValue = 0.4f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1500, easing = FastOutLinearInEasing), RepeatMode.Reverse)
        )
        Box(modifier = Modifier.size(12.dp).background(PkyAiTertiaryFixed.copy(alpha = pulse), CircleShape))
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = text, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
    }
}

@Composable
fun ShimmerPriorityCard() {
    val transition = rememberInfiniteTransition()
    val alpha by transition.animateFloat(
        initialValue = 0.1f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse)
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(SurfaceContainerHigh.copy(alpha = alpha))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(32.dp))
    )
}

@Composable
fun ShimmerStatCard(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition()
    val alpha by transition.animateFloat(
        initialValue = 0.1f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse)
    )
    Card(
        modifier = modifier
            .height(120.dp)
            .clip(RoundedCornerShape(24.dp))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerHigh.copy(alpha = alpha))
    ) {}
}
