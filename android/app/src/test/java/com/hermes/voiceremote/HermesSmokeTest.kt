package com.hermes.voiceremote

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.hermes.voiceremote.network.HermesApiClient
import com.hermes.voiceremote.network.HermesGatewayRoutes
import com.hermes.voiceremote.settings.AudioInputPreference
import com.hermes.voiceremote.settings.HermesSettings
import com.hermes.voiceremote.settings.ResponseMode
import com.hermes.voiceremote.settings.SettingsRepository
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
        val emptySettings = HermesSettings("", "", "Vex Volt", ResponseMode.TEXT_AUDIO, AudioInputPreference.AUTO)
        
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
        assertEquals("/voice/session/sess_456/turn", HermesGatewayRoutes.voiceTurn("sess_456"))
        assertEquals("/voice/session/sess_456/cancel", HermesGatewayRoutes.cancel("sess_456"))
    }
}
