/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BoardGeometryTest {

    @Test
    fun squareSizeIsEighthOfBoard() {
        assertEquals(45f, BoardGeometry.squareSize(360f), 0.001f)
    }

    @Test
    fun unflippedCorners() {
        // a1 bottom-left, h8 top-right when White is at the bottom.
        assertEquals(0 to 7, BoardGeometry.displayCell(0, false))
        assertEquals(7 to 0, BoardGeometry.displayCell(63, false))
        // e4 (square 28): file 4, rank 3 -> col 4, row 4.
        assertEquals(4 to 4, BoardGeometry.displayCell(28, false))
    }

    @Test
    fun flippedCornersMirror() {
        assertEquals(7 to 0, BoardGeometry.displayCell(0, true))
        assertEquals(0 to 7, BoardGeometry.displayCell(63, true))
    }

    @Test
    fun cellRoundTripBothOrientations() {
        for (sq in 0..63) {
            for (flipped in listOf(false, true)) {
                val (col, row) = BoardGeometry.displayCell(sq, flipped)
                assertEquals(sq, BoardGeometry.squareForCell(col, row, flipped))
            }
        }
    }

    @Test
    fun rectAndCenterAgree() {
        val rect = BoardGeometry.squareRect(28, 360f, false)
        assertEquals(180f, rect.left, 0.001f)
        assertEquals(180f, rect.top, 0.001f)
        val center = BoardGeometry.squareCenter(28, 360f, false)
        assertEquals(202.5f, center.x, 0.001f)
        assertEquals(202.5f, center.y, 0.001f)
    }

    @Test
    fun hitTestCenters() {
        assertEquals(28, BoardGeometry.hitTest(202.5f, 202.5f, 360f, false, 8f))
        assertEquals(0, BoardGeometry.hitTest(22.5f, 337.5f, 360f, false, 8f))
    }

    @Test
    fun hitTestSlopCatchesEdgeMisses() {
        // 4px off the left edge is still the a-file with 8px slop.
        assertEquals(0, BoardGeometry.hitTest(-4f, 337.5f, 360f, false, 8f))
        // 20px off is a genuine miss.
        assertNull(BoardGeometry.hitTest(-20f, 337.5f, 360f, false, 8f))
        assertNull(BoardGeometry.hitTest(200f, 400f, 360f, false, 8f))
    }

    @Test
    fun hitTestRespectsFlip() {
        // Bottom-left on screen is h1 (63) when flipped.
        assertEquals(63, BoardGeometry.hitTest(22.5f, 337.5f, 360f, true, 8f))
    }

    @Test
    fun coordinateLabelsSitOnEdges() {
        val unflipped = BoardGeometry.coordinateLabels(false)
        assertEquals(16, unflipped.size)
        val bySquare = unflipped.groupBy { it.square }
        // a1 carries both the file letter and the rank digit.
        assertEquals(
            setOf("a", "1"),
            bySquare[0]?.map { it.text }?.toSet(),
        )
        assertEquals(
            listOf(true, false).sorted(),
            bySquare[0]?.map { it.isFile }?.sorted(),
        )
        assertEquals("h", bySquare[7]?.single { it.isFile }?.text)
        assertEquals("8", bySquare[56]?.single { !it.isFile }?.text)
        val flipped = BoardGeometry.coordinateLabels(true)
        val flippedBy = flipped.groupBy { it.square }
        assertEquals(setOf("h", "8"), flippedBy[63]?.map { it.text }?.toSet())
        assertEquals(setOf("a"), flippedBy[56]?.map { it.text }?.toSet())
        assertEquals(setOf("1"), flippedBy[7]?.map { it.text }?.toSet())
    }

    @Test
    fun squareNames() {
        assertEquals("a1", squareName(0))
        assertEquals("e4", squareName(28))
        assertEquals("h8", squareName(63))
        assertEquals(28, parseSquare("e4"))
        assertNotNull(parseUci("e2e4"))
        assertEquals("e7e8q", parseUci("e7e8q")?.toUci())
        assertNull(parseSquare("i9"))
        assertNull(parseUci("oops"))
    }
}
