/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 *
 * Evaluation properties: symmetry at the start, material awareness,
 * side-to-move relativity, bishop pair, and king taper direction.
 */
package com.krafttools.chesskraft.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvalTest {
    @Test
    fun startposIsLevel() {
        val score = Eval.evaluate(Position.fromFen(Position.STARTPOS))
        assertTrue("startpos $score out of band", score in -30..30)
    }

    @Test
    fun extraQueenScoresNearNinePawns() {
        val base = Eval.evaluate(Position.fromFen("4k3/8/8/8/8/8/8/4K2Q w - - 0 1"))
        assertTrue("queen up scored $base", base > 700)
    }

    @Test
    fun sideToMoveRelative() {
        val white = Eval.evaluate(Position.fromFen("4k3/8/8/8/8/3P4/8/4K3 w - - 0 1"))
        val black = Eval.evaluate(Position.fromFen("4k3/8/8/8/8/3P4/8/4K3 b - - 0 1"))
        assertEquals(white, -black)
        assertTrue(white > 50)
    }

    @Test
    fun bishopPairBonus() {
        val pair = Eval.evaluate(Position.fromFen("4k3/8/8/8/8/8/8/2B1KB2 w - - 0 1"))
        val single = Eval.evaluate(Position.fromFen("4k3/8/8/8/8/8/8/4KB2 w - - 0 1"))
        assertTrue("pair=$pair single=$single", pair > single)
    }

    @Test
    fun kingCentralisesInEndgame() {
        val center = Eval.evaluate(Position.fromFen("8/8/8/3K4/8/8/8/7k w - - 0 1"))
        val corner = Eval.evaluate(Position.fromFen("K7/8/8/8/8/8/8/7k w - - 0 1"))
        assertTrue("center=$center corner=$corner", center > corner)
    }

    @Test
    fun mirroredPositionScoresMirror() {
        val a = Eval.evaluate(Position.fromFen("4k3/8/8/3N4/8/8/8/4K3 w - - 0 1"))
        val b = Eval.evaluate(Position.fromFen("4k3/8/8/8/4N3/8/8/4K3 w - - 0 1"))
        assertEquals(a, b)
    }
}
