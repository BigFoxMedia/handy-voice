package com.bigfoxmedia.handyvoice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream

class AudioRecorder {
    private var record: AudioRecord? = null
    private var thread: Thread? = null
    private val pcm = ByteArrayOutputStream()

    @Volatile
    private var recording = false

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (recording) return false
        pcm.reset()

        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) return false
        val bufferSize = maxOf(minBuffer * 2, 4096)

        try {
            val audioRecord = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(bufferSize)
                .build()

            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord.release()
                return false
            }

            audioRecord.startRecording()
            if (audioRecord.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord.release()
                return false
            }

            record = audioRecord
            recording = true
            thread = Thread {
                val buffer = ByteArray(bufferSize)
                while (recording) {
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read <= 0) break
                    synchronized(pcm) {
                        pcm.write(buffer, 0, read)
                    }
                }
            }.apply {
                name = "handy-voice-recorder"
                start()
            }
            return true
        } catch (error: Exception) {
            recording = false
            record?.release()
            record = null
            return false
        }
    }

    fun stop(): ByteArray {
        recording = false
        val audioRecord = record
        record = null
        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }
        thread?.join(3000)
        thread = null
        audioRecord?.release()
        synchronized(pcm) {
            return pcm.toByteArray()
        }
    }

    fun isRecording(): Boolean = recording

    fun cancel() {
        recording = false
        record?.stop()
        record?.release()
        record = null
        thread?.join(1000)
        thread = null
        pcm.reset()
    }

    companion object {
        const val SAMPLE_RATE = 16_000
    }
}
