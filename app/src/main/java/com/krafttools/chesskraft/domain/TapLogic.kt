/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft.domain

/**
 * The tap-tap state machine. Pure Kotlin so the ViewModel is a thin shell and
 * every branch is a JVM unit test.
 *
 * The board never owns legality: this machine reads the position and answers
 * what a tap means. Illegal taps resolve to [Nudge] — the UI shakes the
 * square silently, no sound, no haptic.
 */
sealed interface TapOutcome {
    /** A piece was picked up; show its legal targets. */
    data class Selected(val from: Int, val targets: List<ChessMove>) : TapOutcome

    /** A complete move is ready to play. */
    data class MoveReady(val move: ChessMove) : TapOutcome

    /** Pawn reached the last rank: ask which piece (Queen pre-selected). */
    data class PromotionNeeded(val from: Int, val to: Int, val options: List<ChessMove>) : TapOutcome

    /** Tapped empty space: put the piece back down. */
    data object Deselected : TapOutcome

    /** Illegal tap: silent visual nudge. */
    data object Nudge : TapOutcome
}

object TapLogic {
    fun resolveTap(
        position: Position,
        selected: Int?,
        tapSquare: Int,
    ): TapOutcome {
        if (tapSquare !in 0..63) return TapOutcome.Nudge
        val legal = position.generateLegalMoves()

        // Tapped one of the highlighted targets: play it.
        if (selected != null) {
            val candidates = legal.filter { it.from == selected && it.to == tapSquare }
            if (candidates.isNotEmpty()) {
                return if (candidates.size > 1) {
                    TapOutcome.PromotionNeeded(selected, tapSquare, candidates)
                } else {
                    TapOutcome.MoveReady(candidates.first())
                }
            }
        }

        val code = position.board[tapSquare]
        return if (code != 0 && PieceCode.sideOf(code) == position.sideToMove) {
            val targets = legal.filter { it.from == tapSquare }
            if (targets.isEmpty()) TapOutcome.Nudge else TapOutcome.Selected(tapSquare, targets)
        } else if (selected != null) {
            TapOutcome.Deselected
        } else {
            TapOutcome.Nudge
        }
    }

    /** Drag-and-drop routes through the same legality: drop must be a target. */
    fun resolveDrop(
        position: Position,
        from: Int,
        to: Int,
    ): TapOutcome {
        if (from !in 0..63 || to !in 0..63 || from == to) return TapOutcome.Nudge
        val code = position.board[from]
        if (code == 0 || PieceCode.sideOf(code) != position.sideToMove) return TapOutcome.Nudge
        val candidates = position.generateLegalMoves().filter { it.from == from && it.to == to }
        if (candidates.isEmpty()) return TapOutcome.Nudge
        return if (candidates.size > 1) {
            TapOutcome.PromotionNeeded(from, to, candidates)
        } else {
            TapOutcome.MoveReady(candidates.first())
        }
    }
}
