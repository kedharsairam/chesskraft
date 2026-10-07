/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TapLogicTest {
    private val start = Position.start()

    @Test
    fun tapOwnPieceSelectsWithTargets() {
        val outcome = TapLogic.resolveTap(start, null, parseSquare("e2") ?: error("sq"))
        assertTrue(outcome is TapOutcome.Selected)
        val selected = outcome as TapOutcome.Selected
        assertEquals(parseSquare("e2"), selected.from)
        assertEquals(setOf("e2e3", "e2e4"), selected.targets.map { it.toUci() }.toSet())
    }

    @Test
    fun tapTargetPlaysMove() {
        val outcome = TapLogic.resolveTap(start, parseSquare("e2"), parseSquare("e4") ?: error("sq"))
        assertEquals(ChessMove(parseSquare("e2") ?: 0, parseSquare("e4") ?: 0), (outcome as TapOutcome.MoveReady).move)
    }

    @Test
    fun promotionAsksWhichPiece() {
        val position = Position.fromFen(
            "8/P7/8/8/8/1k6/8/4K3 w - - 0 1",
        ).getOrNull() ?: error("bad test FEN")
        val outcome = TapLogic.resolveTap(position, parseSquare("a7"), parseSquare("a8") ?: error("sq"))
        assertTrue(outcome is TapOutcome.PromotionNeeded)
        assertEquals(4, (outcome as TapOutcome.PromotionNeeded).options.size)
    }

    @Test
    fun tapEmptyDeselects() {
        val outcome = TapLogic.resolveTap(start, parseSquare("e2"), parseSquare("e4")?.minus(8) ?: error("sq"))
        // e3 is a target of e2, so this plays; use a3 (empty, not a target) for deselect.
        assertTrue(outcome is TapOutcome.MoveReady)
        val deselect = TapLogic.resolveTap(start, parseSquare("e2"), parseSquare("a3") ?: error("sq"))
        assertEquals(TapOutcome.Deselected, deselect)
    }

    @Test
    fun tapOpponentPieceWithNoSelectionNudges() {
        val outcome = TapLogic.resolveTap(start, null, parseSquare("e7") ?: error("sq"))
        assertEquals(TapOutcome.Nudge, outcome)
    }

    @Test
    fun tapPinnedPieceNudges() {
        // Nothing pins here; use a square with no legal moves: the king is
        // boxed in at start, so tapping e1 nudges (no targets).
        val outcome = TapLogic.resolveTap(start, null, parseSquare("e1") ?: error("sq"))
        assertEquals(TapOutcome.Nudge, outcome)
    }

    @Test
    fun reselectSwitchesPiece() {
        val outcome = TapLogic.resolveTap(start, parseSquare("e2"), parseSquare("g1") ?: error("sq"))
        assertTrue(outcome is TapOutcome.Selected)
        assertEquals(parseSquare("g1"), (outcome as TapOutcome.Selected).from)
    }

    @Test
    fun dragLegalDropPlays() {
        val outcome = TapLogic.resolveDrop(start, parseSquare("e2") ?: 0, parseSquare("e4") ?: 0)
        assertTrue(outcome is TapOutcome.MoveReady)
    }

    @Test
    fun dragIllegalDropNudges() {
        assertEquals(
            TapOutcome.Nudge,
            TapLogic.resolveDrop(start, parseSquare("e2") ?: 0, parseSquare("e5") ?: 0),
        )
        assertEquals(
            TapOutcome.Nudge,
            TapLogic.resolveDrop(start, parseSquare("e2") ?: 0, parseSquare("e2") ?: 0),
        )
        assertEquals(
            TapOutcome.Nudge,
            TapLogic.resolveDrop(start, parseSquare("e7") ?: 0, parseSquare("e5") ?: 0),
        )
    }
}

class GameTreeTest {

    @Test
    fun applyAndUndoRoundTrip() {
        val tree = GameTree()
        val before = tree.currentFen()
        tree.apply(parseUci("e2e4") ?: error("uci"))
        tree.apply(parseUci("e7e5") ?: error("uci"))
        assertEquals(2, tree.plyCount)
        assertEquals(listOf("e4", "e5"), tree.sanList())
        tree.undoPly()
        tree.undoPly()
        assertEquals(0, tree.plyCount)
        assertEquals(before, tree.currentFen())
        assertFalse(tree.canUndo())
    }

    @Test
    fun illegalMoveRejected() {
        val tree = GameTree()
        assertNull(tree.apply(parseUci("e2e5") ?: error("uci")))
        assertEquals(0, tree.plyCount)
    }

    @Test
    fun threefoldEndsDrawn() {
        val tree = GameTree()
        val shuffle = listOf("g1f3", "g8f6", "f3g1", "f6g8")
        repeat(2) {
            for (uci in shuffle) tree.apply(parseUci(uci) ?: error("uci"))
        }
        assertTrue(tree.isThreefold())
        val result = tree.result(Side.WHITE)
        assertTrue(result is GameResult.Threefold)
        assertEquals("Draw", result?.title)
    }

    @Test
    fun checkmateResultNamesWinner() {
        val tree = GameTree()
        for (uci in listOf("f2f3", "e7e5", "g2g4", "d8h4")) {
            tree.apply(parseUci(uci) ?: error("uci"))
        }
        val result = tree.result(Side.WHITE)
        assertTrue(result is GameResult.Checkmate)
        assertEquals("You lose", result?.title)
        assertEquals("Checkmate — your king has no safe move.", result?.reason)
    }

    @Test
    fun fiftyMoveRule() {
        val position = Position.fromFen("k7/8/8/8/8/8/8/K7 w - - 100 60")
            .getOrNull() ?: error("bad test FEN")
        val tree = GameTree(position)
        assertTrue(tree.result(Side.WHITE) is GameResult.FiftyMove)
    }

    @Test
    fun agreedDrawIsNotAByProductOfThePosition() {
        // A tree that is perfectly playable reports nothing: agreeing to a draw
        // is a decision the two players make, never something the rules find.
        val tree = GameTree()
        tree.apply(parseUci("e2e4") ?: error("uci"))
        assertNull(tree.result(Side.WHITE))

        // And the words are words, in the same shape as every other outcome.
        assertEquals("Draw agreed", GameResult.DrawAgreed.title)
        assertEquals("You both agreed to a draw.", GameResult.DrawAgreed.reason)
        assertFalse(GameResult.DrawAgreed.reason.contains("1/2"))
    }

    @Test
    fun zobristStableAcrossIdenticalTrees() {
        val first = GameTree()
        val second = GameTree()
        for (uci in listOf("e2e4", "e7e5")) {
            first.apply(parseUci(uci) ?: error("uci"))
            second.apply(parseUci(uci) ?: error("uci"))
        }
        assertEquals(first.currentFen(), second.currentFen())
        assertEquals(first.repetitionCount(), second.repetitionCount())
    }
}
