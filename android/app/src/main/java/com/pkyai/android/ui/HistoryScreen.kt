package com.pkyai.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pkyai.android.ui.theme.*

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = hiltViewModel()) {
    val historyItems by viewModel.historyItems.collectAsState(initial = emptyList())

    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceDim)
            .padding(top = 48.dp, start = 24.dp, end = 24.dp)
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(color = PkyAiPrimaryDim.copy(alpha = 0.15f), radius = 800f, center = androidx.compose.ui.geometry.Offset(size.width, size.height))
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Memory Archives",
                style = MaterialTheme.typography.headlineLarge,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search your knowledge...", color = TextSecondary) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = PkyAiPrimary) },
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = SurfaceContainerHigh.copy(alpha = 0.6f),
                    focusedContainerColor = SurfaceContainerHigh.copy(alpha = 0.8f),
                    unfocusedTextColor = TextPrimary,
                    focusedTextColor = TextPrimary,
                    focusedIndicatorColor = PkyAiPrimary,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = PkyAiPrimary,
                divider = {},
                indicator = { tabPositions ->
                    TabRowDefaults.Indicator(
                        modifier = Modifier.height(3.dp).clip(RoundedCornerShape(3.dp)),
                        color = PkyAiPrimary
                    )
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Chat History", style = MaterialTheme.typography.bodyLarge, color = if (selectedTab == 0) PkyAiPrimary else TextSecondary) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Documents", style = MaterialTheme.typography.bodyLarge, color = if (selectedTab == 1) PkyAiPrimary else TextSecondary) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                val filteredItems = historyItems.filter { it.text.contains(searchQuery, ignoreCase = true) }

                if (selectedTab == 0) {
                    val chatItems = filteredItems.filter { it.type == "chat" }
                    if (chatItems.isEmpty() && historyItems.isEmpty()) {
                        item { Text("Loading history...", color = TextSecondary, style = MaterialTheme.typography.bodyMedium) }
                    } else if (chatItems.isEmpty()) {
                        item { Text("No chat history found.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium) }
                    } else {
                        items(chatItems, key = { it.id }) { item ->
                            ArchiveItem(item.text, Icons.Default.History)
                        }
                    }
                } else {
                    val docItems = filteredItems.filter { it.type == "document" }
                    if (docItems.isEmpty() && historyItems.isEmpty()) {
                        item { Text("Loading history...", color = TextSecondary, style = MaterialTheme.typography.bodyMedium) }
                    } else if (docItems.isEmpty()) {
                        item { Text("No documents found.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium) }
                    } else {
                        items(docItems, key = { it.id }) { item ->
                            ArchiveItem(item.text, Icons.Default.Description)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ArchiveItem(text: String, icon: ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceContainerHigh.copy(alpha = 0.5f))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(20.dp))
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).background(SurfaceContainerHighest, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = PkyAiPrimary, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = text, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
    }
}
