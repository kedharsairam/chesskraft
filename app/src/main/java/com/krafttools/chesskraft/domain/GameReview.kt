/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 *
 * Grading a finished game: one verdict per move, one accuracy number per side.
 * Pure Kotlin — no Android, no Compose, and no search either. The engine
 * arrives as a function, [GameReview.build]'s `analyze` parameter, so every
 * number in here can be pinned with scripted scores and no board in sight.
 *
 * The verdicts are the usual chess-tool convention: centipawns lost against the
 * engine's best move. The accuracy numbers are chess.com-shaped, not
 * chess.com-identical — see the formula notes on [GameReview].
 *
 * Every move also records where it left the game, [ReviewedMove.evalAfterCp], so
 * a review can draw an evaluation line off the same single pass over the
 * positions. Nothing here is searched twice for that.
 */
package com.krafttools.chesskraft.domain

import com.krafttools.chesskraft.engine.Analysis
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/** How a move compares with the engine's choice in the same position. */
enum class MoveVerdict {
    /** No measurable loss: played what the engine wanted. */
    BEST,

    /** Under 20cp — a move that changes nothing. */
    GOOD,

    /** Under 80cp — sloppy, still playable. */
    INACCURACY,

    /** Under 200cp — a real mistake. */
    MISTAKE,

    /** 200cp or more, or a lost mate. */
    BLUNDER,

}

/**
 * The same ladder the review uses, exposed so one move can be graded mid-game
 * without replaying the whole game. One place decides what a number means, and
 * these are the same thresholds GameReview grades against.
 */
fun verdictForCpLoss(cpLoss: Int): MoveVerdict = when {
    cpLoss <= 0 -> MoveVerdict.BEST
    cpLoss < 20 -> MoveVerdict.GOOD
    cpLoss < 80 -> MoveVerdict.INACCURACY
    cpLoss < 200 -> MoveVerdict.MISTAKE
    else -> MoveVerdict.BLUNDER
}


/**
 * One graded move.
 *
 * [ply] is 0-based, [number] is the move number a score sheet prints (1 for
 * both of White's and Black's first moves), [san] is what was actually played,
 * [cpLoss] the centipawns it gave away against the engine's best move, and
 * [bestSan] what the engine wanted instead — null when the analyser had no
 * answer for that position.
 *
 * [evalAfterCp] is the score of the position *after* this move, from White's
 * point of view, so a review can draw an evaluation line without searching
 * anything again. It is null when the analyser could not see that position —
 * which is also what the default says, for a caller with no evaluation to pass
 * — and it carries the search's own mate encoding rather than a flattened
 * "infinite": a mate arrives as a large number in one direction or the other
 * ([GameReview.MATE_THRESHOLD] and up means mate, never a pawn evaluation).
 */
data class ReviewedMove(
    val ply: Int,
    val number: Int,
    val san: String,
    val verdict: MoveVerdict,
    val cpLoss: Int,
    val bestSan: String?,
    val evalAfterCp: Int? = null,
)

/**
 * The whole grade: every move in order, plus one accuracy per side. A side
 * that made no moves — or whose moves the analyser never saw — scores 100,
 * because nothing was played against it.
 */
data class GameReviewResult(
    val moves: List<ReviewedMove>,
    val accuracyWhite: Int,
    val accuracyBlack: Int,
)

