/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft.domain

/**
 * Core chess vocabulary. Pure Kotlin — no Android, no Compose.
 *
 * Square: Int 0..63, file = sq % 8, rank = sq / 8. Rank 0 is White's home rank,
 * so square 0 is a1 and square 63 is h8.
 */
enum class Side {
    WHITE,
    BLACK,
    ;

    fun opponent(): Side = if (this == WHITE) BLACK else WHITE

    fun pawnDir(): Int = if (this == WHITE) 8 else -8

    fun homeRank(): Int = if (this == WHITE) 0 else 7

    fun promoRank(): Int = if (this == WHITE) 7 else 0
}

enum class PieceType {
    PAWN,
    KNIGHT,
    BISHOP,
    ROOK,
    QUEEN,
    KING,
}

/** Piece codes on [Position.board]: sign is the side, magnitude is the type. */
object PieceCode {
    const val EMPTY = 0

    fun of(side: Side, type: PieceType): Int {
        val magnitude = type.ordinal + 1
        return if (side == Side.WHITE) magnitude else -magnitude
    }

    fun sideOf(code: Int): Side? = when {
        code > 0 -> Side.WHITE
        code < 0 -> Side.BLACK
        else -> null
    }

    fun typeOf(code: Int): PieceType? {
        val magnitude = kotlin.math.abs(code)
        return if (magnitude in 1..6) PieceType.entries[magnitude - 1] else null
    }
}

data class Piece(val side: Side, val type: PieceType)

/**
 * A move. Promotion carries the piece promoted to (null = no promotion).
 * Castling, double pushes and en passant are recognised from the position
 * at apply time, not stored as flags.
 */
data class ChessMove(val from: Int, val to: Int, val promotion: PieceType? = null) {
    fun toUci(): String = squareName(from) + squareName(to) +
        (promotion?.let { promoLetter(it).lowercase() } ?: "")
}

fun promoLetter(type: PieceType): String = when (type) {
    PieceType.KNIGHT -> "N"
    PieceType.BISHOP -> "B"
    PieceType.ROOK -> "R"
    PieceType.QUEEN -> "Q"
    else -> ""
}

fun fileOf(sq: Int): Int = sq % 8

fun rankOf(sq: Int): Int = sq / 8

fun squareAt(file: Int, rank: Int): Int = rank * 8 + file

fun isOnBoard(file: Int, rank: Int): Boolean = file in 0..7 && rank in 0..7

fun squareName(sq: Int): String {
    require(sq in 0..63)
    return "${'a' + fileOf(sq)}${'1' + rankOf(sq)}"
}

/** Parses "e4" -> square, or null. */
fun parseSquare(name: String): Int? {
    if (name.length != 2) return null
    val file = name[0] - 'a'
    val rank = name[1] - '1'
    return if (isOnBoard(file, rank)) squareAt(file, rank) else null
}

/** Parses "e2e4" / "e7e8q" -> move, or null. */
fun parseUci(text: String): ChessMove? {
    if (text.length < 4 || text.length > 5) return null
    val from = parseSquare(text.substring(0, 2)) ?: return null
    val to = parseSquare(text.substring(2, 4)) ?: return null
    val promo = if (text.length == 5) {
        when (text[4].lowercaseChar()) {
            'n' -> PieceType.KNIGHT
            'b' -> PieceType.BISHOP
            'r' -> PieceType.ROOK
            'q' -> PieceType.QUEEN
            else -> return null
        }
    } else {
        null
    }
    return ChessMove(from, to, promo)
}

/** TalkBack-grade description of one square, e.g. "e4, white knight". */
fun describeSquare(position: Position, sq: Int): String {
    val code = position.board[sq]
    val name = squareName(sq)
    val type = PieceCode.typeOf(code) ?: return "$name, empty"
    val side = PieceCode.sideOf(code)
    val sideWord = if (side == Side.WHITE) "white" else "black"
    val pieceWord = when (type) {
        PieceType.PAWN -> "pawn"
        PieceType.KNIGHT -> "knight"
        PieceType.BISHOP -> "bishop"
        PieceType.ROOK -> "rook"
        PieceType.QUEEN -> "queen"
        PieceType.KING -> "king"
    }
    return "$name, $sideWord $pieceWord"
}
