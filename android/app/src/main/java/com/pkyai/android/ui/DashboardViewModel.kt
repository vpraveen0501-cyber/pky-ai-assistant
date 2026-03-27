package com.pkyai.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pkyai.android.data.model.SystemStats
import com.pkyai.android.data.repository.DataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class DashboardUiState {
    data object Loading : DashboardUiState()
    data class Success(val stats: SystemStats) : DashboardUiState()
    data class Error(val message: String) : DashboardUiState()
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val dataRepository: DataRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState

    init {
        loadStats()
    }

    fun loadStats() {
        viewModelScope.launch {
            _uiState.value = DashboardUiState.Loading
            val result = dataRepository.getSystemStats()
            result.fold(
                onSuccess = { stats -> _uiState.value = DashboardUiState.Success(stats) },
                onFailure = { e -> _uiState.value = DashboardUiState.Error(e.message ?: "Unknown error") }
            )
        }
    }
}
