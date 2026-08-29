package com.pieter.atomfx.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType

/**
 * Design §15 — a reusable horizontally-scrollable pill row: Tradeable Now / Watch, calendar
 * chips, and (later phases) pair-sheet tabs and momentum TF toggles all use this same shape.
 */
data class Pill(
    val text: String,
    val tint: Color,
    val emphasized: Boolean = false, // Design §11: A+ pills get a brighter rim
    val onClick: (() -> Unit)? = null,
)

@Composable
fun ScrollingPills(pills: List<Pill>, colors: AtomColors, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
    ) {
        pills.forEach { pill ->
            val clickable = pill.onClick?.let { Modifier.clickable(onClick = it) } ?: Modifier
            val shape = RoundedCornerShape(999.dp)
            Row(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .background(pill.tint.copy(alpha = 0.13f), shape)
                    .let { if (pill.emphasized) it.border(1.dp, pill.tint, shape) else it }
                    .then(clickable)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(text = pill.text, style = AtomType.Caption.copy(color = pill.tint))
            }
        }
    }
}
