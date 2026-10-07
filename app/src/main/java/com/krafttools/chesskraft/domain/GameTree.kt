/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft.domain

import com.kraft.core.AppError
import com.kraft.core.KraftResult
import com.krafttools.chesskraft.engine.Difficulty

/**
 * Game outcome in plain words. The UI renders [title] big and [reason] small —
 * never a bare "1-0" or a code.
 */
sealed interface GameResult {
    val title: String
    val reason: String

    data class Checkmate(val winner: Side, val playerSide: Side) : GameResult {
        override val title: String
            get() = if (winner == playerSide) "You win" else "You lose"
        override val reason: String
            get() = if (winner == playerSide) {
                "Checkmate — their king has no safe move."
            } else {
                "Checkmate — your king has no safe move."
            }
    }

    data object Stalemate : GameResult {
        override val title: String get() = "Draw"
        override val reason: String get() = "Stalemate — no legal moves, but the king is safe."
    }

    data object FiftyMove : GameResult {
        override val title: String get() = "Draw"
        override val reason: String get() = "Draw — fifty moves with no capture or pawn move."
    }

    data object Threefold : GameResult {
        override val title: String get() = "Draw"
        override val reason: String get() = "Draw — the same position came up three times."
    }

    data object InsufficientMaterial : GameResult {
        override val title: String get() = "Draw"
        override val reason: String get() = "Draw — neither side can mate with these pieces."
    }

    /**
     * The game ended because both players said yes. Not a rule's verdict and
     * not a position on the board — a decision, and worded like one.
     */
    data object DrawAgreed : GameResult {
        override val title: String get() = "Draw agreed"
        override val reason: String get() = "You both agreed to a draw."
    }

    data class Resigned(val playerResigned: Boolean) : GameResult {
        override val title: String get() = if (playerResigned) "You resigned" else "They resigned"
        override val reason: String get() = "Game over — the game was resigned."
    }

    data class TimeForfeit(val loser: Side, val playerSide: Side) : GameResult {
        override val title: String get() = if (loser == playerSide) "You lost on time" else "You win on time"
        override val reason: String
            get() = if (loser == playerSide) {
                "Flag — your clock ran out."
            } else {
                "Flag — their clock ran out."
            }
    }
}

/**
 * Move history with Zobrist keys. Undo is a full round-trip vs the AI (two
 * plies), and threefold repetition is always free to claim — the game simply
 * ends drawn, no claim button.
 */
class GameTree(start: Position = Position.start()) {
    private val positions = mutableListOf(start)
    private val sans = mutableListOf<String>()
    private val ucis = mutableListOf<String>()
    private val keys = mutableListOf(Zobrist.key(start))

    val plyCount: Int get() = sans.size

    fun current(): Position = positions.last()

    fun currentFen(): String = current().toFen()

    fun sanList(): List<String> = sans.toList()

    /**
     * The move list as UCI ("e2e4", "e7e8q"). This is what gets persisted:
     * replaying UCI from the start position is the only save format that cannot
     * disagree with the rules, because the rules re-derive every position from
     * it. A stored FEN list could drift from a buggy version; this cannot.
     */
    fun uciList(): List<String> = ucis.toList()

    fun fenHistory(): List<String> = positions.map { it.toFen() }

    fun legalMoves(): List<ChessMove> = current().generateLegalMoves()

    /** Applies [move] if legal. Returns the SAN, or null when illegal. */
    fun apply(move: ChessMove): String? {
        val position = current()
        if (position.generateLegalMoves().none { it == move }) return null
        val san = position.toSan(move)
        val next = position.makeMove(move)
        positions.add(next)
        sans.add(san)
        ucis.add(move.toUci())
        keys.add(Zobrist.key(next))
        return san
    }

    fun canUndo(): Boolean = positions.size > 1

    /** Pops one ply. Returns false when there is nothing to undo. */
    fun undoPly(): Boolean {
        if (!canUndo()) return false
        positions.removeAt(positions.lastIndex)
        sans.removeAt(sans.lastIndex)
        ucis.removeAt(ucis.lastIndex)
        keys.removeAt(keys.lastIndex)
        return true
    }

    fun repetitionCount(): Int = keys.count { it == keys.last() }

    fun isThreefold(): Boolean = repetitionCount() >= 3

    /** Terminal result, or null while the game continues. */
    fun result(playerSide: Side): GameResult? {
        val position = current()
        val legal = position.generateLegalMoves()
        if (legal.isEmpty()) {
            return if (position.isInCheck()) {
                GameResult.Checkmate(position.sideToMove.opponent(), playerSide)
            } else {
                GameResult.Stalemate
            }
        }
        if (position.halfmove >= 100) return GameResult.FiftyMove
        if (isThreefold()) return GameResult.Threefold
        if (position.hasInsufficientMaterial()) return GameResult.InsufficientMaterial
        return null
    }
}
