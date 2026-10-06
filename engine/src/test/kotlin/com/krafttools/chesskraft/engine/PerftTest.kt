/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 *
 * The v1 gate: perft depths 1-4 over the six standard positions must be
 * green before any search work counts. Counts are cross-checked facts
 * (Chess Programming Wiki, Perft Results); the move generator producing
 * them is ours.
 */
package com.krafttools.chesskraft.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class PerftTest {
    private fun perft(fen: String, depth: Int): Long {
        val pos = Position.fromFen(fen)
        return MoveGen.perft(pos, depth)
    }

    @Test
    fun startpos() {
        val fen = Position.STARTPOS
        assertEquals(20L, perft(fen, 1))
        assertEquals(400L, perft(fen, 2))
        assertEquals(8902L, perft(fen, 3))
        assertEquals(197281L, perft(fen, 4))
    }

    @Test
    fun kiwipete() {
        val fen = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1"
        assertEquals(48L, perft(fen, 1))
        assertEquals(2039L, perft(fen, 2))
        assertEquals(97862L, perft(fen, 3))
        assertEquals(4085603L, perft(fen, 4))
    }

    @Test
    fun position3() {
        val fen = "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1"
        assertEquals(14L, perft(fen, 1))
        assertEquals(191L, perft(fen, 2))
        assertEquals(2812L, perft(fen, 3))
        assertEquals(43238L, perft(fen, 4))
    }

    @Test
    fun position4() {
        val fen = "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1"
        assertEquals(6L, perft(fen, 1))
        assertEquals(264L, perft(fen, 2))
        assertEquals(9467L, perft(fen, 3))
        assertEquals(422333L, perft(fen, 4))
    }

    @Test
    fun position5() {
        val fen = "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8"
        assertEquals(44L, perft(fen, 1))
        assertEquals(1486L, perft(fen, 2))
        assertEquals(62379L, perft(fen, 3))
        assertEquals(2103487L, perft(fen, 4))
    }

    @Test
    fun rookMirror() {
        val fen = "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10"
        assertEquals(46L, perft(fen, 1))
        assertEquals(2079L, perft(fen, 2))
        assertEquals(89890L, perft(fen, 3))
        assertEquals(3894594L, perft(fen, 4))
    }
}
