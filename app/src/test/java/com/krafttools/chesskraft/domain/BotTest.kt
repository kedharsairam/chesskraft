/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 *
 * Four opponents, one per level, with strings a player can believe. The tests
 * here are mostly about the ladder: the taglines are the app's claim about
 * strength, so they must stay free of numbers (which rot the moment a limit
 * changes), free of engine internals (which mean nothing to the person at the
 * board), and free of the cheap register the rest of the app avoids.
 */
package com.krafttools.chesskraft.domain

import com.krafttools.chesskraft.engine.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BotTest {

    /** Whole words the copy must not contain, at any level. */
    private val engineWords = listOf(
        "engine",
        "search",
        "node",
        "nodes",
        "millis",
        "centipawn",
        "eval",
        "cp",
        "top",
    )

    private fun mentions(text: String, word: String): Boolean =
        Regex("(?<![A-Za-z])${Regex.escape(word)}(?![A-Za-z])", RegexOption.IGNORE_CASE)
            .containsMatchIn(text)

    @Test
    fun thereIsOneBotPerDifficulty() {
        assertEquals(Difficulty.entries.size, Bots.all.size)
        for (level in Difficulty.entries) {
            assertEquals(level, Bots.forLevel(level).level)
        }
        // No level twice, or the picker would have two opponents to choose from.
        assertEquals(Difficulty.entries.toSet(), Bots.all.map { it.level }.toSet())
    }

    @Test
    fun forLevelReturnsTheSameBotTheListShows() {
        for (bot in Bots.all) assertEquals(bot, Bots.forLevel(bot.level))
    }

    @Test
    fun namesAndPersonalitiesAreDistinct() {
        // Four levels that read the same would make the picker a lie in the same
        // way four identical taglines would.
        assertEquals(Bots.all.size, Bots.all.map { it.name }.toSet().size)
        assertEquals(Bots.all.size, Bots.all.map { it.tagline }.toSet().size)
        assertEquals(Bots.all.size, Bots.all.map { it.style }.toSet().size)
    }

    @Test
    fun nothingIsBlank() {
        for (bot in Bots.all) {
            assertTrue(bot.name, bot.name.isNotBlank())
            assertTrue(bot.tagline, bot.tagline.isNotBlank())
            assertTrue(bot.style, bot.style.isNotBlank())
        }
    }

    @Test
    fun taglinesMentionNoNumbers() {
        // A number in a tagline is a number that goes stale: the limits behind
        // these levels are free to change without the copy noticing.
        for (bot in Bots.all) {
            assertTrue(bot.tagline, bot.tagline.none { it.isDigit() })
            for (word in listOf("elo", "centipawn", "depth", "milliseconds", "percent")) {
                assertFalse(bot.tagline, mentions(bot.tagline, word))
            }
        }
    }

    @Test
    fun copyDoesNotLeakTheEngine() {
        // Depth, search, nodes and evaluations are the engine's own business. A
        // player cannot act on them, and naming them turns a personality into a
        // settings screen.
        for (bot in Bots.all) {
            for (text in listOf(bot.name, bot.tagline, bot.style)) {
                for (word in engineWords) {
                    assertFalse("$text / $word", mentions(text, word))
                }
            }
        }
    }

    @Test
    fun copyIsQuietAndPlain() {
        for (bot in Bots.all) {
            for (text in listOf(bot.name, bot.tagline, bot.style)) {
                assertFalse(text, text.contains('!'))
                // Printable ASCII only: no emoji, no smart quotes, no dashes
                // that render at a different width on the budget phone.
                assertTrue(text, text.all { it.code in 32..126 })
                assertFalse(text, text.contains("  "))
            }
            // Both sentences are sentences.
            assertTrue(bot.tagline, bot.tagline.endsWith("."))
            assertTrue(bot.style, bot.style.endsWith("."))
            assertTrue(bot.tagline, bot.tagline.first().isUpperCase())
        }
    }

    @Test
    fun theWeakestLevelPromisesNothingItCannotDo() {
        // The relaxed level searches a single ply and picks from a shortlist of
        // near-best moves, so its copy may not advertise tactics, punishment or
        // long calculation. This is the check that keeps the line honest if the
        // limits are ever retuned.
        val relaxed = Bots.forLevel(Difficulty.RELAXED)
        val copy = "${relaxed.tagline} ${relaxed.style}"
        for (word in listOf("tactic", "punish", "punishes", "combination", "deep", "forces", "trick")) {
            assertFalse(copy, mentions(copy, word))
        }
    }

    @Test
    fun theLadderRunsFromRelaxedToTough() {
        assertEquals(Difficulty.RELAXED, Bots.all.first().level)
        assertEquals(Difficulty.TOUGH, Bots.all.last().level)
        assertEquals(
            Difficulty.entries.toList(),
            Bots.all.map { it.level },
        )
    }
}
