package com.targetcrafter.haalarmclock.ui.clock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import java.time.LocalTime
import kotlin.math.cos
import kotlin.math.sin

/** The classic red for the second hand and pivot dot — deliberately not theme-driven, so it reads
 * the same way a real clock's red second hand does regardless of the app's Material color scheme.
 * [com.targetcrafter.haalarmclock.widget.AnalogClockRenderer] uses the same hex value for the
 * home-screen widget's second hand so the two stay visually matched.
 */
val ANALOG_CLOCK_SECOND_HAND_COLOR = Color(0xFFE53935)

/** A live analog clock face drawn with Compose's [Canvas] — the in-app counterpart to
 * [com.targetcrafter.haalarmclock.widget.AnalogClockRenderer], which draws the same shape onto a
 * Bitmap for the home-screen widget (Compose can draw directly here, no RemoteViews involved).
 */
@Composable
fun AnalogClockFace(
    time: LocalTime,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    accentColor: Color = ANALOG_CLOCK_SECOND_HAND_COLOR,
) {
    Canvas(modifier = modifier.aspectRatio(1f)) {
        val radius = size.minDimension / 2f * 0.9f

        drawCircle(color = color, radius = radius, style = Stroke(width = radius * 0.035f))

        for (i in 0 until 12) {
            val angle = Math.toRadians((i * 30 - 90).toDouble())
            val outer = radius * 0.95f
            val inner = radius * (if (i % 3 == 0) 0.80f else 0.88f)
            drawLine(
                color = color,
                start = center + Offset((cos(angle) * inner).toFloat(), (sin(angle) * inner).toFloat()),
                end = center + Offset((cos(angle) * outer).toFloat(), (sin(angle) * outer).toFloat()),
                strokeWidth = radius * 0.02f,
                cap = StrokeCap.Round,
            )
        }

        fun hand(turnFraction: Double, length: Float, width: Float, handColor: Color) {
            val angle = Math.toRadians(turnFraction * 360.0 - 90.0)
            drawLine(
                color = handColor,
                start = center,
                end = center + Offset((cos(angle) * length).toFloat(), (sin(angle) * length).toFloat()),
                strokeWidth = width,
                cap = StrokeCap.Round,
            )
        }

        val hourFraction = ((time.hour % 12) + time.minute / 60.0) / 12.0
        val minuteFraction = (time.minute + time.second / 60.0) / 60.0
        val secondFraction = time.second / 60.0

        hand(hourFraction, radius * 0.5f, radius * 0.05f, color)
        hand(minuteFraction, radius * 0.75f, radius * 0.03f, color)
        hand(secondFraction, radius * 0.85f, radius * 0.012f, accentColor)

        drawCircle(color = accentColor, radius = radius * 0.03f, center = center)
    }
}
