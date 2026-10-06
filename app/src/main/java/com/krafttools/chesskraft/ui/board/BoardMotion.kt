/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft.ui.board

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Board motion state. Slide 150ms ease-out cubic (FastOutSlowIn) with the
 * capture fade concurrent at 80ms, illegal-tap nudge, one single selection
 * pulse — every number from the frozen spec, all collapsed to snap under
 * ReduceMotion (snap at 0, no loops anywhere).
 */
class BoardMotion(private val reduceMotion: Boolean) {
    var slideProgress by mutableFloatStateOf(1f)
        private set
    var captureAlpha by mutableFloatStateOf(0f)
        private set
    var nudgePx by mutableFloatStateOf(0f)
        private set

    /** One-shot selection pulse, 1 -> 0. The board draws it, nothing reads it twice. */
    var selectPulse by mutableFloatStateOf(0f)
        private set

    private var seenMove: Pair<Int?, Int?> = null to null
    private var seenCount: Int = -1
    private val slide = Animatable(1f)
    private val flash = Animatable(0f)
    private val pulse = Animatable(0f)

    suspend fun onPositionChanged(pieces: List<Int>, from: Int?, to: Int?) {
        val move = from to to
        val count = pieces.count { it != 0 }
        val isNewMove = move != seenMove
        val isCapture = isNewMove && seenCount >= 0 && count < seenCount
        seenMove = move
        seenCount = count
        if (!isNewMove) return
        if (reduceMotion) {
            slideProgress = 1f
            captureAlpha = 0f
            return
        }
        if (from != null && to != null) {
            // Slide and capture fade run concurrently: the fade rides on the
            // landing square while the piece is still arriving.
            coroutineScope {
                launch {
                    slide.snapTo(0f)
                    slideProgress = 0f
                    slide.animateTo(1f, animationSpec = SlideSpec) { slideProgress = value }
                    slideProgress = 1f
                }
                launch {
                    if (isCapture) {
                        flash.snapTo(1f)
                        captureAlpha = 1f
                        flash.animateTo(0f, animationSpec = CaptureSpec) { captureAlpha = value }
                        captureAlpha = 0f
                    } else {
                        captureAlpha = 0f
                    }
                }
            }
        } else {
            slideProgress = 1f
            captureAlpha = 0f
        }
    }

    /** Single selection pulse: out and gone, never a loop. Silent under ReduceMotion. */
    suspend fun pulse() {
        if (reduceMotion) {
            selectPulse = 0f
            return
        }
        pulse.snapTo(1f)
        selectPulse = 1f
        pulse.animateTo(0f, animationSpec = PulseSpec) { selectPulse = value }
        selectPulse = 0f
    }

    /** Silent shake: out and back, four frames, no sound, no haptic. */
    suspend fun nudge() {
        if (reduceMotion) return
        for (step in NUDGE_STEPS) {
            nudgePx = step
            delay(NudgeFrameMs)
        }
        nudgePx = 0f
    }

    private companion object {
        const val SlideMs = 150
        const val CaptureMs = 80
        const val PulseMs = 180
        const val NudgeFrameMs = 30L
        val NUDGE_STEPS = listOf(7f, -5f, 3f, 0f)
        val SlideSpec = tween<Float>(durationMillis = SlideMs, easing = FastOutSlowInEasing)
        val CaptureSpec = tween<Float>(durationMillis = CaptureMs)
        val PulseSpec = tween<Float>(durationMillis = PulseMs, easing = FastOutSlowInEasing)
    }
}

@Composable
fun rememberBoardMotion(reduceMotion: Boolean): BoardMotion {
    return remember(reduceMotion) { BoardMotion(reduceMotion) }
}
