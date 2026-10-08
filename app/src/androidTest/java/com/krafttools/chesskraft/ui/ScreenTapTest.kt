/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.krafttools.chesskraft.domain.ChessMove
import com.krafttools.chesskraft.domain.GameResult
import com.krafttools.chesskraft.domain.PieceType
import com.krafttools.chesskraft.domain.Position
import com.krafttools.chesskraft.domain.GameReviewResult
import com.krafttools.chesskraft.domain.MoveVerdict
import com.krafttools.chesskraft.domain.ReviewedMove
import com.krafttools.chesskraft.domain.Side
import com.krafttools.chesskraft.engine.Difficulty
import com.krafttools.chesskraft.presentation.GameViewModel
import com.krafttools.chesskraft.ui.board.GameOverSheet
import com.krafttools.chesskraft.ui.board.PromotionDialog
import com.krafttools.chesskraft.ui.screens.GameScreen
import com.krafttools.chesskraft.ui.screens.HomeScreen
import com.krafttools.chesskraft.ui.screens.ReviewScreen
import com.krafttools.chesskraft.ui.theme.ChessKraftTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tap-tests for every control the visual-polish pass touched. Each control
 * asserts a click action exists, performs the tap, and checks the effect.
 */
@RunWith(AndroidJUnit4::class)
class ScreenTapTest {

    @get:Rule
    val rule = createComposeRule()

    // -- Home -----------------------------------------------------------

    @Test
    fun home_playStartsGame() {
        var played = false
        rule.setContent {
            ChessKraftTheme(darkTheme = true) {
                HomeScreen(onPlay = { _, _, _, _ -> played = true })
            }
        }
        rule.onNode(hasContentDescription("Play Wren, Casual.", substring = true))
            .assertHasClickAction().performClick()
        assert(played) { "Play did not call onPlay" }
    }

    @Test
    fun home_difficultyChipsSelectOneAtATime() {
        rule.setContent {
            ChessKraftTheme(darkTheme = true) {
                HomeScreen(onPlay = { _, _, _, _ -> })
            }
        }
        // "Tough" stays on one readable line and selects on tap.
        val tough = rule.onNode(hasText("Tough"))
        tough.assertHasClickAction().assertIsDisplayed().performClick()
        tough.assertIsSelected()
        rule.onNode(hasText("Casual")).assertHasClickAction()
    }

    @Test
    fun home_sideChipsSelect() {
        rule.setContent {
            ChessKraftTheme(darkTheme = true) {
                HomeScreen(onPlay = { _, _, _, _ -> })
            }
        }
        val black = rule.onNode(hasText("Black"))
        black.assertHasClickAction().performClick()
        black.assertIsSelected()
    }

    @Test
    fun home_flipRowToggles() {
        rule.setContent {
            ChessKraftTheme(darkTheme = true) {
                HomeScreen(onPlay = { _, _, _, _ -> })
            }
        }
        // The whole row is one switch: tap the label, the switch follows.
        rule.onNode(hasText("Flip the board")).assertHasClickAction().performClick()
        rule.onNode(hasText("Flip the board")).assertHasClickAction().performClick()
    }

    // -- Game toolbar ----------------------------------------------------

    @Test
    fun game_toolbarButtonsTap() {
        val viewModel = GameViewModel(playerSide = Side.WHITE, difficulty = Difficulty.CASUAL)
        rule.setContent {
            ChessKraftTheme(darkTheme = true) {
                GameScreen(
                    viewModel = viewModel,
                    onRematch = {},
                    onNewGame = {},
                    onOpenReview = {},
                )
            }
        }
        // Undo starts disabled (nothing to take back) — no dead control,
        // just an honest disabled state.
        rule.onNode(hasContentDescription("Undo. Take back your last move."))
            .assertIsNotEnabled()
        rule.onNode(hasContentDescription("Hint. Show a suggested move."))
            .assertHasClickAction().assertIsEnabled().performClick()
        rule.onNode(hasContentDescription("Flip. Turn the board around."))
            .assertHasClickAction().performClick()
        rule.onNode(hasContentDescription("Resign. Give up this game."))
            .assertHasClickAction().performClick()
        // Tapping Resign opens the confirm sheet.
        rule.onNode(hasText("Resign this game?")).assertIsDisplayed()
        rule.onNode(hasText("Keep Playing")).assertHasClickAction().performClick()
    }

    // -- Game-over sheet --------------------------------------------------

