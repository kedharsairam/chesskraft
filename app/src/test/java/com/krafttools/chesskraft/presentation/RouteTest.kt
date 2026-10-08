/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 *
 * Rotation used to drop a live game back to Home. These pin the encoding that
 * fixes it, and — more importantly — pin that a damaged value falls back to
 * Home instead of throwing on the launch path.
 */
package com.krafttools.chesskraft.presentation

import com.krafttools.chesskraft.domain.Side
import com.krafttools.chesskraft.engine.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RouteTest {

    @Test
    fun homeRoundTrips() {
        assertEquals(Route.Home, decodeRoute(encodeRoute(Route.Home)))
    }

    @Test
    fun gameRoundTripsEveryField() {
        val route = Route.Game(
            key = 42L,
            difficulty = Difficulty.SHARP,
            playerSide = Side.BLACK,
            flipped = true,
            timeControlMs = 600_000L,
            resume = true,
        )
        assertEquals(route, decodeRoute(encodeRoute(route)))
    }

    @Test
    fun gameWithoutAClockRoundTrips() {
        val route = Route.Game(
            key = 7L,
            difficulty = Difficulty.RELAXED,
            playerSide = Side.WHITE,
            flipped = false,
            timeControlMs = null,
        )
        assertEquals(route, decodeRoute(encodeRoute(route)))
    }

    @Test
    fun everyDifficultyAndSideSurvives() {
        for (difficulty in Difficulty.entries) {
            for (side in listOf(Side.WHITE, Side.BLACK)) {
                val route = Route.Game(1L, difficulty, side, false, 300_000L)
                assertEquals(route, decodeRoute(encodeRoute(route)))
            }
        }
    }

    @Test
    fun nullDecodesToNull() {
        assertNull(decodeRoute(null))
    }

    @Test
    fun garbageDecodesToNullWithoutThrowing() {
        assertNull(decodeRoute("nonsense"))
        assertNull(decodeRoute(""))
        assertNull(decodeRoute(RouteSep + RouteSep))
        assertNull(decodeRoute("game"))
        assertNull(decodeRoute("game${RouteSep}notanumber"))
        assertNull(decodeRoute("game${RouteSep}1${RouteSep}NOT_A_DIFFICULTY"))
        assertNull(decodeRoute("game${RouteSep}1${RouteSep}RELAXED${RouteSep}NOT_A_SIDE"))
        assertNull(decodeRoute("game${RouteSep}1${RouteSep}RELAXED${RouteSep}WHITE${RouteSep}maybe"))
        assertNull(decodeRoute("game${RouteSep}1${RouteSep}RELAXED${RouteSep}WHITE${RouteSep}false${RouteSep}notanumber"))
    }

    @Test
    fun everyFixedRouteSurvivesARoundTrip() {
        // The screens that carry no state are cheap to restore and must survive
        // rotation like the rest; they carry nothing, so there is nothing stale
        // about re-entering them.
        for (route in listOf(Route.Home, Route.History, Route.About)) {
            val encoded = encodeRoute(route)
            assertNotNull("$route must be encodable", encoded)
            assertEquals(route, decodeRoute(encoded))
        }
    }

    @Test
    fun anUnknownRouteNameIsHome() {
        assertNull(decodeRoute("settings"))
        assertNull(decodeRoute("About"))
    }

    @Test
    fun trailingFieldsFromAFutureVersionAreIgnored() {
        // An older build reading a newer build's route should land on the
        // screen it knows, not drop the player at Home. The Game case does the
        // same thing already, one field at a time.
        assertEquals(Route.About, decodeRoute("about${RouteSep}something${RouteSep}new"))
    }

    @Test
    fun aReviewIsNotRestorableAndSaysSo() {
        // The review holds engine output; re-deriving it on rotation would re-run
        // the search. It is deliberately left unrestorable rather than stale.
        val review = Route.Review(
            review = com.krafttools.chesskraft.domain.GameReview.build(
                fens = emptyList(),
                sans = emptyList(),
                analyze = { null },
            ),
            playerSide = Side.WHITE,
        )
        assertNull(encodeRoute(review))
        assertNull(decodeRoute(null))
    }
}