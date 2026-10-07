/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft.domain

import com.krafttools.chesskraft.engine.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The save format, with no Context and no disk.
 *
 * The parser is the one piece of this feature that can crash a launch, so it is
 * tested mostly on what it refuses: truncated text, wrong types, names this
 * build does not have. Every one of those must be a null.
 */
class GameSaveTest {

    private fun save(
        inProgress: SavedGame? = null,
        history: List<FinishedGame> = emptyList(),
    ) = GameSave(inProgress = inProgress, history = history)

    private fun game(
        playerSide: Side = Side.WHITE,
        difficulty: Difficulty = Difficulty.CASUAL,
        flipped: Boolean = false,
        timeControlMs: Long? = null,
        clockWhiteMs: Long? = null,
        clockBlackMs: Long? = null,
        moves: List<String> = listOf("e2e4", "e7e5"),
    ) = SavedGame(playerSide, difficulty, flipped, timeControlMs, clockWhiteMs, clockBlackMs, moves)

    private fun finished(
        result: String = "You win",
        moveCount: Int = 31,
        difficulty: Difficulty = Difficulty.SHARP,
        playerSide: Side = Side.WHITE,
        endedAtMs: Long = 1_760_000_000_000L,
    ) = FinishedGame(result, moveCount, difficulty, playerSide, endedAtMs)

    // -- Round trip -------------------------------------------------------

    @Test
    fun roundTripKeepsEveryField() {
        val original = save(
            inProgress = game(
                playerSide = Side.BLACK,
                difficulty = Difficulty.TOUGH,
                flipped = true,
                timeControlMs = 300_000L,
                clockWhiteMs = 180_000L,
                clockBlackMs = 120_500L,
                moves = listOf("e2e4", "c7c5", "g1f3", "e7e8q"),
            ),
            history = listOf(
                finished(),
                finished(result = "Draw agreed", moveCount = 44, endedAtMs = 2L),
            ),
        )
        assertEquals(original, GameSave.fromJson(original.toJson()))
    }

    @Test
    fun roundTripKeepsAnUntimedGameWithoutClocks() {
        val original = save(inProgress = game(timeControlMs = null))
        assertEquals(original, GameSave.fromJson(original.toJson()))
    }

    @Test
    fun roundTripKeepsASaveWithNoGameInProgress() {
        val original = save(history = listOf(finished()))
        assertEquals(original, GameSave.fromJson(original.toJson()))
    }

    @Test
    fun roundTripKeepsPromotionsAndAnEmptyMoveList() {
        val original = save(
            inProgress = game(moves = listOf("a7a8q", "h7h5", "a8a1")),
        )
        assertEquals(original, GameSave.fromJson(original.toJson()))
        val noMoves = save(inProgress = game(moves = emptyList()))
        assertEquals(noMoves, GameSave.fromJson(noMoves.toJson()))
    }

    @Test
    fun everyDifficultyAndSideSurvives() {
        for (difficulty in Difficulty.entries) {
            for (side in Side.entries) {
                val original = save(
                    inProgress = game(playerSide = side, difficulty = difficulty),
                    history = listOf(finished(difficulty = difficulty, playerSide = side)),
                )
                assertEquals(original, GameSave.fromJson(original.toJson()))
            }
        }
    }

    /**
     * A result string is prose the UI shows, so the writer has to escape a
     * quote rather than produce a file the reader cannot parse.
     */
    @Test
    fun quotesInAResultSurviveTheRoundTrip() {
        val awkward = finished(result = "You win — \"a draw\" was refused")
        val original = save(history = listOf(awkward))
        val text = original.toJson()
        assertTrue(text.contains("\\\"a draw\\\""))
        assertEquals(original, GameSave.fromJson(text))
    }

    // -- Nothing saved ----------------------------------------------------

    @Test
    fun emptyTextIsNull() {
        assertNull(GameSave.fromJson(""))
    }

    @Test
    fun whitespaceOnlyIsNull() {
        assertNull(GameSave.fromJson("   \n\t "))
    }

    @Test
    fun aNullLiteralIsNotASave() {
        assertNull(GameSave.fromJson("null"))
    }

    @Test
    fun anEmptyObjectIsNull() {
        assertNull(GameSave.fromJson("{}"))
    }

    @Test
    fun anArrayIsNotASave() {
        assertNull(GameSave.fromJson("[]"))
    }

    @Test
    fun aJsonNumberIsNotASave() {
        assertNull(GameSave.fromJson("42"))
    }

    // -- Truncated -------------------------------------------------------

    @Test
    fun truncatedJsonIsNull() {
        val text = save(inProgress = game(), history = listOf(finished())).toJson()
        // Every prefix short of the closing brace is an unfinished document.
        for (cut in 1 until text.length - 1) {
            assertNull("prefix of length $cut parsed", GameSave.fromJson(text.substring(0, cut)))
        }
    }

    @Test
    fun anUnclosedStringIsNull() {
        assertNull(GameSave.fromJson("{\"version\":1,\"inProgress\":null,\"history\":[\"You wi"))
    }

    // -- Wrong types -----------------------------------------------------

