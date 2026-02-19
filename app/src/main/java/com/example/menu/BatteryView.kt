package com.example.menu

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class BatteryView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var level: Int = 0
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val bodyRect = RectF()
    private val levelRect = RectF()
    private val tipRect = RectF()

    fun setBatteryLevel(level: Int) {
        this.level = level.coerceIn(0, 100)
        invalidate()
    }

    fun setColors(fgColor: Int) {
        borderPaint.color = fgColor
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        
        val padding = borderPaint.strokeWidth
        val tipHeight = h * 0.1f
        
        bodyRect.set(padding, tipHeight + padding, w - padding, h - padding)
        canvas.drawRect(bodyRect, borderPaint)
        
        val tipWidth = w * 0.4f
        tipRect.set(w / 2 - tipWidth / 2, padding, w / 2 + tipWidth / 2, tipHeight + padding)
        canvas.drawRect(tipRect, borderPaint)
        
        if (level > 0) {
            paint.color = Color.GREEN
            paint.style = Paint.Style.FILL
            val fillHeight = (bodyRect.height() - 2 * padding) * (level / 100f)
            levelRect.set(
                bodyRect.left + padding * 2,
                bodyRect.bottom - padding * 2 - fillHeight,
                bodyRect.right - padding * 2,
                bodyRect.bottom - padding * 2
            )
            canvas.drawRect(levelRect, paint)
        }
    }
}
