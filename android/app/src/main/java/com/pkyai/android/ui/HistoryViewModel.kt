package com.pkyai.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pkyai.android.data.ChatHistoryItem
import com.pkyai.android.data.HistoryDao
import com.pkyai.android.data.repository.DataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val dataRepository: DataRepository,
    private val historyDao: HistoryDao
) : ViewModel() {

    val historyItems: Flow<List<ChatHistoryItem>> = historyDao.getAllHistory()

    init {
        refreshHistory()
    }

    fun refreshHistory() {
        viewModelScope.launch {
            dataRepository.getHistory()
        }
    }
}
