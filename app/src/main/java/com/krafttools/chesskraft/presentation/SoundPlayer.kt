/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft.presentation

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.exp
import kotlin.math.sin

/**
 * One wood tap. Generated, not sampled: a short decaying knock with a click
 * transient, written into a static [AudioTrack] buffer. No assets, no
 * permissions, no network. A single on/off toggle lives in the ViewModel.
 */
class SoundPlayer(context: Context) {
    private val appContext = context.applicationContext

    @Volatile
    var enabled: Boolean = true

    private val buffer: ShortArray by lazy { woodTap() }

    fun playTap() {
        if (!enabled) return
        try {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(buffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(buffer, 0, buffer.size)
            track.setPlaybackPositionUpdateListener(
                object : AudioTrack.OnPlaybackPositionUpdateListener {
                    override fun onMarkerReached(track: AudioTrack) {
                        track.release()
                    }

                    override fun onPeriodicNotification(track: AudioTrack) = Unit
                },
            )
            track.notificationMarkerPosition = buffer.size
            track.play()
        } catch (_: Exception) {
            // Sound is garnish: a device that cannot play it still plays chess.
            @Suppress("UNUSED_EXPRESSION")
            appContext
        }
    }

    private fun woodTap(): ShortArray {
        val length = (SAMPLE_RATE * DURATION_MS / 1000).toInt()
        val out = ShortArray(length)
        // Deterministic rattle so every tap sounds like the same wooden piece.
        var noise = 0x12345678
        for (i in 0 until length) {
            val t = i.toDouble() / SAMPLE_RATE
            noise = noise * 1103515245 + 12345
            val click = ((noise shr 16) % 1000) / 1000.0 / (1 + i)
            val body = sin(2.0 * Math.PI * 190.0 * t) * exp(-t * 55.0) +
                0.5 * sin(2.0 * Math.PI * 380.0 * t) * exp(-t * 80.0)
            val sample = (body * 0.8 + click * 0.4) * exp(-t * 30.0)
            out[i] = (sample.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private companion object {
        const val SAMPLE_RATE = 22050
        const val DURATION_MS = 90
    }
}
