/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 *
 * Naming the opening a game is being played in, the way a club player would.
 * Pure data and a lookup: no board, no search, no Android. The book is a list
 * of move prefixes and the name each one earns, and the only question is which
 * prefix to believe — see [OpeningBook.nameFor].
 *
 * Everything here is a prefix of legal moves from the start position, so a game
 * that walked through one of these lines is in that opening, and a game that
 * diverged is in none of them. The codes are the standard Encyclopaedia of
 * Chess Openings letters. An entry whose letter could not be pinned honestly
 * carries an empty string rather than a plausible-looking guess: [Opening.eco]
 * is shown to nobody in v1 and a wrong code is worse than no code.
 */
package com.krafttools.chesskraft.domain

/**
 * One named opening, and the moves that get there.
 *
 * [moves] is SAN-ish and only has to be *san-shaped*: [OpeningBook.nameFor]
 * compares it ignoring check and mate marks, annotation glyphs, and the two
 * ways a PGN writes castling, so "Bb5" here matches a game that recorded
 * "Bb5+" and a "0-0" in the list matches "O-O".
 */
data class Opening(val name: String, val eco: String, val moves: List<String>)

/**
 * The openings a beginner meets in the first six to ten moves, and the lookup
 * that names them.
 *
 * Scope is deliberately narrow. A real opening book is thousands of lines of
 * theory; this one exists to answer "what am I playing?" at the point where a
 * new player still asks it, so it stops where the lines stop being recognisable
 * from memory.
 */
object OpeningBook {

    /**
     * The book, shortest entry first only for readability — the lookup does not
     * depend on the order.
     *
     * Two entries carry no ECO letter on purpose:
     *
     * - `Italian Game` at `1.e4 e5 2.Nf3` is the name a club player gives the
     *   position, but no single letter belongs to it. The next move decides:
     *   `3.Bc4` is the Italian Game proper (C50), `3.Bb5` is the Ruy Lopez
     *   (C60), `3.d4` is the Scotch Game (C44) and `3...Nf6` is Petrov (C42).
     *   Those four entries below name the position again the moment the choice
     *   is made, so this one stays deliberately unlettered rather than claiming
     *   a code that would be wrong for three of the four continuations.
     *
     * Every other letter was checked against the move order as it is listed in
     * the ECO, not guessed from the name.
     */
    private val BOOK: List<Opening> = listOf(
        // -- 1.e4 ----------------------------------------------------------
        Opening("King's Pawn Opening", "B00", listOf("e4")),
        Opening("King's Pawn Game", "C20", listOf("e4", "e5")),
        Opening("Italian Game", "", listOf("e4", "e5", "Nf3")),
        Opening("Petrov's Defence", "C42", listOf("e4", "e5", "Nf3", "Nf6")),
        Opening("Three Knights Opening", "C46", listOf("e4", "e5", "Nf3", "Nc6", "Nc3")),
        Opening("Scotch Game", "C44", listOf("e4", "e5", "Nf3", "Nc6", "d4")),
        Opening("Italian Game", "C50", listOf("e4", "e5", "Nf3", "Nc6", "Bc4")),
        Opening("Italian Game, Giuoco Piano", "C50", listOf("e4", "e5", "Nf3", "Nc6", "Bc4", "Bc5")),
        Opening("Italian Game, Evans Gambit", "C51", listOf("e4", "e5", "Nf3", "Nc6", "Bc4", "Bc5", "b4")),
        Opening("Italian Game, Two Knights Defence", "C55", listOf("e4", "e5", "Nf3", "Nc6", "Bc4", "Nf6")),
        Opening("Ruy Lopez", "C60", listOf("e4", "e5", "Nf3", "Nc6", "Bb5")),
        Opening("Ruy Lopez, Berlin Defence", "C65", listOf("e4", "e5", "Nf3", "Nc6", "Bb5", "Nf6")),
        Opening(
            "Ruy Lopez, Closed Defence",
            "C84",
            listOf("e4", "e5", "Nf3", "Nc6", "Bb5", "a6", "Ba4", "Nf6", "O-O", "Be7"),
        ),

        // -- Sicilian ------------------------------------------------------
        Opening("Sicilian Defence", "B20", listOf("e4", "c5")),
        Opening("Sicilian Defence, Modern Variations", "B50", listOf("e4", "c5", "Nf3", "d6")),
        Opening(
            "Sicilian Defence, Open",
            "B50",
            listOf("e4", "c5", "Nf3", "d6", "d4", "cxd4", "Nf6", "Nc3"),
        ),
        Opening(
            "Sicilian Defence, Najdorf Variation",
            "B90",
            listOf("e4", "c5", "Nf3", "d6", "d4", "cxd4", "Nf6", "Nc3", "a6"),
        ),

        // -- French --------------------------------------------------------
        Opening("French Defence", "C00", listOf("e4", "e6")),
        Opening("French Defence, Exchange Variation", "C01", listOf("e4", "e6", "d4", "d5", "exd5")),
        Opening("French Defence, Advance Variation", "C02", listOf("e4", "e6", "d4", "d5", "e5")),
        Opening("French Defence, Classical Variation", "C11", listOf("e4", "e6", "d4", "d5", "Nc3", "Nf6")),
        Opening("French Defence, Winawer Variation", "C15", listOf("e4", "e6", "d4", "d5", "Nc3", "Bb4")),

        // -- Caro-Kann -----------------------------------------------------
        Opening("Caro-Kann Defence", "B10", listOf("e4", "c6")),
        Opening("Caro-Kann Defence, Advance Variation", "B12", listOf("e4", "c6", "d4", "d5", "e5")),
        Opening(
            "Caro-Kann Defence, Classical Variation",
            "B18",
            listOf("e4", "c6", "d4", "d5", "Nd2", "dxe4", "Nxe4", "Bf5"),
        ),

        // -- 1.d4 ----------------------------------------------------------
        Opening("Queen's Pawn Game", "D00", listOf("d4")),
        Opening("Queen's Gambit", "D06", listOf("d4", "d5", "c4")),
        Opening("Slav Defence", "D10", listOf("d4", "d5", "c4", "c6")),
        Opening("London System", "D02", listOf("d4", "d5", "Bf4")),
        Opening("London System", "D02", listOf("d4", "d5", "Nf3", "Nf6", "Bf4")),
        Opening("Queen's Gambit Accepted", "D20", listOf("d4", "d5", "c4", "dxc4")),
        Opening("Queen's Gambit Declined", "D30", listOf("d4", "d5", "c4", "e6")),
        Opening(
            "Queen's Gambit Declined, Exchange Variation",
            "D35",
            listOf("d4", "d5", "c4", "e6", "Nc3", "Nf6", "cxd5"),
        ),

        // -- 1.c4 ----------------------------------------------------------
        Opening("English Opening", "A10", listOf("c4")),
        Opening("English Opening, Anglo-Indian Defence", "A15", listOf("c4", "Nf6")),
        Opening("English Opening, King's English Variation", "A20", listOf("c4", "e5")),
        Opening("English Opening, Symmetrical Variation", "A30", listOf("c4", "c5")),
    )

