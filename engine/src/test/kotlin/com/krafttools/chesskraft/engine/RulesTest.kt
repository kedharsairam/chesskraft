/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 *
 * Rules edge cases: en passant (including the pin where capturing exposes
 * the king), castling rights and transit attacks, all four promotions,
 * mate/stalemate detection, the three draws, FEN round-trips, and
 * make/unmake symmetry with Zobrist key restoration.
 */
package com.krafttools.chesskraft.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTest {
    private fun pos(fen: String): Position = Position.fromFen(fen)

    private fun legalUcis(fen: String): Set<String> {
        val p = pos(fen)
        val out = IntArray(MoveGen.BUFFER_SIZE)
        val n = MoveGen.legalMoves(p, out)
        val ucis = HashSet<String>()
        for (i in 0 until n) ucis.add(MoveGen.moveToUci(out[i]))
        return ucis
    }

    @Test
    fun enPassantPinIsIllegal() {
        // Black just pushed d7-d5. Capturing en passant would lift both
        // pawns off rank 5 and open the a5-h5 line to the rook: the white
        // king on h5 would be in check, so e5d6 is illegal while e5e6
        // (d5 still blocks) is fine.
        val moves = legalUcis("4k3/8/8/r2pP2K/8/8/8/8 w - d6 0 1")
        assertFalse(moves.contains("e5d6"))
        assertTrue(moves.contains("e5e6"))
    }

    @Test
    fun enPassantCaptureWorks() {
        val moves = legalUcis("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1")
        assertTrue(moves.contains("e5d6"))
        val p = pos("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1")
        val m = MoveGen.parseUci(p, "e5d6")
        assertTrue(m != NO_MOVE && isEpCapture(m))
        assertTrue(p.makeMove(m))
        assertEquals(EMPTY, p.board[squareFromName("d5", 0)])
        assertEquals(WP, p.board[squareFromName("d6", 0)])
        p.unmakeMove()
        assertEquals("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1", p.toFen())
    }

    @Test
    fun castlingBlockedByTransitAttack() {
        // Black bishop on c4 attacks f1: kingside castling is illegal,
        // queenside castling is still fine.
        val moves = legalUcis("r3k2r/8/8/8/2b5/8/8/R3K2R w KQkq - 0 1")
        assertFalse(moves.contains("e1g1"))
        assertTrue(moves.contains("e1c1"))
    }

    @Test
    fun castlingBlockedWhenInCheck() {
        val moves = legalUcis("r3k2r/8/8/8/8/8/4r3/R3K2R w KQkq - 0 1")
        assertFalse(moves.contains("e1g1"))
        assertFalse(moves.contains("e1c1"))
    }

    @Test
    fun castlingMovesRook() {
        val p = pos("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        val m = MoveGen.parseUci(p, "e1g1")
        assertTrue(m != NO_MOVE && isCastle(m))
        assertTrue(p.makeMove(m))
        assertEquals(WK, p.board[squareFromName("g1", 0)])
        assertEquals(WR, p.board[squareFromName("f1", 0)])
        assertEquals(0, p.castling and (CASTLE_WK or CASTLE_WQ))
        p.unmakeMove()
        assertEquals("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1", p.toFen())
    }

    @Test
    fun rookMoveClearsOwnRightOnly() {
        val p = pos("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        val m = MoveGen.parseUci(p, "h1h2")
        assertTrue(p.makeMove(m))
        assertEquals(0, p.castling and CASTLE_WK)
        assertTrue((p.castling and CASTLE_WQ) != 0)
        assertTrue((p.castling and (CASTLE_BK or CASTLE_BQ)) != 0)
        p.unmakeMove()
        assertEquals("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1", p.toFen())
    }

    @Test
    fun allFourPromotionsGenerated() {
        // b7 takes the rook on a8, or pushes quietly to b8: four ways each.
        val moves = legalUcis("r3k3/1P6/8/8/8/8/8/4K3 w - - 0 1")
        assertTrue(moves.contains("b7a8q"))
        assertTrue(moves.contains("b7a8r"))
        assertTrue(moves.contains("b7a8b"))
        assertTrue(moves.contains("b7a8n"))
        assertTrue(moves.contains("b7b8q"))
        assertTrue(moves.contains("b7b8r"))
        assertTrue(moves.contains("b7b8b"))
        assertTrue(moves.contains("b7b8n"))
    }

    @Test
    fun foolsMateIsCheckmate() {
        val p = pos("rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3")
        assertTrue(MoveGen.isCheckmate(p))
        assertFalse(MoveGen.isStalemate(p))
        assertEquals(0, MoveGen.countLegal(p))
    }

    @Test
    fun classicStalemate() {
        val p = pos("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1")
        assertTrue(MoveGen.isStalemate(p))
        assertFalse(MoveGen.isCheckmate(p))
    }

    @Test
    fun fiftyMoveDraw() {
        assertTrue(pos("7k/5Q2/6K1/8/8/8/8/8 b - - 100 120").isFiftyMove())
        assertFalse(pos("7k/5Q2/6K1/8/8/8/8/8 b - - 99 120").isFiftyMove())
    }

    @Test
    fun threefoldRepetition() {
        // Quiet rook shuffle down the board: no checks, no captures,
        // the start key comes back a third time after eight half-moves.
        val p = pos("7k/8/8/8/8/8/R7/K7 w - - 0 1")
        val seq = arrayOf("a2b2", "h8g8", "b2a2", "g8h8", "a2b2", "h8g8", "b2a2", "g8h8")
        for (uci in seq) {
            val m = MoveGen.parseUci(p, uci)
            assertTrue(m != NO_MOVE)
            assertTrue(p.makeMove(m))
        }
        assertTrue(p.isThreefold())
    }

    @Test
    fun insufficientMaterial() {
        assertTrue(pos("8/8/4k3/8/8/3K4/8/8 w - - 0 1").hasInsufficientMaterial())
        assertTrue(pos("8/5n2/4k3/8/8/3K4/8/8 w - - 0 1").hasInsufficientMaterial())
        assertTrue(pos("8/5b2/4k3/8/8/3K4/8/8 w - - 0 1").hasInsufficientMaterial())
        // Same-colour bishops only: draw.
        assertTrue(pos("5b1k/8/8/8/8/8/8/K5B1 w - - 0 1").hasInsufficientMaterial())
        // Opposite-colour bishops: mate is possible, not a draw.
        // f8 is light-squared, h1 is dark-squared.
        assertFalse(pos("5b1k/8/8/8/8/8/8/K6B w - - 0 1").hasInsufficientMaterial())
        // Rook or pawn on board: not a draw.
        assertFalse(pos("8/8/4k3/8/8/3K4/5R2/8 w - - 0 1").hasInsufficientMaterial())
        assertFalse(pos("8/8/4k3/8/8/3K4/5P2/8 w - - 0 1").hasInsufficientMaterial())
        // Knight each side: mate possible, not an automatic draw.
        assertFalse(pos("5n1k/8/8/8/8/8/8/K5N1 w - - 0 1").hasInsufficientMaterial())
    }

    @Test
    fun fenRoundTrip() {
        val fens = arrayOf(
            Position.STARTPOS,
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
            "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1",
            "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8",
            "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
            "4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1",
        )
        for (fen in fens) {
            assertEquals(fen, pos(fen).toFen())
        }
    }

    @Test
    fun makeUnmakeRestoresEverything() {
        val starts = arrayOf(
            Position.STARTPOS,
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
            "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1",
        )
        for (fen in starts) {
            val p = pos(fen)
            val key0 = p.key
            val buf = IntArray(MoveGen.BUFFER_SIZE)
            val n = MoveGen.generateAll(p, buf, 0)
            var tried = 0
            for (i in 0 until n) {
                p.makeMove(buf[i])
                if (tried < 4) {
                    // Nested make/unmake one ply deeper.
                    val buf2 = IntArray(MoveGen.BUFFER_SIZE)
                    val n2 = MoveGen.generateAll(p, buf2, 0)
                    if (n2 > 0) {
                        val mid = p.key
                        p.makeMove(buf2[0])
                        p.unmakeMove()
                        assertEquals(mid, p.key)
                    }
                    tried++
                }
                p.unmakeMove()
                assertEquals(fen, p.toFen())
                assertEquals(key0, p.key)
            }
        }
    }

    @Test
    fun zobristKeyMatchesRecompute() {
        val p = pos(Position.STARTPOS)
        val rng = java.util.Random(42L)
        repeat(200) {
            val buf = IntArray(MoveGen.BUFFER_SIZE)
            val n = MoveGen.generateAll(p, buf, 0)
            if (n == 0) {
                p.setFen(Position.STARTPOS)
                return@repeat
            }
            p.makeMove(buf[rng.nextInt(n)])
            assertEquals(p.computeKey(), p.key)
        }
        while (p.historySize() > 1) p.unmakeMove()
        assertEquals(Position.STARTPOS, p.toFen())
        assertEquals(p.computeKey(), p.key)
    }
}
