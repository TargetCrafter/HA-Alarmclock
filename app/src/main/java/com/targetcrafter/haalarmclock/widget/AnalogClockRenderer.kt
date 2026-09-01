package com.targetcrafter.haalarmclock.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.content.ContextCompat
import com.targetcrafter.haalarmclock.R
import kotlin.math.cos
import kotlin.math.sin

/** Fixed render size for the analog widget's bitmap; the ImageView it's pushed into scales this
 * down/up to fit the actual widget size, so this just needs to be sharp enough for the largest
 * placement (roughly a 4x4-cell widget) without being wasteful. */
private const val RENDER_SIZE_PX = 360

/** Matches [com.targetcrafter.haalarmclock.ui.clock.ANALOG_CLOCK_SECOND_HAND_COLOR] so the
 * widget's second hand/pivot looks the same as the in-app Clock tab's. */
private val SECOND_HAND_COLOR = Color.parseColor("#E53935")

/**
 * Draws a self-contained "watch face" onto a [Bitmap] to push into the widget's ImageView via
 * [android.widget.RemoteViews.setImageViewBitmap] — face disc, bezel ring, tick marks, hands, and
 * (if there's a next alarm) a centered time badge with its own opaque backing, all baked into one
 * bitmap. Replaces the system [android.widget.AnalogClock] (which rendered blank in practice and
 * — being a fixed system drawable — can't be recolored to match user-chosen widget colors
 * anyway). Since RemoteViews can't run arbitrary drawing code itself, this bitmap needs to be
 * regenerated and re-pushed on every tick — see AnalogWidgetTicker.
 */
object AnalogClockRenderer {

    /** [nextAlarmText], when non-null (formatted "HH:mm", no label/prefix — the icon already says
     * "alarm"), draws a small badge below the pivot: the Material "alarm" glyph plus the time,
     * backed by an opaque [backgroundColor] patch painted *after* the hands, so a hand passing
     * behind the badge is masked out rather than showing through around the glyphs. [context] is
     * only needed to rasterize that glyph from `R.drawable.ic_alarm_glyph`. */
    fun render(
        context: Context,
        hour: Int,
        minute: Int,
        second: Int,
        foregroundColor: Int,
        backgroundColor: Int,
        nextAlarmText: String?,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(RENDER_SIZE_PX, RENDER_SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = RENDER_SIZE_PX / 2f
        val cy = RENDER_SIZE_PX / 2f
        // Right up to the bitmap's own edge (the ImageView carries no padding of its own either —
        // see widget_analog_clock.xml) so the face fills the whole widget instead of floating in
        // a visible inset.
        val discRadius = RENDER_SIZE_PX / 2f * 0.995f
        val radius = RENDER_SIZE_PX / 2f * 0.86f

        val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = backgroundColor; style = Paint.Style.FILL }
        canvas.drawCircle(cx, cy, discRadius, discPaint)

        val bezelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = withAlpha(foregroundColor, 0.3f)
            style = Paint.Style.STROKE
            strokeWidth = RENDER_SIZE_PX * 0.01f
        }
        canvas.drawCircle(cx, cy, discRadius - bezelPaint.strokeWidth / 2f, bezelPaint)

