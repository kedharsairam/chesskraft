/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 *
 * Difficulty contract from the frozen spec: search-limit mapping per
 * level, deterministic seeded randomness, best play when in check, and
 * the golden rule — a move that lets the opponent mate on the spot is
 * never picked from the blunder pool.
 */
package com.krafttools.chesskraft.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DifficultyTest {
    @Test
    fun limitsMapping() {
        assertEquals(SearchLimits(1, 200L), OwnEngine.searchLimitsFor(Difficulty.RELAXED))
        assertEquals(SearchLimits(2, 600L), OwnEngine.searchLimitsFor(Difficulty.CASUAL))
        assertEquals(SearchLimits(3, 1500L), OwnEngine.searchLimitsFor(Difficulty.SHARP))
        assertEquals(SearchLimits(16, 1500L), OwnEngine.searchLimitsFor(Difficulty.TOUGH))
    }

    @Test
    fun seededEngineIsDeterministic() {
        val a = OwnEngine(Random(7)).findBestMove(Position.STARTPOS, Difficulty.RELAXED)
        val b = OwnEngine(Random(7)).findBestMove(Position.STARTPOS, Difficulty.RELAXED)
        assertEquals(a.text, b.text)
    }

    @Test
    fun interfaceMoveIsLegal() {
        for (fen in arrayOf(
            Position.STARTPOS,
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
            "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10",
        )) {
            val uci = OwnEngine().findBestMove(fen, SearchLimits(2, 500L)).text
            val pos = Position.fromFen(fen)
            assertTrue("illegal $uci for $fen", MoveGen.parseUci(pos, uci) != NO_MOVE)
        }
    }

    @Test
    fun relaxedInCheckPlaysBest() {
        // White is in check from Re1; Qxe1 wins the rook and every seed
        // must pick it — no random deviation while in check.
        val fen = "4k3/8/8/8/8/8/8/2Q1r2K w - - 0 1"
        for (seed in 0 until 20) {
            val uci = OwnEngine(Random(seed)).findBestMove(fen, Difficulty.RELAXED).text
            assertEquals("c1e1", uci)
        }
    }

    @Test
    fun scholarsMateAlwaysTaken() {
        val fen = "r1bqkb1r/pppp1ppp/2n2n2/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 0 1"
        for (difficulty in arrayOf(Difficulty.RELAXED, Difficulty.CASUAL, Difficulty.SHARP, Difficulty.TOUGH)) {
            for (seed in 0 until 10) {
                val uci = OwnEngine(Random(seed)).findBestMove(fen, difficulty).text
                assertEquals("$difficulty seed $seed", "h5f7", uci)
            }
        }
    }

    /**
     * Golden rule: black's quiet Qg1# hangs over this position and the
     * depth-1 quiescence cannot see quiet moves, so tempting sidesteps
     * sit inside the blunder window. No seed, on any level, may walk in.
     */
    @Test
    fun neverAllowsMateInOne() {
        val fen = "k7/pp6/1b6/8/8/4q3/PP5P/5Q1K w - - 0 1"
        val levels = arrayOf(
            Difficulty.RELAXED to 40,
            Difficulty.CASUAL to 40,
            Difficulty.SHARP to 40,
            Difficulty.TOUGH to 5,
        )
        for ((difficulty, seeds) in levels) {
            for (seed in 0 until seeds) {
                val uci = OwnEngine(Random(seed)).findBestMove(fen, difficulty).text
                val pos = Position.fromFen(fen)
                val m = MoveGen.parseUci(pos, uci)
                assertTrue("illegal $uci", m != NO_MOVE)
                pos.makeMove(m)
                assertFalse("$difficulty seed $seed played $uci allowing mate", opponentHasMate(pos))
                pos.unmakeMove()
            }
        }
    }

    private fun opponentHasMate(pos: Position): Boolean {
        val buf = IntArray(MoveGen.BUFFER_SIZE)
        val n = MoveGen.generateAll(pos, buf, 0)
        for (i in 0 until n) {
            if (pos.makeMove(buf[i])) {
                if (MoveGen.isCheckmate(pos)) {
                    pos.unmakeMove()
                    return true
                }
            }
            pos.unmakeMove()
        }
        return false
    }
}
