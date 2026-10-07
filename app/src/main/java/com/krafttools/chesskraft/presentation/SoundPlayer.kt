/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft.presentation

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * The four things a player can hear on a board. Sound is garnish: it never
 * blocks a move, and it never says anything the status line has not already
 * said. What it buys is the difference between "something happened" and "the
 * board is asking you something" when eyes are on the pieces.
 */
enum class SoundCue {
    /** A piece set down. Light, wooden, and the most frequent sound by far. */
    MOVE,

    /** A piece taken. Lower, louder, over before it can smear into the next move. */
    CAPTURE,

    /** The king that must be answered. Rising and bright, never a thud. */
    CHECK,

    /** The game is decided. Falling, and the longest of the four. */
    END,
}

/**
 * Generated sound, not sampled: each cue is a short decaying tone synthesised
 * once into a static [AudioTrack] buffer. No assets, no permissions, no
 * network. The [AudioAttributes] are the ones the original single tap used, so
 * cue routing and ringer silence behave exactly as they did before, and a
 * track is released the moment it finishes playing. A single on/off toggle
 * lives in the ViewModel.
 */
class SoundPlayer(context: Context) {
    private val appContext = context.applicationContext

    @Volatile
    var enabled: Boolean = true

    /**
     * Rendered on first use, so a player that is switched off never pays for a
     * buffer it will not play.
     */
    private val buffers: Map<SoundCue, ShortArray> by lazy {
        SoundCue.entries.associateWith { renderCue(it) }
    }

