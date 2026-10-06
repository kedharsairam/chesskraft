/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 *
 * Piece codes, colours, and the packed-Int move layout shared by the
 * board, move generator, search, and tests.
 *
 * Board squares are 0x88: square = rank * 16 + file with rank 0 == rank 1.
 * A square is on the board iff (sq and 0x88) == 0.
 *
 * Piece codes pack colour in bit 3: code = type or (colour shl 3).
 * A packed move is one Int:
 *   bits 0..6   from square (0x88)
 *   bits 7..13  to square (0x88)
 *   bits 14..17 moving piece code
 *   bits 18..21 captured piece code (0 when none)
 *   bits 22..25 promotion piece code (0 when none)
 *   bit 26      double pawn push
 *   bit 27      en-passant capture
 *   bit 28      castling
 * NO_MOVE is 0; a real move always carries a non-zero moving piece.
 */
package com.krafttools.chesskraft.engine

const val EMPTY = 0

const val PAWN = 1
const val KNIGHT = 2
const val BISHOP = 3
const val ROOK = 4
const val QUEEN = 5
const val KING = 6

const val WHITE = 0
const val BLACK = 1

const val WP = 1
const val WN = 2
const val WB = 3
const val WR = 4
const val WQ = 5
const val WK = 6
const val BP = 9
const val BN = 10
const val BB = 11
const val BR = 12
const val BQ = 13
const val BK = 14

const val NO_PIECE = 0
const val NO_MOVE = 0
const val NO_SQUARE = -1

const val CASTLE_WK = 1
const val CASTLE_WQ = 2
const val CASTLE_BK = 4
const val CASTLE_BQ = 8

fun pieceType(piece: Int): Int = piece and 7

fun pieceColor(piece: Int): Int = (piece ushr 3) and 1

fun makePiece(type: Int, color: Int): Int = type or (color shl 3)

fun opposite(color: Int): Int = color xor 1

fun makeMove(from: Int, to: Int, piece: Int, captured: Int = NO_PIECE, promo: Int = NO_PIECE): Int {
    var m = (from and 0x7F) or ((to and 0x7F) shl 7) or ((piece and 0xF) shl 14)
    if (captured != NO_PIECE) m = m or ((captured and 0xF) shl 18)
    if (promo != NO_PIECE) m = m or ((promo and 0xF) shl 22)
    return m
}

fun withDoublePush(m: Int): Int = m or (1 shl 26)

fun withEpCapture(m: Int): Int = m or (1 shl 27)

fun withCastle(m: Int): Int = m or (1 shl 28)

fun moveFrom(m: Int): Int = m and 0x7F

fun moveTo(m: Int): Int = (m ushr 7) and 0x7F

fun movePiece(m: Int): Int = (m ushr 14) and 0xF

fun moveCaptured(m: Int): Int = (m ushr 18) and 0xF

fun movePromo(m: Int): Int = (m ushr 22) and 0xF

fun isDoublePush(m: Int): Boolean = (m and (1 shl 26)) != 0

fun isEpCapture(m: Int): Boolean = (m and (1 shl 27)) != 0

fun isCastle(m: Int): Boolean = (m and (1 shl 28)) != 0

fun isPromotion(m: Int): Boolean = movePromo(m) != NO_PIECE

fun isCapture(m: Int): Boolean = moveCaptured(m) != NO_PIECE || isEpCapture(m)

/** File 0..7 and rank 0..7 of an 0x88 square. */
fun fileOf(sq: Int): Int = sq and 7

fun rankOf(sq: Int): Int = sq ushr 4

fun makeSquare(file: Int, rank: Int): Int = rank * 16 + file

fun onBoard(sq: Int): Boolean = (sq and 0x88) == 0

fun squareName(sq: Int): String {
    val s = StringBuilder(2)
    s.append(('a'.code + fileOf(sq)).toChar())
    s.append(('1'.code + rankOf(sq)).toChar())
    return s.toString()
}

fun squareFromName(name: String, at: Int): Int {
    val file = name[at].code - 'a'.code
    val rank = name[at + 1].code - '1'.code
    return makeSquare(file, rank)
}
