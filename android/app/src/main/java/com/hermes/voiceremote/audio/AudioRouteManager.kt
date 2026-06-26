package com.hermes.voiceremote.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.hermes.voiceremote.settings.AudioInputPreference
import com.hermes.voiceremote.settings.AudioRoute
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class AudioRouteManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun getCurrentRoute(): AudioRoute {
        return try {
            if (isBluetoothHeadsetConnected()) {
                AudioRoute.BLUETOOTH_HEADSET
            } else {
                AudioRoute.PHONE
            }
        } catch (e: Exception) {
            Log.e("AudioRouteManager", "Error checking audio route", e)
            AudioRoute.UNKNOWN
        }
    }

    fun preferConfiguredRoute(preference: AudioInputPreference): AudioRoute {
        val hasBluetooth = isBluetoothHeadsetConnected()
        return try {
            when (preference) {
                AudioInputPreference.BLUETOOTH_HEADSET -> {
                    if (hasBluetooth) {
                        enableBluetoothRouting(true)
                        AudioRoute.BLUETOOTH_HEADSET
                    } else {
                        enableBluetoothRouting(false)
                        AudioRoute.PHONE
                    }
                }
                AudioInputPreference.PHONE_MIC -> {
                    enableBluetoothRouting(false)
                    AudioRoute.PHONE
                }
                AudioInputPreference.AUTO -> {
                    if (hasBluetooth) {
                        enableBluetoothRouting(true)
                        AudioRoute.BLUETOOTH_HEADSET
                    } else {
                        enableBluetoothRouting(false)
                        AudioRoute.PHONE
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AudioRouteManager", "Error preferring configured route", e)
            AudioRoute.UNKNOWN
        }
    }

    fun enableBluetoothRouting(enable: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (enable) {
                    val devices = audioManager.availableCommunicationDevices
                    val bluetoothDevice = devices.find {
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                    }
                    if (bluetoothDevice != null) {
                        val success = audioManager.setCommunicationDevice(bluetoothDevice)
                        Log.d("AudioRouteManager", "setCommunicationDevice to Bluetooth: $success")
                    } else {
                        Log.w("AudioRouteManager", "No bluetooth communication device found")
                        enableLegacyBluetoothSco(true)
                    }
                } else {
                    audioManager.clearCommunicationDevice()
                    enableLegacyBluetoothSco(false)
                }
            } else {
                enableLegacyBluetoothSco(enable)
            }
        } catch (e: Exception) {
            Log.e("AudioRouteManager", "Error enabling Bluetooth routing to $enable", e)
            enableLegacyBluetoothSco(enable)
        }
    }

    private fun enableLegacyBluetoothSco(enable: Boolean) {
        try {
            if (enable) {
                @Suppress("DEPRECATION")
                if (!audioManager.isBluetoothScoOn) {
                    audioManager.startBluetoothSco()
                    audioManager.isBluetoothScoOn = true
                }
            } else {
                @Suppress("DEPRECATION")
                if (audioManager.isBluetoothScoOn) {
                    audioManager.stopBluetoothSco()
                    audioManager.isBluetoothScoOn = false
                }
            }
        } catch (e: Exception) {
            Log.e("AudioRouteManager", "Error setting legacy Bluetooth SCO state to $enable", e)
        }
    }

    suspend fun awaitLegacyScoConnection() {
        @Suppress("DEPRECATION")
        if (!audioManager.isBluetoothScoOn) {
            return
        }
        
        Log.d("AudioRouteManager", "Awaiting legacy SCO connection...")
        withTimeoutOrNull(2000) {
            suspendCancellableCoroutine<Unit> { cont ->
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context?, intent: Intent?) {
                        if (intent?.action == AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED) {
                            val state = intent.getIntExtra(
                                AudioManager.EXTRA_SCO_AUDIO_STATE,
                                AudioManager.SCO_AUDIO_STATE_DISCONNECTED
                            )
                            Log.d("AudioRouteManager", "Legacy SCO state update received: $state")
                            if (state == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
                                try {
                                    ctx?.unregisterReceiver(this)
                                } catch (e: Exception) {
                                    // Ignore if already unregistered
                                }
                                if (cont.isActive) cont.resume(Unit)
                            }
                        }
                    }
                }
                
                val filter = IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    context.registerReceiver(receiver, filter)
                }
                
                cont.invokeOnCancellation {
                    try {
                        context.unregisterReceiver(receiver)
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }
        }
        Log.d("AudioRouteManager", "Legacy SCO connection finished awaiting (or timed out)")
    }

    fun hasBluetoothInput(): Boolean {
        return try {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            for (device in devices) {
                if (device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    device.type == AudioDeviceInfo.TYPE_BLE_HEADSET) {
                    return true
                }
            }
            false
        } catch (e: Exception) {
            Log.e("AudioRouteManager", "Failed to query input devices using modern API", e)
            @Suppress("DEPRECATION")
            audioManager.isBluetoothScoOn
        }
    }

    fun hasBluetoothOutput(): Boolean {
        return try {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            for (device in devices) {
                if (device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    device.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER) {
                    return true
                }
            }
            false
        } catch (e: Exception) {
            Log.e("AudioRouteManager", "Failed to query output devices using modern API", e)
            @Suppress("DEPRECATION")
            audioManager.isBluetoothA2dpOn
        }
    }

    fun isBluetoothHeadsetConnected(): Boolean {
        return hasBluetoothInput()
    }
}
