/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Plies are what the save stores; moves are what a score sheet counts.
 *
 * Printing the ply count said `1.e4 Nc6` was two moves, which is wrong in the
 * one place a learner is most likely to be checking the app against their own
 * understanding of chess.
 */
class FullMovesTest {

    @Test
    fun `no plies is no moves`() {
        assertEquals(0, fullMoves(0))
    }

    @Test
    fun `one ply is one move`() {
        assertEquals(1, fullMoves(1))
    }

    @Test
    fun `a move and a reply are still one move`() {
        assertEquals(1, fullMoves(2))
    }

    @Test
    fun `white's second move starts the second move`() {
        assertEquals(2, fullMoves(3))
        assertEquals(2, fullMoves(4))
    }

    @Test
    fun `the phrase agrees with the number`() {
        assertEquals("no moves", movesPhrase(0))
        assertEquals("1 move", movesPhrase(2))
        assertEquals("2 moves", movesPhrase(3))
        assertEquals("12 moves", movesPhrase(24))
    }
}