        // Ticks sit close to the rim; the four quarter ticks (12/3/6/9) are drawn longer than the
        // other eight so the face reads at a glance without needing numerals.
        val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = withAlpha(foregroundColor, 0.55f)
            style = Paint.Style.STROKE
            strokeWidth = RENDER_SIZE_PX * 0.022f
            strokeCap = Paint.Cap.ROUND
        }
        val tickOuter = RENDER_SIZE_PX / 2f * 0.91f
        for (i in 0 until 12) {
            val angle = Math.toRadians((i * 30 - 90).toDouble())
            val isQuarter = i % 3 == 0
            val inner = tickOuter - RENDER_SIZE_PX * (if (isQuarter) 0.11f else 0.065f)
            canvas.drawLine(
                cx + (cos(angle) * inner).toFloat(),
                cy + (sin(angle) * inner).toFloat(),
                cx + (cos(angle) * tickOuter).toFloat(),
                cy + (sin(angle) * tickOuter).toFloat(),
                tickPaint,
            )
        }

        val hourAngle = Math.toRadians(((hour % 12) * 30 + minute * 0.5 - 90))
        drawHand(canvas, cx, cy, hourAngle, radius * 0.5f, RENDER_SIZE_PX * 0.045f, foregroundColor)

        val minuteAngle = Math.toRadians((minute * 6.0 - 90))
        drawHand(canvas, cx, cy, minuteAngle, radius * 0.75f, RENDER_SIZE_PX * 0.03f, foregroundColor)

        val secondAngle = Math.toRadians((second * 6.0 - 90))
        drawHand(canvas, cx, cy, secondAngle, radius * 0.85f, RENDER_SIZE_PX * 0.012f, SECOND_HAND_COLOR)

        if (nextAlarmText != null) {
            drawNextAlarmBadge(context, canvas, cx, cy, foregroundColor, backgroundColor, nextAlarmText)
        }

        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = foregroundColor; style = Paint.Style.FILL }
        canvas.drawCircle(cx, cy, RENDER_SIZE_PX * 0.045f, haloPaint)
        val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = SECOND_HAND_COLOR; style = Paint.Style.FILL }
        canvas.drawCircle(cx, cy, RENDER_SIZE_PX * 0.024f, centerPaint)

        return bitmap
    }

    private fun drawHand(canvas: Canvas, cx: Float, cy: Float, angle: Double, length: Float, width: Float, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            strokeWidth = width
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(cx, cy, cx + (cos(angle) * length).toFloat(), cy + (sin(angle) * length).toFloat(), paint)
    }

    private fun drawNextAlarmBadge(
        context: Context,
        canvas: Canvas,
        cx: Float,
        cy: Float,
        foregroundColor: Int,
        backgroundColor: Int,
        text: String,
    ) {
        val iconSize = RENDER_SIZE_PX * 0.085f
        val gap = RENDER_SIZE_PX * 0.03f
        val paddingH = RENDER_SIZE_PX * 0.035f
        val rowHeight = RENDER_SIZE_PX * 0.15f

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = foregroundColor
            textSize = RENDER_SIZE_PX * 0.105f
            isFakeBoldText = true
            textAlign = Paint.Align.LEFT
        }
        val textWidth = textPaint.measureText(text)
        val contentWidth = iconSize + gap + textWidth
        val badgeWidth = contentWidth + paddingH * 2
        val badgeCenterY = cy + RENDER_SIZE_PX * 0.16f
        val badgeRect = RectF(cx - badgeWidth / 2f, badgeCenterY - rowHeight / 2f, cx + badgeWidth / 2f, badgeCenterY + rowHeight / 2f)
        val cornerRadius = RENDER_SIZE_PX * 0.035f

        // Painted after the hands (see [render]) so it masks out whatever hand segment happens to
        // fall behind it, instead of a hand line poking through between the glyphs.
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = backgroundColor; style = Paint.Style.FILL }
        canvas.drawRoundRect(badgeRect, cornerRadius, cornerRadius, badgePaint)

        val iconLeft = badgeRect.left + paddingH
        val iconDrawable = ContextCompat.getDrawable(context, R.drawable.ic_alarm_glyph)?.mutate()
        if (iconDrawable != null) {
            iconDrawable.setTint(foregroundColor)
            val iconTop = (badgeCenterY - iconSize / 2f).toInt()
            val iconLeftInt = iconLeft.toInt()
            iconDrawable.setBounds(iconLeftInt, iconTop, iconLeftInt + iconSize.toInt(), iconTop + iconSize.toInt())
            iconDrawable.draw(canvas)
        }

        val fm = textPaint.fontMetrics
        val baseline = badgeCenterY - (fm.ascent + fm.descent) / 2f
        canvas.drawText(text, iconLeft + iconSize + gap, baseline, textPaint)
    }

    private fun withAlpha(color: Int, alphaFraction: Float): Int {
        val alpha = (Color.alpha(color) * alphaFraction).toInt().coerceIn(0, 255)
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }
}
