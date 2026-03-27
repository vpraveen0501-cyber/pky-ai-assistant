package com.pkyai.android.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM chat_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<ChatHistoryItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ChatHistoryItem>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ChatHistoryItem)

    @Query("DELETE FROM chat_history")
    suspend fun clearHistory()
}
