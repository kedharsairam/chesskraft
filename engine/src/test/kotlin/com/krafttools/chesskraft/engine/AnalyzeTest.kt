/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 *
 * The analysis half of the seam: one search that answers with both the move and
 * what it is worth, and a null move where there is nothing to play. Also pins
 * the interface's default body, so an engine that only knows how to pick a move
 * still reports an Analysis.
 */
package com.krafttools.chesskraft.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyzeTest {

    private val limits = SearchLimits(maxDepth = 3, maxMillis = 4000L)

    @Test
    fun startposAnalysisIsALegalMove() {
        val pos = Position.fromFen(Position.STARTPOS)
        val analysis = OwnEngine().analyze(Position.STARTPOS, limits)
        assertNotEquals(OwnEngine.NULL_UCI, analysis.bestMove.text)
        val move = MoveGen.parseUci(pos, analysis.bestMove.text)
        assertNotEquals("illegal analysis move ${analysis.bestMove.text}", NO_MOVE, move)
        assertTrue("absurd score ${analysis.scoreCp}", analysis.scoreCp < Searcher.MATE)
    }

    @Test
    fun analysisAgreesWithFindBestMove() {
        // Same engine, same limits, no difficulty shortlist: one search, one answer.
        val engine = OwnEngine()
        val best = engine.findBestMove(Position.STARTPOS, SearchLimits(maxDepth = 4, maxMillis = 5000L))
        val analysis = engine.analyze(Position.STARTPOS, SearchLimits(maxDepth = 4, maxMillis = 5000L))
        assertEquals(best.text, analysis.bestMove.text)
    }

    @Test
    fun mateIsReportedWithTheSearchMagnitude() {
        // Scholar's mate on the board: Qxf7#, and a mate score, not a pawn count.
        val analysis = OwnEngine().analyze(
            "r1bqkb1r/pppp1ppp/2n2n2/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 0 1",
            limits,
        )
        assertEquals("h5f7", analysis.bestMove.text)
        assertTrue(
            "score ${analysis.scoreCp} is not a mate score",
            Searcher.isMateScore(analysis.scoreCp) && analysis.scoreCp > 0,
        )
        // Beyond the review layer's mate threshold, which is what grades it.
        assertTrue(analysis.scoreCp >= 10_000)
    }

    @Test
    fun checkmatedPositionAnswersTheNullMove() {
        val analysis = OwnEngine().analyze(
            "rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3",
            limits,
        )
        assertEquals(OwnEngine.NULL_UCI, analysis.bestMove.text)
        assertEquals(0, analysis.scoreCp)
    }

    @Test
    fun stalematedPositionAnswersTheNullMove() {
        val analysis = OwnEngine().analyze("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1", limits)
        assertEquals(OwnEngine.NULL_UCI, analysis.bestMove.text)
        assertEquals(0, analysis.scoreCp)
    }

    @Test
    fun interfaceDefaultAnalysisIsTheMoveWithANeutralScore() {
        // An engine that only knows how to choose still satisfies the seam.
        val chooser = object : Engine {
            override fun findBestMove(positionFen: String, limits: SearchLimits): UciMove =
                UciMove("e2e4")
        }
        assertEquals(
            Analysis(UciMove("e2e4"), 0),
            chooser.analyze(Position.STARTPOS, limits),
        )
    }
}