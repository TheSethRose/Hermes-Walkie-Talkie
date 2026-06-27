package com.hermes.voiceremote.wear

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.hermes.voiceremote.service.VoiceServiceController

class WearPttListenerService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        val controller = VoiceServiceController(applicationContext)
        when (messageEvent.path) {
            PATH_PTT_START -> controller.startRecording()
            PATH_PTT_STOP -> controller.stopRecording()
        }
    }

    private companion object {
        const val PATH_PTT_START = "/hermes/ptt/start"
        const val PATH_PTT_STOP = "/hermes/ptt/stop"
    }
}
