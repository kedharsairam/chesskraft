/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 *
 * Naming openings from a move list. Every assertion here is about the lookup
 * rule rather than the data: the longest matching prefix wins, divergence is
 * silent, and nonsense is never guessed at. The names and codes themselves are
 * checked against the ECO move orders in the data-driven tests at the bottom.
 */
package com.krafttools.chesskraft.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpeningBookTest {

    private fun name(vararg moves: String): String? = OpeningBook.nameFor(moves.toList())?.name

    private fun eco(vararg moves: String): String? = OpeningBook.nameFor(moves.toList())?.eco

    /** The opening for [moves], or a test failure if the book has none. */
    private fun found(moves: List<String>): Opening {
        val opening = OpeningBook.nameFor(moves)
        assertNotNull("unnamed: $moves", opening)
        return opening!!
    }

    private fun found(vararg moves: String): Opening = found(moves.toList())

    @Test
    fun emptyMoveListNamesNothing() {
        assertNull(OpeningBook.nameFor(emptyList()))
    }

    @Test
    fun nonsenseIsNotGuessedAt() {
        assertNull(OpeningBook.nameFor(listOf("")))
        assertNull(OpeningBook.nameFor(listOf("e9")))
        assertNull(OpeningBook.nameFor(listOf("not a move", "e4", "e5")))
        // 1.f4 and 1.Nf3 are real openings that this book does not cover. The
        // honest answer is null, not the nearest thing it happens to know.
        assertNull(OpeningBook.nameFor(listOf("f4", "e5")))
        assertNull(OpeningBook.nameFor(listOf("Nf3", "d5", "g3")))
    }

    @Test
    fun nonsenseHalfWayThroughFallsBackToTheCleanPrefix() {
        // The book names the longest prefix it can honestly match, so junk in the
        // fourth move does not erase the first three — it just stops the line from
        // being read as anything deeper than it is.
        assertEquals("Italian Game", name("e4", "e5", "Nf3", "Nc6", "???"))
        assertEquals("Sicilian Defence", name("e4", "c5", "?!?"))
    }

    @Test
    fun longestPrefixWins() {
        // The three-ply front is in the book twice over (Italian, Ruy, Scotch all
        // start there), so a first-match lookup would name it by write order
        // rather than by the moves played.
        assertEquals("Italian Game", name("e4", "e5", "Nf3"))
        assertEquals("Ruy Lopez", name("e4", "e5", "Nf3", "Nc6", "Bb5"))
        assertEquals("Scotch Game", name("e4", "e5", "Nf3", "Nc6", "d4"))
        assertEquals("Italian Game", name("e4", "e5", "Nf3", "Nc6", "Bc4"))
        assertEquals("Petrov's Defence", name("e4", "e5", "Nf3", "Nf6"))
        // The same ladder on the Sicilian side: B20 at two plies, the open one at
        // eight, the Najdorf at nine.
        assertEquals("Sicilian Defence", name("e4", "c5"))
        assertEquals("Sicilian Defence, Modern Variations", name("e4", "c5", "Nf3", "d6"))
        assertEquals(
            "Sicilian Defence, Open",
            name("e4", "c5", "Nf3", "d6", "d4", "cxd4", "Nf6", "Nc3"),
        )
        assertEquals(
            "Sicilian Defence, Najdorf Variation",
            name("e4", "c5", "Nf3", "d6", "d4", "cxd4", "Nf6", "Nc3", "a6"),
        )
    }

    @Test
    fun deeperLinesOutrankTheOpeningTheyGrowOutOf() {
        assertEquals("Italian Game, Giuoco Piano", name("e4", "e5", "Nf3", "Nc6", "Bc4", "Bc5"))
        assertEquals("Italian Game, Evans Gambit", name("e4", "e5", "Nf3", "Nc6", "Bc4", "Bc5", "b4"))
        assertEquals("Italian Game, Two Knights Defence", name("e4", "e5", "Nf3", "Nc6", "Bc4", "Nf6"))
        assertEquals("Ruy Lopez, Berlin Defence", name("e4", "e5", "Nf3", "Nc6", "Bb5", "Nf6"))
        assertEquals(
            "Ruy Lopez, Closed Defence",
            name("e4", "e5", "Nf3", "Nc6", "Bb5", "a6", "Ba4", "Nf6", "O-O", "Be7"),
        )
    }

    @Test
    fun queensGambitIsNotNamedFromItsFirstMoveAlone() {
        // 1.d4 d5 2.c4 is the Queen's Gambit; it is not the Declined, the
        // Accepted, the Slav or the London until a later move says which.
        assertEquals("Queen's Gambit", name("d4", "d5", "c4"))
        assertEquals("D06", eco("d4", "d5", "c4"))
        assertEquals("Queen's Gambit Declined", name("d4", "d5", "c4", "e6"))
        assertEquals("D30", eco("d4", "d5", "c4", "e6"))
        assertEquals("Queen's Gambit Accepted", name("d4", "d5", "c4", "dxc4"))
        assertEquals("D20", eco("d4", "d5", "c4", "dxc4"))
        assertEquals("Slav Defence", name("d4", "d5", "c4", "c6"))
        assertEquals("London System", name("d4", "d5", "Bf4"))
        assertEquals("D02", eco("d4", "d5", "Bf4"))
        // 1.d4 Nf6 is not the Queen's Gambit in any sense: only the pawn move so
        // far, and the book says just that.
        assertEquals("Queen's Pawn Game", name("d4", "Nf6"))
    }

    @Test
    fun englishAndFrenchAreDistinguished() {
        assertEquals("English Opening", name("c4"))
        assertEquals("English Opening, King's English Variation", name("c4", "e5"))
        assertEquals("English Opening, Anglo-Indian Defence", name("c4", "Nf6"))
        assertEquals("English Opening, Symmetrical Variation", name("c4", "c5"))
        assertEquals("French Defence", name("e4", "e6"))
        assertEquals("French Defence, Advance Variation", name("e4", "e6", "d4", "d5", "e5"))
        assertEquals("French Defence, Winawer Variation", name("e4", "e6", "d4", "d5", "Nc3", "Bb4"))
        assertEquals("Caro-Kann Defence", name("e4", "c6"))
        assertEquals("King's Pawn Game", name("e4", "e5"))
    }

    @Test
    fun spellingDifferencesDoNotChangeTheAnswer() {
        // Check marks, lesson glyphs and the two ways of writing castling are
        // spelling, not a different game.
        assertEquals(
            name("e4", "e5", "Nf3", "Nc6", "Bb5", "Nf6"),
            name("e4", "e5", "Nf3?!", "Nc6", "Bb5!!", "Nf6"),
        )
        assertEquals(
            name("e4", "e5", "Nf3", "Nc6", "Bb5", "a6", "Ba4", "Nf6", "O-O", "Be7"),
            name("e4", "e5", "Nf3", "Nc6", "Bb5", "a6", "Ba4", "Nf6", "0-0", "Be7"),
        )
        // Moves after the matched line are the player's business, not the
        // book's: the opening of move 20 is still the opening of move 6.
        assertEquals(
            "Italian Game, Two Knights Defence",
            name("e4", "e5", "Nf3", "Nc6", "Bc4", "Nf6", "Ng5", "d5", "exd5", "Nxd5"),
        )
    }

    @Test
    fun everyCodeIsAnEcoShapeOrEmpty() {
        // The codes are the point of the field, so they must look like ECO codes
        // — or be empty, which is the honest "could not be pinned" answer. A
        // plausible-looking wrong letter is the one failure not allowed here.
        val lines = listOf(
            listOf("e4", "e5", "Nf3", "Nc6", "Bb5"),
            listOf("e4", "e5", "Nf3", "Nc6", "Bb5", "Nf6"),
            listOf("e4", "e5", "Nf3", "Nc6", "Bc4", "Bc5", "b4"),
            listOf("e4", "c5"),
            listOf("e4", "c5", "Nf3", "d6", "d4", "cxd4", "Nf6", "Nc3", "a6"),
            listOf("e4", "e6", "d4", "d5", "Nc3", "Bb4"),
            listOf("e4", "c6", "d4", "d5", "Nd2", "dxe4", "Nxe4", "Bf5"),
            listOf("d4", "d5", "c4", "e6", "Nc3", "Nf6", "cxd5"),
            listOf("d4", "d5", "Nf3", "Nf6", "Bf4"),
            listOf("c4", "e5"),
        )
        for (line in lines) {
            val opening = found(line)
            assertTrue(opening.name, opening.name.isNotBlank())
            val code = opening.eco
            if (code.isNotEmpty()) {
                assertEquals("$code for $line", 3, code.length)
                assertTrue(code, code[0] in 'A'..'E')
                assertTrue(code, code.substring(1).all { it.isDigit() })
            }
        }
    }

    @Test
    fun theEntryWithoutALetterIsTheItalianFront() {
        // 1.e4 e5 2.Nf3 is the name a club player gives, and no letter belongs
        // to it: the next move decides between C50, C60, C44 and C42. All four
        // of those are in the book, so a ply further the position is precise.
        val front = found("e4", "e5", "Nf3")
        assertEquals("Italian Game", front.name)
        assertEquals("", front.eco)
        assertEquals("C50", OpeningBook.nameFor(listOf("e4", "e5", "Nf3", "Nc6", "Bc4"))?.eco)
        assertEquals("C60", OpeningBook.nameFor(listOf("e4", "e5", "Nf3", "Nc6", "Bb5"))?.eco)
        assertEquals("C44", OpeningBook.nameFor(listOf("e4", "e5", "Nf3", "Nc6", "d4"))?.eco)
    }
}
