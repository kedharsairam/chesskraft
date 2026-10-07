/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 *
 * Grading without an engine: a scripted analyser hands back fixed scores per
 * position, so every threshold, every verdict and every accuracy number in
 * GameReview is pinned by arithmetic that is written out in the assertions
 * below. No search runs here and no board is needed beyond building the FENs.
 */
package com.krafttools.chesskraft.domain

import com.krafttools.chesskraft.engine.Analysis
import com.krafttools.chesskraft.engine.UciMove
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameReviewTest {

    /**
     * A scripted analyser: answers per position, and a count of what it was
     * asked. It implements the function type rather than being converted to it,
     * because a class with state is not a lambda.
     */
    private class Script(private val answers: Map<String, Analysis>) : (String) -> Analysis? {
        val asked = ArrayList<String>()

        override fun invoke(fen: String): Analysis? {
            asked.add(fen)
            return answers[fen]
        }
    }

    /** A replay: positions before each move, SAN played, and the UCIs behind them. */
    private class Game(
        val fens: List<String>,
        val sans: List<String>,
        val moveUcis: List<String>,
    )

    /** Replays UCI moves from the start position, recording positions and SAN. */
    private fun game(vararg moveUcis: String): Game {
        var position = Position.start()
        val fens = ArrayList<String>()
        val sans = ArrayList<String>()
        fens.add(position.toFen())
        for (uci in moveUcis) {
            val move = parseUci(uci) ?: error("not a move: $uci")
            check(position.generateLegalMoves().any { it == move }) { "illegal in a test game: $uci" }
            sans.add(position.toSan(move))
            position = position.makeMove(move)
            fens.add(position.toFen())
        }
        return Game(fens, sans, moveUcis.toList())
    }

    /**
     * Answers for every position of [g] with the played move as the engine's
     * pick — so the score sequence is the only thing under test — using [scores]
     * (side-to-move point of view, one per position). The position after the
     * last move has no move of its own and gets the null move.
     */
    private fun script(g: Game, scores: List<Int>): Script =
        Script(
            g.fens.mapIndexed { i, fen ->
                fen to Analysis(UciMove(g.moveUcis.getOrElse(i) { "0000" }), scores[i])
            }.toMap(),
        )

    /** 1.e4 e5 2.Nf3 Nc6 3.d4 exd4 4.Nxd4 Nf6 — eight plies, nine positions. */
    private val opening = game(
        "e2e4", "e7e5", "g1f3", "b8c6", "d2d4", "e5d4", "f3d4", "g8f6",
    )

    @Test
    fun verdictAtEveryThresholdBoundary() {
        // cpLoss(ply) = scoreBefore + scoreAfter, because the position after the
        // move is reported from the other side's point of view and negated back.
        val result = GameReview.build(
            opening.fens,
            opening.sans,
            script(opening, listOf(0, 0, 19, 1, 78, 2, 197, 3, 347)),
        )
        assertEquals(
            listOf(
                MoveVerdict.BEST,
                MoveVerdict.GOOD,
                MoveVerdict.INACCURACY,
                MoveVerdict.INACCURACY,
                MoveVerdict.MISTAKE,
                MoveVerdict.MISTAKE,
                MoveVerdict.BLUNDER,
                MoveVerdict.BLUNDER,
            ),
            result.moves.map { it.verdict },
        )
        assertEquals(listOf(0, 19, 20, 79, 80, 199, 200, 350), result.moves.map { it.cpLoss })
        assertEquals((0 until 8).toList(), result.moves.map { it.ply })
        // Both of White's and Black's first moves belong to move 1.
        assertEquals(listOf(1, 1, 2, 2, 3, 3, 4, 4), result.moves.map { it.number })
        assertEquals(opening.sans, result.moves.map { it.san })
    }

    @Test
    fun eachPositionIsAnalysedOnce() {
        val analyser = script(opening, List(opening.fens.size) { 0 })
        GameReview.build(opening.fens, opening.sans, analyser)
        // Nine positions, asked once each: the previous position is read for the
        // "before" win probability, so a re-search would be a doubled cost.
        assertEquals(opening.fens.size, analyser.asked.size)
        assertEquals(opening.fens.toSet().size, analyser.asked.toSet().size)
    }

    @Test
    fun evalAfterIsTheScoreOfThePositionReached() {
        // Scripted scores are the side-to-move's view, so the value White keeps
        // after a move is the *negated* score of the position that move made —
        // the same number cpLoss is computed from, read the other way round.
        val result = GameReview.build(
            opening.fens,
            opening.sans,
            script(opening, listOf(0, 0, 19, 1, 78, 2, 197, 3, 347)),
        )
        assertEquals(listOf(0, -19, -1, -78, -2, -197, -3, -347), result.moves.map { it.evalAfterCp })
    }

    @Test
    fun everyMoveGetsOneEvaluationAndNoPositionIsSearchedTwice() {
        // The evaluation comes out of the pass that already graded the move, so
        // asking for it must not add a single question to the analyser.
        val analyser = script(opening, List(opening.fens.size) { 40 })
        val result = GameReview.build(opening.fens, opening.sans, analyser)
        assertEquals(8, result.moves.size)
        assertEquals(8, result.moves.count { it.evalAfterCp != null })
        assertEquals(opening.fens.size, analyser.asked.size)
        assertEquals(opening.fens.toSet().size, analyser.asked.toSet().size)
    }

    @Test
    fun evalAfterIsNullWhereTheAnalyserWasNot() {
        // Only the last position is left unanswered: every move that reached it
        // has no evaluation to report, and the one before it still does.
        val partial = Script(
            opening.fens.dropLast(1).mapIndexed { i, fen ->
                fen to Analysis(UciMove(opening.moveUcis.getOrElse(i) { "0000" }), 20 * (i + 1))
            }.toMap(),
        )
        val result = GameReview.build(opening.fens, opening.sans, partial)
        assertEquals(
            listOf(-40, -60, -80, -100, -120, -140, -160, null),
            result.moves.map { it.evalAfterCp },
        )
        // The move that reached the unseen position is graded as usual (140
        // against -160 is a three-hundred blunder) and the move *out* of it has
        // no evaluation to report — but the engine's pick before it is still
        // there, so the line knows what it wanted.
        assertEquals(MoveVerdict.BLUNDER, result.moves[6].verdict)
        assertEquals(300, result.moves[6].cpLoss)
        assertEquals(MoveVerdict.BEST, result.moves[7].verdict)
        assertEquals(0, result.moves[7].cpLoss)
        assertEquals("Nf6", result.moves[7].bestSan)
        assertNull(result.moves[7].evalAfterCp)
    }

    @Test
    fun matePlayedIsBest() {
        // The engine sees mate in three; White mates now instead. The engine's
        // slower line must not turn a mate into a mistake.
        val g = game("e2e4")
        val result = GameReview.build(
            g.fens,
            g.sans,
            Script(
                mapOf(
                    g.fens[0] to Analysis(UciMove("g1f3"), 28_900),
                    g.fens[1] to Analysis(UciMove("b8c6"), -29_000),
                ),
            ),
        )
        val move = result.moves.single()
        assertEquals(MoveVerdict.BEST, move.verdict)
        assertEquals(0, move.cpLoss)
        assertEquals("Nf3", move.bestSan)
        // The evaluation keeps the search's own mate encoding rather than being
        // flattened: from White's side the position it reached is a mate.
        assertEquals(29_000, move.evalAfterCp)
    }

    @Test
    fun missedMateIsBlunder() {
        // Same mate on the board, not taken: Black ends up +300 instead of mated.
        val g = game("e2e4")
        val result = GameReview.build(
            g.fens,
            g.sans,
            Script(
                mapOf(
                    g.fens[0] to Analysis(UciMove("g1f3"), 28_900),
                    g.fens[1] to Analysis(UciMove("b8c6"), -300),
                ),
            ),
        )
        val move = result.moves.single()
        assertEquals(MoveVerdict.BLUNDER, move.verdict)
        // 28_900 - 300: the mate it gave up, counted in centipawns.
        assertEquals(28_600, move.cpLoss)
        // White's evaluation of where the move left it: Black is a pawn up.
        assertEquals(300, move.evalAfterCp)
    }

    @Test
    fun bestMoveIsRenderedAsSanWithItsCheckMark() {
        // Scholar's mate: the engine's h5f7 is Qxf7#, and the mated position
        // answers with the null move and a mate score against the side to move.
        val fens = listOf(
            "r1bqkb1r/pppp1ppp/2n2n2/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 0 1",
            "r1bqkb1r/pppp1ppp/2n2n2/5Q2/2B1P3/8/PPPP1PPP/RNB1K1NR b KQkq - 0 1",
        )
        val result = GameReview.build(
            fens,
            listOf("Qxf7#"),
            Script(
                mapOf(
                    fens[0] to Analysis(UciMove("h5f7"), 29_000),
                    fens[1] to Analysis(UciMove("0000"), -29_000),
                ),
            ),
        )
        val move = result.moves.single()
        assertEquals("Qxf7#", move.san)
        assertEquals("Qxf7#", move.bestSan)
        assertEquals(MoveVerdict.BEST, move.verdict)
    }

    @Test
    fun bestSanIsNullWhenThePickIsNotAMoveHere() {
        val g = game("e2e4", "e7e5")
        val noMove = GameReview.build(
            g.fens,
            g.sans,
            Script(mapOf(g.fens[0] to Analysis(UciMove("0000"), 40))),
        )
        assertNull(noMove.moves.first().bestSan)
        // a1a8 is a real square pair but not a legal White move from the start.
        val illegal = GameReview.build(
            g.fens,
            g.sans,
            Script(mapOf(g.fens[0] to Analysis(UciMove("a1a8"), 40))),
        )
        assertNull(illegal.moves.first().bestSan)
        // A knight move needs its disambiguation letter and is legal here.
        val legal = GameReview.build(
            g.fens,
            g.sans,
            Script(mapOf(g.fens[0] to Analysis(UciMove("g1f3"), 40))),
        )
        assertEquals("Nf3", legal.moves.first().bestSan)
    }

    @Test
    fun blunderScoresZeroForThatMove() {
        // White's only move throws 300cp; Black never moves.
        val g = game("e2e4")
        val result = GameReview.build(
            g.fens,
            g.sans,
            Script(
                mapOf(
                    g.fens[0] to Analysis(UciMove("d1h5"), 0),
                    g.fens[1] to Analysis(UciMove("b8c6"), 300),
                ),
            ),
        )
        val move = result.moves.single()
        assertEquals(MoveVerdict.BLUNDER, move.verdict)
        assertEquals(300, move.cpLoss)
        // One move, scored zero, weighted by whatever was at stake.
        assertEquals(0, result.accuracyWhite)
        assertEquals(100, result.accuracyBlack)
    }

    @Test
    fun perfectPlayScoresHundred() {
        // Every move played is the engine's move: the score of the position
        // reached is the negation of the score of the position it was played
        // from, so no move loses a centipawn.
        val g = game("e2e4", "e7e5", "g1f3", "b8c6")
        val result = GameReview.build(g.fens, g.sans, script(g, listOf(300, -300, 300, -300, 300)))
        assertTrue(result.moves.all { it.verdict == MoveVerdict.BEST })
        assertTrue(result.moves.all { it.cpLoss == 0 })
        assertEquals(100, result.accuracyWhite)
        assertEquals(100, result.accuracyBlack)
    }

    @Test
    fun sidesAreGradedIndependently() {
        // 1.e4 throws 300cp (blunder), Black's reply and 2.Nf3 are both perfect.
        val blunderByWhite = game("e2e4", "e7e5", "g1f3")
        val white = GameReview.build(
            blunderByWhite.fens,
            blunderByWhite.sans,
            script(blunderByWhite, listOf(0, 300, -300, 300)),
        )
        assertEquals(300, white.moves[0].cpLoss)
        // White's two moves carry equal weight and score 0 and 100 → 50.
        assertEquals(50, white.accuracyWhite)
        assertEquals(100, white.accuracyBlack)

        // 1...e5 throws it instead: the same game, the other way round.
        val blunderByBlack = game("e2e4", "e7e5", "g1f3")
        val black = GameReview.build(
            blunderByBlack.fens,
            blunderByBlack.sans,
            script(blunderByBlack, listOf(300, -300, 600, -600)),
        )
        assertEquals(300, black.moves[1].cpLoss)
        assertEquals(MoveVerdict.BLUNDER, black.moves[1].verdict)
        assertEquals(100, black.accuracyWhite)
        assertEquals(0, black.accuracyBlack)
    }

    @Test
    fun accuracyStaysBetweenZeroAndOneHundred() {
        // Sixteen plies of Ruy Lopez with arbitrary scores: nothing may escape
        // the 0..100 range, and no cpLoss may go negative.
        val g = game(
            "e2e4", "e7e5", "g1f3", "b8c6", "f1b5", "a7a6", "b5a4", "g8f6",
            "e1g1", "f8e7", "f1e1", "b7b5", "a4b3", "b5b4", "d2d3", "d7d6",
        )
        val random = Random(11)
        val result = GameReview.build(
            g.fens,
            g.sans,
            script(g, List(g.fens.size) { random.nextInt(-4_000, 4_000) }),
        )
        assertEquals(16, result.moves.size)
        assertTrue("white ${result.accuracyWhite}", result.accuracyWhite in 0..100)
        assertTrue("black ${result.accuracyBlack}", result.accuracyBlack in 0..100)
        assertTrue(result.moves.all { it.cpLoss >= 0 })
        assertTrue(result.moves.any { it.verdict != MoveVerdict.BEST })
    }

    @Test
    fun anUnansweredPositionIsLeftUngraded() {
        val g = game("e2e4", "e7e5", "g1f3")
        var asked = 0
        val result = GameReview.build(g.fens, g.sans) { asked++; null }
        assertEquals(4, asked)
        assertEquals(3, result.moves.size)
        assertTrue(result.moves.all { it.verdict == MoveVerdict.BEST })
        assertTrue(result.moves.all { it.cpLoss == 0 })
        assertTrue(result.moves.all { it.bestSan == null })
        // Nothing was searched, so there is nothing to report as an evaluation
        // either — a null here, never a zero standing in for "unknown".
        assertTrue(result.moves.all { it.evalAfterCp == null })
        // Nothing could be weighed, so nothing counts against either side.
        assertEquals(100, result.accuracyWhite)
        assertEquals(100, result.accuracyBlack)
    }

    @Test
    fun emptyGameIsTwoPerfectScoresAndNoMoves() {
        var asked = 0
        val result = GameReview.build(emptyList(), emptyList()) { asked++; null }
        assertEquals(0, asked)
        assertTrue(result.moves.isEmpty())
        assertEquals(100, result.accuracyWhite)
        assertEquals(100, result.accuracyBlack)
    }
}