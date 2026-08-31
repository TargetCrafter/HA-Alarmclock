package com.targetcrafter.haalarmclock.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.cos
import kotlin.math.sin

/** Fixed render size for the analog widget's bitmap; the ImageView it's pushed into scales this
 * down/up to fit the actual widget size, so this just needs to be sharp enough for the largest
 * placement (roughly a 4x4-cell widget) without being wasteful. */
private const val RENDER_SIZE_PX = 360

/**
 * Draws an analog clock face onto a [Bitmap] to push into the widget's ImageView via
 * [android.widget.RemoteViews.setImageViewBitmap]. Replaces the system [android.widget.AnalogClock]
 * (which rendered blank in practice and — being a fixed system drawable — can't be recolored to
 * match user-chosen widget colors anyway). Since RemoteViews can't run arbitrary drawing code
 * itself, this bitmap needs to be regenerated and re-pushed on every minute tick — see
 * AnalogWidgetTicker.
 */
object AnalogClockRenderer {

    fun render(hour: Int, minute: Int, color: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(RENDER_SIZE_PX, RENDER_SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = RENDER_SIZE_PX / 2f
        val cy = RENDER_SIZE_PX / 2f
        val radius = RENDER_SIZE_PX / 2f * 0.92f

        val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = RENDER_SIZE_PX * 0.025f
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawCircle(cx, cy, radius, facePaint)

        val tickPaint = Paint(facePaint).apply { strokeWidth = RENDER_SIZE_PX * 0.015f }
        for (i in 0 until 12) {
            val angle = Math.toRadians((i * 30 - 90).toDouble())
            val outer = radius * 0.95f
            val inner = radius * (if (i % 3 == 0) 0.80f else 0.88f)
            canvas.drawLine(
                cx + (cos(angle) * inner).toFloat(),
                cy + (sin(angle) * inner).toFloat(),
                cx + (cos(angle) * outer).toFloat(),
                cy + (sin(angle) * outer).toFloat(),
                tickPaint,
            )
        }

        val hourAngle = Math.toRadians(((hour % 12) * 30 + minute * 0.5 - 90))
        drawHand(canvas, cx, cy, hourAngle, radius * 0.5f, RENDER_SIZE_PX * 0.045f, color)

        val minuteAngle = Math.toRadians((minute * 6.0 - 90))
        drawHand(canvas, cx, cy, minuteAngle, radius * 0.75f, RENDER_SIZE_PX * 0.03f, color)

        val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
        canvas.drawCircle(cx, cy, RENDER_SIZE_PX * 0.035f, centerPaint)

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
}
