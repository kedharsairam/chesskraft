/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft.presentation

import com.krafttools.chesskraft.domain.GameResult
import com.krafttools.chesskraft.domain.Position
import com.krafttools.chesskraft.domain.Side
import com.krafttools.chesskraft.domain.parseSquare
import com.krafttools.chesskraft.engine.Analysis
import com.krafttools.chesskraft.engine.Difficulty
import com.krafttools.chesskraft.engine.Engine
import com.krafttools.chesskraft.engine.SearchLimits
import com.krafttools.chesskraft.engine.UciMove
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {
    private val scheduler = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(scheduler)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(side: Side = Side.WHITE) = GameViewModel(
        playerSide = side,
        difficulty = Difficulty.CASUAL,
        engine = FakeEngine(seed = 7L),
        aiDispatcher = scheduler,
    )

    @Test
    fun initialStateIsWhiteToMove() {
        val vm = viewModel()
        val state = vm.state.value
        assertEquals(Side.WHITE, state.sideToMove)
        assertEquals("Your move — White to play.", state.statusText)
        assertEquals(1, state.pieces[parseSquare("e2") ?: error("sq")])
        assertNull(state.result)
    }

    @Test
    fun tapTapPlaysAndAiReplies() = runTest(scheduler) {
        val vm = viewModel()
        vm.onTap(parseSquare("e2") ?: error("sq"))
        assertEquals(parseSquare("e2"), vm.state.value.selected)
        assertTrue(vm.state.value.targets.isNotEmpty())
        vm.onTap(parseSquare("e4") ?: error("sq"))
        advanceUntilIdle()
        assertEquals(2, vm.state.value.sans.size)
        assertEquals("e4", vm.state.value.sans.first())
        assertEquals(Side.WHITE, vm.state.value.sideToMove)
        assertNotNull(vm.state.value.lastMoveTo)
    }

    @Test
    fun illegalTapNudgesSilently() {
        val vm = viewModel()
        vm.onTap(parseSquare("e7") ?: error("sq"))
        assertEquals(1, vm.state.value.nudgeToken)
        assertEquals(0, vm.state.value.sans.size)
    }

    @Test
    fun dragDropPlays() = runTest(scheduler) {
        val vm = viewModel()
        vm.onDrop(parseSquare("d2") ?: error("sq"), parseSquare("d4") ?: error("sq"))
        advanceUntilIdle()
        assertEquals("d4", vm.state.value.sans.first())
    }

    @Test
    fun undoIsFullRoundTrip() = runTest(scheduler) {
        val vm = viewModel()
        vm.onTap(parseSquare("e2") ?: error("sq"))
        vm.onTap(parseSquare("e4") ?: error("sq"))
        advanceUntilIdle()
        assertEquals(2, vm.state.value.sans.size)
        vm.undo()
        assertEquals(0, vm.state.value.sans.size)
        assertEquals(Side.WHITE, vm.state.value.sideToMove)
    }

    @Test
    fun flipAndSoundToggle() {
        val vm = viewModel()
        assertEquals(false, vm.state.value.flipped)
        vm.flip()
        assertEquals(true, vm.state.value.flipped)
        assertEquals(true, vm.state.value.soundOn)
        vm.toggleSound()
        assertEquals(false, vm.state.value.soundOn)
    }

    @Test
    fun resignEndsGame() {
        val vm = viewModel()
        vm.resign()
        val result = vm.state.value.result
        assertTrue(result is GameResult.Resigned)
        assertEquals("You resigned", result?.title)
    }

    @Test
    fun foolsMateLosesAsWhite() = runTest(scheduler) {
        // Deterministic stub would interfere, so drive the tree directly:
        // White plays f3/g4 while Black mates on h4. FakeEngine replies may
        // vary, so instead assert the domain result shape through the tree.
        val vm = viewModel()
        assertTrue(vm.testApplyUci("f2f3"))
        assertTrue(vm.testApplyUci("e7e5"))
        assertTrue(vm.testApplyUci("g2g4"))
        assertTrue(vm.testApplyUci("d8h4"))
        val result = vm.state.value.result
        assertTrue(result is GameResult.Checkmate)
        assertEquals("Checkmate — your king has no safe move.", result?.reason)
    }

    @Test
    fun enPassantWashMarksPawnSquares() {
        val vm = viewModel()
        for (uci in listOf("e2e4", "a7a6", "e4e5", "d7d5", "e5d6")) {
            assertTrue(vm.testApplyUci(uci))
        }
        assertEquals(parseSquare("e5"), vm.state.value.lastMoveFrom)
        assertEquals(parseSquare("d6"), vm.state.value.lastMoveTo)
        assertEquals("exd6", vm.state.value.sans.last())
    }
    @Test
    fun blackPlayerGetsAiOpening() = runTest(scheduler) {
        val vm = viewModel(Side.BLACK)
        advanceUntilIdle()
        assertEquals(1, vm.state.value.sans.size)
        assertEquals(Side.BLACK, vm.state.value.sideToMove)
    }

    @Test
    fun hintSurfacesAMove() = runTest(scheduler) {
        val vm = viewModel()
        vm.hint()
        advanceUntilIdle()
        assertNotNull(vm.state.value.hintMove)
    }

    @Test
    fun untimedGameHasNoClocks() {
        val vm = viewModel()
        assertNull(vm.state.value.clockWhiteMs)
        assertNull(vm.state.value.clockBlackMs)
    }

    @Test
    fun clockBurnsAndFlags() {
        val vm = GameViewModel(
            playerSide = Side.WHITE,
            difficulty = Difficulty.CASUAL,
            engine = FakeEngine(seed = 7L),
            aiDispatcher = scheduler,
            timeControlMs = 10_000L,
        )
        assertEquals(10_000L, vm.state.value.clockWhiteMs)
        // White sits for 9s: bank drains, no verdict yet.
        vm.onElapsed(9_000L)
        assertEquals(1_000L, vm.state.value.clockWhiteMs)
        assertNull(vm.state.value.result)
        // One more second: flag, and the words say so.
        vm.onElapsed(1_000L)
        val result = vm.state.value.result
        assertTrue(result is GameResult.TimeForfeit)
        assertEquals(Side.WHITE, (result as GameResult.TimeForfeit).loser)
        // And it stays flagged: no ticks after the flag.
        vm.onElapsed(5_000L)
        assertEquals(0L, vm.state.value.clockWhiteMs)
    }

    // -- Draw offers ------------------------------------------------------

    /**
     * The threshold is a decision about chess, not an implementation detail,
     * so these two tests are the pair that pins it: a balanced position must
     * accept and a winning one must decline. Move [DRAW_ACCEPT_LIMIT_CP] and
     * one of these goes red, which is the intended way to change it.
     */
    @Test
    fun balancedPositionAcceptsADraw() = runTest(scheduler) {
        val vm = drawViewModel(scoreCp = 12)
        vm.offerDraw()
        advanceUntilIdle()
        assertEquals(GameResult.DrawAgreed, vm.state.value.result)
        assertEquals("Draw agreed", vm.state.value.result?.title)
        assertEquals("You both agreed to a draw.", vm.state.value.result?.reason)
        assertEquals(false, vm.state.value.drawOfferPending)
    }

    @Test
    fun winningPositionDeclinesADraw() = runTest(scheduler) {
        // Up a queen: the computer is not giving this away.
        val vm = drawViewModel(scoreCp = 900)
        vm.offerDraw()
        advanceUntilIdle()
        assertNull(vm.state.value.result)
        assertEquals(false, vm.state.value.drawOfferPending)
    }

    @Test
    fun losingPositionAlsoDeclinesTheBar() = runTest(scheduler) {
        // The bar is on |score|: whoever is ahead, the computer does not agree
        // to throw the game away. (Chess.com bots often accept when losing —
        // this one declines, and that is a decision worth pinning.)
        val vm = drawViewModel(scoreCp = -900)
        vm.offerDraw()
        advanceUntilIdle()
        assertNull(vm.state.value.result)
    }

    @Test
    fun thresholdIsWhereTheBarSits() = runTest(scheduler) {
        // One centipawn under the bar accepts, exactly on it declines. Without
        // this the boundary itself could drift unnoticed.
        assertEquals(GameResult.DrawAgreed, offerAndResult(DRAW_ACCEPT_LIMIT_CP - 1))
        assertNull(offerAndResult(DRAW_ACCEPT_LIMIT_CP))
    }

    @Test
    fun theAnswerComesFromThePositionAfterTheLastMove() = runTest(scheduler) {
        val engine = ScriptedEngine(scoreCp = 12)
        val vm = drawViewModel(engine = engine)
        vm.onDrop(parseSquare("e2") ?: error("sq"), parseSquare("e4") ?: error("sq"))
        advanceUntilIdle()
        val callsBefore = engine.analyzeCalls
        val fens = vm.exportHistory().first
        assertEquals(2, vm.state.value.sans.size)
        vm.offerDraw()
        advanceUntilIdle()
        // The position the computer just replied into is the reference — the
        // last FEN in the trail, not the one the player's own move produced.
        assertEquals(fens.last(), engine.analyzeFen)
        assertTrue(fens[fens.lastIndex - 1] != fens.last())
        assertEquals(Side.WHITE, Position.fromFen(fens.last()).getOrNull()?.sideToMove)
        assertEquals(GameResult.DrawAgreed, vm.state.value.result)
    }

    @Test
    fun offerIsIgnoredWhenTheComputerIsThinking() = runTest(scheduler) {
        val engine = ScriptedEngine(scoreCp = 12)
        val vm = drawViewModel(engine = engine)
        // The player's move puts the AI to work; the offer lands mid-search.
        vm.onDrop(parseSquare("e2") ?: error("sq"), parseSquare("e4") ?: error("sq"))
        assertEquals(true, vm.state.value.aiThinking)
        vm.offerDraw()
        advanceUntilIdle()
        assertEquals(false, vm.state.value.drawOfferPending)
        assertNull(vm.state.value.result)
        assertFalse(engine.askedWith(drawViewModel().drawOfferLimits()))
    }

    @Test
    fun offerIsIgnoredAfterTheGameEnds() = runTest(scheduler) {
        val engine = ScriptedEngine(scoreCp = 12)
        val vm = drawViewModel(engine = engine)
        vm.resign()
        vm.offerDraw()
        advanceUntilIdle()
        assertEquals(false, vm.state.value.drawOfferPending)
        assertFalse(engine.askedWith(drawViewModel().drawOfferLimits()))
        assertTrue(vm.state.value.result is GameResult.Resigned)
    }

    @Test
    fun decliningLeavesTheGameUntouched() = runTest(scheduler) {
        val engine = ScriptedEngine(scoreCp = 900)
        val vm = drawViewModel(engine = engine)
        vm.onDrop(parseSquare("d2") ?: error("sq"), parseSquare("d4") ?: error("sq"))
        advanceUntilIdle()
        val before = vm.state.value
        vm.offerDraw()
        advanceUntilIdle()
        val after = vm.state.value
        // Same moves, same board, same status line — only the pending flag moved.
        assertEquals(before.sans, after.sans)
        assertEquals(before.pieces, after.pieces)
        assertEquals(before.sideToMove, after.sideToMove)
        assertEquals(before.statusText, after.statusText)
        assertEquals(before.result, after.result)
        assertEquals(false, after.drawOfferPending)
    }

    @Test
    fun twoOffersInARowOnlyAskOnce() = runTest(scheduler) {
        val engine = ScriptedEngine(scoreCp = 900)
        val vm = drawViewModel(engine = engine)
        advanceUntilIdle()
        val callsBefore = engine.analyzeCalls
        vm.offerDraw()
        vm.offerDraw()
        advanceUntilIdle()
        // The second offer found the flag already set and did nothing.
        assertEquals(1, engine.analyzeCalls - callsBefore)
        assertNull(vm.state.value.result)
    }

    @Test
    fun anEngineWithNoAnswerDeclines() = runTest(scheduler) {
        // Nothing to judge with: the conservative answer stands, and the game
        // is left exactly as it was.
        val vm = drawViewModel(engine = ScriptedEngine(scoreCp = 0, noAnswer = true))
        vm.offerDraw()
        advanceUntilIdle()
        assertNull(vm.state.value.result)
        assertEquals(false, vm.state.value.drawOfferPending)
    }

    @Test
    fun anOfferThatOutlivedItsPositionIsDropped() = runTest(scheduler) {
        val engine = ScriptedEngine(scoreCp = 12)
        val vm = drawViewModel(engine = engine)
        vm.onDrop(parseSquare("e2") ?: error("sq"), parseSquare("e4") ?: error("sq"))
        advanceUntilIdle()
        val callsBefore = engine.analyzeCalls
        // The offer is asked for, then the game moves on before the answer
        // lands (the scheduler has not run it yet). The stale answer must not
        // end the position the player has taken back to.
        vm.offerDraw()
        vm.undo()
        advanceUntilIdle()
        assertNull(vm.state.value.result)
        assertEquals(false, vm.state.value.drawOfferPending)
        assertEquals(0, vm.state.value.sans.size)
        // It really was asked and really did answer — just too late to count.
        assertEquals(1, engine.analyzeCalls - callsBefore)
    }

    @Test
    fun aThrowingEngineDeclinesRatherThanCrashing() = runTest(scheduler) {
        val vm = drawViewModel(engine = ScriptedEngine(scoreCp = 0, throwsOnAnalyze = true))
        vm.offerDraw()
        advanceUntilIdle()
        assertNull(vm.state.value.result)
        assertEquals(false, vm.state.value.drawOfferPending)
    }

    @Test
    fun drawOfferSearchIsCheapAndNeverRunsOnTheCaller() = runTest(scheduler) {
        val limits = drawViewModel(scoreCp = 12).drawOfferLimits()
        assertEquals(DRAW_OFFER_DEPTH, limits.maxDepth)
        assertEquals(DRAW_OFFER_MILLIS, limits.maxMillis)

        // The search runs on the AI dispatcher, so the call returns before the
        // answer exists: nothing on Main was ever blocked. Unconfined makes the
        // distinction visible — the coroutine resumes in place on the AI
        // dispatcher, and the test thread never runs the analyse.
        val engine = ScriptedEngine(scoreCp = 12)
        val vm = GameViewModel(
            playerSide = Side.WHITE,
            difficulty = Difficulty.CASUAL,
            engine = engine,
            aiDispatcher = Dispatchers.Unconfined,
        )
        // Let the eval bar's own opening evaluation land first, so the delta
        // below counts the offer's search and nothing else.
        advanceUntilIdle()
        val callsBefore = engine.analyzeCalls
        vm.offerDraw()
        advanceUntilIdle()
        assertEquals(1, engine.analyzeCalls - callsBefore)
        assertEquals(GameResult.DrawAgreed, vm.state.value.result)
    }

    private fun drawViewModel(
        scoreCp: Int = 0,
        engine: ScriptedEngine = ScriptedEngine(scoreCp = scoreCp),
        aiDispatcher: kotlinx.coroutines.CoroutineDispatcher = scheduler,
    ) = GameViewModel(
        playerSide = Side.WHITE,
        difficulty = Difficulty.CASUAL,
        engine = engine,
        aiDispatcher = aiDispatcher,
    )

    /** Offers, lets the answer land, and hands back what the game ended as. */
    private fun offerAndResult(scoreCp: Int): GameResult? {
        val vm = drawViewModel(scoreCp = scoreCp)
        runTest(scheduler) {
            vm.offerDraw()
            advanceUntilIdle()
        }
        return vm.state.value.result
    }
}

