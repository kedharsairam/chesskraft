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

    /**
     * Puts both banks back to the values a save carried.
     *
     * A resumed game must not hand back the time its opponent has already spent
     * — that would be a free move — so this is a plain assignment rather than
     * an offset from the constructor's starting banks. Negative values are
     * clamped to zero: a save written by a future version, or edited by hand,
     * must not produce a clock that counts upward.
     */
    fun restore(white: Long, black: Long) {
        whiteMs = white.coerceAtLeast(0L)
        blackMs = black.coerceAtLeast(0L)
    }

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
