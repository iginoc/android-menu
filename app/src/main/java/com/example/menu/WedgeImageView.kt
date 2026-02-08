package com.example.menu

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.atan2
import kotlin.math.sqrt

class WedgeImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    var startAngle: Float = 0f
        set(value) { field = value; invalidate() }
    var sweepAngle: Float = 60f
        set(value) { field = value; invalidate() }
    var iconRadiusFraction: Float = 0.7f 
        set(value) { field = value; invalidate() }
    var wedgeColor: Int = Color.TRANSPARENT
        set(value) { field = value; invalidate() }

    private val clipPath = Path()
    private val rect = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 30f
        strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return
        val centerX = width / 2f
        val centerY = height / 2f
        
        // Usiamo un piccolo padding per evitare che il bordo venga tagliato
        val padding = borderPaint.strokeWidth / 2f
        rect.set(padding, padding, width.toFloat() - padding, height.toFloat() - padding)
        
        clipPath.reset()
        clipPath.moveTo(centerX, centerY)
        clipPath.arcTo(rect, startAngle, sweepAngle)
        clipPath.lineTo(centerX, centerY)
        clipPath.close()
        
        // 1. Disegna lo sfondo colorato dello spicchio
        if (wedgeColor != Color.TRANSPARENT) {
            paint.color = wedgeColor
            canvas.drawPath(clipPath, paint)
        }

        canvas.save()
        canvas.clipPath(clipPath)
        
        // 2. Disegna l'icona centrata nello spicchio
        val midAngleRad = Math.toRadians((startAngle + sweepAngle / 2f).toDouble())
        val distance = (width / 2f) * iconRadiusFraction
        val offsetX = (Math.cos(midAngleRad) * distance).toFloat()
        val offsetY = (Math.sin(midAngleRad) * distance).toFloat()
        
        canvas.translate(offsetX, offsetY)
        canvas.scale(0.7f, 0.7f, centerX, centerY)
        super.onDraw(canvas)
        canvas.restore()

        // 3. Disegna i bordi neri (linee radiali e arco esterno)
        canvas.drawArc(rect, startAngle, sweepAngle, false, borderPaint)
        
        val startRad = Math.toRadians(startAngle.toDouble())
        canvas.drawLine(centerX, centerY, centerX + (rect.width() / 2f) * Math.cos(startRad).toFloat(), centerY + (rect.height() / 2f) * Math.sin(startRad).toFloat(), borderPaint)
        val endRad = Math.toRadians((startAngle + sweepAngle).toDouble())
        canvas.drawLine(centerX, centerY, centerX + (rect.width() / 2f) * Math.cos(endRad).toFloat(), centerY + (rect.height() / 2f) * Math.sin(endRad).toFloat(), borderPaint)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val x = event.x - width / 2f
            val y = event.y - height / 2f
            val distance = sqrt((x * x + y * y).toDouble())
            val isSmall = width < resources.displayMetrics.widthPixels * 0.6
            if (!isSmall && distance < (width / 4f)) return false
            var touchAngle = Math.toDegrees(atan2(y.toDouble(), x.toDouble())).toFloat()
            var nStart = (startAngle % 360 + 360) % 360
            var nTouch = (touchAngle % 360 + 360) % 360
            val end = nStart + sweepAngle
            val isIn = if (end <= 360) nTouch in nStart..end else nTouch >= nStart || nTouch <= (end % 360)
            if (!isIn) return false
        }
        return super.dispatchTouchEvent(event)
    }
}