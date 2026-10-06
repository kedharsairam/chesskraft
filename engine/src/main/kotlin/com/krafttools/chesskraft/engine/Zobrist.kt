/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 *
 * Zobrist keys. Generated once from a fixed-seed xorshift stream so every
 * run of the engine (and every test) sees identical keys. Not random at
 * runtime: determinism here keeps repetition detection reproducible.
 */
package com.krafttools.chesskraft.engine

internal object Zobrist {
    val pieceKeys: Array<LongArray> = Array(15) { LongArray(128) }
    val sideKey: Long
    val castleKeys = LongArray(16)
    val epKeys = LongArray(8)

    init {
        var s = -0x4A1D0B2C3D4E5F61L
        fun next(): Long {
            s = s xor (s shl 13)
            s = s xor (s ushr 7)
            s = s xor (s shl 17)
            return s
        }
        for (p in 0 until 15) {
            for (sq in 0 until 128) {
                pieceKeys[p][sq] = if (onBoard(sq)) next() else 0L
            }
        }
        sideKey = next()
        for (i in 0 until 16) castleKeys[i] = next()
        for (i in 0 until 8) epKeys[i] = next()
    }
}
