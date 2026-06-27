package com.hermes.voiceremote

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.hermes.voiceremote.network.HermesApiClient
import com.hermes.voiceremote.network.HermesGatewayRoutes
import com.hermes.voiceremote.network.HermesProfileDto
import com.hermes.voiceremote.network.StreamAudioChunkEvent
import com.hermes.voiceremote.network.StreamControlEvent
import com.hermes.voiceremote.network.StreamVadDebugEvent
import com.hermes.voiceremote.audio.RmsVad
import com.hermes.voiceremote.audio.VoiceActivityEvent
import com.hermes.voiceremote.settings.AudioInputPreference
import com.hermes.voiceremote.settings.HermesSettings
import com.hermes.voiceremote.settings.ResponseMode
import com.hermes.voiceremote.settings.SettingsRepository
import com.hermes.voiceremote.settings.TalkInteractionMode
import com.hermes.voiceremote.settings.VadEngine
import com.hermes.voiceremote.settings.defaultVadSettings
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class HermesSmokeTest {

    @Test
    fun testPackageName() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        assertEquals(BuildConfig.APPLICATION_ID, context.packageName)
    }

    @Test
    fun testNormalizeBaseUrl() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val repo = SettingsRepository(context)
        assertEquals("https://gateway.example.com", repo.normalizeBaseUrl("https://gateway.example.com/"))
        assertEquals("https://gateway.example.com", repo.normalizeBaseUrl("https://gateway.example.com"))
    }

    @Test
    fun testApiClientGetUrl() {
        val client = HermesApiClient()
        val url = client.getUrl("https://gateway.example.com/", "/health")
        assertEquals("https://gateway.example.com/health", url.toString())
    }

    @Test
    fun testApiClientRejectEmptySettings() {
        val client = HermesApiClient()
        val emptySettings = HermesSettings("", "", "main", "Main", ResponseMode.TEXT_AUDIO, AudioInputPreference.AUTO, TalkInteractionMode.TAP_TO_TALK)
        
        kotlinx.coroutines.runBlocking {
            val healthResult = client.health(emptySettings)
            assertTrue(healthResult.isFailure)
            
            val sessionResult = client.createSession(emptySettings)
            assertTrue(sessionResult.isFailure)
        }
    }

    @Test
    fun testAudioUrlResolution() {
        val baseUrl = "https://gateway.example.com/"
        val relativeUrl = "/audio/abc.mp3"
        val resolvedUrl = if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) {
            relativeUrl
        } else {
            "${baseUrl.trim().removeSuffix("/")}/${relativeUrl.trim().removePrefix("/")}"
        }
        assertEquals("https://gateway.example.com/audio/abc.mp3", resolvedUrl)
    }

    @Test
    fun testHermesGatewayRoutes() {
        assertEquals("/health", HermesGatewayRoutes.HEALTH)
        assertEquals("/voice/session", HermesGatewayRoutes.VOICE_SESSION)
        assertEquals("/voice/stream", HermesGatewayRoutes.stream())
        assertEquals("/profiles", HermesGatewayRoutes.PROFILES)
        assertEquals("/voice/session/sess_456/turn", HermesGatewayRoutes.voiceTurn("sess_456"))
        assertEquals("/voice/session/sess_456/cancel", HermesGatewayRoutes.cancel("sess_456"))
        assertEquals("/voice/session/sess_456/reset", HermesGatewayRoutes.reset("sess_456"))
        assertEquals("/voice/session/sess_456/end", HermesGatewayRoutes.end("sess_456"))
    }

    @Test
    fun testProfileDtoModel() {
        val profile = HermesProfileDto(
            id = "main",
            name = "Main",
            description = "Default Hermes profile",
            isDefault = true,
            source = "hermes",
            sttLabel = "Hermes default",
            ttsLabel = "Hermes default"
        )
        assertEquals("main", profile.id)
        assertTrue(profile.isDefault)
    }

    @Test
    fun testStreamingUrlUsesWebSocketSchemeAndQuery() {
        val client = HermesApiClient()
        val settings = HermesSettings(
            "https://gateway.example.com/",
            "key",
            "main",
            "Main",
            ResponseMode.TEXT_AUDIO,
            AudioInputPreference.AUTO,
            TalkInteractionMode.ALWAYS_LISTENING
        )

        val url = client.getStreamUrl(settings, "sess_123")

        assertTrue(url.startsWith("wss://gateway.example.com/voice/stream?"))
        assertTrue(url.contains("sessionId=sess_123"))
        assertTrue(url.contains("profileId=main"))
        assertTrue(url.contains("agent=Main"))
        assertTrue(url.contains("responseMode=text_audio"))
        assertEquals("https", Request.Builder().url(url).build().url.scheme)
    }

    @Test
    fun testStreamingEventModels() {
        val chunk = StreamAudioChunkEvent(seq = 7, data = "AAAA")
        val control = StreamControlEvent("interrupt")

        assertEquals("audio_chunk", chunk.type)
        assertEquals("pcm16", chunk.format)
        assertEquals(16000, chunk.sampleRate)
        assertEquals("interrupt", control.type)
    }

    @Test
    fun testStreamingVadDebugEventModel() {
        val debug = StreamVadDebugEvent(event = "hearing", speaking = true, rms = 720, bufferedBytes = 2048)

        assertEquals("vad_debug", debug.type)
        assertEquals("hearing", debug.event)
        assertTrue(debug.speaking)
        assertEquals(720, debug.rms)
        assertEquals(2048, debug.bufferedBytes)
    }

    @Test
    fun testVoiceActivityDetectorStartAndEnd() {
        val detector = RmsVad(sampleRate = 16000, threshold = 100.0, startMs = 200, endSilenceMs = 200)
        val voiced = ShortArray(1600) { 1000 }
        val silence = ShortArray(1600) { 0 }

        assertEquals(null, detector.accept(voiced, voiced.size))
        assertEquals(VoiceActivityEvent.SPEECH_START, detector.accept(voiced, voiced.size))
        assertEquals(null, detector.accept(silence, silence.size))
        assertEquals(VoiceActivityEvent.SPEECH_END, detector.accept(silence, silence.size))
    }

    @Test
    fun testVoiceActivityDetectorEndsWhenSpeechDropsToNoiseFloor() {
        val detector = RmsVad(sampleRate = 16000, threshold = 600.0, startMs = 200, endSilenceMs = 200)
        val voiced = ShortArray(1600) { 2000 }
        val noise = ShortArray(1600) { 500 }

        assertEquals(null, detector.accept(voiced, voiced.size))
        assertEquals(VoiceActivityEvent.SPEECH_START, detector.accept(voiced, voiced.size))
        assertEquals(null, detector.accept(noise, noise.size))
        assertEquals(VoiceActivityEvent.SPEECH_END, detector.accept(noise, noise.size))
    }

    @Test
    fun testAlwaysListeningVadPresetUsesRmsAndBargeIn() {
        val preset = TalkInteractionMode.ALWAYS_LISTENING.defaultVadSettings()

        assertEquals(VadEngine.RMS, preset.vadEngine)
        assertEquals(600f, preset.vadSpeechThreshold)
        assertEquals(1200, preset.vadSilenceMs)
        assertTrue(preset.bargeInEnabled)
        assertEquals(400, preset.bargeInMinSpeechMs)
    }
}
