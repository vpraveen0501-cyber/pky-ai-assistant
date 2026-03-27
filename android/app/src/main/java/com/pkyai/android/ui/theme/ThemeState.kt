package com.pkyai.android.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Calendar

enum class PkyAiThemeType {
    DEFAULT,
    MIDNIGHT_GALAXY,
    OCEAN_DEPTHS
}

object PkyAiThemeState {
    var currentTheme by mutableStateOf(PkyAiThemeType.DEFAULT)

    fun updateThemeByTime() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        currentTheme = if (hour in 22..23 || hour in 0..6) {
            PkyAiThemeType.MIDNIGHT_GALAXY
        } else {
            PkyAiThemeType.OCEAN_DEPTHS
        }
    }
}
