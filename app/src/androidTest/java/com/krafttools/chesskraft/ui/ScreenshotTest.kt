/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft.ui

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.krafttools.chesskraft.domain.ChessMove
import com.krafttools.chesskraft.domain.GameResult
import com.krafttools.chesskraft.domain.GameReviewResult
import com.krafttools.chesskraft.domain.MoveVerdict
import com.krafttools.chesskraft.domain.PieceType
import com.krafttools.chesskraft.domain.ReviewedMove
import com.krafttools.chesskraft.domain.Position
import com.krafttools.chesskraft.domain.Side
import com.krafttools.chesskraft.engine.Difficulty
import com.krafttools.chesskraft.presentation.GameViewModel
import com.krafttools.chesskraft.ui.board.GameOverSheet
import com.krafttools.chesskraft.ui.board.PromotionDialog
import com.krafttools.chesskraft.ui.screens.GameScreen
import com.krafttools.chesskraft.ui.screens.HomeScreen
import com.krafttools.chesskraft.ui.screens.ReviewScreen
import com.krafttools.chesskraft.ui.theme.ChessKraftTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Renders every polished screen and sheet on the Realme and saves a PNG per
 * surface for human verification. Not an assertion test — eyeball the files.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTest {

    @get:Rule
    val rule = createComposeRule()

    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private fun snap(name: String) {
        rule.waitForIdle()
        Thread.sleep(400)
        val dir = InstrumentationRegistry.getInstrumentation().targetContext
            .getExternalFilesDir(null)!!
        val file = File(dir, "$name.png")
        device.takeScreenshot(file)
        device.dumpWindowHierarchy(File(dir, "$name.xml"))
        println("SCREENSHOT_SAVED ${file.absolutePath}")
    }

    @Test
    fun shot_home() {
        rule.setContent {
            ChessKraftTheme(darkTheme = true) {
                DarkSurface {
                    HomeScreen(onPlay = { _, _, _, _ -> })
                }
            }
        }
        snap("01_home")
    }

    @Test
    fun shot_game() {
        val viewModel = GameViewModel(playerSide = Side.WHITE, difficulty = Difficulty.CASUAL)
        rule.setContent {
            ChessKraftTheme(darkTheme = true) {
                DarkSurface {
                    GameScreen(viewModel = viewModel, onRematch = {}, onNewGame = {}, onOpenReview = {})
                }
            }
        }
        snap("02_game")
    }

    @Test
    fun shot_resign() {
        val viewModel = GameViewModel(playerSide = Side.WHITE, difficulty = Difficulty.CASUAL)
        rule.setContent {
            ChessKraftTheme(darkTheme = true) {
                DarkSurface {
                    GameScreen(viewModel = viewModel, onRematch = {}, onNewGame = {}, onOpenReview = {})
                }
            }
        }
        rule.onNode(hasContentDescription("Resign. Give up this game.")).performClick()
        rule.onNode(hasText("Resign this game?")).assertExists()
        snap("03_resign")
    }

    @Test
    fun shot_gameOver() {
        rule.setContent {
            ChessKraftTheme(darkTheme = true) {
                DarkSurface {
                    GameOverSheet(
                    result = GameResult.Checkmate(winner = Side.WHITE, playerSide = Side.WHITE),
                    moveCount = 23,
                    onRematch = {},
                    onNewGame = {},
                    onReview = {},
                    reviewRunning = false,
                    onDismiss = {},
                    )
                }
            }
        }
        snap("04_gameover")
    }

    @Test
    fun shot_review() {
        // Real Ruy Lopez line so the board matches the move list.
        val ucis = listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1b5", "a7a6", "b5a4", "g8f6")
        val fens = mutableListOf(Position.start().toFen())
        var position = Position.start()
        for (uci in ucis) {
            val parsed = com.krafttools.chesskraft.domain.parseUci(uci) ?: continue
            val legal = position.generateLegalMoves().firstOrNull {
                it.from == parsed.from && it.to == parsed.to
            } ?: continue
            position = position.makeMove(legal)
            fens.add(position.toFen())
        }
        val sans = listOf("e4", "e5", "Nf3", "Nc6", "Bb5", "a6", "Ba4", "Nf6")
        rule.setContent {
            ChessKraftTheme(darkTheme = true) {
                DarkSurface {
                    ReviewScreen(
                        review = GameReviewResult(
                            moves = listOf(
                                ReviewedMove(1, 1, "e4", MoveVerdict.BEST, 0, "e4", 32),
                                ReviewedMove(2, 1, "e5", MoveVerdict.MISTAKE, 240, "c5", -18),
                                ReviewedMove(3, 2, "Nf3", MoveVerdict.INACCURACY, 55, "Nc3", 12),
                                ReviewedMove(4, 2, "Nc6", MoveVerdict.GOOD, 10, "Nc6", 30),
                            ),
                            accuracyWhite = 88,
                            accuracyBlack = 95,
                        ),
                        fens = fens,
                        playerSide = Side.WHITE,
                        onBack = {},
                    )
                }
            }
        }
        snap("05_review")
    }

    @Test
    fun shot_promotion() {
        val options = listOf(
            ChessMove(48, 56, PieceType.QUEEN),
            ChessMove(48, 56, PieceType.ROOK),
            ChessMove(48, 56, PieceType.BISHOP),
            ChessMove(48, 56, PieceType.KNIGHT),
        )
        rule.setContent {
            ChessKraftTheme(darkTheme = true) {
                DarkSurface {
                    PromotionDialog(options = options, side = Side.WHITE, onChoose = {}, onDismiss = {})
                }
            }
        }
        snap("06_promotion")
    }
}

/** True-black stage, like Theme.ChessKraft's window background. */
@androidx.compose.runtime.Composable
private fun DarkSurface(content: @androidx.compose.runtime.Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        content = content,
    )
}
