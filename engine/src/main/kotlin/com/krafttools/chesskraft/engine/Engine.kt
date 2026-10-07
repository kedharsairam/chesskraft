/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 *
 * The seam the whole app talks to. The board, rules, history, undo, and
 * difficulty picker never know which engine is behind it.
 *
 * v1: OwnEngine (0x88, PVS + captures-only QS, hand-shaped PST).
 * v2 (reserved): StockfishEngine implements this same interface —
 * a 200-line bridge + DI swap, not a rewrite.
 */
package com.krafttools.chesskraft.engine

/** A move in UCI form, e.g. "e2e4", "e7e8q". */
@JvmInline
value class UciMove(val text: String)

/** Limits for one search. The engine must respect the deadline. */
data class SearchLimits(
    val maxDepth: Int,
    val maxMillis: Long,
)

/**
 * One search's answer, not just the move: what the engine wants to play and
 * what it thinks playing it is worth.
 *
 * [scoreCp] is from the side to move's point of view, in centipawns, and a
 * mate keeps the search's own large-magnitude encoding ([Searcher.MATE] is
 * 29000) rather than being flattened to "infinite". Anything with a magnitude
 * at or above 10000 is therefore a mate, never a pawn evaluation — no real
 * position gets anywhere near it.
 */
data class Analysis(
    val bestMove: UciMove,
    val scoreCp: Int,
)

/** Difficulty ladder. No Elo numbers in UI — names only. */
enum class Difficulty {
    RELAXED,
    CASUAL,
    SHARP,
    TOUGH,
}

interface Engine {
    /** Best move for [positionFen] within [limits]. Must be cancellable. */
    fun findBestMove(positionFen: String, limits: SearchLimits): UciMove

    /**
     * Best move and its score from one search — the same call [findBestMove]
     * makes, plus the number a review screen needs. A position with no legal
     * move (mate or stalemate) answers the null move ["0000"] with a zero
     * score rather than throwing.
     *
     * The default body exists so an engine that only knows how to pick a move
     * still satisfies the seam: it reports that move with a neutral score.
     * Engines that keep a score — OwnEngine does — override this instead of
     * searching twice.
     */
    fun analyze(positionFen: String, limits: SearchLimits): Analysis =
        Analysis(findBestMove(positionFen, limits), 0)
}
