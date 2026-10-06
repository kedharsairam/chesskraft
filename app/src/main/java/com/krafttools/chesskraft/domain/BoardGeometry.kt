/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft.domain

/**
 * Board geometry. Pure Kotlin — no Compose, no dp — so it is unit-testable on
 * the JVM. The UI passes pixel sizes in and gets pixels back.
 *
 * Display mapping: column 0 is the a-file when unflipped (White at the
 * bottom), the h-file when flipped. Row 0 is the top edge of the board.
 */
data class PointF(val x: Float, val y: Float)

data class RectF(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun contains(x: Float, y: Float): Boolean =
        x >= left && x <= right && y >= top && y <= bottom
}

/** One coordinate glyph inside a square corner. */
data class CoordLabel(val square: Int, val text: String, val isFile: Boolean)

object BoardGeometry {
    fun squareSize(boardPx: Float): Float = boardPx / 8f

    /** Screen (col, row) for a square, honouring flip. */
    fun displayCell(square: Int, flipped: Boolean): Pair<Int, Int> {
        val file = fileOf(square)
        val rank = rankOf(square)
        val col = if (flipped) 7 - file else file
        val row = if (flipped) rank else 7 - rank
        return col to row
    }

    /** Square for a screen (col, row), honouring flip. */
    fun squareForCell(col: Int, row: Int, flipped: Boolean): Int? {
        if (col !in 0..7 || row !in 0..7) return null
        val file = if (flipped) 7 - col else col
        val rank = if (flipped) row else 7 - row
        return squareAt(file, rank)
    }

    fun squareRect(square: Int, boardPx: Float, flipped: Boolean): RectF {
        val size = squareSize(boardPx)
        val (col, row) = displayCell(square, flipped)
        val left = col * size
        val top = row * size
        return RectF(left, top, left + size, top + size)
    }

    fun squareCenter(square: Int, boardPx: Float, flipped: Boolean): PointF {
        val rect = squareRect(square, boardPx, flipped)
        return PointF((rect.left + rect.right) / 2f, (rect.top + rect.bottom) / 2f)
    }

    /**
     * Hit-test with [slopPx] tolerance (the UI passes 8dp in pixels): a finger
     * that lands just off the board edge still counts as the edge square.
     * Returns null only when the point is outside the board plus slop.
     */
    fun hitTest(x: Float, y: Float, boardPx: Float, flipped: Boolean, slopPx: Float): Int? {
        if (x < -slopPx || y < -slopPx || x > boardPx + slopPx || y > boardPx + slopPx) {
            return null
        }
        val size = squareSize(boardPx)
        val col = (x / size).toInt().coerceIn(0, 7)
        val row = (y / size).toInt().coerceIn(0, 7)
        return squareForCell(col, row, flipped)
    }

    /**
     * File letters along the bottom rank, rank digits along the left file.
     * Corner squares carry both — the board draws the letter bottom-right
     * and the digit top-left.
     */
    fun coordinateLabels(flipped: Boolean): List<CoordLabel> {
        val labels = ArrayList<CoordLabel>(18)
        for (file in 0..7) {
            val rank = if (flipped) 7 else 0
            labels.add(CoordLabel(squareAt(file, rank), "${'a' + file}", isFile = true))
        }
        for (rank in 0..7) {
            val file = if (flipped) 7 else 0
            labels.add(CoordLabel(squareAt(file, rank), "${'1' + rank}", isFile = false))
        }
        return labels
    }
}
