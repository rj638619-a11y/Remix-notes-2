package com.example.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin

object AudioTimerHelper {
    fun playTimerAlarm(times: Int = 3) {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val sampleRate = 44100
                val durationMs = 380
                val numSamples = (sampleRate * durationMs) / 1000
                val sample = ShortArray(numSamples)
                val freqOfTone = 880.0 // 880Hz tone

                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    // Apply smooth envelope fade in and exponential decay
                    val envelope = when {
                        i < 500 -> i.toDouble() / 500.0
                        else -> (1.0 - (i - 500).toDouble() / (numSamples - 500)).coerceAtLeast(0.0)
                    }
                    val sampleVal = (sin(2.0 * Math.PI * freqOfTone * t) * envelope * Short.MAX_VALUE * 0.4).toInt()
                    sample[i] = sampleVal.toShort()
                }

                val bufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(numSamples * 2)

                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack.write(sample, 0, numSamples)

                for (i in 0 until times) {
                    audioTrack.setPlaybackHeadPosition(0)
                    audioTrack.play()
                    delay(380)
                }

                delay(200)
                audioTrack.stop()
                audioTrack.release()
            } catch (_: Exception) {
                // Fallback gracefully
            }
        }
    }
}
