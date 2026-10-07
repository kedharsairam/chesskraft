/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 *
 * The four opponents, with the personalities the difficulty levels actually
 * have. Pure Kotlin — no Android, no Compose, and no engine import beyond the
 * [Difficulty] enum itself; this file names people, it does not move pieces.
 *
 * The strings here are the app's promise about strength, so they have to be
 * true of the search the engine actually runs (see `OwnEngine.searchLimitsFor`
 * and its blunder model). A level that searches two plies deep and picks from
 * the top three near-best moves must not be advertised as one that punishes
 * loose play, because a player who believes the tagline learns less than one
 * who reads the board. Each tagline below describes the level it sits on and
 * nothing more; [style] is the one clause that makes it feel like someone
 * rather than a setting.
 */
package com.krafttools.chesskraft.domain

import com.krafttools.chesskraft.engine.Difficulty

/**
 * One named opponent.
 *
 * [level] is the difficulty it plays at, [name] is what the board says, and
 * the two strings around it are what the player reads before the first move:
 * [tagline] for how strong it is, [style] for how it plays. Neither mentions
 * depth, time or centipawns — those are the engine's business, and a player
 * cannot use them.
 */
data class Bot(
    val level: Difficulty,
    val name: String,
    val tagline: String,
    val style: String,
)

/**
 * One bot per [Difficulty], and no more.
 *
 * The ladder exists so the levels feel like opponents of different strength,
 * and the names are the cheapest part of that: chess.com's bots are people, and
 * "Casual" on its own is a slider label. Four names, four styles, one per level.
 */
object Bots {

    /** Every opponent, weakest first — the order [Difficulty] declares in. */
    val all: List<Bot> = listOf(
        Bot(
            level = Difficulty.RELAXED,
            name = "Bertie",
            tagline = "Sees one move ahead, and it shows.",
            style = "Plays the obvious move and waits to see what you do.",
        ),
        Bot(
            level = Difficulty.CASUAL,
            name = "Wren",
            tagline = "Watches a couple of moves ahead, then stops looking.",
            style = "Keeps its pieces together and waits for you to hurry.",
        ),
        Bot(
            level = Difficulty.SHARP,
            name = "Ines",
            tagline = "Finds the best move nearly every time.",
            style = "Plays the sharp move and keeps coming at you.",
        ),
        Bot(
            level = Difficulty.TOUGH,
            name = "Halvard",
            tagline = "Thinks for as long as it can and plays the best of it.",
            style = "Punishes anything you leave hanging.",
        ),
    )

    /**
     * The opponent for [level].
     *
     * Total on purpose: a level with no opponent would leave the game screen
     * with nothing to render, so a level added to the engine without a bot here
     * is a missing bug, not a null to be handled downstream.
     */
    fun forLevel(level: Difficulty): Bot = all.first { it.level == level }
}
