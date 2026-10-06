/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft.domain

/**
 * Zobrist keys for repetition detection. Pure Kotlin.
 *
 * Tables are derived from a fixed seed with xorshift, so keys are stable
 * across runs and devices — no Random, no global mutable state.
 */
object Zobrist {
    private const val SEED = 0x9E3779B97F4A7C15uL

    private fun xorshift(state: ULong): ULong {
        var x = state
        x = x xor (x shl 13)
        x = x xor (x shr 7)
        x = x xor (x shl 17)
        return x
    }

    private fun table(size: Int, salt: ULong): LongArray {
        var s = SEED xor (salt * 0xBF58476D1CE4E5B9uL)
        return LongArray(size) {
            s = xorshift(s)
            s.toLong()
        }
    }

    private val pieces: LongArray = table(12 * 64, 1u)
    private val side: Long = table(1, 2u)[0]
    private val castlingBits: LongArray = table(4, 3u)
    private val epFiles: LongArray = table(8, 4u)

    fun key(position: Position): Long {
        var key = 0L
        for (sq in 0..63) {
            val code = position.board[sq]
            if (code == 0) continue
            val sideIndex = if (code > 0) 0 else 6
            val typeIndex = kotlin.math.abs(code) - 1
            key = key xor pieces[(sideIndex + typeIndex) * 64 + sq]
        }
        if (position.sideToMove == Side.BLACK) key = key xor side
        for (bit in 0..3) {
            if (position.castling and (1 shl bit) != 0) key = key xor castlingBits[bit]
        }
        if (position.epSquare >= 0) key = key xor epFiles[fileOf(position.epSquare)]
        return key
    }
}
