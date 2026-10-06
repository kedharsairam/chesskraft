/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 *
 * Static evaluation in centipawns, returned relative to the side to move.
 * Material follows the familiar 100/320/330/500/900/20000 ladder; each
 * piece has one square table in the well-known style (pawns rewarded for
 * advancing, minors and majors for central presence, midgame king for
 * staying cornered), with the king blending towards a centralising
 * endgame table as material comes off. Bishop pair scores a small bonus.
 * Integer math throughout; no floats anywhere near the search.
 */
package com.krafttools.chesskraft.engine

object Eval {
    const val PAWN_VALUE = 100
    const val KNIGHT_VALUE = 320
    const val BISHOP_VALUE = 330
    const val ROOK_VALUE = 500
    const val QUEEN_VALUE = 900
    const val KING_VALUE = 20000
    const val BISHOP_PAIR = 10

    // Tables are written rank 8 first, a8..h8 down to a1..h1.
    private val PAWN_PST = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0,
        45, 45, 45, 45, 45, 45, 45, 45,
        12, 12, 18, 28, 28, 18, 12, 12,
        4, 4, 12, 24, 24, 12, 4, 4,
        0, 0, 0, 18, 18, 0, 0, 0,
        4, -6, -12, 0, 0, -12, -6, 4,
        4, 12, 12, -18, -18, 12, 12, 4,
        0, 0, 0, 0, 0, 0, 0, 0,
    )

    private val KNIGHT_PST = intArrayOf(
        -48, -38, -28, -28, -28, -28, -38, -48,
        -38, -18, 2, 2, 2, 2, -18, -38,
        -28, 2, 12, 16, 16, 12, 2, -28,
        -28, 6, 16, 22, 22, 16, 6, -28,
        -28, 2, 14, 22, 22, 14, 2, -28,
        -28, 6, 12, 14, 14, 12, 6, -28,
        -38, -18, 2, 6, 6, 2, -18, -38,
        -48, -38, -28, -28, -28, -28, -38, -48,
    )

    private val BISHOP_PST = intArrayOf(
        -18, -8, -8, -8, -8, -8, -8, -18,
        -8, 2, 0, 0, 0, 0, 2, -8,
        -8, 2, 6, 10, 10, 6, 2, -8,
        -8, 6, 6, 10, 10, 6, 6, -8,
        -8, 2, 10, 10, 10, 10, 2, -8,
        -8, 10, 10, 10, 10, 10, 10, -8,
        -8, 10, 2, 2, 2, 2, 0, -8,
        -18, -8, -8, -8, -8, -8, -8, -18,
    )

    private val ROOK_PST = intArrayOf(
        2, 2, 2, 2, 2, 2, 2, 2,
        6, 12, 12, 12, 12, 12, 12, 6,
        -4, 0, 0, 0, 0, 0, 0, -4,
        -4, 0, 0, 0, 0, 0, 0, -4,
        -4, 0, 0, 0, 0, 0, 0, -4,
        -4, 0, 0, 0, 0, 0, 0, -4,
        -4, 0, 0, 0, 0, 0, 0, -4,
        0, 0, 2, 6, 6, 2, 0, 0,
    )

    private val QUEEN_PST = intArrayOf(
        -18, -8, -8, -4, -4, -8, -8, -18,
        -8, 0, 0, 0, 0, 0, 0, -8,
        -8, 0, 4, 4, 4, 4, 0, -8,
        -4, 0, 4, 6, 6, 4, 0, -4,
        0, 0, 4, 6, 6, 4, 0, -4,
        -8, 4, 4, 4, 4, 4, 0, -8,
        -8, 0, 4, 0, 0, 0, 0, -8,
        -18, -8, -8, -4, -4, -8, -8, -18,
    )

    private val KING_MID_PST = intArrayOf(
        -28, -38, -38, -48, -48, -38, -38, -28,
        -28, -38, -38, -48, -48, -38, -38, -28,
        -28, -38, -38, -48, -48, -38, -38, -28,
        -28, -38, -38, -48, -48, -38, -38, -28,
        -18, -28, -28, -38, -38, -28, -28, -18,
        -8, -18, -18, -18, -18, -18, -18, -8,
        22, 22, 2, 2, 2, 2, 22, 22,
        22, 32, 12, 2, 2, 12, 32, 22,
    )

    private val KING_END_PST = intArrayOf(
        -48, -38, -28, -18, -18, -28, -38, -48,
        -28, -18, -8, 2, 2, -8, -18, -28,
        -28, -8, 22, 32, 32, 22, -8, -28,
        -28, -8, 32, 42, 42, 32, -8, -28,
        -28, -8, 32, 42, 42, 32, -8, -28,
        -28, -8, 22, 32, 32, 22, -8, -28,
        -28, -28, 2, 2, 2, 2, -28, -28,
        -48, -28, -28, -28, -28, -28, -28, -48,
    )

    private const val FULL_PHASE = 24

    fun pieceValue(type: Int): Int = when (type) {
        PAWN -> PAWN_VALUE
        KNIGHT -> KNIGHT_VALUE
        BISHOP -> BISHOP_VALUE
        ROOK -> ROOK_VALUE
        QUEEN -> QUEEN_VALUE
        else -> KING_VALUE
    }

    private fun pstIndex(sq: Int, white: Boolean): Int {
        val file = fileOf(sq)
        val rank = rankOf(sq)
        return if (white) (7 - rank) * 8 + file else rank * 8 + file
    }

    fun evaluate(pos: Position): Int {
        var whiteN = 0
        var whiteB = 0
        var whiteR = 0
        var whiteQ = 0
        var blackN = 0
        var blackB = 0
        var blackR = 0
        var blackQ = 0
        for (sq in 0 until 128) {
            if (!onBoard(sq)) continue
            val p = pos.board[sq]
            if (p == EMPTY) continue
            val white = pieceColor(p) == WHITE
            when (pieceType(p)) {
                KNIGHT -> if (white) whiteN++ else blackN++
                BISHOP -> if (white) whiteB++ else blackB++
                ROOK -> if (white) whiteR++ else blackR++
                QUEEN -> if (white) whiteQ++ else blackQ++
            }
        }
        val phase = phaseOf(whiteN, whiteB, whiteR, whiteQ, blackN, blackB, blackR, blackQ)
        var score = 0
        for (sq in 0 until 128) {
            if (!onBoard(sq)) continue
            val p = pos.board[sq]
            if (p == EMPTY) continue
            val white = pieceColor(p) == WHITE
            val idx = pstIndex(sq, white)
            val sign = if (white) 1 else -1
            when (pieceType(p)) {
                PAWN -> score += sign * (PAWN_VALUE + PAWN_PST[idx])
                KNIGHT -> score += sign * (KNIGHT_VALUE + KNIGHT_PST[idx])
                BISHOP -> score += sign * (BISHOP_VALUE + BISHOP_PST[idx])
                ROOK -> score += sign * (ROOK_VALUE + ROOK_PST[idx])
                QUEEN -> score += sign * (QUEEN_VALUE + QUEEN_PST[idx])
                else -> {
                    val mid = KING_MID_PST[idx]
                    val end = KING_END_PST[idx]
                    score += sign * (mid * phase + end * (FULL_PHASE - phase)) / FULL_PHASE
                }
            }
        }
        if (whiteB >= 2) score += BISHOP_PAIR
        if (blackB >= 2) score -= BISHOP_PAIR
        return if (pos.side == WHITE) score else -score
    }

    private fun phaseOf(wN: Int, wB: Int, wR: Int, wQ: Int, bN: Int, bB: Int, bR: Int, bQ: Int): Int {
        val phase = (wN + bN) + (wB + bB) + 2 * (wR + bR) + 4 * (wQ + bQ)
        return if (phase > FULL_PHASE) FULL_PHASE else phase
    }
}
