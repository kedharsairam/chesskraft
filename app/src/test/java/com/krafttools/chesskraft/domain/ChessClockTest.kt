/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChessClockTest {

    @Test
    fun burnsOnlyTheSideToMove() {
        val clock = ChessClock(60_000L, 60_000L)
        assertNull(clock.tick(Side.WHITE, 1_500L))
        assertEquals(58_500L, clock.whiteMs)
        assertEquals(60_000L, clock.blackMs)
    }

    @Test
    fun flagTheInstantABankEmpties() {
        val clock = ChessClock(1_000L, 60_000L)
        assertEquals(Side.WHITE, clock.tick(Side.WHITE, 1_000L))
        assertEquals(0L, clock.whiteMs)
    }

    @Test
    fun overshootClampsInsteadOfGoingNegative() {
        val clock = ChessClock(60_000L, 500L)
        assertEquals(Side.BLACK, clock.tick(Side.BLACK, 5_000L))
        assertEquals(0L, clock.blackMs)
    }
}
