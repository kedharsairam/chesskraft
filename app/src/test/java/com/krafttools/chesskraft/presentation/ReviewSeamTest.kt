/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft.presentation

import com.krafttools.chesskraft.domain.Position
import com.krafttools.chesskraft.domain.Side
import com.krafttools.chesskraft.domain.parseSquare
import com.krafttools.chesskraft.engine.Difficulty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The hand-off a review screen will read: the whole game, and the limits to
 * grade it with. Nothing here builds a screen — it only guards the two shapes
 * that screen will be written against.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReviewSeamTest {
    private val scheduler = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(scheduler)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(difficulty: Difficulty = Difficulty.CASUAL) = GameViewModel(
        playerSide = Side.WHITE,
        difficulty = difficulty,
        engine = FakeEngine(seed = 11L),
        aiDispatcher = scheduler,
    )

    @Test
    fun reviewLimitsAreDeepEnoughToGradeAndCapped() {
        val limits = viewModel().reviewLimits()
        assertEquals(6, limits.maxDepth)
        assertEquals(1500L, limits.maxMillis)
        // A review is a fact about the game, not about the difficulty it was
        // played at: the limits must not move with the picker.
        assertEquals(
            viewModel(difficulty = Difficulty.RELAXED).reviewLimits(),
            viewModel(difficulty = Difficulty.TOUGH).reviewLimits(),
        )
    }

    @Test
    fun exportHistoryCoversEveryPositionAndPly() = runTest(scheduler) {
        val vm = viewModel()
        vm.onTap(parseSquare("e2") ?: error("sq"))
        vm.onTap(parseSquare("e4") ?: error("sq"))
        advanceUntilIdle()

        val (fens, sans) = vm.exportHistory()
        assertEquals(Position.start().toFen(), fens.first())
        assertEquals(sans.size + 1, fens.size)
        assertEquals("e4", sans.first())
        assertEquals(sans, vm.state.value.sans)
        // Every FEN in the trail is a position the domain can read back, and
        // the trail starts at the initial position rather than after move one.
        assertTrue(fens.all { it.isNotBlank() })
        val unparseable = fens.filter { !Position.fromFen(it).isSuccess }
        assertTrue(
            "history holds a FEN the domain cannot parse: $unparseable",
            unparseable.isEmpty(),
        )
    }

    @Test
    fun exportHistoryTracksUndoAndNewGame() = runTest(scheduler) {
        val vm = viewModel()
        vm.onTap(parseSquare("d2") ?: error("sq"))
        vm.onTap(parseSquare("d4") ?: error("sq"))
        advanceUntilIdle()
        vm.undo()
        assertEquals(0, vm.exportHistory().second.size)
        assertEquals(1, vm.exportHistory().first.size)

        vm.onTap(parseSquare("d2") ?: error("sq"))
        vm.onTap(parseSquare("d4") ?: error("sq"))
        advanceUntilIdle()
        vm.newGame()
        advanceUntilIdle()
        assertEquals(0, vm.exportHistory().second.size)
        assertEquals(listOf(Position.start().toFen()), vm.exportHistory().first)
    }
}