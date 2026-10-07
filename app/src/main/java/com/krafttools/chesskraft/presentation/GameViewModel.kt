/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krafttools.chesskraft.domain.ChessMove
import com.krafttools.chesskraft.domain.ChessClock
import com.krafttools.chesskraft.domain.GameResult
import com.krafttools.chesskraft.domain.GameTree
import com.krafttools.chesskraft.domain.PieceCode
import com.krafttools.chesskraft.domain.PieceType
import com.krafttools.chesskraft.domain.Position
import com.krafttools.chesskraft.domain.Side
import com.krafttools.chesskraft.domain.TapLogic
import com.krafttools.chesskraft.domain.TapOutcome
import com.krafttools.chesskraft.domain.parseUci
import com.krafttools.chesskraft.domain.pieceValue
import com.krafttools.chesskraft.domain.squareName
import com.krafttools.chesskraft.engine.Difficulty
import com.krafttools.chesskraft.engine.Engine
import com.krafttools.chesskraft.engine.OwnEngine
import com.krafttools.chesskraft.engine.UciMove
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Game flow. Tap-tap and drag both land here as square pairs, legality comes
 * from the domain (never the board), and the engine only ever sees FEN.
 */
class GameViewModel(
    val playerSide: Side = Side.WHITE,
    val difficulty: Difficulty = Difficulty.CASUAL,
    private val engine: Engine = FakeEngine(),
    private val aiDispatcher: CoroutineDispatcher = Dispatchers.Default,
    var soundPlayer: SoundPlayer? = null,
    private val timeControlMs: Long? = null,
    private val clockTickMs: Long = 100L,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {
    private val tree = GameTree()
    private var clock: ChessClock? = null
    private var clockStartedAtMs: Long? = null
    /** Set when a clock flags; the tree position itself is not terminal. */
    private var flagLoser: Side? = null

    private val _state = MutableStateFlow(
        GameUiState(playerSide = playerSide, difficulty = difficulty),
    )
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    init {
        refresh()
        startClockIfNeeded()
        maybeAiMove()
    }

    // -- Input ------------------------------------------------------------

    /** Tap-tap: one square per call. */
    fun onTap(square: Int) {
        val current = _state.value
        if (current.result != null || current.aiThinking) return
        if (tree.current().sideToMove != playerSide) return
        when (val outcome = TapLogic.resolveTap(tree.current(), current.selected, square)) {
            is TapOutcome.Selected -> _state.value = current.copy(
                selected = outcome.from,
                targets = outcome.targets.associate { it.to to isCapture(it) },
                hintMove = null,
            )
            is TapOutcome.MoveReady -> playPlayerMove(outcome.move)
            is TapOutcome.PromotionNeeded -> _state.value = current.copy(
                pendingPromotion = outcome.options,
                hintMove = null,
            )
            TapOutcome.Deselected -> _state.value = current.copy(
                selected = null,
                targets = emptyMap(),
            )
            TapOutcome.Nudge -> _state.value = current.copy(
                nudgeToken = current.nudgeToken + 1,
                nudgeSquare = square,
            )
        }
    }

    /** Drag-and-drop: from pickup square to drop square. */
    fun onDrop(from: Int, to: Int) {
        val current = _state.value
        if (current.result != null || current.aiThinking) return
        if (tree.current().sideToMove != playerSide) return
        when (val outcome = TapLogic.resolveDrop(tree.current(), from, to)) {
            is TapOutcome.MoveReady -> playPlayerMove(outcome.move)
            is TapOutcome.PromotionNeeded -> _state.value = current.copy(
                selected = null,
                targets = emptyMap(),
                pendingPromotion = outcome.options,
                hintMove = null,
            )
            else -> _state.value = current.copy(
                selected = null,
                targets = emptyMap(),
                nudgeToken = current.nudgeToken + 1,
                nudgeSquare = to,
            )
        }
    }

    /** Promotion dialog choice. Queen is pre-selected in the UI; this plays it. */
    fun onPromote(choice: ChessMove) {
        if (_state.value.pendingPromotion.none { it == choice }) return
        _state.value = _state.value.copy(pendingPromotion = emptyList())
        playPlayerMove(choice)
    }

    fun dismissPromotion() {
        _state.value = _state.value.copy(
            pendingPromotion = emptyList(),
            selected = null,
            targets = emptyMap(),
        )
    }

    // -- Toolbar ----------------------------------------------------------

    /**
     * Unlimited round-trip undo vs the AI: takes back the AI reply and the
     * player's move together, so it is always the player's turn again.
     */
    fun undo() {
        val current = _state.value
        if (current.aiThinking || !tree.canUndo()) return
        tree.undoPly()
        if (tree.current().sideToMove != playerSide && tree.canUndo()) tree.undoPly()
        _state.value = current.copy(
            selected = null,
            targets = emptyMap(),
            pendingPromotion = emptyList(),
            hintMove = null,
        )
        refresh()
    }

    /** Shows the engine's suggestion until the next tap. The stub answers. */
    fun hint() {
        val current = _state.value
        if (current.result != null || current.aiThinking) return
        if (tree.current().sideToMove != playerSide) return
        viewModelScope.launch(aiDispatcher) {
            val reply = askEngine(tree.currentFen())
            val move = coerceEngineMove(tree.current(), reply)
            if (move != null) {
                _state.value = _state.value.copy(hintMove = move)
            }
        }
    }

    fun newGame() {
        while (tree.canUndo()) tree.undoPly()
        flagLoser = null
        _state.value = GameUiState(playerSide = playerSide, difficulty = difficulty)
        refresh()
        startClockIfNeeded()
        maybeAiMove()
    }

    fun resign() {
        val current = _state.value
        if (current.result != null) return
        _state.value = current.copy(result = GameResult.Resigned(playerResigned = true))
        refreshStatus()
    }

    fun flip() {
        _state.value = _state.value.copy(flipped = !_state.value.flipped)
    }

    fun toggleSound() {
        val next = !_state.value.soundOn
        soundPlayer?.enabled = next
        _state.value = _state.value.copy(soundOn = next)
    }

    /** Review hand-off: full FEN history plus the SAN list. */
    fun exportHistory(): Pair<List<String>, List<String>> =
        tree.fenHistory() to tree.sanList()

    // -- Clock ------------------------------------------------------------

    /**
     * The clock is elapsed-driven, not a coroutine loop: a ticker inside the
     * ViewModel fights the test scheduler (found the hard way — a `delay`
     * loop under runTest spins at 100% CPU forever) and burns a core for a
     * number nobody reads most of the time. Instead the UI asks for elapsed
     * time, and this decides. Same state, one authority, testable without a
     * scheduler.
     */
    fun startClockIfNeeded() {
        val total = timeControlMs
        flagLoser = null
        if (total == null) {
            clock = null
            clockStartedAtMs = null
            _state.value = _state.value.copy(clockWhiteMs = null, clockBlackMs = null)
            return
        }
        clock = ChessClock(total, total)
        clockStartedAtMs = null
        _state.value = _state.value.copy(clockWhiteMs = total, clockBlackMs = total)
    }

    /**
     * Burns [elapsedMs] from the side to move and publishes both banks.
     * Called by the UI's ticker, and by tests directly. Idempotent-safe to
     * call with 0.
     */
    fun onElapsed(elapsedMs: Long) {
        val active = clock ?: return
        val flag = flagLoser
        if (flag != null || elapsedMs <= 0L) return
        val mover = tree.current().sideToMove
        val flagged = active.tick(mover, elapsedMs)
        _state.value = _state.value.copy(
            clockWhiteMs = active.whiteMs,
            clockBlackMs = active.blackMs,
        )
        if (flagged != null) {
            flagLoser = flagged
            clockStartedAtMs = null
            refresh()
            refreshStatus()
        }
    }

    /** Begins (or resumes) measuring the current turn for the UI ticker. */
    fun markClockStart(nowMs: Long) {
        if (clock != null && flagLoser == null) clockStartedAtMs = nowMs
    }

    fun clockStartedAt(): Long? = clockStartedAtMs

    /** One-second heartbeats keep ticking while the AI thinks; moves reset it. */
    private fun stopClockSegment() {
        clockStartedAtMs = null
    }

    // -- Internals --------------------------------------------------------

    private fun playPlayerMove(move: ChessMove) {
        if (tree.apply(move) == null) {
            _state.value = _state.value.copy(
                nudgeToken = _state.value.nudgeToken + 1,
                nudgeSquare = move.to,
            )
            return
        }
        soundPlayer?.playTap()
        stopClockSegment()
        refresh()
        maybeAiMove()
    }

    private fun maybeAiMove() {
        val position = tree.current()
        if (flagLoser != null || tree.result(playerSide) != null) {
            refreshStatus()
            return
        }
        if (position.sideToMove == playerSide) return
        _state.value = _state.value.copy(aiThinking = true)
        refreshStatus()
        viewModelScope.launch {
            val fen = position.toFen()
            val reply = withContext(aiDispatcher) { askEngine(fen) }
            val move = coerceEngineMove(tree.current(), reply)
                ?: tree.current().generateLegalMoves().firstOrNull()
            if (move != null) tree.apply(move)
            soundPlayer?.playTap()
            stopClockSegment()
            _state.value = _state.value.copy(aiThinking = false)
            refresh()
        }
    }

    /**
     * Difficulty-aware engine call. OwnEngine gets the difficulty overload (blunder
     * model per spec); any other Engine gets the plain limits call. Suspended by
     * the caller on [aiDispatcher].
     */
    private fun askEngine(fen: String): UciMove {
        val own = engine as? OwnEngine
        return own?.findBestMove(fen, difficulty)
            ?: engine.findBestMove(fen, limitsFor(difficulty))
    }

    private fun isCapture(move: ChessMove): Boolean {
        val position = tree.current()
        return position.board[move.to] != 0 ||
            (PieceCode.typeOf(position.board[move.from]) == PieceType.PAWN &&
                move.to == position.epSquare)
    }

    private fun refresh() {
        val position = tree.current()
        val previous = _state.value
        val legal = position.generateLegalMoves()
        val inCheck = position.isInCheck()
        val flag = flagLoser
        val result = if (flag != null) {
            GameResult.TimeForfeit(loser = flag, playerSide = playerSide)
        } else {
            tree.result(playerSide)
        }
        val lastUci = if (tree.plyCount > 0) {
            // Re-derive last move squares from the FEN trail: cheap and truthful.
            lastMoveOf(tree)
        } else {
            null
        }
        _state.value = previous.copy(
            pieces = position.board.toList(),
            sideToMove = position.sideToMove,
            selected = if (result != null) null else previous.selected?.takeIf { square ->
                legal.any { it.from == square }
            },
            targets = if (result != null || previous.selected == null) {
                emptyMap()
            } else {
                legal.filter { it.from == previous.selected }
                    .associate { it.to to (position.board[it.to] != 0) }
            },
            lastMoveFrom = lastUci?.from,
            lastMoveTo = lastUci?.to,
            checkSquare = if (inCheck) position.kingSquare(position.sideToMove) else null,
            statusText = statusText(position, result, playerSide),
            result = result,
            sans = tree.sanList(),
            capturedByWhite = captured(position, Side.BLACK),
            capturedByBlack = captured(position, Side.WHITE),
            canUndo = tree.canUndo() && !previous.aiThinking,
            clockWhiteMs = clock?.whiteMs,
            clockBlackMs = clock?.blackMs,
        )
    }

    private fun refreshStatus() {
        val position = tree.current()
        val flag = flagLoser
        val result = if (flag != null) {
            GameResult.TimeForfeit(loser = flag, playerSide = playerSide)
        } else {
            tree.result(playerSide)
        }
        _state.value = _state.value.copy(
            statusText = statusText(position, result, playerSide),
            result = tree.result(playerSide) ?: _state.value.result,
            canUndo = tree.canUndo() && !_state.value.aiThinking,
        )
    }

    private fun lastMoveOf(tree: GameTree): ChessMove? {
        val history = tree.fenHistory()
        if (history.size < 2) return null
        val before = Position.fromFen(history[history.size - 2]).getOrNull() ?: return null
        val after = Position.fromFen(history[history.size - 1]).getOrNull() ?: return null
        // Squares that changed between the two snapshots.
        val changed = (0..63).filter { before.board[it] != after.board[it] }
        val mover = before.sideToMove
        // Destination: the square holding the mover's piece that did not before
        // (or the rook's square in castling — the king's move is the headline).
        val kingFrom = before.kingSquare(mover)
        val kingTo = after.kingSquare(mover)
        if (kingFrom != kingTo) return ChessMove(kingFrom, kingTo)
        val to = changed.firstOrNull { after.board[it] != 0 && PieceCode.sideOf(after.board[it]) == mover }
            ?: return null
        // Origin: the emptied square that held the mover's piece. Side first
        // (en passant empties two squares — the victim's sorts lower), then
        // type so promotions still resolve to the pawn's square.
        val toType = PieceCode.typeOf(after.board[to])
        val emptied = changed.filter { it != to && before.board[it] != 0 && after.board[it] == 0 }
        val from = emptied.firstOrNull {
            PieceCode.sideOf(before.board[it]) == mover &&
                (PieceCode.typeOf(before.board[it]) == toType ||
                    PieceCode.typeOf(before.board[it]) == PieceType.PAWN)
        } ?: return null
        val promo = PieceCode.typeOf(after.board[to])
            ?.takeIf { PieceCode.typeOf(before.board[from]) == PieceType.PAWN && it != PieceType.PAWN && it != PieceType.KING }
        return ChessMove(from, to, promo)
    }

    private fun captured(position: Position, victimSide: Side): List<PieceType> {
        val startCounts = mapOf(
            PieceType.PAWN to 8,
            PieceType.KNIGHT to 2,
            PieceType.BISHOP to 2,
            PieceType.ROOK to 2,
            PieceType.QUEEN to 1,
        )
        val onBoard = mutableMapOf<PieceType, Int>()
        for (sq in 0..63) {
            val code = position.board[sq]
            if (PieceCode.sideOf(code) == victimSide) {
                val type = PieceCode.typeOf(code)
                if (type != null && type != PieceType.KING) {
                    onBoard[type] = (onBoard[type] ?: 0) + 1
                }
            }
        }
        // Promotion edge: extra queens on the board shrink the missing count,
        // never below zero — the strip is garnish, not accounting.
        val missing = ArrayList<PieceType>()
        for ((type, start) in startCounts) {
            val gone = (start - (onBoard[type] ?: 0)).coerceAtLeast(0)
            repeat(gone) { missing.add(type) }
        }
        missing.sortByDescending { pieceValue(it) }
        return missing
    }

    /** Test seam: drive moves without an engine round-trip. */
    internal fun testApplyUci(uci: String): Boolean {
        val move = parseUci(uci) ?: return false
        val ok = tree.apply(move) != null
        if (ok) refresh()
        return ok
    }
}

/** Plain-English status line. No codes, noEval numbers. */
fun statusText(position: Position, result: GameResult?, playerSide: Side): String {
    if (result != null) return result.title + " — " + result.reason
    val mover = if (position.sideToMove == Side.WHITE) "White" else "Black"
    if (position.isInCheck()) {
        return if (position.sideToMove == playerSide) {
            "Check — your king is under attack."
        } else {
            "Check — $mover to move."
        }
    }
    if (position.sideToMove == playerSide) {
        return if (playerSide == Side.WHITE) "Your move — White to play." else "Your move — Black to play."
    }
    return "$mover to move."
}

/** Announcement text for TalkBack on every state change. */
fun announceText(position: Position, lastSan: String?, result: GameResult?): String {
    val mover = if (position.sideToMove == Side.WHITE) "White" else "Black"
    val movePart = if (lastSan != null) "Last move $lastSan. " else ""
    val resultPart = result?.let { "${it.title}. ${it.reason}" } ?: "$mover to move."
    if (result == null && position.isInCheck()) {
        val kingSq = squareName(position.kingSquare(position.sideToMove))
        return "${movePart}Check — the ${mover.lowercase()} king on $kingSq is under attack."
    }
    return movePart + resultPart
}
