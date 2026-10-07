/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft.presentation

import com.krafttools.chesskraft.domain.GameResult
import com.krafttools.chesskraft.domain.Side
import com.krafttools.chesskraft.domain.parseSquare
import com.krafttools.chesskraft.engine.Difficulty
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
}
