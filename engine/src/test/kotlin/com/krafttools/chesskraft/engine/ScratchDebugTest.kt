package com.krafttools.chesskraft.engine

import org.junit.Test

class ScratchDebugTest {
    @Test
    fun debugToughTime() {
        val fen = "k7/pp6/1b6/8/8/4q3/PP5P/5Q1K w - - 0 1"
        val t0 = System.currentTimeMillis()
        val uci = OwnEngine().findBestMove(fen, Difficulty.TOUGH).text
        val took = System.currentTimeMillis() - t0
        println("TOUGH move=$uci took=${took}ms")
        val s = Searcher(16, 1500L)
        val pos = Position.fromFen(fen)
        val t1 = System.currentTimeMillis()
        val m = s.findBest(pos)
        val took2 = System.currentTimeMillis() - t1
        println("RAW move=" + MoveGen.moveToUci(m) + " took=${took2}ms nodes=" + s.nodes)
    }
}
