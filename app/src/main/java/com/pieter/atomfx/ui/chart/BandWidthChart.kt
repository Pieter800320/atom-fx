package com.pieter.atomfx.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType

/**
 * Bollinger **BandWidth** — (upper − lower) / middle × 100, the volatility half of the glance
 * panel (Design §19.4b). Reads `pairs.<PAIR>.bollinger_series.<tf>.bandwidth`, computed by
 * `scanner/extend/bollinger_series.py` on standard 20-period bands. No on-device band math
 * (Architecture §8.3). 2026-09-18, Pieter's ask.
 *
 * Unlike RSI and %B this has **no fixed domain** — BandWidth is a percentage of price whose
 * typical range differs by pair and by timeframe (a D1 BandWidth of 1.0 is ordinary; an H1 one
 * is wide). So the Y axis is scaled to the visible window's own min/max rather than to invented
 * "wide"/"narrow" reference levels this project has no data to place. The shape is the read:
 * rising = bands expanding, falling = converging.
 *
 * **Squeeze marks** are John Bollinger's own published definition — BandWidth at its lowest in
 * the trailing 125 bars — flagged per bar by the backend, never recomputed here. Pieter's
 * explicit call (2026-09-18) to use the standard rather than invent a percentile cut-off, the
 * same caution `bb_touch.py` already flags about its own untuned `width_trend` numbers. Drawn as
 * a soft full-height column behind the line plus a dot on it, in the `watch` token — the app's
 * existing "worth attention, not yet a verdict" hue, which is exactly what a squeeze is: the
 * quiet before a move, with no direction implied.
 */
@Composable
fun BandWidthChart(
    values: List<Double>,
    colors: AtomColors,
    squeeze: List<Boolean> = emptyList(),
    dates: List<String> = emptyList(),
    modifier: Modifier = Modifier,
) {
    if (values.size < 2) {
        Box(modifier = modifier.fillMaxWidth().height(BASE_CHART_HEIGHT), contentAlignment = Alignment.Center) {
            Text(text = "Not available yet", style = AtomType.Body.copy(color = colors.textMuted))
        }
        return
    }

    val n = values.size
    val hasDates = dates.size == n
    val hasSqueeze = squeeze.size == n

    Canvas(modifier = modifier.fillMaxWidth().height(chartHeight(hasDates))) {
        val padTop = 10f
        val padBottom = 10f
        val dateRowHeight = if (hasDates) DATE_ROW_HEIGHT.toPx() else 0f
        val plotH = size.height - padTop - padBottom - dateRowHeight

        // Data-driven domain, padded 8% top and bottom so the extremes don't sit flush against
        // the frame. A dead-flat series (every value identical) would give a zero-height domain
        // and divide by zero — floored to draw as a centred flat line instead.
        val lo = values.min()
        val hi = values.max()
        val pad = ((hi - lo) * 0.08).takeIf { it > 0.0 } ?: 1.0
        val domainLo = lo - pad
        val domainHi = hi + pad
        val span = (domainHi - domainLo).takeIf { it > 0.0 } ?: 1.0
        fun py(v: Double): Float = (padTop + (1.0 - (v - domainLo) / span) * plotH).toFloat()

        val stepX = size.width / (n - 1)
        fun px(i: Int): Float = i * stepX

        // Squeeze columns first, so the line draws over them rather than under.
        if (hasSqueeze) {
            val columnWidth = (stepX * 0.9f).coerceAtLeast(1.5.dp.toPx())
            squeeze.forEachIndexed { i, flagged ->
                if (!flagged) return@forEachIndexed
                drawRect(
                    color = colors.watchSoft,
                    topLeft = Offset(px(i) - columnWidth / 2f, padTop),
                    size = Size(columnWidth, plotH),
                )
            }
        }

        val path = Path().apply {
            moveTo(px(0), py(values[0]))
            for (i in 1 until n) lineTo(px(i), py(values[i]))
        }
        // 2026-09-18 (Pieter's restyle ask, 2nd) — always white, matching every other
        // glance-panel line. Was watch-tinted whenever the current bar was a squeeze; the
        // shaded column + dots below already carry that read on their own.
        drawPath(path, color = colors.textPrimary, style = Stroke(width = 1.2.dp.toPx()))

        // Squeeze markers stay watch-tinted — they're an annotation on top of the line (which
        // bars/bars are a 125-bar low), not the line's own reading, so they keep their own colour
        // even though the line itself no longer changes colour.
        if (hasSqueeze) {
            squeeze.forEachIndexed { i, flagged ->
                if (flagged) drawCircle(color = colors.watch, radius = 2.dp.toPx(), center = Offset(px(i), py(values[i])))
            }
        }

        val endpoint = Offset(px(n - 1), py(values.last()))
        glowDot(endpoint, colors.textPrimary, 8.dp.toPx())
        drawCircle(color = colors.textPrimary, radius = 3.dp.toPx(), center = endpoint)

        if (hasDates) drawDateRow(dates, ::px, padTop + plotH + dateRowHeight - 4.dp.toPx(), colors)
    }
}
