package com.bigfoxmedia.handyvoice

import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavEncoder {
    fun pcm16ToWav(pcm: ByteArray, sampleRate: Int = 16_000): ByteArray {
        val evenLength = pcm.size - (pcm.size % 2)
        val dataSize = evenLength
        val header = ByteArray(44)

        writeAscii(header, 0, "RIFF")
        writeIntLe(header, 4, 36 + dataSize)
        writeAscii(header, 8, "WAVE")
        writeAscii(header, 12, "fmt ")
        writeIntLe(header, 16, 16)
        writeShortLe(header, 20, 1) // PCM
        writeShortLe(header, 22, 1) // mono
        writeIntLe(header, 24, sampleRate)
        writeIntLe(header, 28, sampleRate * 2)
        writeShortLe(header, 32, 2)
        writeShortLe(header, 34, 16)
        writeAscii(header, 36, "data")
        writeIntLe(header, 40, dataSize)

        val result = ByteArray(44 + dataSize)
        System.arraycopy(header, 0, result, 0, header.size)
        System.arraycopy(pcm, 0, result, 44, dataSize)
        return result
    }

    private fun writeAscii(target: ByteArray, offset: Int, value: String) {
        for (index in value.indices) {
            target[offset + index] = value[index].code.toByte()
        }
    }

    private fun writeIntLe(target: ByteArray, offset: Int, value: Int) {
        val bytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
        System.arraycopy(bytes, 0, target, offset, bytes.size)
    }

    private fun writeShortLe(target: ByteArray, offset: Int, value: Int) {
        val bytes = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort()).array()
        System.arraycopy(bytes, 0, target, offset, bytes.size)
    }
}
