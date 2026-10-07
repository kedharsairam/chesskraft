/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The cue decision rule, not the audio. A JVM test cannot hear an AudioTrack,
 * and it should not pretend to; what is worth pinning down is *which* cue the
 * board asks for, because that is the part a player learns to rely on.
 */
class SoundCueTest {
    @Test
    fun aQuietMoveIsTheWoodTap() {
        assertEquals(SoundCue.MOVE, cueFor(false, false, false, false))
    }

    @Test
    fun takingAPieceIsItsOwnSound() {
        assertEquals(SoundCue.CAPTURE, cueFor(true, false, false, false))
    }

    @Test
    fun aNewCheckAlerts() {
        assertEquals(SoundCue.CHECK, cueFor(false, true, false, false))
    }

    @Test
    fun theEndOfTheGameHasItsOwnSound() {
        assertEquals(SoundCue.END, cueFor(false, false, false, true))
    }

    @Test
    fun escapingYourOwnCheckIsNotAnAlert() {
        // In check before and in check after: the state never changed, so the
        // move is judged on what it actually did.
        assertEquals(SoundCue.MOVE, cueFor(false, true, true, false))
        assertEquals(SoundCue.CAPTURE, cueFor(true, true, true, false))
    }

    @Test
    fun checkBeatsCapture() {
        assertEquals(SoundCue.CHECK, cueFor(true, true, false, false))
    }

    @Test
    fun theEndBeatsEverythingElse() {
        assertEquals(SoundCue.END, cueFor(true, true, false, true))
        assertEquals(SoundCue.END, cueFor(true, true, true, true))
        assertEquals(SoundCue.END, cueFor(false, false, false, true))
    }

    /** The whole truth table, so a future edit cannot quietly reorder it. */
    @Test
    fun everyCombinationResolvesToExactlyOneCue() {
        for (capture in BOOLS) {
            for (checkNow in BOOLS) {
                for (checkBefore in BOOLS) {
                    for (result in BOOLS) {
                        val cue = cueFor(capture, checkNow, checkBefore, result)
                        val label = "capture=$capture check=$checkNow before=$checkBefore end=$result"
                        val expected = when {
                            result -> SoundCue.END
                            checkNow && !checkBefore -> SoundCue.CHECK
                            capture -> SoundCue.CAPTURE
                            else -> SoundCue.MOVE
                        }
                        assertEquals(label, expected, cue)
                    }
                }
            }
        }
    }

    @Test
    fun everyCueRendersAudioThatIsShortAndDistinct() {
        val rendered = SoundCue.entries.associateWith { renderCue(it) }
        val ceiling = 22050 * CUE_CEILING_MS / 1000
        for ((cue, buffer) in rendered) {
            assertTrue("$cue renders nothing", buffer.isNotEmpty())
            assertTrue("$cue is ${buffer.size} samples, over the ceiling", buffer.size <= ceiling)
            assertTrue("$cue is silent", buffer.any { it != 0.toShort() })
        }
        // Two cues that sounded alike would be two names for one sound.
        val entries = SoundCue.entries
        for (i in entries.indices) {
            for (j in entries.indices) {
                if (i < j) {
                    assertNotEquals(
                        "${entries[i]} and ${entries[j]} render the same audio",
                        rendered[entries[i]]!!.toList(),
                        rendered[entries[j]]!!.toList(),
                    )
                }
            }
        }
    }

    @Test
    fun theMoveKeepsTheLengthOfTheTapItReplaced() {
        // 22050Hz at 90ms. The everyday cue is the one a player hears dozens of
        // times a game, so its length is pinned rather than re-tuned.
        assertEquals(1984, renderCue(SoundCue.MOVE).size)
    }

    @Test
    fun aCaptureIsLouderThanAMove() {
        // Weight is amplitude as much as pitch: the quieter cue must not be the
        // more consequential one.
        assertTrue(
            "capture peaks at ${peak(renderCue(SoundCue.CAPTURE))}, move at ${peak(renderCue(SoundCue.MOVE))}",
            peak(renderCue(SoundCue.CAPTURE)) > peak(renderCue(SoundCue.MOVE)),
        )
    }

    @Test
    fun checkRisesAndEndFalls() {
        // Pitch direction is the acoustic intent, so assert the direction: a
        // zero-crossing rate is a crude but honest stand-in for "what the ear
        // hears" without an ear.
        assertTrue(
            "the alert does not climb",
            pitchSlope(renderCue(SoundCue.CHECK)) > 1.2,
        )
        assertTrue(
            "the closing motif does not fall",
            pitchSlope(renderCue(SoundCue.END)) < 0.8,
        )
    }

    /** Ratio of sign changes in the second half to the first. */
    private fun pitchSlope(buffer: ShortArray): Double {
        val half = buffer.size / 2
        val early = crossings(buffer, 0, half)
        val late = crossings(buffer, half, buffer.size)
        if (early == 0) return 0.0
        return late.toDouble() / early
    }

    /** Sign changes over [from] until [to): frequency without a filter bank. */
    private fun crossings(buffer: ShortArray, from: Int, to: Int): Int {
        var count = 0
        var previous = 0
        for (i in from until to) {
            val value = if (buffer[i] > 0) 1 else if (buffer[i] < 0) -1 else 0
            if (value != 0 && previous != 0 && value != previous) count++
            if (value != 0) previous = value
        }
        return count
    }

    private fun peak(buffer: ShortArray): Int = buffer.maxOf { abs(it.toInt()) }

    private companion object {
        val BOOLS = listOf(false, true)
    }
}