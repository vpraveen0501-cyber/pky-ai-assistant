package com.pkyai.android.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pkyai.android.ui.theme.*

@Composable
fun SettingsScreen(
    onNavigateToPrivacy: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val personaName by viewModel.personaName.collectAsState()
    val connectionTestResult by viewModel.connectionTestResult.collectAsState()

    var biometricEnabled by remember { mutableStateOf(true) }
    var encryptionEnabled by remember { mutableStateOf(true) }
    var localProcessingOnly by remember { mutableStateOf(false) }

    var backendHost by remember { mutableStateOf(viewModel.configRepository.getHost()) }
    var backendPort by remember { mutableStateOf(viewModel.configRepository.getPort()) }

    var expanded by remember { mutableStateOf(false) }
    val voices = listOf("PKY AI Assistant", "Ram")

    LaunchedEffect(connectionTestResult) {
        connectionTestResult?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceDim)
            .padding(top = 48.dp, start = 24.dp, end = 24.dp)
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(color = PkyAiSecondaryDim.copy(alpha = 0.2f), radius = 600f, center = androidx.compose.ui.geometry.Offset(0f, 0f))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "System Preferences",
                style = MaterialTheme.typography.headlineLarge,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            SectionTitle("AI Persona")
            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainerHigh.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(text = "Active Character Voice", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Box(modifier = Modifier.padding(top = 12.dp)) {
                        OutlinedButton(
                            onClick = { expanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(personaName, style = MaterialTheme.typography.bodyLarge)
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.background(SurfaceContainerHighest).border(1.dp, Color.White.copy(alpha = 0.05f))
                        ) {
                            voices.forEach { voice ->
                                DropdownMenuItem(
                                    text = { Text(voice, color = TextPrimary) },
                                    onClick = {
                                        expanded = false
                                        viewModel.updatePersonaName(voice)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            SectionTitle("Security & Privacy")
            ToggleItem("Biometric Authentication", biometricEnabled) { biometricEnabled = it }
            ToggleItem("Local Database Encryption", encryptionEnabled) { encryptionEnabled = it }
            ToggleItem("Local-Only Processing", localProcessingOnly) { localProcessingOnly = it }

            SectionTitle("Compliance")
            Button(
                onClick = onNavigateToPrivacy,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainerHigh.copy(alpha = 0.6f))
            ) {
                Text(text = "Privacy Policy (DPDP India)", color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
            }

            SectionTitle("Backend Connection")
            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainerHigh.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    TextField(
                        value = backendHost,
                        onValueChange = { backendHost = it },
                        label = { Text("Host IP", color = TextSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = SurfaceContainerHighest,
                            unfocusedContainerColor = SurfaceContainerHighest,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedIndicatorColor = PkyAiPrimary,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    TextField(
                        value = backendPort,
                        onValueChange = { backendPort = it },
                        label = { Text("Port", color = TextSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = SurfaceContainerHighest,
                            unfocusedContainerColor = SurfaceContainerHighest,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedIndicatorColor = PkyAiPrimary,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { viewModel.testConnection() },
                            modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainerHighest)
                        ) {
                            Text("Test", color = TextPrimary)
                        }
                        Button(
                            onClick = {
                                viewModel.saveConfig(backendHost, backendPort)
                                Toast.makeText(context, "Config saved! Please restart app.", Toast.LENGTH_LONG).show()
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PkyAiPrimary)
                        ) {
                            Text("Save", color = SurfaceDim)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(120.dp))
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = PkyAiTertiaryFixed,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
fun ToggleItem(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceContainerHigh.copy(alpha = 0.6f))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = SurfaceDim,
                checkedTrackColor = PkyAiPrimary,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = SurfaceContainerHighest
            )
        )
    }
}
