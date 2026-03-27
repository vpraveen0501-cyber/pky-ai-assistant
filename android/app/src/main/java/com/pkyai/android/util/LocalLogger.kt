package com.pkyai.android.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.*

data class LogEntry(
    val timestamp: String,
    val level: String,
    val tag: String,
    val message: String
)

object LocalLogger {
    private const val MAX_LOG_ENTRIES = 200
    private val buffer = ArrayDeque<LogEntry>(MAX_LOG_ENTRIES)
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs

    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun log(level: String, tag: String, message: String) {
        val entry = LogEntry(
            timestamp = dateFormat.format(Date()),
            level = level,
            tag = tag,
            message = message
        )
        synchronized(buffer) {
            if (buffer.size >= MAX_LOG_ENTRIES) buffer.removeFirst()
            buffer.addLast(entry)
            _logs.value = buffer.toList()
        }
    }

    fun d(tag: String, message: String) = log("DEBUG", tag, message)
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        log("ERROR", tag, message + (throwable?.let { "\n${it.stackTraceToString()}" } ?: ""))
    }
}
