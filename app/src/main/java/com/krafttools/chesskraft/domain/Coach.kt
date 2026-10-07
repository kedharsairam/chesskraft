/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 *
 * One quiet line after a move, written from what the review already knows.
 * Pure Kotlin — no Android, no Compose, and no board: the coach is handed a
 * verdict, a loss in centipawns and the two moves, and it never looks at
 * anything else. That is the trap it has to avoid. With no board there is no
 * way to know *why* a move lost, so nothing here may claim to — "that let the
 * knight take your bishop" is a sentence this file cannot write, because it
 * cannot see the bishop. The only facts on the table are how much the move
 * cost and which two moves were available, and every line below is built from
 * those and nothing else.
 *
 * The other trap is enthusiasm. A coach that gushes on every move trains a
 * player to stop reading it, so the default is silence: [lineFor] returns null
 * far more often than it returns a line, and when it does speak it says one
 * plain sentence.
 */
package com.krafttools.chesskraft.domain

/**
 * What the coach has to say about a move, or nothing at all.
 *
 * The three cases are the only three a verdict can produce that are worth a
 * sentence: ground given away, a win thrown away, and a move that was simply
 * the best one. [GaveAway] covers everything from a slip to a dropped piece;
 * [MissedWin] is the case where the thing left on the table was the game.
 */
sealed interface CoachLine {
    /**
     * The move cost [lossCp] centipawns against the engine's choice, and [what]
     * says so in one sentence.
     */
    data class GaveAway(val lossCp: Int, val what: String) : CoachLine

    /**
     * The move cost [gainCp] centipawns, but the engine's move was mate: the
     * game was there and is not now. Kept apart from [GaveAway] because "you
     * gave something away" and "there was a win on the board" are different
     * sentences and deserve different words.
     */
    data class MissedWin(val gainCp: Int, val what: String) : CoachLine

    /** The move was the best one available, and [what] says so plainly. */
    data class Accurate(val what: String) : CoachLine
}

/**
 * Turns a graded move into at most one sentence.
 *
 * Nothing here is clever. The rules are fixed and small, and each exists
 * because the alternative was a sentence that could be wrong:
 *
 * - [MoveVerdict.GOOD] says nothing. A move that changed nothing carries no
 *   lesson, and a line about it would only be noise.
 * - [MoveVerdict.BEST] praises the move, but only when the engine actually named
 *   one. A move the analyser never saw is graded BEST because it is *ungraded*,
 *   not because it was the engine's pick, and calling that the best move here
 *   would be a sentence the coach cannot support.
 * - [MoveVerdict.INACCURACY], [MoveVerdict.MISTAKE] and [MoveVerdict.BLUNDER]
 *   name the move the engine wanted instead, and say what was lost.
 * - A mate that was on the board and not taken is [CoachLine.MissedWin] rather
 *   than [CoachLine.GaveAway]. It is the one case the coach can name exactly,
 *   because a "#" in the engine's move is a fact about the position rather than
 *   a guess about it.
 */
object Coach {

    /**
     * Losses are quoted to the nearest fifty centipawns. The exact number is
     * noise — a review that says "78" invites an argument about 78 — and half a
     * pawn is the coarsest statement that is still honest.
     */
    private const val ROUNDING_STEP = 50

    /**
     * Below this a reported mate is not believed. A mate the engine sees always
     * costs thousands of centipawns, so a small loss next to a mate in `bestSan`
     * means the two answers disagree — usually that the move is not legal in
     * that position — and the plain sentence is the honest one.
     */
    private const val MISSED_WIN_MIN_CP = 200

    /**
     * The line for one move, or null when there is nothing true to say.
     *
     * [verdict] and [cpLoss] come from [GameReview], [bestSan] is the engine's
     * move in the same position (null when it had none) and [playedSan] is what
     * the player actually played. The loss is rounded, never invented.
     */
    fun lineFor(
        verdict: MoveVerdict,
        cpLoss: Int,
        bestSan: String?,
        playedSan: String,
    ): CoachLine? = when (verdict) {
        // Nothing happened. Silence is the honest review of a quiet move.
        MoveVerdict.GOOD -> null

        // Ungraded moves are BEST as well, and nothing is known about them.
        // No "Good." in front of it. The verdict chip beside the sentence
        // already says "best", and a sentence that opens by calling the best
        // move merely good makes the two disagree about the same move.
        MoveVerdict.BEST ->
            if (bestSan == null) {
                null
            } else if (bestSan == playedSan) {
                CoachLine.Accurate("$bestSan was the best move here.")
            } else {
                // Graded best, yet a different move is named as best — a
                // search that disagreed with itself. Say the smaller claim.
                CoachLine.Accurate("You found the best move in this position.")
            }

        MoveVerdict.INACCURACY,
        MoveVerdict.MISTAKE,
        MoveVerdict.BLUNDER,
        -> {
            val loss = roundedLoss(cpLoss)
            val mate = bestSan?.takeIf { it.endsWith("#") }
            if (mate != null && cpLoss >= MISSED_WIN_MIN_CP) {
                CoachLine.MissedWin(
                    gainCp = loss,
                    what = "$mate ends the game, and $playedSan did not.",
                )
            } else {
                CoachLine.GaveAway(
                    lossCp = loss,
                    what = if (bestSan == null) {
                        "${fault(verdict)} The engine did not say what it wanted here."
                    } else {
                        "${fault(verdict)} $bestSan was the move."
                    },
                )
            }
        }
    }

    /**
     * The first half of a "this cost you" sentence, one phrasing per verdict.
     *
     * Named for the fault rather than for an opening, because [Opening] means
     * something else entirely in this package.
     */
    private fun fault(verdict: MoveVerdict): String = when (verdict) {
        MoveVerdict.INACCURACY -> "That slipped."
        MoveVerdict.MISTAKE -> "That was a mistake."
        else -> "That dropped something real."
    }

    /**
     * Nearest [ROUNDING_STEP], but never zero for a real loss: twenty
     * centipawns round to nothing, and a coach reporting a giveaway of zero
     * reads as a bug rather than a rounding.
     */
    private fun roundedLoss(cpLoss: Int): Int {
        val rounded = ((cpLoss + ROUNDING_STEP / 2) / ROUNDING_STEP) * ROUNDING_STEP
        return if (cpLoss > 0) maxOf(ROUNDING_STEP, rounded) else 0
    }
}
