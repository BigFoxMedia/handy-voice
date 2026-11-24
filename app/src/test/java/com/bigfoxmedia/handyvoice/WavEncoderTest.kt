package com.bigfoxmedia.handyvoice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavEncoderTest {
    @Test
    fun headerIsValidMono16kPcm() {
        val pcm = ByteArray(3200) // 100 ms of 16-bit mono at 16 kHz
        val wav = WavEncoder.pcm16ToWav(pcm, 16_000)

        assertEquals(44 + pcm.size, wav.size)
        assertEquals("RIFF", String(wav, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(wav, 8, 4, Charsets.US_ASCII))
        assertEquals("fmt ", String(wav, 12, 4, Charsets.US_ASCII))
        assertEquals("data", String(wav, 36, 4, Charsets.US_ASCII))

        val buffer = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(36 + pcm.size, buffer.getInt(4))
        assertEquals(1, buffer.getShort(20).toInt())
        assertEquals(1, buffer.getShort(22).toInt())
        assertEquals(16_000, buffer.getInt(24))
        assertEquals(32_000, buffer.getInt(28))
        assertEquals(2, buffer.getShort(32).toInt())
        assertEquals(16, buffer.getShort(34).toInt())
        assertEquals(pcm.size, buffer.getInt(40))
    }

    @Test
    fun oddPcmLengthIsTruncatedToWholeSamples() {
        val wav = WavEncoder.pcm16ToWav(ByteArray(3201), 16_000)
        assertEquals(44 + 3200, wav.size)
        assertTrue(wav.size % 2 == 0)
    }
}
