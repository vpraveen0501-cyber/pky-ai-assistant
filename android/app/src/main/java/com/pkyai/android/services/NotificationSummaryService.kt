package com.pkyai.android.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * NotificationSummaryService — AI-powered notification summarizer.
 *
 * When 3+ notifications accumulate from the same app within 5 minutes,
 * this service uses Gemini Nano (on-device) to summarize them into
 * a single concise notification — reducing interruption fatigue.
 *
 * Matches Android 16's built-in AI notification summary feature.
 * All processing is on-device (Gemini Nano via AICore).
 *
 * Enable in AndroidManifest.xml:
 *   <service
 *       android:name=".services.NotificationSummaryService"
 *       android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
 *       android:exported="true">
 *       <intent-filter>
 *           <action android:name="android.service.notification.NotificationListenerService" />
 *       </intent-filter>
 *   </service>
 *
 * User must grant notification access in:
 *   Settings → Apps → Special app access → Notification access → PKY AI
 */
class NotificationSummaryService : NotificationListenerService() {

    companion object {
        private const val TAG = "NotifSummaryService"
        private const val SUMMARY_CHANNEL_ID = "pky_summaries"
        private const val BATCH_WINDOW_MS = 5 * 60 * 1000L  // 5 minutes
        private const val BATCH_MIN_COUNT = 3                 // summarize after 3+ notifs
        private const val SUMMARY_NOTIF_ID = 2001
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Pending notifications grouped by app package
    private val pendingByApp = mutableMapOf<String, MutableList<NotificationData>>()

    // Debounce timers per app
    private val debounceJobs = mutableMapOf<String, kotlinx.coroutines.Job>()

    override fun onCreate() {
        super.onCreate()
        createSummaryChannel()
        Log.i(TAG, "NotificationSummaryService started")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Skip PKY AI's own notifications to avoid loops
        if (sbn.packageName == packageName) return

        // Skip system notifications (no-clear, ongoing)
        if (sbn.isOngoing) return

        val text = extractNotificationText(sbn) ?: return
        val appPackage = sbn.packageName

        // Log to privacy audit (notification content — processed on-device)
        PrivacyAuditLog.log(
            source = "NotificationListener",
            dataType = "notification_text",
            processedLocally = true,
            context = this,
            appPackage = appPackage
        )

        // Accumulate notifications per app
        val pending = pendingByApp.getOrPut(appPackage) { mutableListOf() }
        pending.add(NotificationData(text = text, timestamp = System.currentTimeMillis(), sbn = sbn))

        // Cancel existing debounce timer and restart
        debounceJobs[appPackage]?.cancel()
        debounceJobs[appPackage] = scope.launch {
            delay(BATCH_WINDOW_MS)
            processBatch(appPackage)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // If user dismissed a notification manually, remove from pending
        pendingByApp[sbn.packageName]?.removeAll { it.sbn.key == sbn.key }
    }

    private suspend fun processBatch(appPackage: String) {
        val batch = pendingByApp[appPackage] ?: return
        if (batch.size < BATCH_MIN_COUNT) {
            pendingByApp.remove(appPackage)
            return
        }

        Log.d(TAG, "Summarizing ${batch.size} notifications from $appPackage")

        val combinedText = batch.joinToString("\n") { "• ${it.text}" }
        val appLabel = getAppLabel(appPackage)

        // On-device summarization via Gemini Nano
        val summary = summarizeOnDevice(combinedText, appLabel, batch.size)

        // Show the summary notification
        showSummaryNotification(appLabel, summary, batch.size)

        // Cancel the individual notifications that were summarized
        batch.forEach { notifData ->
            try { cancelNotification(notifData.sbn.key) } catch (_: Exception) {}
        }

        // Clear the batch
        pendingByApp.remove(appPackage)
        debounceJobs.remove(appPackage)
    }

    /**
     * Summarize notifications on-device using Gemini Nano.
     * Falls back to a simple count-based summary if AI unavailable.
     */
    private fun summarizeOnDevice(texts: String, appName: String, count: Int): String {
        return try {
            // Attempt Gemini Nano via reflection (same pattern as OnDeviceLLMService)
            val modelClass = Class.forName("com.google.ai.edge.aicore.GenerativeModel")
            val model = modelClass.getConstructor(String::class.java).newInstance("gemini-nano")
            val prompt = "Summarize these $count $appName notifications in one sentence (max 20 words):\n$texts"
            val response = model.javaClass.getMethod("generateContent", String::class.java).invoke(model, prompt)
            response?.javaClass?.getMethod("getText")?.invoke(response) as? String
                ?: fallbackSummary(appName, count)
        } catch (_: Exception) {
            fallbackSummary(appName, count)
        }
    }

    private fun fallbackSummary(appName: String, count: Int) =
        "You have $count new notifications from $appName"

    private fun showSummaryNotification(appName: String, summary: String, count: Int) {
        val notification = NotificationCompat.Builder(this, SUMMARY_CHANNEL_ID)
            .setContentTitle("$appName ($count messages)")
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(SUMMARY_NOTIF_ID, notification)
    }

    private fun extractNotificationText(sbn: StatusBarNotification): String? {
        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getString(Notification.EXTRA_TEXT) ?: ""
        val bigText = extras.getString(Notification.EXTRA_BIG_TEXT) ?: ""
        val content = bigText.ifEmpty { text }
        if (title.isEmpty() && content.isEmpty()) return null
        return if (title.isNotEmpty()) "$title: $content" else content
    }

    private fun getAppLabel(packageName: String): String {
        return try {
            val info = applicationContext.packageManager.getApplicationInfo(packageName, 0)
            applicationContext.packageManager.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            packageName.substringAfterLast(".")
        }
    }

    private fun createSummaryChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                SUMMARY_CHANNEL_ID,
                "PKY AI Notification Summaries",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "AI-summarized groups of notifications"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private data class NotificationData(
        val text: String,
        val timestamp: Long,
        val sbn: StatusBarNotification
    )
}
