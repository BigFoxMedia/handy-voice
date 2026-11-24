package com.bigfoxmedia.handyvoice

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class SttOutcome {
    data class Success(val text: String) : SttOutcome()
    data class Failure(val message: String) : SttOutcome()
}

class RemoteSttClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun transcribe(
        baseUrl: String,
        model: String,
        apiKey: String,
        language: String,
        pcm: ByteArray,
    ): SttOutcome {
        if (baseUrl.isBlank()) return SttOutcome.Failure("LiteLLM base URL is empty")
        if (model.isBlank()) return SttOutcome.Failure("Model is empty")
        if (pcm.isEmpty()) return SttOutcome.Failure("No audio recorded")

        val wav = WavEncoder.pcm16ToWav(pcm)
        val form = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", model)
            .addFormDataPart("response_format", "json")
            .addFormDataPart(
                "file",
                "audio.wav",
                wav.toRequestBody("audio/wav".toMediaType()),
            )

        if (language.isNotBlank() && language != "auto") {
            form.addFormDataPart("language", language)
        }

        val requestBuilder = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/audio/transcriptions")
            .post(form.build())
            .header("User-Agent", "HandyVoice/0.1 Android")

        if (apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $apiKey")
        }

        return try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return SttOutcome.Failure("HTTP ${response.code}: ${shorten(body)}")
                }
                val text = JSONObject(body).optString("text", "")
                if (text.isBlank()) {
                    SttOutcome.Failure("Remote response contained no text")
                } else {
                    SttOutcome.Success(text)
                }
            }
        } catch (error: Exception) {
            SttOutcome.Failure("Network error: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    fun testConnection(baseUrl: String, apiKey: String): SttOutcome {
        if (baseUrl.isBlank()) return SttOutcome.Failure("LiteLLM base URL is empty")
        val builder = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/models")
            .get()
            .header("User-Agent", "HandyVoice/0.1 Android")
        if (apiKey.isNotBlank()) {
            builder.header("Authorization", "Bearer $apiKey")
        }
        return try {
            client.newCall(builder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    SttOutcome.Success("Connection OK")
                } else {
                    SttOutcome.Failure("HTTP ${response.code}: ${shorten(response.body?.string().orEmpty())}")
                }
            }
        } catch (error: Exception) {
            SttOutcome.Failure("Network error: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    private fun shorten(value: String): String {
        val compact = value.replace(Regex("\\s+"), " ").trim()
        return if (compact.length > 220) compact.take(220) + "…" else compact
    }
}
