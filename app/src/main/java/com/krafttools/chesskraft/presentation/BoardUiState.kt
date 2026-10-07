/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft.presentation

import com.krafttools.chesskraft.domain.ChessMove
import com.krafttools.chesskraft.domain.GameResult
import com.krafttools.chesskraft.domain.PieceType
import com.krafttools.chesskraft.domain.Side
import com.krafttools.chesskraft.engine.Difficulty

/**
 * Everything the Game screen renders. Snapshots, never live objects: the
 * board draws this and nothing else.
 */
data class GameUiState(
    /** 64 piece codes (see PieceCode), a1 first. */
    val pieces: List<Int> = List(64) { 0 },
    val sideToMove: Side = Side.WHITE,
    val playerSide: Side = Side.WHITE,
    val difficulty: Difficulty = Difficulty.CASUAL,
    val selected: Int? = null,
    /** Legal targets for [selected]: destination -> isCapture. */
    val targets: Map<Int, Boolean> = emptyMap(),
    val lastMoveFrom: Int? = null,
    val lastMoveTo: Int? = null,
    /** King square when the side to move is in check, else null. */
    val checkSquare: Int? = null,
    val flipped: Boolean = false,
    val statusText: String = "White to move",
    val result: GameResult? = null,
    val sans: List<String> = emptyList(),
    /** Black pieces White has captured, strongest first. */
    val capturedByWhite: List<PieceType> = emptyList(),
    /** White pieces Black has captured, strongest first. */
    val capturedByBlack: List<PieceType> = emptyList(),
    val pendingPromotion: List<ChessMove> = emptyList(),
    val hintMove: ChessMove? = null,
    val aiThinking: Boolean = false,
    /** A draw offer is with the computer: it is thinking about its answer. */
    val drawOfferPending: Boolean = false,
    val soundOn: Boolean = true,
    /** Bumped on every illegal tap so the board can shake once. */
    val nudgeToken: Int = 0,
    val nudgeSquare: Int? = null,
    val canUndo: Boolean = false,
    /** Engine score for the position, White's point of view, null if unknown. */
    val evalCp: Int? = null,
    /** Clock banks in ms, null when the game has no clock. */
    val clockWhiteMs: Long? = null,
    val clockBlackMs: Long? = null,
)
