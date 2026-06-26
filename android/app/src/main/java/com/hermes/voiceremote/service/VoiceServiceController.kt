package com.hermes.voiceremote.service

import android.content.Context
import android.content.Intent
import android.os.Build

class VoiceServiceController(private val context: Context) {

    fun startRecording() {
        sendIntent(VoiceSessionService.ACTION_START_RECORDING)
    }

    fun stopRecording() {
        sendIntent(VoiceSessionService.ACTION_STOP_RECORDING)
    }

    fun cancel() {
        sendIntent(VoiceSessionService.ACTION_CANCEL)
    }

    fun interrupt() {
        sendIntent(VoiceSessionService.ACTION_INTERRUPT)
    }

    fun reset() {
        sendIntent(VoiceSessionService.ACTION_RESET)
    }

    fun replayLastResponse() {
        sendIntent(VoiceSessionService.ACTION_REPLAY_LAST_RESPONSE)
    }

    fun newSession() {
        sendIntent(VoiceSessionService.ACTION_NEW_SESSION)
    }

    private fun sendIntent(action: String) {
        val intent = Intent(context, VoiceSessionService::class.java).apply {
            this.action = action
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