    /** Plays [cue]. Silent when [enabled] is false. */
    fun playFor(cue: SoundCue) {
        if (!enabled) return
        val buffer = buffers[cue] ?: return
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

    /** The everyday spelling of a plain move, kept for callers that just tap. */
    fun playTap() = playFor(SoundCue.MOVE)
}

/** Mono rate for every cue. A decaying knock has nothing to say above this. */
private const val SAMPLE_RATE = 22050

/** Nothing here is music: a cue that outlasts this is a bug, not a design. */
internal const val CUE_CEILING_MS = 250

/**
 * Renders one cue into 16-bit mono samples at [SAMPLE_RATE]. Deliberately pure
 * Kotlin — no Android, no context — so the acoustic shape of a cue is the same
 * on every device and can be examined by an ordinary unit test.
 */
internal fun renderCue(cue: SoundCue): ShortArray = when (cue) {
    SoundCue.MOVE -> moveTap()
    SoundCue.CAPTURE -> captureClack()
    SoundCue.CHECK -> twoNotes(
        notes = listOf(Note(hz = 880.0, millis = 55, gain = 0.9), Note(hz = 1320.0, millis = 60, gain = 0.9)),
        gapMs = 20,
        decay = 70.0,
        brightness = 0.3,
        peak = 0.62,
    )
    SoundCue.END -> twoNotes(
        notes = listOf(Note(hz = 440.0, millis = 95, gain = 1.0), Note(hz = 330.0, millis = 125, gain = 0.9)),
        gapMs = 0,
        decay = 24.0,
        brightness = 0.22,
        peak = 0.72,
    )
}

/**
 * MOVE: one wood tap. A 190Hz body with its octave, plus a deterministic
 * rattle at the front for the click of wood on wood. Kept byte for byte as the
 * original single sound — it is the cue a player hears dozens of times a game,
 * so anything that makes it "nicer" makes it worse.
 */
private fun moveTap(): ShortArray {
    val length = (SAMPLE_RATE * MOVE_MS / 1000).toInt()
    val out = ShortArray(length)
    // Deterministic rattle so every tap sounds like the same wooden piece.
    var noise = 0x12345678
    for (i in 0 until length) {
        val t = i.toDouble() / SAMPLE_RATE
        noise = noise * 1103515245 + 12345
        val click = ((noise shr 16) % 1000) / 1000.0 / (1 + i)
        val body = sin(2.0 * PI * 190.0 * t) * exp(-t * 55.0) +
            0.5 * sin(2.0 * PI * 380.0 * t) * exp(-t * 80.0)
        val sample = (body * 0.8 + click * 0.4) * exp(-t * 30.0)
        out[i] = toPcm(sample)
    }
    return out
}

/**
 * CAPTURE: the same wooden family, made heavier. Fundamental drops to 105Hz
 * with a fifth and an octave above it, so the ear reads mass rather than
 * pitch; the strike transient is sharper and the body decays at roughly twice
 * the move's rate, which keeps a busy middlegame from turning into a rumble.
 * Normalised to full scale — a capture is the loudest thing on the board.
 */
private fun captureClack(): ShortArray {
    val samples = DoubleArray(msToSamples(CAPTURE_MS))
    var noise = 0x2468ace0
    for (i in samples.indices) {
        val t = i.toDouble() / SAMPLE_RATE
        noise = noise * 1103515245 + 12345
        val grit = ((noise shr 16) % 1000) / 1000.0
        val body = sin(2.0 * PI * 105.0 * t) * exp(-t * 45.0) +
            0.45 * sin(2.0 * PI * 157.0 * t) * exp(-t * 60.0) +
            0.22 * sin(2.0 * PI * 210.0 * t) * exp(-t * 75.0)
        // The clack itself: a very short burst of wood dust ahead of the body.
        val strike = grit * exp(-t * 220.0) * 0.55
        samples[i] = body + strike
    }
    return normalise(toSamples(samples), 1.0)
}

/**
 * Two notes in one buffer, which is what turns a cue into a phrase: CHECK
 * ascends a fifth, END falls a fourth. [brightness] is the level of the octave
 * partial, and it is not decoration — a phone speaker reproduces almost nothing
 * of a bare fundamental, so the partial is what makes the pitch legible.
 * [gapMs] of silence between the notes is what makes two blips rather than one
 * longer tone.
 */
private fun twoNotes(
    notes: List<Note>,
    gapMs: Int,
    decay: Double,
    brightness: Double,
    peak: Double,
): ShortArray {
    val total = msToSamples(notes.sumOf { it.millis } + gapMs)
    val samples = DoubleArray(total)
    var offsetMs = 0
    for (note in notes) {
        val start = msToSamples(offsetMs)
        val length = minOf(msToSamples(note.millis), total - start)
        val attack = ATTACK_MS_SAMPLES
        for (i in 0 until length) {
            val t = i.toDouble() / SAMPLE_RATE
            val ramp = minOf(1.0, i.toDouble() / attack)
            val envelope = exp(-t * decay) * ramp
            samples[start + i] = (sin(2.0 * PI * note.hz * t) +
                brightness * sin(2.0 * PI * note.hz * 2.0 * t)) * envelope * note.gain
        }
        offsetMs += note.millis + gapMs
    }
    return normalise(toSamples(samples), peak)
}

/** One note of a multi-note cue: pitch, length, and level. */
private data class Note(val hz: Double, val millis: Int, val gain: Double)

/** Two milliseconds: long enough to kill the click, too short to hear as lag. */
private const val ATTACK_MS_SAMPLES = 44

/** MOVE is the everyday cue, and the short one: ~90ms. */
private const val MOVE_MS = 90

/** CAPTURE runs a little longer so the low fundamental has room to land. */
private const val CAPTURE_MS = 130

/** Samples for [millis] at [SAMPLE_RATE]. */
private fun msToSamples(millis: Int): Int =
    (SAMPLE_RATE * millis / 1000.0).toInt().coerceAtLeast(1)

/** Float mix buffer to 16-bit, hard-clipped the way the original tap was. */
private fun toSamples(samples: DoubleArray): ShortArray {
    val out = ShortArray(samples.size)
    for (i in samples.indices) {
        out[i] = toPcm(samples[i])
    }
    return out
}

/** Full-scale magnitude for 16-bit PCM. */
private const val MAX_AMPLITUDE = 32_767

/** One clamped float sample as 16-bit PCM. */
private fun toPcm(value: Double): Short {
    val clamped: Double = if (value > 1.0) 1.0 else if (value < -1.0) -1.0 else value
    return (clamped * MAX_AMPLITUDE).toInt().toShort()
}

/**
 * Scales [out] so its loudest sample sits at [peak] of full scale. Done here
 * rather than by clipping, so a cue is as loud as it was designed to be on
 * every device, instead of however loud the mix happened to come out.
 */
private fun normalise(out: ShortArray, peak: Double): ShortArray {
    var loudest = 0
    for (sample in out) {
        val magnitude = abs(sample.toInt())
        if (magnitude > loudest) loudest = magnitude
    }
    if (loudest == 0) return out
    val scale = peak * MAX_AMPLITUDE / loudest
    for (i in out.indices) {
        val scaled: Int = (out[i].toInt() * scale).toInt()
        val clamped: Int = if (scaled > MAX_AMPLITUDE) MAX_AMPLITUDE else scaled
        out[i] = (if (clamped < -MAX_AMPLITUDE) -MAX_AMPLITUDE else clamped).toShort()
    }
    return out
}