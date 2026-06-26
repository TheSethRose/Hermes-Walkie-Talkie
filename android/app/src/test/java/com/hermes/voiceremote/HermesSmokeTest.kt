package com.hermes.voiceremote

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.hermes.voiceremote.network.HermesApiClient
import com.hermes.voiceremote.network.HermesGatewayRoutes
import com.hermes.voiceremote.network.HermesProfileDto
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
        val emptySettings = HermesSettings("", "", "main", "Main", ResponseMode.TEXT_AUDIO, AudioInputPreference.AUTO)
        
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
}
