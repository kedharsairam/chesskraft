/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft.data

import com.kraft.core.AppError
import com.kraft.core.KraftResult
import com.krafttools.chesskraft.domain.GameSave

/**
 * A store that keeps the save in a field.
 *
 * Two uses. It is the default a ViewModel gets when nothing is passed, so
 * nothing has to save to be constructed, and it is what a unit test substitutes
 * for the file. The same behaviour either way means the default is exercised by
 * every test, which is the point of having it.
 */
class InMemoryGameStore(
    initial: GameSave? = null,
    /** Set to make [save] fail, the way a full disk would. */
    private val failWrites: Boolean = false,
) : GameStore {

    /** What a load would return; null is "nothing saved". */
    var stored: GameSave? = initial

    /** How many times [save] was called. Lets a test assert a write did not happen. */
    var saveCount: Int = 0
        private set

    override fun load(): KraftResult<GameSave> = stored
        ?.let { KraftResult.Success(it) }
        ?: KraftResult.Failure(AppError.DataError.NotFound)

    override fun save(save: GameSave): KraftResult<Unit> {
        saveCount++
        if (failWrites) return KraftResult.Failure(AppError.StorageError.DiskFull)
        stored = save
        return KraftResult.Success(Unit)
    }
}