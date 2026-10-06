/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.krafttools.chesskraft.domain.Side
import com.krafttools.chesskraft.engine.Difficulty
import com.krafttools.chesskraft.engine.OwnEngine
import com.krafttools.chesskraft.presentation.GameViewModel
import com.krafttools.chesskraft.ui.screens.GameScreen
import com.krafttools.chesskraft.ui.screens.HomeScreen
import com.krafttools.chesskraft.ui.screens.ReviewScreen
import kotlin.random.Random

/**
 * Four screens, no nav library: Home, Game, the Game-Over sheet (owned by
 * the Game screen), and Review. A fresh [GameConfig] with a fresh key means
 * a fresh ViewModel — Rematch swaps the colours, New deals a new game.
 */
private data class GameConfig(
    val key: Long,
    val difficulty: Difficulty,
    val playerSide: Side,
    val flipped: Boolean,
)

private sealed interface Route {
    data object Home : Route
    data class Game(val config: GameConfig) : Route
    data class Review(val fens: List<String>, val sans: List<String>, val playerSide: Side) : Route
}

@Composable
fun ChessKraftApp() {
    var route by remember { mutableStateOf<Route>(Route.Home) }
    var gameSeq by remember { mutableStateOf(0L) }

    when (val current = route) {
        Route.Home -> HomeScreen(
            onPlay = { difficulty, side, flipped ->
                gameSeq += 1
                route = Route.Game(GameConfig(gameSeq, difficulty, side, flipped))
            },
        )
        is Route.Game -> {
            val viewModel: GameViewModel = viewModel(
                key = "game-${current.config.key}",
                factory = GameViewModelFactory(current.config),
            )
            GameScreen(
                viewModel = viewModel,
                onRematch = {
                    gameSeq += 1
                    route = Route.Game(
                        current.config.copy(
                            key = gameSeq,
                            playerSide = current.config.playerSide.opponent(),
                        ),
                    )
                },
                onNewGame = { route = Route.Home },
                onOpenReview = {
                    val (fens, sans) = viewModel.exportHistory()
                    route = Route.Review(fens, sans, viewModel.playerSide)
                },
            )
        }
        is Route.Review -> ReviewScreen(
            fens = current.fens,
            sans = current.sans,
            playerSide = current.playerSide,
            onBack = { route = Route.Home },
        )
    }
}

private class GameViewModelFactory(private val config: GameConfig) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val viewModel = GameViewModel(
            playerSide = config.playerSide,
            difficulty = config.difficulty,
            engine = OwnEngine(Random(config.key)),
        )
        if (config.flipped) viewModel.flip()
        return viewModel as T
    }
}
