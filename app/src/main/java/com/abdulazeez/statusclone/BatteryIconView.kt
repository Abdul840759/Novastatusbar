package com.abdulazeez.statusclone

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Draws a battery indicator the way most polished custom status bar apps do:
 * an outline body + nub, with a proportional solid fill bar inside showing
 * the real charge level, plus a bolt overlay when charging. Drawn in code
 * instead of static icons so the fill is always pixel-accurate to the
 * actual battery percentage.
 */
class BatteryIconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var levelPercent: Int = 100
        set(value) {
            field = value.coerceIn(0, 100)
            invalidate()
        }

    var isCharging: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var iconColor: Int = Color.WHITE
        set(value) {
            field = value
            invalidate()
        }

    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val boltPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FFD60A")
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        outlinePaint.color = iconColor
        fillPaint.color = iconColor

        val nubWidth = width * 0.12f
        val bodyWidth = width - nubWidth
        val corner = height * 0.28f

        val bodyRect = RectF(1.5f, 1.5f, bodyWidth, height - 1.5f)
        canvas.drawRoundRect(bodyRect, corner, corner, outlinePaint)

        val nubTop = height * 0.28f
        val nubBottom = height * 0.72f
        canvas.drawRoundRect(RectF(bodyWidth - 1f, nubTop, width.toFloat(), nubBottom), 2f, 2f, outlinePaint)

        val padding = outlinePaint.strokeWidth + 2f
        val innerRect = RectF(
            bodyRect.left + padding,
            bodyRect.top + padding,
            bodyRect.right - padding,
            bodyRect.bottom - padding
        )
        val fillWidth = innerRect.width() * (levelPercent / 100f)
        if (fillWidth > 0f) {
            val fillRect = RectF(innerRect.left, innerRect.top, innerRect.left + fillWidth, innerRect.bottom)
            canvas.drawRoundRect(fillRect, corner / 2, corner / 2, fillPaint)
        }

        if (isCharging) {
            val cx = bodyRect.centerX()
            val cy = bodyRect.centerY()
            val boltPath = Path().apply {
                moveTo(cx + width * 0.05f, cy - height * 0.38f)
                lineTo(cx - width * 0.14f, cy + height * 0.05f)
                lineTo(cx - width * 0.01f, cy + height * 0.05f)
                lineTo(cx - width * 0.09f, cy + height * 0.38f)
                lineTo(cx + width * 0.17f, cy - height * 0.05f)
                lineTo(cx + width * 0.02f, cy - height * 0.05f)
                close()
            }
            canvas.drawPath(boltPath, boltPaint)
        }
    }
}
