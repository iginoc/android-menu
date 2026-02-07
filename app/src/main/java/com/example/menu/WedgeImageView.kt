package com.example.menu

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.atan2
import kotlin.math.sqrt

class WedgeImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    var startAngle: Float = 0f
        set(value) {
            field = value
            invalidate()
        }
        
    var sweepAngle: Float = 60f
        set(value) {
            field = value
            invalidate()
        }

    // Posizione dell'icona espressa come frazione del raggio (0.0 a 1.0)
    var iconRadiusFraction: Float = 0.7f 
        set(value) {
            field = value
            invalidate()
        }

    private val clipPath = Path()
    private val borderPath = Path()
    private val rect = RectF()
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 30f
    }

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return
        
        val centerX = width / 2f
        val centerY = height / 2f
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        
        clipPath.reset()
        clipPath.moveTo(centerX, centerY)
        clipPath.arcTo(rect, startAngle, sweepAngle)
        clipPath.lineTo(centerX, centerY)
        clipPath.close()
        
        canvas.save()
        canvas.clipPath(clipPath)
        
        val midAngleRad = Math.toRadians((startAngle + sweepAngle / 2f).toDouble())
        // Calcola la distanza in base alla dimensione reale della vista
        val distance = (width / 2f) * iconRadiusFraction
        val offsetX = (Math.cos(midAngleRad) * distance).toFloat()
        val offsetY = (Math.sin(midAngleRad) * distance).toFloat()
        
        canvas.translate(offsetX, offsetY)
        canvas.scale(0.8f, 0.8f, centerX, centerY)
        
        super.onDraw(canvas)
        canvas.restore()

        borderPath.reset()
        borderPath.moveTo(centerX, centerY)
        val startRad = Math.toRadians(startAngle.toDouble())
        borderPath.lineTo(centerX + (width / 2f) * Math.cos(startRad).toFloat(), centerY + (height / 2f) * Math.sin(startRad).toFloat())
        borderPath.moveTo(centerX, centerY)
        val endRad = Math.toRadians((startAngle + sweepAngle).toDouble())
        borderPath.lineTo(centerX + (width / 2f) * Math.cos(endRad).toFloat(), centerY + (height / 2f) * Math.sin(endRad).toFloat())
        
        canvas.drawPath(borderPath, borderPaint)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val x = event.x - width / 2f
            val y = event.y - height / 2f
            val distance = sqrt((x * x + y * y).toDouble())
            
            // Per gli spicchi interni (piccoli), non filtriamo il centro. Per quelli grandi sì.
            val isSmallWedge = width < resources.displayMetrics.widthPixels * 0.6
            if (!isSmallWedge && distance < (width / 4f)) return false

            var touchAngle = Math.toDegrees(atan2(y.toDouble(), x.toDouble())).toFloat()
            var normalizedStart = (startAngle % 360 + 360) % 360
            var normalizedTouch = (touchAngle % 360 + 360) % 360
            val endAngle = normalizedStart + sweepAngle
            
            val isInWedge = if (endAngle <= 360) normalizedTouch in normalizedStart..endAngle
                            else normalizedTouch >= normalizedStart || normalizedTouch <= (endAngle % 360)

            if (!isInWedge) return false
        }
        return super.dispatchTouchEvent(event)
    }
}