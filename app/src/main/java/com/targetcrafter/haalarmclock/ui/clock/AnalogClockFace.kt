package com.targetcrafter.haalarmclock.ui.clock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
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

/** A live analog clock face drawn with Compose's [Canvas] — styled to match the home-screen
 * widget's face (see [com.targetcrafter.haalarmclock.widget.AnalogClockRenderer], which draws the
 * same shape onto a Bitmap since RemoteViews can't run arbitrary drawing code): a filled disc with
 * a bezel ring, uniform dimmed tick marks with the four quarter ones (12/3/6/9) drawn longer, and
 * a two-tone white-halo/red-center pivot dot.
 */
@Composable
fun AnalogClockFace(
    time: LocalTime,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    accentColor: Color = ANALOG_CLOCK_SECOND_HAND_COLOR,
) {
    Canvas(modifier = modifier.aspectRatio(1f)) {
        val half = size.minDimension / 2f
        val discRadius = half * 0.995f
        val radius = half * 0.86f
        val tickOuter = half * 0.91f

        drawCircle(color = backgroundColor, radius = discRadius)

        val bezelWidth = half * 0.02f
        drawCircle(
            color = color.copy(alpha = 0.3f),
            radius = discRadius - bezelWidth / 2f,
            style = Stroke(width = bezelWidth),
        )

        for (i in 0 until 12) {
            val angle = Math.toRadians((i * 30 - 90).toDouble())
            val isQuarter = i % 3 == 0
            val inner = tickOuter - half * (if (isQuarter) 0.22f else 0.13f)
            drawLine(
                color = color.copy(alpha = 0.55f),
                start = center + Offset((cos(angle) * inner).toFloat(), (sin(angle) * inner).toFloat()),
                end = center + Offset((cos(angle) * tickOuter).toFloat(), (sin(angle) * tickOuter).toFloat()),
                strokeWidth = half * 0.044f,
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

        hand(hourFraction, radius * 0.5f, half * 0.09f, color)
        hand(minuteFraction, radius * 0.75f, half * 0.06f, color)
        hand(secondFraction, radius * 0.85f, half * 0.024f, accentColor)

        drawCircle(color = color, radius = half * 0.09f)
        drawCircle(color = accentColor, radius = half * 0.048f)
    }
}
