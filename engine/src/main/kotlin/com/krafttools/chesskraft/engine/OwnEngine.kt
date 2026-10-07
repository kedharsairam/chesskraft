/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 *
 * ChessKraft's own engine behind the Engine seam. The interface method
 * plays the best move the search finds; the difficulty overload adds the
 * v1 blunder model from the frozen spec: weaker levels sometimes pick
 * from a shortlist of near-best moves, but a move that lets the opponent
 * mate on the spot is never in that shortlist, and the engine always
 * plays best when in check or delivering mate.
 *
 * Randomness is an injected kotlin.random.Random so tests can seed it.
 */
package com.krafttools.chesskraft.engine

import kotlin.random.Random

class OwnEngine(private val random: Random = Random.Default) : Engine {
    override fun findBestMove(positionFen: String, limits: SearchLimits): UciMove {
        val pos = Position.fromFen(positionFen)
        val searcher = Searcher(limits.maxDepth, limits.maxMillis)
        val m = searcher.findBest(pos)
        if (m == NO_MOVE) return UciMove(NULL_UCI)
        return UciMove(MoveGen.moveToUci(m))
    }

    /**
     * Same search as [findBestMove], keeping the score it already produced.
     * The searcher sorts the root list as it deepens and keeps the best move of
     * the last *completed* iteration, so index 0 of [Searcher.rootMoves] and
     * [Searcher.rootScores] are always one honest pair — including when the
     * time cap fired mid-iteration, where both still describe the last full
     * depth. Nothing here searches twice or adds a second code path.
     */
    override fun analyze(positionFen: String, limits: SearchLimits): Analysis {
        val pos = Position.fromFen(positionFen)
        val searcher = Searcher(limits.maxDepth, limits.maxMillis)
        val best = searcher.findBest(pos)
        if (best == NO_MOVE || searcher.rootCount == 0) return Analysis(UciMove(NULL_UCI), 0)
        return Analysis(
            bestMove = UciMove(MoveGen.moveToUci(searcher.rootMoves[0])),
            scoreCp = searcher.rootScores[0],
        )
    }

    fun findBestMove(positionFen: String, difficulty: Difficulty): UciMove {
        val pos = Position.fromFen(positionFen)
        val limits = searchLimitsFor(difficulty)
        val searcher = Searcher(limits.maxDepth, limits.maxMillis)
        val best = searcher.findBest(pos)
        if (best == NO_MOVE) return UciMove(NULL_UCI)
        if (searcher.rootCount == 1) return UciMove(MoveGen.moveToUci(best))

        val moves = IntArray(searcher.rootCount) { searcher.rootMoves[it] }
        val scores = IntArray(searcher.rootCount) { searcher.rootScores[it] }
        val bestScore = scores[0]

        // Always take a mate we have already found.
        if (bestScore > Searcher.MATE - Searcher.MAX_PLY * 4) {
            return UciMove(MoveGen.moveToUci(best))
        }

        val pool = candidatePool(moves, scores, difficulty)
        pruneMateAllowers(pos, pool, moves)
        // Every move walks into mate: the position is lost and the
        // search's choice stands.
        if (pool.isEmpty()) pool.add(best)
        if (pool.size > 1 && pos.isInCheck(pos.side)) {
            return UciMove(MoveGen.moveToUci(pool[0]))
        }
        val chosen = when (difficulty) {
            Difficulty.RELAXED ->
                if (random.nextInt(100) < 15) pool.random(random) else pool[0]
            Difficulty.CASUAL ->
                if (random.nextInt(100) < 5) pool.random(random) else pool[0]
            Difficulty.SHARP -> jitterPick(pool, moves, scores)
            Difficulty.TOUGH -> pool[0]
        }
        return UciMove(MoveGen.moveToUci(chosen))
    }

    private fun candidatePool(moves: IntArray, scores: IntArray, difficulty: Difficulty): MutableList<Int> {
        val window = when (difficulty) {
            Difficulty.RELAXED -> 80
            Difficulty.CASUAL -> 40
            Difficulty.SHARP -> 8
            Difficulty.TOUGH -> 0
        }
        val top = when (difficulty) {
            Difficulty.RELAXED -> 5
            Difficulty.CASUAL -> 3
            Difficulty.SHARP -> 3
            Difficulty.TOUGH -> 1
        }
        val pool = ArrayList<Int>(top)
        pool.add(moves[0])
        for (i in 1 until moves.size) {
            if (pool.size >= top) break
            if (scores[0] - scores[i] <= window) pool.add(moves[i])
        }
        return pool
    }

    /**
     * Drops every move that lets the opponent mate on the spot, best move
     * included: anything is better than immediate mate. When nothing in
     * the window survives, the whole root list is scanned for a safe
     * move before giving up.
     */
    private fun pruneMateAllowers(pos: Position, pool: MutableList<Int>, all: IntArray) {
        if (pool.size <= 1) {
            if (!allowsMateInOne(pos, pool[0])) return
            pool.clear()
        } else {
            val it = pool.iterator()
            while (it.hasNext()) {
                if (allowsMateInOne(pos, it.next())) it.remove()
            }
            if (pool.isNotEmpty()) return
        }
        for (m in all) {
            if (!allowsMateInOne(pos, m)) pool.add(m)
        }
    }

    private fun allowsMateInOne(pos: Position, m: Int): Boolean {
        pos.makeMove(m)
        val buf = IntArray(MoveGen.BUFFER_SIZE)
        val n = MoveGen.generateAll(pos, buf, 0)
        var mated = false
        for (i in 0 until n) {
            if (pos.makeMove(buf[i])) {
                if (MoveGen.isCheckmate(pos)) mated = true
            }
            pos.unmakeMove()
            if (mated) break
        }
        pos.unmakeMove()
        return mated
    }

    private fun jitterPick(pool: List<Int>, moves: IntArray, scores: IntArray): Int {
        val scoreOf = HashMap<Int, Int>(pool.size * 2)
        for (i in moves.indices) scoreOf[moves[i]] = scores[i]
        var pick = pool[0]
        var pickJittered = Int.MIN_VALUE
        for (m in pool) {
            val jittered = (scoreOf[m] ?: 0) + random.nextInt(9) - 4
            if (jittered > pickJittered) {
                pickJittered = jittered
                pick = m
            }
        }
        return pick
    }

    companion object {
        const val NULL_UCI = "0000"

        fun searchLimitsFor(difficulty: Difficulty): SearchLimits = when (difficulty) {
            Difficulty.RELAXED -> SearchLimits(maxDepth = 1, maxMillis = 200L)
            Difficulty.CASUAL -> SearchLimits(maxDepth = 2, maxMillis = 600L)
            Difficulty.SHARP -> SearchLimits(maxDepth = 3, maxMillis = 1500L)
            Difficulty.TOUGH -> SearchLimits(maxDepth = 16, maxMillis = 1500L)
        }
    }
}
