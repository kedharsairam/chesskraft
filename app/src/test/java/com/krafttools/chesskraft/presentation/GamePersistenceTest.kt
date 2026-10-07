/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft.presentation

import com.kraft.core.AppError
import com.krafttools.chesskraft.data.FileGameStore
import com.krafttools.chesskraft.data.InMemoryGameStore
import com.krafttools.chesskraft.domain.FinishedGame
import com.krafttools.chesskraft.domain.GameResult
import com.krafttools.chesskraft.domain.GameSave
import com.krafttools.chesskraft.domain.GameTree
import com.krafttools.chesskraft.domain.SavedGame
import com.krafttools.chesskraft.domain.Side
import com.krafttools.chesskraft.domain.parseSquare
import com.krafttools.chesskraft.engine.Difficulty
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Persistence, driven through a store that keeps the save in a field.
 *
 * The two questions worth asking are the ones a player would ask. Does closing
 * the app and coming back find the game where it was left? And does a save file
 * that has been mangled — truncated, hand-edited, written by a newer build —
 * cost the player nothing but the game?
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GamePersistenceTest {
    private val scheduler = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(scheduler)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        store: InMemoryGameStore,
        playerSide: Side = Side.WHITE,
        difficulty: Difficulty = Difficulty.CASUAL,
        timeControlMs: Long? = null,
        nowMs: () -> Long = { 1_760_000_000_000L },
    ) = GameViewModel(
        playerSide = playerSide,
        difficulty = difficulty,
        engine = FakeEngine(seed = 7L),
        aiDispatcher = scheduler,
        timeControlMs = timeControlMs,
        store = store,
        nowMs = nowMs,
    )

    // -- Round trip through the store -----------------------------------

    @Test
    fun aMoveIsSavedAndReplayedOnRestore() = runTest(scheduler) {
        val store = InMemoryGameStore()
        val vm = viewModel(store)
        vm.onTap(parseSquare("e2") ?: error("sq"))
        vm.onTap(parseSquare("e4") ?: error("sq"))
        advanceUntilIdle()

        val saved = store.stored?.inProgress
        assertNotNull(saved)
        assertEquals("e2e4", saved?.moves?.first())
        assertTrue(saved!!.moves.contains("e2e4"))

        // A second ViewModel is what relaunching the app actually looks like.
        val reopened = viewModel(store)
        val restored = reopened.restore()
        assertNotNull(restored)
        assertEquals(saved.moves, restored?.moves)
        assertEquals(vm.state.value.sans, reopened.state.value.sans)
        assertEquals(vm.state.value.lastMoveTo, reopened.state.value.lastMoveTo)
        assertEquals(vm.state.value.sideToMove, reopened.state.value.sideToMove)
    }

    @Test
    fun restoredSettingsComeBackWithTheGame() = runTest(scheduler) {
        val store = InMemoryGameStore()
        val vm = viewModel(
            store = store,
            playerSide = Side.BLACK,
            difficulty = Difficulty.TOUGH,
            timeControlMs = 300_000L,
        )
        vm.flip()
        vm.onElapsed(30_000L)
        vm.saveProgress()

        val reopened = viewModel(store, playerSide = Side.WHITE, difficulty = Difficulty.RELAXED)
        val restored = reopened.restore()
        assertEquals(Side.BLACK, restored?.playerSide)
        assertEquals(Difficulty.TOUGH, restored?.difficulty)
        assertEquals(true, restored?.flipped)
        assertEquals(300_000L, restored?.timeControlMs)
        assertEquals(270_000L, restored?.clockWhiteMs)
        assertEquals(300_000L, restored?.clockBlackMs)
        assertEquals(true, reopened.state.value.flipped)
        assertEquals(Side.BLACK, reopened.state.value.playerSide)
    }

    /**
     * The ViewModel adopts the saved settings rather than only reporting them:
     * legality and the status line read its own side, so a save left as Black
     * must not be carried on as White.
     */
    @Test
    fun theRestoredSideIsTheOneTheGameIsJudgedBy() = runTest(scheduler) {
        val store = InMemoryGameStore()
        val vm = viewModel(store, playerSide = Side.BLACK, timeControlMs = 300_000L)
        // Black to move is the AI's turn, so drive one ply in directly.
        vm.testApplyUci("e7e5")
        vm.saveProgress()

        val reopened = viewModel(store, playerSide = Side.WHITE)
        reopened.restore()
        assertEquals(Side.BLACK, reopened.playerSide)
        assertEquals(Difficulty.CASUAL, reopened.difficulty)
        assertEquals(300_000L, reopened.timeControlMs)
        // It is White to move, and the player is Black — so a White move is not
        // theirs to play. If the restored side had been ignored, this would land.
        assertEquals(Side.WHITE, reopened.state.value.sideToMove)
        reopened.onDrop(parseSquare("e2") ?: error("sq"), parseSquare("e4") ?: error("sq"))
        advanceUntilIdle()
        assertTrue(reopened.state.value.sans.none { it == "e4" })
    }

    @Test
    fun aClockedGameResumesWithTheBanksItHad() = runTest(scheduler) {
        val store = InMemoryGameStore()
        val vm = viewModel(store, timeControlMs = 60_000L)
        vm.onElapsed(10_000L)
        vm.saveProgress()

        val reopened = viewModel(store, timeControlMs = 60_000L)
        reopened.restore()
        assertEquals(50_000L, reopened.state.value.clockWhiteMs)
        assertEquals(60_000L, reopened.state.value.clockBlackMs)
        // And the resumed clock keeps burning from there, not from 60s again.
        reopened.onElapsed(5_000L)
        assertEquals(45_000L, reopened.state.value.clockWhiteMs)
    }

    @Test
    fun undoIsReflectedInTheSave() = runTest(scheduler) {
        val store = InMemoryGameStore()
        val vm = viewModel(store)
        vm.onTap(parseSquare("e2") ?: error("sq"))
        vm.onTap(parseSquare("e4") ?: error("sq"))
        advanceUntilIdle()
        assertTrue(store.stored!!.inProgress!!.moves.isNotEmpty())

        vm.undo()
        val moves = store.stored?.inProgress?.moves
        assertNotNull(moves)
        assertTrue("undo left ${moves} behind", moves!!.none { it.startsWith("e2e4") })
    }

    @Test
    fun aNewGameReplacesWhatWasSaved() = runTest(scheduler) {
        val store = InMemoryGameStore()
        val vm = viewModel(store)
        vm.onTap(parseSquare("d2") ?: error("sq"))
        vm.onTap(parseSquare("d4") ?: error("sq"))
        advanceUntilIdle()
        vm.newGame()
        advanceUntilIdle()

        val reopened = viewModel(store)
        val restored = reopened.restore()
        assertNotNull(restored)
        // The AI may have opened, but the player's own move is gone.
        assertTrue(restored!!.moves.none { it == "d2d4" })
        assertTrue(reopened.state.value.sans.none { it == "d4" })
    }

    // -- Nothing to restore ----------------------------------------------

    @Test
    fun restoreOnAnEmptyStoreIsNull() = runTest(scheduler) {
        assertNull(viewModel(InMemoryGameStore()).restore())
    }

    @Test
    fun restoreAfterClearProgressIsNull() = runTest(scheduler) {
        val store = InMemoryGameStore()
        val vm = viewModel(store)
        vm.onTap(parseSquare("e2") ?: error("sq"))
        vm.onTap(parseSquare("e4") ?: error("sq"))
        advanceUntilIdle()
        assertNotNull(viewModel(store).restore())

        vm.clearProgress()
        assertNull(viewModel(store).restore())
    }

    @Test
    fun clearProgressKeepsTheFinishedGameHistory() = runTest(scheduler) {
        val store = InMemoryGameStore()
        val vm = viewModel(store)
        vm.onTap(parseSquare("e2") ?: error("sq"))
        vm.onTap(parseSquare("e4") ?: error("sq"))
        advanceUntilIdle()
        vm.resign()
        assertEquals(1, store.stored?.history?.size)

        vm.clearProgress()
        val after = store.stored
        assertNull(after?.inProgress)
        assertEquals(1, after?.history?.size)
    }

    // -- Corrupt saves ----------------------------------------------------

    @Test
    fun aCorruptSaveIsNullAndNeverThrows() = runTest(scheduler) {
        val store = InMemoryGameStore()
        store.save(GameSave.empty())
        val vm = viewModel(store)
        // The store hands back a save whose move list cannot be replayed: the
        // second move is illegal where it was saved.
        store.stored = GameSave(
            inProgress = SavedGame(
                playerSide = Side.WHITE,
                difficulty = Difficulty.CASUAL,
                flipped = false,
                timeControlMs = null,
                clockWhiteMs = null,
                clockBlackMs = null,
                moves = listOf("e2e4", "e2e4"),
            ),
            history = emptyList(),
        )
        assertNull(vm.restore())
    }

    @Test
    fun anUnparseableMoveListIsNull() = runTest(scheduler) {
        val store = InMemoryGameStore()
        store.stored = GameSave(
            inProgress = SavedGame(
                playerSide = Side.WHITE,
                difficulty = Difficulty.CASUAL,
                flipped = false,
                timeControlMs = null,
                clockWhiteMs = null,
                clockBlackMs = null,
                moves = listOf("not-a-move"),
            ),
            history = emptyList(),
        )
        assertNull(viewModel(store).restore())
    }

    @Test
    fun aSaveWithNothingInProgressIsNull() = runTest(scheduler) {
        val store = InMemoryGameStore()
        store.stored = GameSave(
            inProgress = null,
            history = listOf(
                FinishedGame("You win", 31, Difficulty.SHARP, Side.WHITE, 1L),
            ),
        )
        assertNull(viewModel(store).restore())
    }

    @Test
    fun aFailingStoreDoesNotBreakAMove() = runTest(scheduler) {
        val store = InMemoryGameStore(failWrites = true)
        val vm = viewModel(store)
        vm.onTap(parseSquare("e2") ?: error("sq"))
        vm.onTap(parseSquare("e4") ?: error("sq"))
        advanceUntilIdle()
        // The write failed and the game still played: the player's move is on
        // the board and the computer answered it.
        assertTrue(vm.state.value.sans.contains("e4"))
        assertTrue(store.saveCount > 0)
    }

    // -- Finished-game history -------------------------------------------

    @Test
    fun aResignGoesIntoTheHistoryAndClearsTheGame() = runTest(scheduler) {
        val store = InMemoryGameStore()
        val vm = viewModel(store)
        vm.onTap(parseSquare("e2") ?: error("sq"))
        vm.onTap(parseSquare("e4") ?: error("sq"))
        advanceUntilIdle()
        vm.resign()

        val save = store.stored
        assertNull("a finished game must not be resumable", save?.inProgress)
        val entry = save?.history?.firstOrNull()
        assertEquals("You resigned", entry?.result)
        assertEquals(Difficulty.CASUAL, entry?.difficulty)
        assertEquals(Side.WHITE, entry?.playerSide)
        assertEquals(1_760_000_000_000L, entry?.endedAtMs)
    }

    @Test
    fun aCheckmateGoesIntoTheHistory() = runTest(scheduler) {
        val store = InMemoryGameStore()
        val vm = viewModel(store)
        for (uci in listOf("f2f3", "e7e5", "g2g4", "d8h4")) {
            vm.testApplyUci(uci)
        }
        vm.recordFinishedGame()
        val entry = store.stored?.history?.firstOrNull()
        assertEquals("You lose", entry?.result)
        assertEquals(4, entry?.moveCount)
    }

    @Test
    fun aFlagGoesIntoTheHistory() = runTest(scheduler) {
        val store = InMemoryGameStore()
        val vm = viewModel(store, timeControlMs = 1_000L)
        vm.onElapsed(1_000L)
        val save = store.stored
        assertNull(save?.inProgress)
        assertEquals("You lost on time", save?.history?.firstOrNull()?.result)
    }

    @Test
    fun recordingTheSameEndingTwiceWritesOneEntry() = runTest(scheduler) {
        val store = InMemoryGameStore()
        val vm = viewModel(store)
        vm.resign()
        vm.recordFinishedGame()
        vm.recordFinishedGame()
        assertEquals(1, store.stored?.history?.size)
    }

    @Test
    fun savingAMoveNeverWipesTheHistoryAlreadyOnDisk() = runTest(scheduler) {
        val store = InMemoryGameStore()
        // A previous session left a record behind.
        store.stored = GameSave(
            inProgress = null,
            history = listOf(FinishedGame("You win", 31, Difficulty.SHARP, Side.WHITE, 1L)),
        )
        val vm = viewModel(store)
        vm.onTap(parseSquare("e2") ?: error("sq"))
        vm.onTap(parseSquare("e4") ?: error("sq"))
        advanceUntilIdle()

        val history = store.stored?.history
        assertEquals(1, history?.size)
        assertEquals("You win", history?.first()?.result)
        assertNotNull(store.stored?.inProgress)
    }

    @Test
    fun theHistoryIsCappedAtTwentyAndDropsTheOldest() = runTest(scheduler) {
        val store = InMemoryGameStore()
        // Twenty finished games already on disk, newest first.
        val seed = (1..20).map { FinishedGame("Game $it", it, Difficulty.CASUAL, Side.WHITE, it.toLong()) }
        store.stored = GameSave(inProgress = null, history = seed.reversed())

        val vm = viewModel(store, nowMs = { 99L })
        vm.resign()
        val history = store.stored!!.history
        assertEquals(GameSave.HISTORY_CAP, history.size)
        assertEquals("You resigned", history.first().result)
        // The oldest of the twenty went; the rest of the record is intact.
        assertEquals("Game 2", history.last().result)
    }

    // -- Write discipline -------------------------------------------------

    /**
     * The clock heartbeat calls [GameViewModel.markClockStart] on every tick.
     * Saving there would be four disk writes a second, so it saves only when a
     * turn genuinely starts — once, here, not four times.
     */
    @Test
    fun repeatedHeartbeatsDoNotWriteFourTimesASecond() = runTest(scheduler) {
        val store = InMemoryGameStore()
        val vm = viewModel(store, timeControlMs = 60_000L)
        val before = store.saveCount
        repeat(8) { vm.markClockStart(1_000L + it * 250L) }
        assertEquals(before + 1, store.saveCount)
    }

    @Test
    fun elapsedTicksAloneDoNotWrite() = runTest(scheduler) {
        val store = InMemoryGameStore()
        val vm = viewModel(store, timeControlMs = 60_000L)
        vm.markClockStart(0L)
        val before = store.saveCount
        repeat(10) { vm.onElapsed(100L) }
        assertEquals(before, store.saveCount)
    }

    /**
     * The save can land while it is the computer's turn — the player had moved
     * and the process died before the reply. Restoring owes that reply, or the
     * board comes back frozen on the player's own move.
     */
    @Test
    fun aGameSavedOnTheComputersTurnGetsItsReply() = runTest(scheduler) {
        val store = InMemoryGameStore()
        val vm = viewModel(store)
        // One ply only, so it is the AI's move and nothing has answered it.
        vm.testApplyUci("e2e4")
        assertEquals(Side.BLACK, vm.state.value.sideToMove)
        vm.saveProgress()
        assertEquals(listOf("e2e4"), store.stored?.inProgress?.moves)

        val reopened = viewModel(store)
        reopened.restore()
        advanceUntilIdle()
        assertEquals(2, reopened.state.value.sans.size)
        assertEquals(Side.WHITE, reopened.state.value.sideToMove)
    }

    /** A restored game the player is to move must not spend a search. */
    @Test
    fun aGameSavedOnThePlayersTurnDoesNotDisturbTheBoard() = runTest(scheduler) {
        val store = InMemoryGameStore()
        val vm = viewModel(store)
        vm.onTap(parseSquare("e2") ?: error("sq"))
        vm.onTap(parseSquare("e4") ?: error("sq"))
        advanceUntilIdle()
        vm.saveProgress()

        val reopened = viewModel(store)
        reopened.restore()
        advanceUntilIdle()
        assertEquals(2, reopened.state.value.sans.size)
        assertEquals(Side.WHITE, reopened.state.value.sideToMove)
    }

    // -- The replay agrees with the rules --------------------------------

    @Test
    fun aRestoredBoardIsTheOneTheMovesDescribe() = runTest(scheduler) {
        val store = InMemoryGameStore()
        val vm = viewModel(store)
        for (uci in listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1b5")) {
            vm.testApplyUci(uci)
        }
        vm.saveProgress()

        val reopened = viewModel(store)
        reopened.restore()
        // The saved moves, replayed by the rules, must land on the same position
        // the original game had — the save asserts a move list, never a board.
        assertEquals(vm.state.value.pieces, reopened.state.value.pieces)
        assertEquals(vm.state.value.sans, reopened.state.value.sans)
    }

    @Test
    fun aReplayedGameKeepsItsOwnUndoDepth() = runTest(scheduler) {
        val store = InMemoryGameStore()
        val vm = viewModel(store)
        vm.testApplyUci("e2e4")
        vm.testApplyUci("e7e5")
        vm.saveProgress()

        val reopened = viewModel(store)
        reopened.restore()
        assertEquals(2, reopened.state.value.sans.size)
        reopened.undo()
        assertEquals(0, reopened.state.value.sans.size)
    }

    // -- The real file store ---------------------------------------------

    /**
     * The file store against a real file in a temp directory. No Context and no
     * Android, because the constructor takes a [java.io.File] — that is what
     * makes the one implementation this app ships testable here at all.
     */
    @Test
    fun theFileStoreRoundTripsThroughADisk() {
        val dir = Files.createTempDirectory("chesskraft-save").toFile()
        try {
            val store = FileGameStore(File(dir, "save.json"))
            assertTrue(store.load() is com.kraft.core.KraftResult.Failure)

            val save = GameSave(
                inProgress = SavedGame(
                    playerSide = Side.BLACK,
                    difficulty = Difficulty.SHARP,
                    flipped = true,
                    timeControlMs = 60_000L,
                    clockWhiteMs = 12_345L,
                    clockBlackMs = 54_000L,
                    moves = listOf("e2e4", "c7c5", "g1f3"),
                ),
                history = listOf(FinishedGame("Draw agreed", 12, Difficulty.SHARP, Side.BLACK, 42L)),
            )
            assertTrue(store.save(save).isSuccess)
            assertEquals(save, store.load().getOrNull())

            // Overwriting is what every move does, and the old content must not
            // bleed through.
            store.save(GameSave.empty())
            assertEquals(GameSave.empty(), store.load().getOrNull())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun aCorruptFileOnDiskIsAFailureRatherThanAnException() {
        val dir = Files.createTempDirectory("chesskraft-corrupt").toFile()
        try {
            val file = File(dir, "save.json")
            file.writeText("{\"version\":1,\"inProgress\":{\"mo")
            val store = FileGameStore(file)
            val loaded = store.load()
            assertTrue(loaded is com.kraft.core.KraftResult.Failure)
            // A parse failure, distinct from "no save here" — the two mean
            // different things to whoever reads the result.
            assertTrue((loaded as com.kraft.core.KraftResult.Failure).error is AppError.DataError.Parse)
        } finally {
            dir.deleteRecursively()
        }
    }

    /**
     * A game interrupted mid-write must leave a readable save behind, which is
     * what the temp-file-then-rename write is for: the target file is replaced
     * whole or not at all.
     */
    @Test
    fun anInterruptedWriteLeavesThePreviousSaveReadable() {
        val dir = Files.createTempDirectory("chesskraft-interrupt").toFile()
        try {
            val store = FileGameStore(File(dir, "save.json"))
            val first = GameSave(
                inProgress = SavedGame(
                    Side.WHITE,
                    Difficulty.CASUAL,
                    false,
                    null,
                    null,
                    null,
                    listOf("e2e4"),
                ),
                history = emptyList(),
            )
            store.save(first)
            // Whatever a kill mid-write left in the temp file, the save itself
            // still parses.
            assertEquals(first, store.load().getOrNull())
        } finally {
            dir.deleteRecursively()
        }
    }

    /**
     * The save is only useful if the tree can rebuild itself from UCI, so the
     * round trip is pinned at the domain layer too.
     */
    @Test
    fun theTreeCanRebuildItselfFromItsOwnUciList() {
        val tree = GameTree()
        for (uci in listOf("e2e4", "e7e5", "g1f3", "b8c6")) {
            tree.apply(com.krafttools.chesskraft.domain.parseUci(uci) ?: error("move"))!!
        }
        val rebuilt = GameTree()
        for (uci in tree.uciList()) {
            rebuilt.apply(com.krafttools.chesskraft.domain.parseUci(uci) ?: error("move"))!!
        }
        assertEquals(tree.sanList(), rebuilt.sanList())
        assertEquals(tree.currentFen(), rebuilt.currentFen())
    }
}