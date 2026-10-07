/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.krafttools.chesskraft.data.FileGameStore
import com.krafttools.chesskraft.data.GameStore
import com.krafttools.chesskraft.domain.GameReviewResult
import com.krafttools.chesskraft.domain.Side
import com.krafttools.chesskraft.engine.Difficulty
import com.krafttools.chesskraft.engine.OwnEngine
import com.krafttools.chesskraft.presentation.GameViewModel
import com.krafttools.chesskraft.presentation.RestoredGame
import com.krafttools.chesskraft.presentation.Route
import com.krafttools.chesskraft.presentation.decodeRoute
import com.krafttools.chesskraft.presentation.encodeRoute
import androidx.compose.ui.platform.LocalContext
import com.krafttools.chesskraft.ui.screens.GameScreen
import com.krafttools.chesskraft.ui.screens.HomeScreen
import com.krafttools.chesskraft.ui.screens.ReviewScreen
import kotlin.random.Random

@Composable
fun ChessKraftApp() {
    // Route and its key sequence survive config change (rotation, font
    // scale): without this the activity recreate drops an in-progress game
    // back to Home and strands the retained GameViewModel. The ViewModel
    // itself is untouched — it already survives, keyed on the config key.
    var route by rememberSaveable(stateSaver = RouteSaver) { mutableStateOf(Route.Home) }
    var gameSeq by rememberSaveable { mutableLongStateOf(0L) }
    val context = LocalContext.current
    val store = remember { FileGameStore.inInternalStorage(context) }

    // Is there a game to continue? Read once, cheaply: this asks whether the
    // file holds an unfinished game, it does NOT replay it. The replay happens
    // on the game screen's own ViewModel, so no throwaway ViewModel is ever
    // constructed. An unreadable save answers false, which is why a corrupt file
    // can never hold the door shut.
    var hasSavedGame by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        hasSavedGame = store.load().getOrNull()?.inProgress != null
    }

    when (val current = route) {
        Route.Home -> HomeScreen(
            saved = hasSavedGame,
            onContinue = {
                gameSeq += 1
                // The settings come back from the save itself when the game
                // screen restores it; the config only names the new ViewModel.
                route = Route.Game(
                    key = gameSeq,
                    difficulty = Difficulty.CASUAL,
                    playerSide = Side.WHITE,
                    flipped = false,
                    timeControlMs = null,
                    resume = true,
                )
            },
            onPlay = { difficulty, side, flipped, timeControlMs ->
                gameSeq += 1
                route = Route.Game(gameSeq, difficulty, side, flipped, timeControlMs)
            },
        )
        is Route.Game -> {
            val viewModel: GameViewModel = viewModel(
                key = "game-${current.key}",
                factory = GameViewModelFactory(current, store),
            )
            if (current.resume) {
                // Replay the saved moves into this ViewModel, once, on entry.
                LaunchedEffect(viewModel) { viewModel.restore() }
            }
            GameScreen(
                viewModel = viewModel,
                onRematch = {
                    gameSeq += 1
                    route = Route.Game(
                        key = gameSeq,
                        difficulty = current.difficulty,
                        playerSide = current.playerSide.opponent(),
                        flipped = current.flipped,
                        timeControlMs = current.timeControlMs,
                    )
                },
                onNewGame = { route = Route.Home },
                onOpenReview = { review ->
                    route = Route.Review(review, viewModel.playerSide)
                },
            )
        }
        is Route.Review -> ReviewScreen(
            review = current.review,
            playerSide = current.playerSide,
        )
    }
}

private class GameViewModelFactory(
    private val config: Route.Game,
    private val store: GameStore,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val viewModel = GameViewModel(
            playerSide = config.playerSide,
            difficulty = config.difficulty,
            engine = OwnEngine(Random(config.key)),
            timeControlMs = config.timeControlMs,
            store = store,
        )
        if (config.flipped) viewModel.flip()
        return viewModel as T
    }
}

/**
 * `rememberSaveable` needs a primitive; the route is not one. encode/decode live
 * in Route.kt and are unit-tested there, including the damaged-value paths, so
 * this stays a one-liner.
 */
private val RouteSaver: Saver<Route, String> = Saver(
    save = { encodeRoute(it) },
    restore = { decodeRoute(it) },
)
