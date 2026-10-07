/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft.domain

/**
 * A pure countdown clock: two banks, the side to move burns. No coroutines,
 * no time source inside — the ViewModel feeds elapsed millis and this
 * decides. Flag the instant a bank empties; a bank at exactly zero after a
 * tick is a flag, not a reprieve.
 */
class ChessClock(firstMs: Long, secondMs: Long) {
    var whiteMs: Long = firstMs
        private set
    var blackMs: Long = secondMs
        private set

    /** Burns [elapsedMs] from [side]'s bank. Returns the flagged side, if any. */
    fun tick(side: Side, elapsedMs: Long): Side? {
        if (side == Side.WHITE) {
            whiteMs = (whiteMs - elapsedMs).coerceAtLeast(0L)
            if (whiteMs == 0L) return Side.WHITE
        } else {
            blackMs = (blackMs - elapsedMs).coerceAtLeast(0L)
            if (blackMs == 0L) return Side.BLACK
        }
        return null
    }

    fun forSide(side: Side): Long = if (side == Side.WHITE) whiteMs else blackMs
}
