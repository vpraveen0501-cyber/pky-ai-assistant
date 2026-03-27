package com.pkyai.android.services

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * PrivacyAuditLog — TRAPS framework transparency log.
 *
 * Records every access to sensitive data (screen content, contacts, location)
 * to a local file that the user can view in Settings → Privacy → Data Access Log.
 *
 * TRAPS = Trusted, Responsible, Auditable, Private, Secure.
 *
 * Log format (TSV):
 *   timestamp | source | dataType | processedLocally | appPackage
 *
 * The log file is stored in internal app storage and never uploaded to the server.
 * Maximum 10,000 entries — older entries are pruned automatically.
 */
object PrivacyAuditLog {

    private const val TAG = "PrivacyAuditLog"
    private const val LOG_FILENAME = "privacy_audit.log"
    private const val MAX_ENTRIES = 10_000
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    /**
     * Record a data access event.
     *
     * @param source          What triggered the access (e.g. "AssistAPI", "Camera", "Microphone").
     * @param dataType        Type of data accessed (e.g. "screen_content", "audio", "image").
     * @param processedLocally True = data never left device; False = sent to server.
     * @param context         Android context for file access.
     * @param appPackage      Package name of the app whose data was accessed (optional).
     */
    fun log(
        source: String,
        dataType: String,
        processedLocally: Boolean,
        context: Context,
        appPackage: String = ""
    ) {
        try {
            val logFile = File(context.filesDir, LOG_FILENAME)
            val timestamp = dateFormat.format(Date())
            val localLabel = if (processedLocally) "ON_DEVICE" else "CLOUD"
            val entry = "$timestamp\t$source\t$dataType\t$localLabel\t$appPackage\n"

            // Prune old entries before appending to prevent unbounded growth
            pruneIfNeeded(logFile)

            logFile.appendText(entry)
            Log.d(TAG, "Privacy access logged: $source → $dataType [$localLabel]")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to write privacy audit log: $e")
        }
    }

    /**
     * Read all audit log entries for display in the Settings UI.
     *
     * @return List of [AuditEntry] sorted newest first.
     */
    fun readLog(context: Context): List<AuditEntry> {
        return try {
            val logFile = File(context.filesDir, LOG_FILENAME)
            if (!logFile.exists()) return emptyList()

            logFile.readLines()
                .filter { it.isNotBlank() }
                .mapNotNull { line ->
                    val parts = line.split("\t")
                    if (parts.size >= 4) {
                        AuditEntry(
                            timestamp = parts[0],
                            source = parts[1],
                            dataType = parts[2],
                            processedLocally = parts[3] == "ON_DEVICE",
                            appPackage = parts.getOrElse(4) { "" }
                        )
                    } else null
                }
                .reversed()   // newest first
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read privacy audit log: $e")
            emptyList()
        }
    }

    /**
     * Clear the audit log. Called when user explicitly requests log wipe.
     */
    fun clearLog(context: Context) {
        try {
            val logFile = File(context.filesDir, LOG_FILENAME)
            logFile.writeText("")
            Log.i(TAG, "Privacy audit log cleared by user")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear privacy audit log: $e")
        }
    }

    /**
     * Get a summary of recent accesses for the Settings privacy dashboard.
     */
    fun getSummary(context: Context): PrivacySummary {
        val entries = readLog(context)
        val screenAccesses = entries.count { it.dataType == "screen_content" }
        val allOnDevice = entries.all { it.processedLocally }
        val lastAccess = entries.firstOrNull()?.timestamp ?: "Never"
        return PrivacySummary(
            totalAccesses = entries.size,
            screenContextAccesses = screenAccesses,
            allProcessedOnDevice = allOnDevice,
            lastAccessTime = lastAccess
        )
    }

    private fun pruneIfNeeded(logFile: File) {
        val lines = logFile.readLines()
        if (lines.size > MAX_ENTRIES) {
            // Keep the most recent MAX_ENTRIES lines
            logFile.writeText(lines.takeLast(MAX_ENTRIES).joinToString("\n") + "\n")
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Data classes
    // ──────────────────────────────────────────────────────────────

    data class AuditEntry(
        val timestamp: String,
        val source: String,
        val dataType: String,
        val processedLocally: Boolean,
        val appPackage: String
    )

    data class PrivacySummary(
        val totalAccesses: Int,
        val screenContextAccesses: Int,
        val allProcessedOnDevice: Boolean,
        val lastAccessTime: String
    )
}
