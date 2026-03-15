package com.example.menu

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

class BatteryView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var level: Int = -1
    private var isCharging: Boolean = false
    private var pulseAlpha: Int = 255
    private var pulseAnimator: ValueAnimator? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val bodyRect = RectF()
    private val levelRect = RectF()
    private val tipRect = RectF()
    private val boltPath = Path()

    fun setBatteryStatus(level: Int, isCharging: Boolean) {
        this.level = level.coerceIn(0, 100)
        if (this.isCharging != isCharging) {
            this.isCharging = isCharging
            if (isCharging) {
                startPulseAnimation()
            } else {
                stopPulseAnimation()
            }
        }
        invalidate()
    }

    // For compatibility with any existing code calling this
    fun setBatteryLevel(level: Int) {
        this.level = level.coerceIn(0, 100)
        invalidate()
    }

    private fun startPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = ValueAnimator.ofInt(100, 255).apply {
            duration = 800
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                pulseAlpha = it.animatedValue as Int
                invalidate()
            }
            start()
        }
    }

    private fun stopPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAlpha = 255
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
        if (w <= 0 || h <= 0) return
        
        val padding = borderPaint.strokeWidth
        val tipHeight = h * 0.1f
        
        bodyRect.set(padding, tipHeight + padding, w - padding, h - padding)
        canvas.drawRect(bodyRect, borderPaint)
        
        val tipWidth = w * 0.4f
        tipRect.set(w / 2 - tipWidth / 2, padding, w / 2 + tipWidth / 2, tipHeight + padding)
        canvas.drawRect(tipRect, borderPaint)
        
        // Use a default visible level if not yet received
        val displayLevel = if (level < 0) 50 else level

        if (displayLevel > 0) {
            paint.color = if (isCharging) Color.CYAN else Color.GREEN
            paint.style = Paint.Style.FILL
            val fillHeight = (bodyRect.height() - 2 * padding) * (displayLevel / 100f)
            levelRect.set(
                bodyRect.left + padding * 2,
                bodyRect.bottom - padding * 2 - fillHeight,
                bodyRect.right - padding * 2,
                bodyRect.bottom - padding * 2
            )
            canvas.drawRect(levelRect, paint)
        }

        if (isCharging) {
            drawPulsingBolt(canvas)
        }
    }

    private fun drawPulsingBolt(canvas: Canvas) {
        val cx = bodyRect.centerX()
        val cy = bodyRect.centerY()
        val bw = bodyRect.width() * 0.5f
        val bh = bodyRect.height() * 0.6f

        boltPath.reset()
        boltPath.moveTo(cx + bw * 0.2f, cy - bh * 0.5f)
        boltPath.lineTo(cx - bw * 0.5f, cy + bh * 0.1f)
        boltPath.lineTo(cx + bw * 0.1f, cy + bh * 0.1f)
        boltPath.lineTo(cx - bw * 0.2f, cy + bh * 0.5f)
        boltPath.lineTo(cx + bw * 0.5f, cy - bh * 0.1f)
        boltPath.lineTo(cx - bw * 0.1f, cy - bh * 0.1f)
        boltPath.close()

        paint.color = Color.WHITE
        paint.alpha = pulseAlpha
        paint.style = Paint.Style.FILL
        canvas.drawPath(boltPath, paint)
        
        paint.color = Color.BLACK
        paint.alpha = pulseAlpha
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawPath(boltPath, paint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pulseAnimator?.cancel()
    }
}
