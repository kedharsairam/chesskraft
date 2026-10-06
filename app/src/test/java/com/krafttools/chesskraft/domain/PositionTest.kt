/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PositionTest {

    private fun perft(position: Position, depth: Int): Long {
        if (depth == 0) return 1
        var nodes = 0L
        for (move in position.generateLegalMoves()) {
            nodes += perft(position.makeMove(move), depth - 1)
        }
        return nodes
    }

    @Test
    fun startposPerft() {
        val start = Position.start()
        assertEquals(20, perft(start, 1))
        assertEquals(400, perft(start, 2))
        assertEquals(8902, perft(start, 3))
    }

    @Test
    fun kiwipetePerft() {
        val kiwipete = Position.fromFen(
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
        ).getOrNull() ?: error("bad test FEN")
        assertEquals(48, perft(kiwipete, 1))
        assertEquals(2039, perft(kiwipete, 2))
    }

    @Test
    fun fenRoundTrip() {
        val fens = listOf(
            Position.START_FEN,
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
            "8/8/8/3pP3/8/8/8/4K2k w - d6 0 1",
            "r3k2r/8/8/8/8/8/8/R3K2R b Kq - 1 7",
        )
        for (fen in fens) {
            val position = Position.fromFen(fen).getOrNull() ?: error("bad test FEN $fen")
            assertEquals(fen, position.toFen())
        }
    }

    @Test
    fun badFenIsAResultNotAThrow() {
        assertTrue(Position.fromFen("nope").isFailure)
        assertTrue(Position.fromFen("8/8/8/8/8/8/8/8 w - - 0").isFailure)
    }

    @Test
    fun castlingBothSides() {
        val position = Position.fromFen(
            "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1",
        ).getOrNull() ?: error("bad test FEN")
        val uci = position.generateLegalMoves().map { it.toUci() }
        assertTrue(uci.contains("e1g1"))
        assertTrue(uci.contains("e1c1"))
    }

    @Test
    fun castlingBlockedThroughCheck() {
        // Bf2 eyes e1: neither White castle is legal.
        val position = Position.fromFen(
            "r3k2r/8/8/8/8/8/5b2/R3K2R w KQkq - 0 1",
        ).getOrNull() ?: error("bad test FEN")
        val uci = position.generateLegalMoves().map { it.toUci() }
        assertFalse(uci.contains("e1g1"))
        assertFalse(uci.contains("e1c1"))
    }

    @Test
    fun enPassantPinIsIllegal() {
        // Ka5, Pe5, pd5, rh5: exd6 e.p. uncovers the rook, so only e6 plays.
        val position = Position.fromFen(
            "8/8/8/K2pP2r/8/8/8/5k2 w - d6 0 1",
        ).getOrNull() ?: error("bad test FEN")
        val fromE5 = position.generateLegalMoves()
            .filter { it.from == parseSquare("e5") }
            .map { it.toUci() }
        assertEquals(listOf("e5e6"), fromE5)
    }

    @Test
    fun enPassantCaptureWorks() {
        val position = Position.fromFen(
            "8/8/8/3pP3/8/8/8/4K2k w - d6 0 1",
        ).getOrNull() ?: error("bad test FEN")
        val moves = position.generateLegalMoves().filter {
            it.from == parseSquare("e5")
        }.map { it.toUci() }
        assertTrue(moves.contains("e5d6"))
        val next = position.makeMove(parseUci("e5d6") ?: error("uci"))
        assertEquals(0, next.board[parseSquare("d5") ?: error("sq")])
    }

    @Test
    fun promotionOffersAllFour() {
        val position = Position.fromFen(
            "8/P7/8/8/8/1k6/8/4K3 w - - 0 1",
        ).getOrNull() ?: error("bad test FEN")
        val promos = position.generateLegalMoves()
            .filter { it.from == parseSquare("a7") }
            .map { it.toUci() }
            .sorted()
        assertEquals(listOf("a7a8b", "a7a8n", "a7a8q", "a7a8r"), promos)
    }

    @Test
    fun checkmateDetected() {
        val position = Position.fromFen(
            "7k/6Q1/6K1/8/8/8/8/8 b - - 0 1",
        ).getOrNull() ?: error("bad test FEN")
        assertTrue(position.isInCheck())
        assertTrue(position.generateLegalMoves().isEmpty())
    }

    @Test
    fun stalemateDetected() {
        val position = Position.fromFen(
            "7k/5Q2/6K1/8/8/8/8/8 b - - 0 1",
        ).getOrNull() ?: error("bad test FEN")
        assertFalse(position.isInCheck())
        assertTrue(position.generateLegalMoves().isEmpty())
    }

    @Test
    fun startIsNotCheckOrMate() {
        val start = Position.start()
        assertFalse(start.isInCheck())
        assertFalse(start.generateLegalMoves().isEmpty())
    }

    @Test
    fun insufficientMaterial() {
        fun material(fen: String): Boolean =
            (Position.fromFen(fen).getOrNull() ?: error("bad FEN")).hasInsufficientMaterial()
        assertTrue(material("k7/8/8/8/8/8/8/K7 w - - 0 1"))
        assertTrue(material("kn6/8/8/8/8/8/8/K7 w - - 0 1"))
        assertFalse(material(Position.START_FEN))
        assertFalse(material("k7/8/8/8/8/8/5R2/K7 w - - 0 1"))
    }

    @Test
    fun sanBasics() {
        val start = Position.start()
        assertEquals("e4", start.toSan(parseUci("e2e4") ?: error("uci")))
        assertEquals("Nf3", start.toSan(parseUci("g1f3") ?: error("uci")))
        val afterWhite = start.makeMove(parseUci("e2e4") ?: error("uci"))
        assertEquals("e5", afterWhite.toSan(parseUci("e7e5") ?: error("uci")))
        val afterBlack = afterWhite.makeMove(parseUci("e7e5") ?: error("uci"))
        assertEquals("Nf3", afterBlack.toSan(parseUci("g1f3") ?: error("uci")))
    }

    @Test
    fun sanCaptureAndCheck() {
        // Scholar's mate end: Qxf7#.
        val position = Position.fromFen(
            "r1bqkb1r/pppp1ppp/2n2n2/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 4 4",
        ).getOrNull() ?: error("bad test FEN")
        assertEquals("Qxf7#", position.toSan(parseUci("h5f7") ?: error("uci")))
    }

    @Test
    fun describeSquareReadsAloud() {
        val start = Position.start()
        assertEquals("e4, empty", describeSquare(start, parseSquare("e4") ?: error("sq")))
        assertEquals("e2, white pawn", describeSquare(start, parseSquare("e2") ?: error("sq")))
        assertEquals("b8, black knight", describeSquare(start, parseSquare("b8") ?: error("sq")))
        assertNull(parseSquare("z9"))
    }
}