/**
 * Builds a [GameReviewResult] from a finished game.
 *
 * Input is a replay, not a move list: `fens[i]` is the position *before* the
 * i-th move and `sans[i]` is that move. [GameTree.fenHistory] and
 * [GameTree.sanList] line up exactly like this.
 *
 * Verdicts
 * --------
 * The analyser is asked for `fens[i]` — its score is the side-to-move's view,
 * which is the mover's view, and is therefore the score of the best move. The
 * score of what was played comes from the position actually reached,
 * `fens[i+1]`, negated because the side to move has flipped:
 *
 * ```
 * cpLoss = max(0, scoreBest - scorePlayed)
 * ```
 *
 * A mate is not a centipawn count and is not graded as one: the search's mate
 * sentinel (magnitude >= [MATE_THRESHOLD]) means the move delivers mate, and a
 * move that mates is [MoveVerdict.BEST] with no loss, however much longer the
 * engine's own line was. Missing a mate that was on the board, on the other
 * hand, shows up as the huge centipawn loss it is.
 *
 * Accuracy — an approximation, said plainly
 * ----------------------------------------
 * These numbers follow the chess.com *shape*: win probability rather than raw
 * centipawns, a per-move score, a weighted average per side. They are **not**
 * the chess.com formula, and they do not reproduce its numbers. chess.com
 * evaluates each position statically before the move and knows the exact
 * best-move position afterwards; this app has neither — only search scores for
 * the positions that were actually visited. What is computed here, per move,
 * with all win probabilities from the mover's point of view:
 *
 * ```
 * winProb(cp) = 1 / (1 + 10 ^ (-cp / 400))
 *
 * wpBefore     = winProb(-scoreOf(fens[i - 1]))   // what you inherited; 0.5 for the first move
 * wpAfterBest  = winProb(scoreOf(fens[i]))        // the analyser's move, from its own search
 * wpAfterPlayed= winProb(-scoreOf(fens[i + 1]))  // what you played
 *
 * gainBest   = clamp(wpAfterBest  - wpBefore, -1, 1)
 * gainPlayed = clamp(wpAfterPlayed- wpBefore, -1, 1)
 *
 * moveAccuracy = 100 * clamp(1 - (gainBest - max(0, gainPlayed)) / (abs(gainBest) + 0.1), 0, 1)
 * weight       = abs(gainBest) + 0.1
 * gameAccuracy = round(100 * sum(weight * moveAccuracy) / sum(weight))    // 0..100
 * ```
 *
 * Two consequences worth knowing. A move that throws away win probability the
 * engine never had available is not punished for it — only the share it
 * actually gave up counts. And each move is weighted by `abs(gainBest) + 0.1`,
 * so a swing decides the average and a quiet move barely registers, while the
 * 0.1 keeps a totally quiet move from being worth exactly nothing.
 *
 * The one real departure from a static "before" evaluation is `wpBefore`: the
 * value the mover inherited is read off the previous ply (negated, because the
 * side to move flipped), and 0.5 stands in for the opening move. After an
 * opponent's blunder that inherited value flatters the mover, so the next move
 * can read a little lower than chess.com would score it. The +0.1 in the
 * denominator absorbs the resulting noise near zero.
 *
 * A [MoveVerdict.BLUNDER] scores 0 for its move whatever the probabilities say.
 * Moves the analyser could not see on both sides of the board are left out of
 * the average entirely rather than counted as perfect.
 */
object GameReview {

    /**
     * Score magnitude that means "mate", not "centipawns". The search's own
     * sentinel is bigger ([com.krafttools.chesskraft.engine.Searcher.MATE] is
     * 29000), so every mate it reports clears this bar, and no real evaluation
     * reaches it — a dozen queens would not.
     */
    const val MATE_THRESHOLD = 10_000

    /** Win probability moves 400cp between a 50% and a 90% game. */
    private const val WIN_PROB_SCALE = 400.0

    /** Weight floor: a move where nothing was at stake still counts a little. */
    private const val WEIGHT_FLOOR = 0.1

