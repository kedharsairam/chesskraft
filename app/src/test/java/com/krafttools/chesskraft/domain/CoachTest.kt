/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 *
 * The coaching voice, branch by branch. There is no board in here and no
 * engine, which is the point: every line Coach can say must be derivable from
 * a verdict, a loss in centipawns and two moves. The tests below also pin what
 * it refuses to say — a GOOD move gets nothing, and an ungraded move is not
 * praised as the best one when the engine never named one.
 */
package com.krafttools.chesskraft.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachTest {

    @Test
    fun goodMovesGetNothing() {
        // The most important branch: a move that changed nothing has no lesson,
        // and a sentence about it is noise the player learns to skip.
        for (cpLoss in 0..19) {
            assertNull(Coach.lineFor(MoveVerdict.GOOD, cpLoss, "Nf3", "d4"))
        }
        // Even with a named alternative, GOOD stays silent.
        assertNull(Coach.lineFor(MoveVerdict.GOOD, 19, "Nf3", "d4"))
    }

    @Test
    fun bestMovesArePraised() {
        val line = Coach.lineFor(MoveVerdict.BEST, 0, "Nf3", "Nf3")
        assertEquals(CoachLine.Accurate("Nf3 was the best move here."), line)
    }

    @Test
    fun aBestMoveWhoseBestSanDisagreesMakesTheSmallerClaim() {
        // Two searches, two answers. Naming one of them would be a guess, so
        // the sentence claims only what the verdict itself asserts.
        val line = Coach.lineFor(MoveVerdict.BEST, 0, "Nf3", "d4")
        assertEquals(CoachLine.Accurate("You found the best move in this position."), line)
    }

    @Test
    fun ungradedMovesAreNotCalledTheBestOne() {
        // A move the analyser never saw is graded BEST because it is ungraded.
        // With no bestSan there is nothing true to praise, so nothing is said.
        assertNull(Coach.lineFor(MoveVerdict.BEST, 0, null, "d4"))
    }

    @Test
    fun inaccuracyNamesTheBetterMove() {
        val line = Coach.lineFor(MoveVerdict.INACCURACY, 47, "Nf3", "Bc4")
        assertEquals(CoachLine.GaveAway(50, "That slipped. Nf3 was the move."), line)
    }

    @Test
    fun mistakesAndBlundersGetTheirOwnPhrasing() {
        assertEquals(
            CoachLine.GaveAway(250, "That was a mistake. Nf3 was the move."),
            Coach.lineFor(MoveVerdict.MISTAKE, 250, "Nf3", "Bc4"),
        )
        assertEquals(
            CoachLine.GaveAway(800, "That dropped something real. Nf3 was the move."),
            Coach.lineFor(MoveVerdict.BLUNDER, 800, "Nf3", "Bc4"),
        )
    }

    @Test
    fun lossIsRoundedToTheNearestFifty() {
        val rounded = listOf(20, 24, 25, 49, 74, 75, 124, 200, 350, 1_240).map { cp ->
            (Coach.lineFor(MoveVerdict.BLUNDER, cp, "Nf3", "Bc4") as CoachLine.GaveAway).lossCp
        }
        assertEquals(listOf(50, 50, 50, 50, 50, 100, 100, 200, 350, 1_250), rounded)
    }

    @Test
    fun aSmallLossNeverRoundsAwayToNothing() {
        // Twenty centipawns is the smallest real giveaway the review produces
        // (the GOOD/INACCURACY boundary). Rounded to fifty it must still read as
        // a loss, not as zero.
        val line = Coach.lineFor(MoveVerdict.INACCURACY, 20, "Nf3", "Bc4")
        assertEquals(CoachLine.GaveAway(50, "That slipped. Nf3 was the move."), line)
    }

    @Test
    fun aLostMateIsAMissedWinNotAGiveaway() {
        val line = Coach.lineFor(MoveVerdict.BLUNDER, 28_600, "Qxf7#", "Bc4")
        assertEquals(
            CoachLine.MissedWin(28_600, "Qxf7# ends the game, and Bc4 did not."),
            line,
        )
    }

    @Test
    fun aMateClaimIsIgnoredWhenTheLossIsSmall() {
        // A mate the engine reports always costs thousands of centipawns. A
        // "#" next to a small loss means the two answers disagree, and the
        // plain sentence is the honest one.
        val line = Coach.lineFor(MoveVerdict.MISTAKE, 180, "Qxf7#", "Bc4")
        assertEquals(
            CoachLine.GaveAway(200, "That was a mistake. Qxf7# was the move."),
            line,
        )
    }

    @Test
    fun aCheckIsNotAMissedWin() {
        // "+" is not "#": a move that gives check is an ordinary alternative.
        val line = Coach.lineFor(MoveVerdict.BLUNDER, 400, "Bb5+", "d4")
        assertEquals(
            CoachLine.GaveAway(400, "That dropped something real. Bb5+ was the move."),
            line,
        )
    }

    @Test
    fun aMissingBestMoveIsSaidSoRatherThanGuessed() {
        val line = Coach.lineFor(MoveVerdict.MISTAKE, 210, null, "Bc4")
        assertEquals(
            CoachLine.GaveAway(200, "That was a mistake. The engine did not say what it wanted here."),
            line,
        )
    }

    @Test
    fun noLineIsEnthusiasticOrJargon() {
        // One sentence, no exclamation, no jargon, and nothing claimed about the
        // board — the coach cannot see it, so it must not talk about pieces.
        val lines = listOf(
            Coach.lineFor(MoveVerdict.BEST, 0, "Nf3", "Nf3"),
            Coach.lineFor(MoveVerdict.INACCURACY, 60, "Nf3", "Bc4"),
            Coach.lineFor(MoveVerdict.MISTAKE, 250, "Nf3", "Bc4"),
            Coach.lineFor(MoveVerdict.BLUNDER, 900, "Nf3", "Bc4"),
            Coach.lineFor(MoveVerdict.BLUNDER, 28_600, "Qxf7#", "Bc4"),
        ).map { line ->
            when (line) {
                is CoachLine.GaveAway -> line.what
                is CoachLine.MissedWin -> line.what
                is CoachLine.Accurate -> line.what
                null -> error("every branch here must say something")
            }
        }
        for (what in lines) {
            assertTrue(what, what.none { it == '!' })
            assertTrue(what, what.length in 1..120)
            assertTrue(what, what.split(". ").size <= 2)
            assertTrue(what, what.none { it in "?!*" })
        }
        // The banned register, in one place.
        val all = lines.joinToString(" ")
        for (word in listOf("great", "brilliant", "wow", "centipawn", "eval", "blunder of")) {
            assertTrue(word, !all.contains(word, ignoreCase = true))
        }
    }
}
