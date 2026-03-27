package com.pkyai.android.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class VoiceViewModel @Inject constructor() : ViewModel() {

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _statusText = MutableStateFlow("Tap to Wake PKY AI Assistant")
    val statusText: StateFlow<String> = _statusText

    private val _selectedModel = MutableStateFlow("general")
    val selectedModel: StateFlow<String> = _selectedModel

    val availableModels = listOf("general", "build", "plan", "adaptive", "extended")

    fun setRecordingState(active: Boolean) {
        _isRecording.value = active
    }

    fun updateStatus(text: String) {
        _statusText.value = text
    }

    fun setModel(model: String) {
        if (availableModels.contains(model)) {
            _selectedModel.value = model
        }
    }
}