    /**
     * Grades every move of a finished game. [analyze] is asked once per distinct
     * position and may return null when it has no answer; the game is still
     * reviewed, just without grades on the positions it could not see.
     *
     * Each move also carries [ReviewedMove.evalAfterCp], the evaluation of the
     * position it produced. That is the same answer already fetched for the
     * grading, read from White's side instead of the mover's — a review screen
     * can draw the whole line from this one pass, and a caller that wanted the
     * evaluations separately must not have to pay for a second search per ply.
     */
    fun build(
        fens: List<String>,
        sans: List<String>,
        analyze: (String) -> Analysis?,
    ): GameReviewResult {
        val answers = HashMap<String, Analysis?>()
        fun answered(fen: String): Analysis? {
            if (!answers.containsKey(fen)) answers[fen] = analyze(fen)
            return answers[fen]
        }

        val reviewed = ArrayList<ReviewedMove>(sans.size)
        var whiteWeight = 0.0
        var whiteWeighted = 0.0
        var blackWeight = 0.0
        var blackWeighted = 0.0

        // A move needs the position before it and the one after it.
        val plies = minOf(sans.size, fens.size - 1)
        for (ply in 0 until plies) {
            val before = Position.fromFen(fens[ply]).getOrNull()
            val best = answered(fens[ply])
            val reached = answered(fens[ply + 1])
            // fens[ply + 1] has the other side to move, so negate back to the mover.
            val bestScore = best?.scoreCp
            // One number read two ways. The position after the move is reported
            // from the other side's point of view, so negating it gives both the
            // score of what was played in the mover's terms — which is what
            // cpLoss is arithmetic on — and that same position's evaluation in
            // White's terms, which is [ReviewedMove.evalAfterCp]. Deriving the
            // second from the first is the whole point: asking the analyser again
            // for it would double the search cost for no new information.
            val playedScore = reached?.let { -it.scoreCp }

            val cpLoss: Int
            val verdict: MoveVerdict
            when {
                bestScore == null || playedScore == null -> {
                    // Nothing to compare against: ungraded, not wrong.
                    cpLoss = 0
                    verdict = MoveVerdict.BEST
                }
                playedScore >= MATE_THRESHOLD -> {
                    // It mates. Centipawn arithmetic stops here.
                    cpLoss = 0
                    verdict = MoveVerdict.BEST
                }
                else -> {
                    cpLoss = maxOf(0, bestScore - playedScore)
                    verdict = verdictFor(cpLoss)
                }
            }

            reviewed.add(
                ReviewedMove(
                    ply = ply,
                    // The FEN's own move counter, which already counts Black's
                    // reply as part of the current move.
                    number = before?.fullmove ?: ply / 2 + 1,
                    san = sans[ply],
                    verdict = verdict,
                    cpLoss = cpLoss,
                    bestSan = best?.let { sanOf(before, it.bestMove.text) },
                    // White's view of where the move left the game. Already in
                    // hand from the same search that graded the move.
                    evalAfterCp = playedScore,
                ),
            )

            val inheritedScore = if (ply > 0) answered(fens[ply - 1])?.scoreCp else null
            if (bestScore != null && playedScore != null &&
                (inheritedScore != null || ply == 0)
            ) {
                val wpBefore = if (inheritedScore == null) 0.5 else winProbability(-inheritedScore)
                val gainBest = (winProbability(bestScore) - wpBefore).coerceIn(-1.0, 1.0)
                val gainPlayed = (winProbability(playedScore) - wpBefore).coerceIn(-1.0, 1.0)
                val weight = abs(gainBest) + WEIGHT_FLOOR
                val accuracy = moveAccuracy(gainBest, gainPlayed, verdict)
                if (moverOf(ply, before) == Side.WHITE) {
                    whiteWeight += weight
                    whiteWeighted += weight * accuracy
                } else {
                    blackWeight += weight
                    blackWeighted += weight * accuracy
                }
            }
        }

        return GameReviewResult(
            moves = reviewed,
            accuracyWhite = gameAccuracy(whiteWeight, whiteWeighted),
            accuracyBlack = gameAccuracy(blackWeight, blackWeighted),
        )
    }

    /** Threshold ladder from the frozen review spec, in centipawns. */
    private fun verdictFor(cpLoss: Int): MoveVerdict = when {
        cpLoss <= 0 -> MoveVerdict.BEST
        cpLoss < 20 -> MoveVerdict.GOOD
        cpLoss < 80 -> MoveVerdict.INACCURACY
        cpLoss < 200 -> MoveVerdict.MISTAKE
        else -> MoveVerdict.BLUNDER
    }

    /** Logistic win probability, clamped so an absurd score cannot overflow. */
    private fun winProbability(cp: Int): Double {
        val clamped = cp.coerceIn(-MATE_THRESHOLD, MATE_THRESHOLD)
        return 1.0 / (1.0 + 10.0.pow(-clamped / WIN_PROB_SCALE))
    }

    /** Loses only the share of available win probability the move gave up. */
    private fun moveAccuracy(gainBest: Double, gainPlayed: Double, verdict: MoveVerdict): Double {
        if (verdict == MoveVerdict.BLUNDER) return 0.0
        val givenUp = (gainBest - max(0.0, gainPlayed)) / (abs(gainBest) + WEIGHT_FLOOR)
        return (1.0 - givenUp).coerceIn(0.0, 1.0)
    }

    private fun gameAccuracy(weight: Double, weightedAccuracy: Double): Int {
        if (weight <= 0.0) return 100
        return (100.0 * weightedAccuracy / weight).roundToInt().coerceIn(0, 100)
    }

    /** SAN of [uci] in [position], or null if it is not a legal move there. */
    private fun sanOf(position: Position?, uci: String): String? {
        if (position == null) return null
        val move = parseUci(uci) ?: return null
        if (position.generateLegalMoves().none { it == move }) return null
        return position.toSan(move)
    }

    /** White moves on even plies; an unreadable FEN falls back to that. */
    private fun moverOf(ply: Int, position: Position?): Side =
        position?.sideToMove ?: if (ply % 2 == 0) Side.WHITE else Side.BLACK
}