/**
 * An engine with a scripted answer. Never OwnEngine in a unit test: the point
 * of these tests is the ViewModel's decision, not the search behind it.
 */
private class ScriptedEngine(
    private val scoreCp: Int = 0,
    private val noAnswer: Boolean = false,
    private val throwsOnAnalyze: Boolean = false,
) : Engine {
    /** How many times the draw-answer search was asked for a number. */
    var analyzeCalls = 0

    /** The FEN the search was given: the reference position, recorded. */
    var analyzeFen: String? = null

    /**
     * Every limit set the fake was asked to search with. The eval bar, the
     * draw answer and the review all analyse; a test that wants "the offer
     * never searched" must be able to tell which search it meant.
     */
    val analyzeLimitsSeen = mutableListOf<SearchLimits>()

    override fun findBestMove(positionFen: String, limits: SearchLimits): UciMove {
        val position = Position.fromFen(positionFen).getOrNull() ?: return UciMove("0000")
        val legal = position.generateLegalMoves()
        return UciMove(if (legal.isEmpty()) "0000" else legal.first().toUci())
    }

    fun askedWith(limits: SearchLimits): Boolean = analyzeLimitsSeen.any { it == limits }

    override fun analyze(positionFen: String, limits: SearchLimits): Analysis {
        analyzeCalls++
        analyzeFen = positionFen
        analyzeLimitsSeen += limits
        if (throwsOnAnalyze) throw IllegalStateException("engine has no answer")
        val move = if (noAnswer) UciMove("0000") else findBestMove(positionFen, limits)
        return Analysis(move, scoreCp)
    }
}
