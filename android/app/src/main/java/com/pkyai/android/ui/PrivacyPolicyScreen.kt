package com.pkyai.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pkyai.android.ui.theme.*

@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(AppBackground, Color(0xFF001220))
                )
            )
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Privacy Policy",
                color = PkyAiBlue,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )

            PolicySection(
                title = "DPDP Act Compliance (India)",
                content = "PKY AI Assistant is built to comply with the Digital Personal Data Protection (DPDP) Act. We respect your digital sovereignty and provide clear controls over your personal information."
            )

            PolicySection(
                title = "Local-First Processing",
                content = "Most AI reasoning, knowledge (RAG), and voice processing happen directly on your device. This ensures your data never leaves your control unless explicitly required for external integrations (e.g., Google/Microsoft OAuth)."
            )

            PolicySection(
                title = "End-to-End Encryption",
                content = "All local data, including chat history and persona settings, are encrypted with hardware-backed AES-256 (SQLCipher) to prevent unauthorized physical access."
            )

            PolicySection(
                title = "Data Deletion",
                content = "You have the right to erase all local data at any time via the 'Reset System' option in the settings."
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GlassSurface)
            ) {
                Text("Return to System", color = TextPrimary)
            }
        }
    }
}

@Composable
fun PolicySection(title: String, content: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(text = title, color = PkyAiGold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = content, color = TextPrimary, fontSize = 14.sp, lineHeight = 20.sp)
    }
}
