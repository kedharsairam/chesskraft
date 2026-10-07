/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft.presentation

import androidx.lifecycle.ViewModel
import com.kraft.core.KraftResult
import androidx.lifecycle.viewModelScope
import com.krafttools.chesskraft.data.GameStore
import com.krafttools.chesskraft.data.InMemoryGameStore
import com.krafttools.chesskraft.domain.ChessMove
import com.krafttools.chesskraft.domain.ChessClock
import com.krafttools.chesskraft.domain.FinishedGame
import com.krafttools.chesskraft.domain.GameResult
import com.krafttools.chesskraft.domain.GameSave
import com.krafttools.chesskraft.domain.GameTree
import com.krafttools.chesskraft.domain.SavedGame
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
import com.krafttools.chesskraft.engine.SearchLimits
import com.krafttools.chesskraft.engine.UciMove
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Game flow. Tap-tap and drag both land here as square pairs, legality comes
 * from the domain (never the board), and the engine only ever sees FEN.
 */
class GameViewModel(
    playerSide: Side = Side.WHITE,
    difficulty: Difficulty = Difficulty.CASUAL,
    private val engine: Engine = FakeEngine(),
    private val aiDispatcher: CoroutineDispatcher = Dispatchers.Default,
    var soundPlayer: SoundPlayer? = null,
    timeControlMs: Long? = null,
    private val clockTickMs: Long = 100L,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    /**
     * Where the game is saved. Defaults to a store that keeps nothing, so a
     * ViewModel built without one still works and a test can pass its own.
     */
    private val store: GameStore = InMemoryGameStore(),
) : ViewModel() {
    // Not vals: a restored game brings its own settings, and everything
    // downstream — legality, the status line, the search limits, the save —
    // reads these rather than the constructor arguments.
    /**
     * Which side the player is on. Restoring a saved game sets this, so it is
     * the value the whole ViewModel is working from, not the one it was built
     * with.
     */
    var playerSide: Side = playerSide
        private set

    var difficulty: Difficulty = difficulty
        private set

    var timeControlMs: Long? = timeControlMs
        private set

    // Not a val: restoring a saved game replaces the tree wholesale with one that
    // has already replayed the saved moves through the rules.
    private var tree: GameTree = GameTree()
    private var clock: ChessClock? = null
    private var clockStartedAtMs: Long? = null
    /** Set when a clock flags; the tree position itself is not terminal. */
    private var flagLoser: Side? = null

    /**
     * Finished games, newest first, cached from the store so that saving a move
     * never has to re-read the file. [historyLoaded] is what stops the first
     * save from writing an empty history over the one already on disk.
     */
    private var history: List<FinishedGame> = emptyList()

    private var historyLoaded: Boolean = false

    /**
     * The result already written to the history. A game-over path can be reached
     * twice for one game — the sheet asks, and so does the state refresh — and
     * the history should hold the game once.
     */
    private var recordedResult: GameResult? = null

    private val _state = MutableStateFlow(
        GameUiState(playerSide = playerSide, difficulty = difficulty),
    )
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    init {
        refresh()
        requestEval()
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
            drawOfferPending = false,
        )
        refresh()
        // An undo changes the position, so the save has to follow it back.
        saveProgress()
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

    /**
     * Offers a draw and answers it the way a person would: from the position
     * on the board right now, which is always the position the computer just
     * replied into — its own view of the game, not a re-search of the player's
     * move from before.
     *
     * The answer is the engine's judgement, never a coin toss, so the same
     * position always gets the same answer. Accepting and declining differ
     * only in what they publish: an accepted offer sets [GameResult.DrawAgreed],
     * and a declined one leaves the game exactly as it was with the pending
     * flag cleared. Nothing else in the state moves.
     *
     * Only legal on the player's own turn: mid-search the computer is not
     * listening, and after the game is over there is nothing to agree to.
     * The search runs on [aiDispatcher] — the caller never waits on it.
     */
    fun offerDraw() {
        val current = _state.value
        if (current.result != null || current.aiThinking || current.drawOfferPending) return
        if (tree.current().sideToMove != playerSide) return
        val fen = tree.currentFen()
        _state.value = current.copy(drawOfferPending = true)
        viewModelScope.launch(aiDispatcher) {
            val scoreCp = drawOfferScore(fen)
            // An offer that outlived its position is not an offer any more:
            // undo, a new game or a resign during the search clears the flag,
            // and the fen check catches anything that changed the board.
            val stillCurrent = _state.value.drawOfferPending && tree.currentFen() == fen
            _state.value = _state.value.copy(drawOfferPending = false)
            if (scoreCp != null && stillCurrent && abs(scoreCp) < DRAW_ACCEPT_LIMIT_CP) {
                _state.value = _state.value.copy(result = GameResult.DrawAgreed)
                playResultCue()
                refreshStatus()
                recordFinishedGame()
            }
        }
    }

    /**
     * The computer's score for the offered position, or null when it has none
     * to give.
     *
     * Null means decline. An engine that throws, and an engine that answers
     * with the null move ["0000"], have both said they cannot see this
     * position; agreeing to end a game on a score nobody could produce would
     * be inventing a verdict, so the conservative answer stands.
     */
    private fun drawOfferScore(fen: String): Int? = try {
        val analysis = engine.analyze(fen, drawOfferLimits())
        if (analysis.bestMove.text == NULL_UCI) null else analysis.scoreCp
    } catch (_: RuntimeException) {
        null
    }

    /**
     * Cheap on purpose. A draw offer is answered while the player waits, so
     * the answer must cost about one human think: depth 4 is enough to tell a
     * balanced position from a won one, which is the only question being
     * asked of it.
     */
    fun drawOfferLimits(): SearchLimits =
        SearchLimits(maxDepth = DRAW_OFFER_DEPTH, maxMillis = DRAW_OFFER_MILLIS)

    fun newGame() {
        while (tree.canUndo()) tree.undoPly()
        flagLoser = null
        recordedResult = null
        _state.value = GameUiState(playerSide = playerSide, difficulty = difficulty)
        refresh()
        requestEval()
        startClockIfNeeded()
        // The old game is gone, so the save must not still offer it for resume.
        // This is a position change like any other and is written the same way.
        saveProgress()
        maybeAiMove()
    }

    fun resign() {
        val current = _state.value
        if (current.result != null) return
        _state.value = current.copy(
            result = GameResult.Resigned(playerResigned = true),
            drawOfferPending = false,
        )
        playResultCue()
        refreshStatus()
        recordFinishedGame()
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

    /**
     * Review hand-off: the limits a review screen searches each position with.
     * Depth 6 is deep enough to name the missed mate and the hanging queen —
     * which is the whole point of reviewing — and 1.5s per position is the same
     * ceiling the engine plays under, so grading a hundred-move game stays a
     * bounded amount of work instead of a freeze. Never raised per difficulty:
     * a review is a fact about the game, not about how the game was played.
     */
    suspend fun buildReview(): com.krafttools.chesskraft.domain.GameReviewResult? =
        withContext(aiDispatcher) {
            val sans = tree.sanList()
            if (sans.isEmpty()) return@withContext null
            com.krafttools.chesskraft.domain.GameReview.build(
                fens = tree.fenHistory(),
                sans = sans,
                analyze = { fen -> engine.analyze(fen, reviewLimits()) },
            )
        }

    /**
     * Review grading is deliberately independent of difficulty: a lesson that
     * grades you against a weakened engine teaches you nothing. Depth 6 with a
     * 1.5s cap per position.
     */
    fun reviewLimits(): SearchLimits = SearchLimits(maxDepth = 6, maxMillis = 1500L)

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
            playResultCue()
            refresh()
            refreshStatus()
            // The game ended on the clock, so both banks are recorded here — the
            // only time a clock value is written outside a move boundary.
            recordFinishedGame()
        }
    }

    /**
     * Begins (or resumes) measuring the current turn for the UI ticker.
     *
     * The heartbeat calls this on every tick, so it is also the save point only
     * when a turn genuinely starts — that is, when no segment was already
     * running. Saving on every call would be four disk writes a second, which is
     * the one thing persistence here must not cost.
     */
    fun markClockStart(nowMs: Long) {
        val startingNewTurn = clockStartedAtMs == null
        if (clock != null && flagLoser == null) clockStartedAtMs = nowMs
        if (startingNewTurn) saveProgress()
    }

    fun clockStartedAt(): Long? = clockStartedAtMs

    /** One-second heartbeats keep ticking while the AI thinks; moves reset it. */
    private fun stopClockSegment() {
        clockStartedAtMs = null
    }

    // -- Internals --------------------------------------------------------

    private fun playPlayerMove(move: ChessMove) {
        // The position the move came from: a capture is only knowable against it.
        val before = tree.current()
        if (tree.apply(move) == null) {
            _state.value = _state.value.copy(
                nudgeToken = _state.value.nudgeToken + 1,
                nudgeSquare = move.to,
            )
            return
        }
        playMoveCue(before, tree.current(), isCapture(before, move))
        stopClockSegment()
        refresh()
        // A move boundary: both clock banks are what they will be recorded as.
        saveProgress()
        recordFinishedGame()
        maybeAiMove()
    }

    /**
     * Queues a live evaluation of the current position for the bar. Depth 4,
     * 300ms, off the UI thread, and it never blocks a move: a stale answer is
     * dropped rather than shown against a position it was not asked about.
     */
    private fun requestEval() {
        val fen = tree.currentFen()
        viewModelScope.launch {
            val analysis = withContext(aiDispatcher) {
                runCatching { engine.analyze(fen, evalLimits()) }.getOrNull()
            }
            if (analysis == null || tree.currentFen() != fen) return@launch
            val cp = if (tree.current().sideToMove == Side.WHITE) {
                analysis.scoreCp
            } else {
                -analysis.scoreCp
            }
            _state.value = _state.value.copy(evalCp = cp)
        }
    }

    /**
     * The bar needs a signal, not a judgement: depth 2 in 200ms is plenty to
     * move a marker, and it keeps the bar off the same budget the draw answer
     * and the review spend. Deliberately distinct from both so the three are
     * never confused for each other.
     */
    fun evalLimits(): SearchLimits = SearchLimits(maxDepth = 2, maxMillis = 200L)

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
            // The answer is about [fen]. If the board has moved on — an undo, a
            // new game, a restore — applying it here would play a reply to a
            // position the engine never saw, so it is dropped instead.
            if (tree.currentFen() != fen) {
                _state.value = _state.value.copy(aiThinking = false)
                refresh()
                return@launch
            }
            // Judge the reply against the position the engine actually answered
            // for, not against a tree that may have moved on underneath it.
            val move = coerceEngineMove(position, reply)
                ?: position.generateLegalMoves().firstOrNull()
            if (move != null && tree.apply(move) != null) {
                playMoveCue(position, tree.current(), isCapture(position, move))
            }
            stopClockSegment()
            _state.value = _state.value.copy(aiThinking = false)
            refresh()
            // Same reason as the player's move: this is a move boundary, so the
            // save reflects the position the computer left.
            saveProgress()
            recordFinishedGame()
            requestEval()
        }
    }

    /**
     * The player's move and the engine's reply are the same event from the
     * board's point of view, so they share one call: [before] is the position
     * the move was played from, [after] the one it produced, and [wasCapture] is
     * the capture test run against [before]. The choice between cues is
     * [cueFor]'s job, not this method's.
     */
    private fun playMoveCue(before: Position, after: Position, wasCapture: Boolean) {
        soundPlayer?.playFor(
            cueFor(
                wasCapture = wasCapture,
                isCheckNow = after.isInCheck(),
                wasCheckBefore = before.isInCheck(),
                resultJustArrived = tree.result(playerSide) != null,
            ),
        )
    }

    /**
     * A game can also end without a move — a resign, or a flag. The board has
     * not changed and no move was played, so the only fact here is the result,
     * and the cue rule says what that sounds like.
     */
    private fun playResultCue() {
        soundPlayer?.playFor(
            cueFor(
                wasCapture = false,
                isCheckNow = tree.current().isInCheck(),
                wasCheckBefore = false,
                resultJustArrived = true,
            ),
        )
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

    /** Capture test against the position as it stands now. */
    private fun isCapture(move: ChessMove): Boolean = isCapture(tree.current(), move)

    /**
     * Was [move] a capture in [position]? Occupied square, or an en passant
     * that takes a pawn off a square the move itself never touches. Callers
     * that need this must pass the position *before* the move — afterwards the
     * victim is gone and every move looks like a quiet one.
     */
    private fun isCapture(position: Position, move: ChessMove): Boolean {
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

    // -- Persistence ------------------------------------------------------

    /**
     * Writes the in-progress game to the store.
     *
     * Called on the events that change what a restore would produce — a move, an
     * undo, a new game, a turn starting — and never on a clock tick, which would
     * be a disk write four times a second for a number nobody reads that often.
     * The JSON is a few hundred bytes; writing it inline on the calling thread
     * costs far less than introducing a dispatcher to move it off.
     *
     * A store that fails is not worth breaking a move over, so the result is
     * ignored here. The next save is another chance.
     */
    fun saveProgress() {
        val current = _state.value
        store.save(
            GameSave(
                inProgress = SavedGame(
                    playerSide = playerSide,
                    difficulty = difficulty,
                    flipped = current.flipped,
                    timeControlMs = timeControlMs,
                    clockWhiteMs = current.clockWhiteMs,
                    clockBlackMs = current.clockBlackMs,
                    moves = tree.uciList(),
                ),
                history = cachedHistory(),
            ),
        )
    }

    /**
     * Forgets the in-progress game but keeps the finished-game history.
     *
     * Used when the player declines to resume: the next game must not offer the
     * one they walked away from, and losing their record of finished games to
     * the same action would be a poor trade.
     */
    fun clearProgress() {
        store.save(GameSave(inProgress = null, history = cachedHistory()))
    }

    /**
     * Replays a saved game and hands back the settings it was saved with.
     *
     * Null for every reason a resume should not happen: nothing saved, a save
     * this build cannot read, a move list that no longer replays legally. All of
     * them answer null rather than throwing, because this is on the launch path
     * and a corrupt file must cost the player a game, not the app.
     *
     * The moves are re-applied to a fresh tree and the position is re-derived by
     * the rules, so what comes back is the rules' own position — the save cannot
     * assert a board.
     */
    suspend fun restore(): RestoredGame? = try {
        val save = (store.load() as? KraftResult.Success)?.data ?: return null
        val saved = save.inProgress ?: return null
        val replayed = replayFromStart(saved.moves) ?: return null
        history = save.history
        historyLoaded = true
        applyRestored(saved, replayed)
        RestoredGame(
            difficulty = saved.difficulty,
            playerSide = saved.playerSide,
            flipped = saved.flipped,
            timeControlMs = saved.timeControlMs,
            clockWhiteMs = saved.clockWhiteMs,
            clockBlackMs = saved.clockBlackMs,
            moves = saved.moves,
        )
    } catch (e: RuntimeException) {
        // Belt and braces. Every step above already answers null rather than
        // throwing — a corrupt save must cost the player their game, not the
        // launch — and this is the last thing between a hand-edited file and a
        // crash on the way in.
        null
    }

    /**
     * Replays [ucis] from the standard start position.
     *
     * A move that no longer parses or no longer applies means the save is not
     * this build's save, or was written by rules that have since changed. Both
     * are answered with null: the honest response to a game that cannot be
     * rebuilt is to start a new one.
     */
    private fun replayFromStart(ucis: List<String>): GameTree? {
        val replayed = GameTree()
        for (uci in ucis) {
            val move = parseUci(uci) ?: return null
            if (replayed.apply(move) == null) return null
        }
        return replayed
    }

    /**
     * Puts a replayed game back in front of the player, clocks and settings
     * included.
     *
     * The saved side and difficulty are adopted rather than merely reported:
     * legality, the status line and the search limits all read them, so leaving
     * the constructor's values in place would have the restored game judged as
     * the wrong player.
     */
    private fun applyRestored(saved: SavedGame, replayed: GameTree) {
        playerSide = saved.playerSide
        difficulty = saved.difficulty
        timeControlMs = saved.timeControlMs
        val restoredClock = if (saved.timeControlMs != null) {
            ChessClock(saved.timeControlMs, saved.timeControlMs)
                .also { clock ->
                    if (saved.clockWhiteMs != null && saved.clockBlackMs != null) {
                        clock.restore(saved.clockWhiteMs, saved.clockBlackMs)
                    }
                }
        } else {
            null
        }
        clock = restoredClock
        clockStartedAtMs = null
        flagLoser = null
        recordedResult = null
        tree = replayed
        _state.value = _state.value.copy(
            playerSide = saved.playerSide,
            difficulty = saved.difficulty,
            flipped = saved.flipped,
            clockWhiteMs = restoredClock?.whiteMs,
            clockBlackMs = restoredClock?.blackMs,
        )
        refresh()
        // The app may have been killed with the computer's move still owed — the
        // player had just moved when the process went. Asking for it here is the
        // only thing that gets the game moving again; if it is the player's
        // turn, this returns without searching.
        maybeAiMove()
    }

    /**
     * Pushes the finished game into the history and clears the in-progress save,
     * so a game that is over is not also offered for resuming.
     *
     * Called for every ending — checkmate, stalemate, the draws, a resign, a
     * flag — and writes one entry per game: [recordedResult] remembers what was
     * written, because the game-over state is reached from more than one path
     * and the history should not gain the same game twice.
     *
     * Does nothing when the game has not ended. A caller can wire this to any
     * game-over event without having to know whether it is the first one.
     */
    fun recordFinishedGame() {
        val result = _state.value.result ?: return
        if (recordedResult == result) return
        recordedResult = result
        val finished = GameSave(inProgress = null, history = cachedHistory()).withFinished(
            FinishedGame(
                result = result.title,
                moveCount = tree.plyCount,
                difficulty = difficulty,
                playerSide = playerSide,
                endedAtMs = nowMs(),
            ),
        )
        history = finished.history
        store.save(finished)
    }

    /**
     * The finished-game history, read once.
     *
     * The first call loads it from the store; after that the cached copy is
     * authoritative, because this runs inside every save and re-reading the file
     * on each move would be a read per move for a list that changes once per
     * game. A failed load is an empty history — losing a record is better than
     * losing the game being saved.
     */
    private fun cachedHistory(): List<FinishedGame> {
        if (!historyLoaded) {
            history = (store.load() as? KraftResult.Success)?.data?.history ?: emptyList()
            historyLoaded = true
        }
        return history
    }
}

/** A game picked back up, with the settings it was left on. */
data class RestoredGame(
    val difficulty: Difficulty,
    val playerSide: Side,
    val flipped: Boolean,
    val timeControlMs: Long?,
    val clockWhiteMs: Long?,
    val clockBlackMs: Long?,
    /** UCI moves, oldest first. */
    val moves: List<String>,
)

/**
 * The computer's bar for agreeing to a draw, in centipawns.
 *
 * 200cp is a knight and a bit, or two clean pawns: the smallest edge that is
 * worth *declining* a draw over. Below it the game is close and taking half
 * a point is the right call for both sides; at or above it one side is winning
 * and settling would throw the game away. A mate scores 29000, so a
 * computer that is mating always declines.
 *
 * Deliberately well clear of the noise a shallow search produces. A depth-4
 * evaluation of an even position wanders, and the limit was chosen so that
 * noise cannot reach the bar: roughly even game positions measure inside
 * ±80cp, and a clearly winning one measures several hundred up. A bar at 200
 * is far enough from both that neither side of the decision is close.
 */
const val DRAW_ACCEPT_LIMIT_CP = 200

/** Depth of the quick evaluation behind a draw answer. See [DRAW_ACCEPT_LIMIT_CP]. */
const val DRAW_OFFER_DEPTH = 4

/** Ceiling for that evaluation, in ms. One human think, no more. */
const val DRAW_OFFER_MILLIS = 300L

/** The engine's own "no answer" move, per the Engine seam. */
private const val NULL_UCI = "0000"

/**
 * The one rule that turns four facts about a just-played move into one cue.
 * It lives outside the class because it is the part worth testing: audio has
 * no place in a unit test, this does, and both call sites — the player's move
 * and the engine's reply — must agree on it or the board will sound like two
 * different games.
 *
 * Precedence runs most-consequential first, and only one cue ever plays. A
 * finished game outranks everything (there is no move left to react to), then a
 * check the player has to answer, then a capture, then an ordinary move. A
 * check counts only when it is *new*: answering your own check while giving one
 * is a single move, and alerting on both would train the player to ignore the
 * alert.
 */
internal fun cueFor(
    wasCapture: Boolean,
    isCheckNow: Boolean,
    wasCheckBefore: Boolean,
    resultJustArrived: Boolean,
): SoundCue = when {
    resultJustArrived -> SoundCue.END
    isCheckNow && !wasCheckBefore -> SoundCue.CHECK
    wasCapture -> SoundCue.CAPTURE
    else -> SoundCue.MOVE
}

/** Plain-English status line. No codes, no eval numbers. */
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
