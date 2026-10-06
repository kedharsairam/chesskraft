/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft.presentation

import com.krafttools.chesskraft.domain.Position
import com.krafttools.chesskraft.domain.parseUci
import com.krafttools.chesskraft.engine.Difficulty
import com.krafttools.chesskraft.engine.Engine
import com.krafttools.chesskraft.engine.SearchLimits
import com.krafttools.chesskraft.engine.UciMove
import kotlin.random.Random

/**
 * Stand-in behind the [Engine] seam until the real search lands (parallel
 * track). Plays the first capture it sees, otherwise a random legal move —
 * deterministic per game so behaviour is reproducible in tests and demos.
 *
 * The board and ViewModel never know this is a stub: they only see [Engine].
 */
class FakeEngine(seed: Long = 0L) : Engine {
    private val random = Random(seed)

    override fun findBestMove(positionFen: String, limits: SearchLimits): UciMove {
        val position = Position.fromFen(positionFen).getOrNull() ?: return UciMove("0000")
        val legal = position.generateLegalMoves()
        if (legal.isEmpty()) return UciMove("0000")
        // Mate in one when we see it; otherwise first capture; otherwise random.
        for (move in legal) {
            val next = position.makeMove(move)
            if (next.generateLegalMoves().isEmpty() && next.isInCheck()) {
                return UciMove(move.toUci())
            }
        }
        val captures = legal.filter { position.board[it.to] != 0 }
        val pool = captures.ifEmpty { legal }
        return UciMove(pool[random.nextInt(pool.size)].toUci())
    }
}

/** Time caps from the frozen spec: 200 / 600 / 1500 / 1500 ms. */
fun limitsFor(difficulty: Difficulty): SearchLimits = when (difficulty) {
    Difficulty.RELAXED -> SearchLimits(maxDepth = 1, maxMillis = 200L)
    Difficulty.CASUAL -> SearchLimits(maxDepth = 2, maxMillis = 600L)
    Difficulty.SHARP -> SearchLimits(maxDepth = 3, maxMillis = 1500L)
    Difficulty.TOUGH -> SearchLimits(maxDepth = 3, maxMillis = 1500L)
}

/** Best-effort parse of an engine reply into a legal move, else null. */
fun coerceEngineMove(position: Position, reply: UciMove): com.krafttools.chesskraft.domain.ChessMove? {
    val move = parseUci(reply.text) ?: return null
    return position.generateLegalMoves().firstOrNull { it == move }
}
