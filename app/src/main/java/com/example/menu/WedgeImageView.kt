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

    var centerRadiusDp: Float = 140f
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
        strokeWidth = 30f // Spessore del tratto nero triplicato (da 10f a 30f)
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
        
        // Disegno dello sfondo dello spicchio con ritaglio
        canvas.save()
        canvas.clipPath(clipPath)
        
        val midAngleRad = Math.toRadians((startAngle + sweepAngle / 2f).toDouble())
        val distancePx = centerRadiusDp * resources.displayMetrics.density
        val offsetX = (Math.cos(midAngleRad) * distancePx).toFloat()
        val offsetY = (Math.sin(midAngleRad) * distancePx).toFloat()
        
        canvas.translate(offsetX, offsetY)
        canvas.scale(0.8f, 0.8f, centerX, centerY)
        
        super.onDraw(canvas)
        canvas.restore()

        // Disegno del bordo nero tra gli spicchi
        borderPath.reset()
        borderPath.moveTo(centerX, centerY)
        // Disegno la linea radiale all'angolo di inizio
        val startRad = Math.toRadians(startAngle.toDouble())
        val startX = centerX + (width / 2f) * Math.cos(startRad).toFloat()
        val startY = centerY + (height / 2f) * Math.sin(startRad).toFloat()
        borderPath.lineTo(startX, startY)
        
        // Disegno la linea radiale all'angolo di fine
        borderPath.moveTo(centerX, centerY)
        val endRad = Math.toRadians((startAngle + sweepAngle).toDouble())
        val endX = centerX + (width / 2f) * Math.cos(endRad).toFloat()
        val endY = centerY + (height / 2f) * Math.sin(endRad).toFloat()
        borderPath.lineTo(endX, endY)
        
        canvas.drawPath(borderPath, borderPaint)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val x = event.x - width / 2f
            val y = event.y - height / 2f
            
            val distance = sqrt((x * x + y * y).toDouble())
            
            if (distance < (width / 4f)) return false

            var touchAngle = Math.toDegrees(atan2(y.toDouble(), x.toDouble())).toFloat()
            
            var normalizedStart = startAngle % 360
            if (normalizedStart < 0) normalizedStart += 360
            
            var normalizedTouch = touchAngle % 360
            if (normalizedTouch < 0) normalizedTouch += 360
            
            val endAngle = normalizedStart + sweepAngle
            
            val isInWedge = if (endAngle <= 360) {
                normalizedTouch in normalizedStart..endAngle
            } else {
                normalizedTouch >= normalizedStart || normalizedTouch <= (endAngle % 360)
            }

            if (!isInWedge) return false
        }
        return super.dispatchTouchEvent(event)
    }
}