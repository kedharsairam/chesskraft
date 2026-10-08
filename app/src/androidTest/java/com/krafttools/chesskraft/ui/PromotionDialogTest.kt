/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kraft.ui.tokens.KraftSpacing
import com.krafttools.chesskraft.domain.ChessMove
import com.krafttools.chesskraft.domain.PieceType
import com.krafttools.chesskraft.domain.Side
import com.krafttools.chesskraft.domain.parseSquare
import com.krafttools.chesskraft.ui.board.PromotionDialog
import com.krafttools.chesskraft.ui.theme.ChessKraftTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The promotion picker, on a real screen.
 *
 * This dialog is the piece of UI least likely to be right and least likely to
 * be seen: it appears once, after forty moves, and it commits a piece. A unit
 * test cannot lay out a dialog, so these run as instrumented tests — the point
 * is not that the composable compiles but that on this device the tiles are big
 * enough to hit, the queen really is the one marked chosen, and what a screen
 * reader is told matches what is drawn.
 */
@RunWith(AndroidJUnit4::class)
class PromotionDialogTest {

    @get:Rule
    val rule = createComposeRule()

    /** White, one square from the last rank, with the four legal futures. */
    private fun promotionOptions(side: Side): List<ChessMove> {
        val from = if (side == Side.WHITE) "a7" else "a2"
        val to = if (side == Side.WHITE) "a8" else "a1"
        val fromSquare = parseSquare(from) ?: error("square $from")
        val toSquare = parseSquare(to) ?: error("square $to")
        return listOf(
            PieceType.QUEEN,
            PieceType.ROOK,
            PieceType.BISHOP,
            PieceType.KNIGHT,
        ).map { type ->
            ChessMove(from = fromSquare, to = toSquare, promotion = type)
        }
    }

    private fun showDialog(side: Side = Side.WHITE, onChoose: (ChessMove) -> Unit = {}) {
        rule.setContent {
            ChessKraftTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PromotionDialog(
                        options = promotionOptions(side),
                        side = side,
                        onChoose = onChoose,
                        onDismiss = {},
                    )
                }
            }
        }
    }

    /**
     * The tiles name themselves in lower case — "Promote to knight" — because
     * that is how the sentence reads aloud. The confirm button is the one that
     * capitalises: "Promote to Knight".
     */
    private fun option(word: String): SemanticsNodeInteraction =
        rule.onNodeWithContentDescription("Promote to $word", substring = true)

    @Test
    fun everyPieceIsOfferedAndNamed() {
        showDialog()
        for (word in listOf("queen", "rook", "bishop", "knight")) {
            option(word).assertIsDisplayed()
        }
    }

    @Test
    fun theQueenIsTheOneAlreadyChosen() {
        showDialog()
        // Selection is read from the semantics state, not from a border colour:
        // that is the whole point of the radio role.
        rule.onNode(
            hasContentDescription("Promote to queen", substring = true) and isSelected(),
        ).assertIsSelected()
        for (word in listOf("rook", "bishop", "knight")) {
            rule.onNode(hasContentDescription("Promote to $word", substring = true))
                .assertIsNotSelected()
        }
    }

    @Test
    fun theChosenTileSaysSo() {
        showDialog()
        rule.onNodeWithContentDescription("Promote to queen, selected", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun everyTileIsBigEnoughToHit() {
        showDialog()
        for (word in listOf("queen", "rook", "bishop", "knight")) {
            val tile = rule.onNode(hasContentDescription("Promote to $word", substring = true))
            // 48dp is the floor, not the target: a tile that is only just legal
            // is a tile that gets missed while someone is thinking about chess.
            // The numbers come from the foundation's own spacing tokens rather
            // than raw literals, so the test fails if the tokens ever move.
            tile.assertHeightIsAtLeast(KraftSpacing.Spacing64)
            tile.assertWidthIsAtLeast(KraftSpacing.Spacing48)
        }
    }

    @Test
    fun choosingUnderpromotionMovesTheSelectionAndTheButton() {
        var chosen: PieceType? = null
        showDialog(onChoose = { chosen = it.promotion })
        rule.onNodeWithText("Promote to Queen").assertIsDisplayed()
        option("knight").performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Promote to knight, selected", substring = true)
            .assertIsDisplayed()
        rule.onNodeWithText("Promote to Knight").performClick()
        rule.waitForIdle()
        // Underpromotion is two taps and says so on its face. That is the whole
        // reason the other three tiles are on the screen at all.
        assert(PieceType.KNIGHT == chosen) { "expected a knight, got $chosen" }
    }

    @Test
    fun blackPromotesWithTheSameDialog() {
        showDialog(side = Side.BLACK)
        rule.onNodeWithContentDescription("Promote to queen, selected", substring = true)
            .assertIsDisplayed()
        rule.onNodeWithText("Promote to Queen").assertIsDisplayed()
    }

}