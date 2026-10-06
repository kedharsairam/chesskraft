/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 *
 * The seam proof: app-domain FEN out, OwnEngine reply back in, parses to a
 * legal app-domain move. If either side changes its FEN/UCI dialect, this
 * fails instead of the app silently falling back to first-legal-move.
 */
package com.krafttools.chesskraft.domain

import com.krafttools.chesskraft.engine.Difficulty
import com.krafttools.chesskraft.engine.OwnEngine
import com.krafttools.chesskraft.presentation.coerceEngineMove
import kotlin.random.Random
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineBoundaryTest {

    private val engine = OwnEngine(Random(7))

    private fun assertEngineReplies(fen: String, difficulty: Difficulty) {
        val appPos = Position.fromFen(fen).getOrNull() ?: error("bad test FEN: $fen")
        val legal = appPos.generateLegalMoves()
        assertTrue("test position has no legal moves: $fen", legal.isNotEmpty())
        val reply = engine.findBestMove(fen, difficulty)
        val move = coerceEngineMove(appPos, reply)
        assertNotNull("engine reply '${reply.text}' did not parse for $fen", move)
        assertTrue(
            "engine reply '${reply.text}' is illegal for $fen",
            legal.any { it == move },
        )
    }

    @Test
    fun startposAllDifficulties() {
        val startpos = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
        for (difficulty in Difficulty.entries) assertEngineReplies(startpos, difficulty)
    }

    @Test
    fun midgameBlackToMove() {
        val fen = "r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 2 3"
        for (difficulty in Difficulty.entries) assertEngineReplies(fen, difficulty)
    }

    @Test
    fun promotionPresent() {
        val fen = "8/P7/8/8/8/1k6/8/4K3 w - - 0 1"
        for (difficulty in Difficulty.entries) assertEngineReplies(fen, difficulty)
    }
}
