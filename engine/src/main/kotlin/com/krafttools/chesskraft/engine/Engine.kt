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
}
