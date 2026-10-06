/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.kraft.ui.tokens.KraftTypeScale

/**
 * The app's theme.
 *
 * The MaterialTheme wrapper is not optional (rule `type.m3-wrapper-present`).
 * Type is built from `KraftTypeScale`, never a bare `Typography()` (rule `type.scale-declared`).
 * Accent lives in `ChessKraftColors`; no colour literal anywhere else
 * (rule `colour.per-app-declared`).
 */
@Composable
fun ChessKraftTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = ChessKraftColors.scheme(darkTheme),
        typography = ChessKraftTypography,
        content = content,
    )
}

/**
 * The Kraft type scale, mapped onto Material's slot names.
 */
private val ChessKraftTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = KraftTypeScale.LargeTitle.value.sp,
        lineHeight = 41.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = KraftTypeScale.Title2.value.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = KraftTypeScale.Title3.value.sp,
        lineHeight = 25.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = KraftTypeScale.Body.value.sp,
        lineHeight = 26.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = KraftTypeScale.Subheadline.value.sp,
        lineHeight = 22.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = KraftTypeScale.Caption1.value.sp,
        letterSpacing = KraftTypeScale.LabelSpacing.value.sp,
    ),
)
