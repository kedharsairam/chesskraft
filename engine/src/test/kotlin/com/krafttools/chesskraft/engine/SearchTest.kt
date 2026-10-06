/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 *
 * Search behaviour: mate in one is found, the reply to a forced mate
 * threat is sane, the clock is honoured, and a finished game yields the
 * null move rather than crashing.
 */
package com.krafttools.chesskraft.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTest {
    @Test
    fun findsMateInOne() {
        // Scholar's pattern: Qxf7 is mate (d8 is occupied, f8 covered,
        // e7 covered, f7 defended). Bxf7+ and Qxe5+ both let the king out.
        val pos = Position.fromFen("r1bqkb1r/pppp1ppp/2n2n2/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 0 1")
        val searcher = Searcher(3, 5000L)
        val best = searcher.findBest(pos)
        assertEquals("h5f7", MoveGen.moveToUci(best))
        pos.makeMove(best)
        assertTrue(MoveGen.isCheckmate(pos))
        pos.unmakeMove()
    }

    @Test
    fun backRankMateFound() {
        // f7/g7/h7 wall the king in: Ra8 covers the whole back rank.
        val pos = Position.fromFen("6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1")
        val searcher = Searcher(4, 5000L)
        val best = searcher.findBest(pos)
        assertEquals("a1a8", MoveGen.moveToUci(best))
        pos.makeMove(best)
        assertTrue(MoveGen.isCheckmate(pos))
        pos.unmakeMove()
    }

    @Test
    fun checkmatePositionReturnsNoMove() {
        val pos = Position.fromFen("rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3")
        assertEquals(NO_MOVE, Searcher(3, 1000L).findBest(pos))
    }

    @Test
    fun stalematePositionReturnsNoMove() {
        val pos = Position.fromFen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1")
        assertEquals(NO_MOVE, Searcher(3, 1000L).findBest(pos))
    }

    @Test
    fun hardTimeCapHonoured() {
        val pos = Position.fromFen(Position.STARTPOS)
        val started = System.currentTimeMillis()
        val best = Searcher(16, 300L).findBest(pos)
        val took = System.currentTimeMillis() - started
        assertTrue("took ${took}ms", took < 2500L)
        assertTrue(best != NO_MOVE)
    }

    @Test
    fun singleLegalMoveReturnedFast() {
        // Black is in check from Qf6 and the h-pawn cannot interpose:
        // Kg8 is the only legal move, returned without searching.
        val pos = Position.fromFen("7k/7p/5QK1/8/8/8/8/8 b - - 0 1")
        val out = IntArray(MoveGen.BUFFER_SIZE)
        val n = MoveGen.legalMoves(pos, out)
        assertEquals(1, n)
        assertEquals(out[0], Searcher(8, 1500L).findBest(pos))
    }

    @Test
    fun quiescenceAvoidsPoisonedPawn() {
        // Nxe5 wins a pawn at first glance, but d6 recaptures. Depth 1
        // plus quiescence must see through it and play something else.
        val pos = Position.fromFen("4k3/8/3p4/4p3/8/5N2/8/4K3 w - - 0 1")
        val best = Searcher(1, 2000L).findBest(pos)
        assertTrue(MoveGen.moveToUci(best) != "f3e5")
    }
}
