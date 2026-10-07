/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft.ui.screens

import com.krafttools.chesskraft.domain.MoveVerdict
import com.krafttools.chesskraft.domain.Position
import com.krafttools.chesskraft.domain.ReviewedMove
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The review board's move markers.
 *
 * Both of these exist to turn a SAN string into two squares, and both have the
 * same trap: the string only means anything in the position it was *played
 * from*. Resolving it against the position the board is now showing finds
 * nothing, returns null, and the board then draws a perfectly correct position
 * with no marker on it — which looks like a styling bug and is actually a
 * lookup against the wrong position. The device found this; these tests are
 * here so it cannot come back.
 */
class ReviewScrubTest {

    private val start: Position = checkNotNull(Position.fromFen(StartFen).getOrNull()) {
        "the start position must parse"
    }

    /** After `e4`: Black to move. */
    private val afterE4: Position = checkNotNull(
        Position.fromFen("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1")
            .getOrNull(),
    ) { "the post-e4 position must parse" }

    /** After `e4 Nc6`: the position the review board shows when the knight is on c6. */
    private val afterKnightOut: Position = checkNotNull(
        Position.fromFen("r1bqkbnr/pppppppp/2n5/8/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 1 2")
            .getOrNull(),
    ) { "the post-Nc6 position must parse" }

    @Test
    fun `a played move resolves in the position it was played from`() {
        val move = resolveSan(start, "e4")
        assertEquals(12, move?.from) // e2
        assertEquals(28, move?.to) // e4
    }

    @Test
    fun `the same move does not resolve in the position it produced`() {
        // The trap, pinned. This returned a non-null move once, and the review
        // board shipped with a silent null instead of a highlight.
        assertNull(resolveSan(afterKnightOut, "Nc6"))
    }

    @Test
    fun `black's reply resolves in the position white's move produced`() {
        val move = resolveSan(afterE4, "e5")
        assertEquals(52, move?.from) // e7
        assertEquals(36, move?.to) // e5
    }

    @Test
    fun `white's knight resolves once the black knight is out of the way`() {
        val move = resolveSan(afterKnightOut, "Nc3")
        assertEquals(1, move?.from) // b1
        assertEquals(18, move?.to) // c3
    }

    @Test
    fun `a nonsense SAN resolves to nothing rather than to a wrong square`() {
        assertNull(resolveSan(start, "Qz9"))
        assertNull(resolveSan(start, ""))
        assertNull(resolveSan(start, null))
    }

    @Test
    fun `no engine ring when the played move was the best one`() {
        val best = ReviewedMove(
            ply = 1,
            number = 1,
            san = "e4",
            verdict = MoveVerdict.BEST,
            cpLoss = 0,
            bestSan = "e4",
        )
        assertNull(engineAlternative(start, best))
    }

    @Test
    fun `the engine's better move is resolved in the position it could have been played`() {
        val played = ReviewedMove(
            ply = 1,
            number = 1,
            san = "Nf3",
            verdict = MoveVerdict.MISTAKE,
            cpLoss = 220,
            bestSan = "e4",
        )
        val move = engineAlternative(start, played)
        assertEquals(12, move?.from) // e2
        assertEquals(28, move?.to) // e4
    }

    @Test
    fun `an unknown engine move resolves to nothing`() {
        val played = ReviewedMove(
            ply = 1,
            number = 1,
            san = "Nf3",
            verdict = MoveVerdict.MISTAKE,
            cpLoss = 220,
            bestSan = null,
        )
        assertNull(engineAlternative(start, played))
    }

    private companion object {
        const val StartFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
    }
}