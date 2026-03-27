package com.pkyai.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pkyai.android.data.model.SystemStats
import com.pkyai.android.data.repository.ConfigRepository
import com.pkyai.android.data.repository.DataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataRepository: DataRepository,
    val configRepository: ConfigRepository
) : ViewModel() {

    private val _personaName = MutableStateFlow("PKY AI Assistant")
    val personaName: StateFlow<String> = _personaName

    private val _connectionTestResult = MutableStateFlow<String?>(null)
    val connectionTestResult: StateFlow<String?> = _connectionTestResult

    init {
        loadPreferences()
    }

    private fun loadPreferences() {
        viewModelScope.launch {
            dataRepository.getPreferences().onSuccess { name ->
                _personaName.value = name
            }
        }
    }

    fun updatePersonaName(name: String) {
        _personaName.value = name
        viewModelScope.launch {
            dataRepository.updatePreferences(name)
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            _connectionTestResult.value = "Testing..."
            dataRepository.getSystemStats().fold(
                onSuccess = { _connectionTestResult.value = "Connection Successful" },
                onFailure = { _connectionTestResult.value = "Connection Failed: ${it.message}" }
            )
        }
    }

    fun saveConfig(host: String, port: String) {
        configRepository.updateConfig(host, port)
    }
}
