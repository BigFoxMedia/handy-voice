package com.bigfoxmedia.handyvoice

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RemoteSttClientTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun transcribeReturnsTextFromJson() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"text\":\"hello from litellm\"}"),
        )

        val client = RemoteSttClient()
        val outcome = client.transcribe(
            baseUrl = server.url("/v1/").toString(),
            model = "stt-whisper-local",
            apiKey = "test-key",
            language = "auto",
            pcm = ByteArray(3200),
        )

        assertTrue(outcome is SttOutcome.Success)
        assertEquals("hello from litellm", (outcome as SttOutcome.Success).text)

        val request = server.takeRequest()
        assertEquals("/v1/audio/transcriptions", request.path)
        assertEquals("Bearer test-key", request.getHeader("Authorization"))
        assertTrue(request.getHeader("Content-Type")?.startsWith("multipart/form-data") == true)
    }

    @Test
    fun httpFailureIsReported() {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("{\"error\":\"bad key\"}"),
        )

        val outcome = RemoteSttClient().transcribe(
            baseUrl = server.url("/v1/").toString(),
            model = "stt-whisper-local",
            apiKey = "bad",
            language = "auto",
            pcm = ByteArray(3200),
        )

        assertTrue(outcome is SttOutcome.Failure)
        assertTrue((outcome as SttOutcome.Failure).message.contains("401"))
    }

    @Test
    fun testConnectionUsesModelsEndpoint() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"data\":[]}"),
        )

        val outcome = RemoteSttClient().testConnection(
            baseUrl = server.url("/v1/").toString(),
            apiKey = "test-key",
        )

        assertTrue(outcome is SttOutcome.Success)
        assertEquals("/v1/models", server.takeRequest().path)
    }
}
