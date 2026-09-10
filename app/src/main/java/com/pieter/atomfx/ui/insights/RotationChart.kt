package com.pieter.atomfx.ui.insights

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.data.model.RotationBlock
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType

/**
 * Concept 01 (2026-09-10) — a quadrant scatter of currency strength (x, CSM h4) against the
 * momentum of that strength (y, CSM Delta h4): the same real-world Relative Rotation Graph
 * convention `rotation.py`'s own doc comment describes. Quadrant colour reuses the 4 existing
 * status tokens as-is (Pieter's call, 2026-09-10) — Leading=bull, Weakening=watch, Lagging=bear,
 * Improving=neutral — rather than a 5th chromatic token, per Design §2's "colour encodes market
 * state, never variety."
 */
@Composable
fun RotationChart(rotation: RotationBlock?, colors: AtomColors, modifier: Modifier = Modifier) {
    if (rotation == null || rotation.points.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
            Text(text = "Not available yet", style = AtomType.Body.copy(color = colors.textMuted))
        }
        return
    }

    fun quadrantColor(quadrant: String?): Color = when (quadrant) {
        "leading" -> colors.bull
        "weakening" -> colors.watch
        "lagging" -> colors.bear
        else -> colors.neutral // "improving", or unknown
    }

    Canvas(modifier = modifier.fillMaxWidth().height(260.dp)) {
        val padLeft = 44f
        val padRight = 16f
        val padTop = 16f
        val padBottom = 28f
        val plotW = size.width - padLeft - padRight
        val plotH = size.height - padTop - padBottom

        // CSM Delta (y) is the difference between two independently 0-100 min-max-normalised
        // snapshots, so it's mathematically bounded to +-100, same span as CSM (x) itself — NOT
        // the +-60 this chart shipped with, which was a guess that real data promptly broke (a
        // single H4 scan produced deltas past +-90). Clip below is belt-and-suspenders on top of
        // this, not a substitute for it — Canvas draws are not clipped to their own bounds by
        // default, so an out-of-range point would otherwise bleed into whatever sits outside this
        // composable (confirmed live: a CAD dot rendered on top of the THRUST tab label above it).
        val xMin = 0.0; val xMax = 100.0
        val yMin = -100.0; val yMax = 100.0
        fun px(x: Double): Float = (padLeft + ((x - xMin) / (xMax - xMin) * plotW)).toFloat()
        fun py(y: Double): Float = (padTop + (1.0 - (y - yMin) / (yMax - yMin)) * plotH).toFloat()

        clipRect(0f, 0f, size.width, size.height) {

        val midX = px(50.0)
        val midY = py(0.0)

        drawRect(colors.bullSoft, topLeft = Offset(midX, padTop), size = androidx.compose.ui.geometry.Size(padLeft + plotW - midX, midY - padTop))
        drawRect(colors.watchSoft, topLeft = Offset(midX, midY), size = androidx.compose.ui.geometry.Size(padLeft + plotW - midX, padTop + plotH - midY))
        drawRect(colors.bearSoft, topLeft = Offset(padLeft, midY), size = androidx.compose.ui.geometry.Size(midX - padLeft, padTop + plotH - midY))
        drawRect(colors.neutral.copy(alpha = 0.10f), topLeft = Offset(padLeft, padTop), size = androidx.compose.ui.geometry.Size(midX - padLeft, midY - padTop))

        drawLine(colors.hairlineStrong, Offset(midX, padTop), Offset(midX, padTop + plotH), strokeWidth = 1.dp.toPx())
        drawLine(colors.hairlineStrong, Offset(padLeft, midY), Offset(padLeft + plotW, midY), strokeWidth = 1.dp.toPx())

        val labelPaint = Paint().apply {
            isAntiAlias = true
            textSize = 10.dp.toPx()
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            color = colors.textMuted.toArgb()
        }
        val nativeCanvas = drawContext.canvas.nativeCanvas
        nativeCanvas.drawText("CSM →", padLeft + plotW, padTop + plotH + 20f, labelPaint.apply { textAlign = Paint.Align.RIGHT })

        rotation.points.forEach { (ccy, pt) ->
            val x = pt.x; val y = pt.y
            if (x == null || y == null) return@forEach
            val dotColor = quadrantColor(pt.quadrant)
            val center = Offset(px(x), py(y))

            val trail = rotation.history[ccy].orEmpty()
            if (trail.size > 1) {
                for (i in 0 until trail.size - 1) {
                    val a = trail[i]; val b = trail[i + 1]
                    if (a.size < 2 || b.size < 2) continue
                    val alpha = 0.08f + (i.toFloat() / trail.size) * 0.24f
                    drawLine(
                        color = dotColor.copy(alpha = alpha),
                        start = Offset(px(a[0]), py(a[1])),
                        end = Offset(px(b[0]), py(b[1])),
                        strokeWidth = 2.dp.toPx(),
                    )
                }
            }

            // Squircle marker, name inside — SheetComponents.kt's SmallPillCell wash (tint at 18%
            // alpha, 8dp corners) and text-adopts-tint convention (TfAlignmentStrip's technical
            // pills, PairSheet), not a dot + separate external label (which needed its own
            // edge-flip logic to avoid running off the chart — see this file's own prior history).
            val namePaint = Paint().apply {
                isAntiAlias = true
                textSize = 10.dp.toPx()
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                color = dotColor.toArgb()
                textAlign = Paint.Align.CENTER
            }
            val textWidth = namePaint.measureText(ccy)
            val boxW = textWidth + 12.dp.toPx()
            val boxH = 20.dp.toPx()
            // Clamp so the squircle itself always stays fully inside the plot area, even for a
            // point sitting right at an axis extreme (e.g. CSM=100) — the clipRect above is a
            // last-resort safety net, this keeps the common case from ever needing it.
            val cx = center.x.coerceIn(padLeft + boxW / 2f, padLeft + plotW - boxW / 2f)
            val cy = center.y.coerceIn(padTop + boxH / 2f, padTop + plotH - boxH / 2f)

            drawRoundRect(
                color = dotColor.copy(alpha = 0.18f),
                topLeft = Offset(cx - boxW / 2f, cy - boxH / 2f),
                size = Size(boxW, boxH),
                cornerRadius = CornerRadius(8.dp.toPx()),
            )
            nativeCanvas.drawText(ccy, cx, cy + namePaint.textSize / 3f, namePaint)
        }

        }
    }
}
