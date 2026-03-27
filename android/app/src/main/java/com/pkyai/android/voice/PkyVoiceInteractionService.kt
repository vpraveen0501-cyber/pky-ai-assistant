package com.pkyai.android.voice

import android.service.voice.VoiceInteractionService
import android.util.Log

/**
 * PkyVoiceInteractionService — makes PKY AI Assistant the system-level assistant.
 *
 * When the user selects PKY AI as their default assistant
 * (Settings → Apps → Default Apps → Assist & voice input → PKY AI),
 * this service is bound at boot and runs continuously in the background.
 *
 * Global invocation triggers (after user selects PKY AI as default):
 *   - Long-press power button
 *   - Navigation bar home gesture (hold)
 *   - Push-to-talk hardware buttons (Android Automotive)
 *   - "Hey PKY" hotword (when hotword detection is implemented)
 *
 * When invoked, Android starts a PkyVoiceInteractionSession which reads
 * the current screen via the Assist API (AssistStructure).
 *
 * AndroidManifest.xml requirements (added separately):
 *   <service
 *       android:name=".voice.PkyVoiceInteractionService"
 *       android:permission="android.permission.BIND_VOICE_INTERACTION"
 *       android:exported="true">
 *       <intent-filter>
 *           <action android:name="android.service.voice.VoiceInteractionService" />
 *       </intent-filter>
 *       <meta-data
 *           android:name="android.voice_interaction"
 *           android:resource="@xml/pky_interaction" />
 *   </service>
 */
class PkyVoiceInteractionService : VoiceInteractionService() {

    companion object {
        private const val TAG = "PkyVoiceInteractionSvc"
    }

    override fun onReady() {
        super.onReady()
        Log.i(TAG, "PKY AI is now the active system assistant")
        // Future: start hotword detector here (e.g. Picovoice Porcupine "Hey PKY")
    }

    override fun onShutdown() {
        super.onShutdown()
        Log.i(TAG, "PKY AI voice interaction service shutting down")
    }
}
