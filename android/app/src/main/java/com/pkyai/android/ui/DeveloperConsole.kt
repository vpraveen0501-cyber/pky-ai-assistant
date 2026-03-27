package com.pkyai.android.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.pkyai.android.data.repository.DataRepository
import com.pkyai.android.ui.theme.SurfaceContainerHighest
import com.pkyai.android.ui.theme.TextPrimary
import com.pkyai.android.ui.theme.TextSecondary
import com.pkyai.android.util.LocalLogger

@Composable
fun DeveloperConsoleDialog(dataRepository: DataRepository, onDismiss: () -> Unit) {
    val logs by LocalLogger.logs.collectAsState()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f),
            shape = RoundedCornerShape(16.dp),
            color = SurfaceContainerHighest,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "DEVELOPER CONSOLE",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Divider(color = Color.White.copy(alpha = 0.1f))

                var systemHealth by remember { mutableStateOf<Map<String, String>?>(null) }

                LaunchedEffect(Unit) {
                    systemHealth = dataRepository.getSystemHealth().getOrNull()
                }

                if (systemHealth != null) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Text("SYSTEM HEALTH", fontSize = 10.sp, color = TextSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            systemHealth?.forEach { (k, v) ->
                                Text("$k: $v", fontSize = 10.sp, color = if (v.contains("error")) Color.Red else Color.Green)
                            }
                        }
                    }
                    Divider(color = Color.White.copy(alpha = 0.05f))
                }

                LazyColumn(
                    modifier = Modifier.weight(1f).padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(logs.reversed()) { log ->
                        Column {
                            Row {
                                Text(
                                    text = "[${log.timestamp}]",
                                    fontSize = 10.sp,
                                    color = TextSecondary,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = log.level,
                                    fontSize = 10.sp,
                                    color = if (log.level == "ERROR") Color.Red else Color.Cyan,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Text(
                                text = "${log.tag}: ${log.message}",
                                fontSize = 12.sp,
                                color = TextPrimary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                val context = LocalContext.current

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val logText = logs.reversed().joinToString("\n") { "[${it.timestamp}] ${it.level} ${it.tag}: ${it.message}" }
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "PKY AI Assistant Debug Logs")
                                putExtra(Intent.EXTRA_TEXT, logText)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share Logs"))
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("Share Logs")
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}
