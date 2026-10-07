/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft.presentation

import com.krafttools.chesskraft.domain.GameReviewResult
import com.krafttools.chesskraft.domain.Side
import com.krafttools.chesskraft.engine.Difficulty

/**
 * Where the app is, in a form that survives a configuration change.
 *
 * Rotation used to drop an in-progress game back to Home: the ViewModel
 * survived, but the route was a plain `remember`, so the recreated activity
 * asked for Home and left a live game stranded. The game itself is saved to
 * disk now too, which meant the position was recoverable — but making the
 * player tap "Unfinished game" after every rotation is not an acceptable
 * answer.
 *
 * Encoding is a unit separator string because a Bundle-friendly primitive is
 * all `rememberSaveable` can store, and this is deliberately not clever: it is
 * three cases and a handful of fields, and anything that cannot be parsed
 * returns null so the caller falls back to Home rather than crashing.
 */
sealed interface Route {
    data object Home : Route

    data class Game(
        val key: Long,
        val difficulty: Difficulty,
        val playerSide: Side,
        val flipped: Boolean,
        val timeControlMs: Long?,
        val resume: Boolean = false,
    ) : Route

    data class Review(
        val review: GameReviewResult,
        val playerSide: Side,
    ) : Route
}

/** Unit separator: cannot appear in a difficulty name, an enum name or a digit. */
const val RouteSep = ""

/** Marks the boundary between the FEN list and the SAN list inside a review. */
const val RouteMid = ""

/** Encodes [route] for `rememberSaveable`, or null when it cannot be stored. */
fun encodeRoute(route: Route): String? = when (route) {
    Route.Home -> "home"
    is Route.Game -> listOf(
        "game",
        route.key.toString(),
        route.difficulty.name,
        route.playerSide.name,
        route.flipped.toString(),
        route.timeControlMs?.toString() ?: "none",
        route.resume.toString(),
    ).joinToString(RouteSep)
    // A review holds graded moves and accuracies. Re-deriving that on rotation
    // would re-run the engine, so the review route is simply not restorable:
    // Review returns Home instead of freezing on a stale grade.
    is Route.Review -> null
}

/** Decodes what [encodeRoute] wrote. Anything unexpected is null, never a throw. */
fun decodeRoute(saved: String?): Route? {
    if (saved == null) return null
    val parts = saved.split(RouteSep)
    return try {
        when (parts.firstOrNull()) {
            "home" -> Route.Home
            "game" -> Route.Game(
                key = parts[1].toLong(),
                difficulty = Difficulty.valueOf(parts[2]),
                playerSide = Side.valueOf(parts[3]),
                flipped = parts[4].toBooleanStrict(),
                timeControlMs = parts[5].takeIf { it != "none" }?.toLong(),
                resume = parts.getOrNull(6)?.toBooleanStrict() ?: false,
            )
            else -> null
        }
    } catch (_: IllegalArgumentException) {
        // A value that will not parse is a value from another app version. Home.
        null
    } catch (_: IndexOutOfBoundsException) {
        null
    }
}