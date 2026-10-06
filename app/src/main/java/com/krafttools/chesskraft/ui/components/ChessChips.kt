/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import com.kraft.ui.tokens.KraftRadius
import com.kraft.ui.tokens.KraftSpacing

/**
 * The app's one chip language. Selected is a brass fill with an on-brass
 * semibold label and no border; unselected is transparent with an on-surface
 * label and a 1dp outline hairline. State is never colour-alone: the selected
 * chip also reads semibold and announces itself as selected.
 */
@Composable
fun ChessChoiceChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                maxLines = 1,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            containerColor = Color.Transparent,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outline,
            selectedBorderColor = Color.Transparent,
            borderWidth = KraftSpacing.BorderWidth,
            selectedBorderWidth = KraftSpacing.BorderWidth,
        ),
        shape = RoundedCornerShape(KraftRadius.Pill),
        modifier = modifier.heightIn(min = KraftSpacing.Spacing40),
    )
}

/**
 * A single-line horizontally scrolling row of [ChessChoiceChip]s: 8dp gaps,
 * 16dp edge inset, labels that never wrap.
 */
@Composable
fun ChessChoiceRow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = KraftSpacing.ScreenEdge),
    ) {
        content()
    }
}