    /**
     * The opening a game is in, or null when nothing here recognises it.
     *
     * The longest matching prefix wins, which is the whole rule and also the
     * whole trap. `1.e4 e5 2.Nf3` is in this book twice over — it is the front of
     * the Italian, the Ruy and the Scotch — and returning the first entry found
     * would name the opening by whichever line happened to be written first.
     * Taking the longest match instead means the name always follows the moves
     * that were actually played, so `1.e4 e5 2.Nf3 Nc6 3.Bb5` says Ruy Lopez and
     * not Italian Game.
     *
     * Divergence is silent by design: a game that leaves every line returns
     * null rather than the nearest thing it resembles. Half the value of the
     * book is not saying "Queen's Gambit" when White has answered `1...d5` with
     * `2.Nf3`.
     */
    fun nameFor(sans: List<String>): Opening? {
        if (sans.isEmpty()) return null
        val played = sans.map(::normalise)
        var best: Opening? = null
        for (opening in BOOK) {
            if (opening.moves.size <= (best?.moves?.size ?: 0)) continue
            if (matches(played, opening.moves)) best = opening
        }
        return best
    }

    /**
     * Puts one recorded move into the shape the book is written in.
     *
     * The trims are all about spelling, not about chess: the same move arrives
     * as "Bb5+" from this app's own SAN and "Bb5" from a book, as "O-O" or as
     * "0-0" from a PGN, and with "e4?!" on the end from a lesson. None of that
     * changes which opening the move belongs to, so none of it should change
     * the answer.
     */
    private fun normalise(san: String): String =
        san.trim()
            .trimEnd('+', '#', '!', '?')
            .replace("0-0", "O-O")

    /**
     * True when [game]'s opening moves agree with [prefix], move for move. A
     * shorter game cannot contain a longer line, so it cannot match it.
     */
    private fun matches(game: List<String>, prefix: List<String>): Boolean {
        if (game.size < prefix.size) return false
        for (i in prefix.indices) if (game[i] != prefix[i]) return false
        return true
    }
}