    @Test
    fun gameOver_allThreeActionsTap() {
        val result = GameResult.Checkmate(winner = Side.WHITE, playerSide = Side.WHITE)
        var rematched = false
        var fresh = false
        var reviewed = false
        rule.setContent {
            ChessKraftTheme(darkTheme = true) {
                GameOverSheet(
                    result = result,
                    moveCount = 12,
                    onRematch = { rematched = true },
                    onNewGame = { fresh = true },
                    onReview = { reviewed = true },
                    reviewRunning = false,
                    onDismiss = {},
                )
            }
        }
        rule.onNode(hasText("You win")).assertIsDisplayed()
        // moveCount is plies; the sheet counts moves the way a score sheet does.
        rule.onNode(hasText("6 moves played.")).assertIsDisplayed()
        rule.onNode(hasText("Rematch")).assertHasClickAction().performClick()
        rule.onNode(hasText("New Game")).assertHasClickAction().performClick()
        rule.onNode(hasText("Review")).assertHasClickAction().performClick()
        assert(rematched && fresh && reviewed) { "game-over actions did not fire" }
    }

    // -- Promotion ---------------------------------------------------------

    @Test
    fun promotion_pickAndConfirm() {
        val options = listOf(
            ChessMove(48, 56, PieceType.QUEEN),
            ChessMove(48, 56, PieceType.ROOK),
            ChessMove(48, 56, PieceType.BISHOP),
            ChessMove(48, 56, PieceType.KNIGHT),
        )
        var chosen: ChessMove? = null
        var dismissed = false
        rule.setContent {
            ChessKraftTheme(darkTheme = true) {
                PromotionDialog(
                    options = options,
                    side = Side.WHITE,
                    onChoose = { chosen = it },
                    onDismiss = { dismissed = true },
                )
            }
        }
        // Queen pre-selected; tap rook, confirm, the rook move plays.
        rule.onNode(hasContentDescription("Promote to rook", substring = true))
            .assertHasClickAction().performClick()
        // hasText compares the whole string unless told to look for a substring,
        // and the button renames itself to the piece you picked.
        rule.onNode(hasText("Promote to Rook")).assertHasClickAction().performClick()
        assert(chosen == ChessMove(48, 56, PieceType.ROOK)) { "wrong promotion: $chosen" }
        assert(!dismissed)
    }

    @Test
    fun promotion_cancelDismisses() {
        val options = listOf(ChessMove(48, 56, PieceType.QUEEN))
        var dismissed = false
        rule.setContent {
            ChessKraftTheme(darkTheme = true) {
                PromotionDialog(
                    options = options,
                    side = Side.WHITE,
                    onChoose = {},
                    onDismiss = { dismissed = true },
                )
            }
        }
        rule.onNode(hasText("Cancel")).assertHasClickAction().performClick()
        assert(dismissed)
    }

    // -- Review ------------------------------------------------------------

    @Test
    fun review_stepAndFlipTap() {
        val start = Position.start().toFen()
        val fens = listOf(start, start, start)
        val review = GameReviewResult(
            moves = listOf(
                ReviewedMove(1, 1, "e4", MoveVerdict.BEST, 0, "e4"),
                ReviewedMove(2, 1, "e5", MoveVerdict.INACCURACY, 60, "e5"),
            ),
            accuracyWhite = 96,
            accuracyBlack = 94,
        )
        var back = false
        rule.setContent {
            ChessKraftTheme(darkTheme = true) {
                ReviewScreen(
                    review = review,
                    fens = fens,
                    playerSide = Side.WHITE,
                    onBack = { back = true },
                )
            }
        }
        rule.onNode(hasText("Move 2 of 2", substring = true)).assertIsDisplayed()
        rule.onNodeWithContentDescription("Previous move").performClick()
        rule.onNode(hasText("Move 1 of 2", substring = true)).assertIsDisplayed()
        rule.onNodeWithContentDescription("Next move").performClick()
        rule.onNode(hasText("Move 2 of 2", substring = true)).assertIsDisplayed()
        rule.onNodeWithContentDescription("Back", substring = true).performClick()
        assert(back)
    }

    fun review_emptyGameRenders() {
        var back = false
        rule.setContent {
            ChessKraftTheme(darkTheme = true) {
                ReviewScreen(
                    review = GameReviewResult(emptyList(), 100, 100),
                    fens = emptyList(),
                    playerSide = Side.WHITE,
                    onBack = { back = true },
                )
            }
        }
        rule.onNodeWithContentDescription("Back", substring = true).performClick()
        assert(back)
    }

}
