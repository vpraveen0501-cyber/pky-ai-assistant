package com.pkyai.android.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.pkyai.android.AuthManager
import net.sqlcipher.database.SupportFactory

@Database(entities = [ChatHistoryItem::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, authManager: AuthManager): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val dbPassword = authManager.getDatabasePassword()
                val factory = SupportFactory(dbPassword.toByteArray())
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pky_ai_database"
                )
                .openHelperFactory(factory)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