    @Test
    fun aStringVersionIsNull() {
        assertNull(GameSave.fromJson("{\"version\":\"1\",\"inProgress\":null,\"history\":[]}"))
    }

    @Test
    fun aMissingVersionIsNull() {
        assertNull(GameSave.fromJson("{\"inProgress\":null,\"history\":[]}"))
    }

    @Test
    fun aFutureVersionIsNull() {
        val text = save().toJson().replace("\"version\":1", "\"version\":2")
        assertNull(GameSave.fromJson(text))
    }

    @Test
    fun flippedAsAStringIsNull() {
        val text = save(inProgress = game()).toJson().replace("\"flipped\":false", "\"flipped\":\"no\"")
        assertNull(GameSave.fromJson(text))
    }

    @Test
    fun movesAsAStringIsNull() {
        val text = save(inProgress = game())
            .toJson()
            .replace("\"moves\":[\"e2e4\",\"e7e5\"]", "\"moves\":\"e2e4\"")
        assertNull(GameSave.fromJson(text))
    }

    @Test
    fun aMoveThatIsNotAStringIsNull() {
        val text = save(inProgress = game())
            .toJson()
            .replace("\"e7e5\"", "5")
        assertNull(GameSave.fromJson(text))
    }

    @Test
    fun clockBanksAsDecimalsAreNull() {
        val text = save(inProgress = game(timeControlMs = 60_000L, clockWhiteMs = 1L, clockBlackMs = 2L))
            .toJson()
            .replace("\"clockWhiteMs\":1", "\"clockWhiteMs\":1.5")
        assertNull(GameSave.fromJson(text))
    }

    @Test
    fun aNullClockBankIsAnUntimedGameNotACorruptOne() {
        val original = save(inProgress = game(timeControlMs = null, clockWhiteMs = null, clockBlackMs = null))
        assertEquals(original, GameSave.fromJson(original.toJson()))
    }

    @Test
    fun historyAsAnObjectIsNull() {
        val text = save().toJson().replace("\"history\":[]", "\"history\":{}")
        assertNull(GameSave.fromJson(text))
    }

    @Test
    fun aHistoryEntryWithTheWrongTypesIsNull() {
        val text = save(history = listOf(finished()))
            .toJson()
            .replace("\"moveCount\":31", "\"moveCount\":\"31\"")
        assertNull(GameSave.fromJson(text))
    }

    @Test
    fun inProgressAsAnArrayIsNull() {
        val text = save().toJson().replace("\"inProgress\":null", "\"inProgress\":[]")
        assertNull(GameSave.fromJson(text))
    }

    // -- Unknown names ---------------------------------------------------

    /**
     * A difficulty this build does not have is a save from another version of
     * the app. Guessing a default would resume a game the player never chose, so
     * it is refused outright and the caller starts fresh.
     */
    @Test
    fun anUnknownDifficultyIsNull() {
        val text = save(inProgress = game(difficulty = Difficulty.TOUGH))
            .toJson()
            .replace("\"difficulty\":\"TOUGH\"", "\"difficulty\":\"IMPOSSIBLE\"")
        assertNull(GameSave.fromJson(text))
    }

    @Test
    fun aLowercasedDifficultyIsNull() {
        val text = save(inProgress = game()).toJson().replace("\"CASUAL\"", "\"casual\"")
        assertNull(GameSave.fromJson(text))
    }

    @Test
    fun anUnknownSideIsNull() {
        val text = save(inProgress = game(playerSide = Side.BLACK))
            .toJson()
            .replace("\"playerSide\":\"BLACK\"", "\"playerSide\":\"PURPLE\"")
        assertNull(GameSave.fromJson(text))
    }

    @Test
    fun aMissingDifficultyIsNull() {
        val text = save(inProgress = game()).toJson().replace("\"difficulty\":\"CASUAL\",", "")
        assertNull(GameSave.fromJson(text))
    }

    @Test
    fun aMissingResultIsNull() {
        val text = save(history = listOf(finished()))
            .toJson()
            .replace("\"result\":\"You win\",", "")
        assertNull(GameSave.fromJson(text))
    }

    // -- History cap -----------------------------------------------------

    @Test
    fun historyKeepsTheMostRecentAndDropsTheOldest() {
        var accumulated = save()
        for (i in 1..25) {
            accumulated = accumulated.withFinished(finished(result = "Game $i", endedAtMs = i.toLong()))
        }
        assertEquals(GameSave.HISTORY_CAP, accumulated.history.size)
        assertEquals("Game 25", accumulated.history.first().result)
        assertEquals("Game 6", accumulated.history.last().result)
        assertNull(accumulated.inProgress)
    }

    @Test
    fun aCapOfZeroKeepsNothing() {
        assertTrue(save().withFinished(finished(), cap = 0).history.isEmpty())
    }

    @Test
    fun emptySaveIsTheAbsenceOfEverything() {
        val empty = GameSave.empty()
        assertNull(empty.inProgress)
        assertTrue(empty.history.isEmpty())
        assertEquals(empty, GameSave.fromJson(empty.toJson()))
    }
}