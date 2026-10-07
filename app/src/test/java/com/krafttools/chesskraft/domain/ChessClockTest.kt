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

    // -- Restore ---------------------------------------------------------

    @Test
    fun restorePutsBothBanksBack() {
        val clock = ChessClock(60_000L, 60_000L)
        clock.restore(42_000L, 7_500L)
        assertEquals(42_000L, clock.whiteMs)
        assertEquals(7_500L, clock.blackMs)
    }

    /**
     * The point of restoring rather than offsetting: a resumed game must not
     * hand the side that already spent time a full bank back.
     */
    @Test
    fun tickingContinuesFromTheRestoredBanks() {
        val clock = ChessClock(60_000L, 60_000L)
        clock.restore(10_000L, 60_000L)
        assertNull(clock.tick(Side.WHITE, 4_000L))
        assertEquals(6_000L, clock.whiteMs)
        assertEquals(60_000L, clock.blackMs)
    }

    @Test
    fun forSideReadsTheRestoredBank() {
        val clock = ChessClock(60_000L, 60_000L)
        clock.restore(1L, 2L)
        assertEquals(1L, clock.forSide(Side.WHITE))
        assertEquals(2L, clock.forSide(Side.BLACK))
    }

    @Test
    fun restoreClampsInsteadOfCountingUpward() {
        val clock = ChessClock(60_000L, 60_000L)
        clock.restore(-500L, -1L)
        assertEquals(0L, clock.whiteMs)
        assertEquals(0L, clock.blackMs)
    }

    /** A restored bank at exactly zero has already run out, and must say so. */
    @Test
    fun aRestoredEmptyBankFlagsOnItsFirstTick() {
        val clock = ChessClock(60_000L, 60_000L)
        clock.restore(0L, 30_000L)
        assertEquals(Side.WHITE, clock.tick(Side.WHITE, 100L))
        assertEquals(0L, clock.whiteMs)
    }
}
