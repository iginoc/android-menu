package com.example.menu

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.google.mlkit.vision.digitalink.Ink
import kotlin.math.sqrt

class DrawingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 10f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    private val path = Path()
    private var inkBuilder = Ink.builder()
    private var strokeBuilder = Ink.Stroke.builder()
    
    var onStrokeFinished: ((Ink) -> Unit)? = null

    override fun onDraw(canvas: Canvas) {
        canvas.drawPath(path, paint)
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        val t = System.currentTimeMillis()

        val centerX = width / 2f
        val centerY = height / 2f
        val distance = sqrt(((x - centerX) * (x - centerX) + (y - centerY) * (y - centerY)).toDouble())
        
        // Area pulsanti (centrale)
        val buttonAreaRadius = 150 * resources.displayMetrics.density
        
        // Area Swipe Down (superiore, sopra l'orologio)
        val topThreshold = height * 0.15

        if (event.action == MotionEvent.ACTION_DOWN) {
            // Se tocchi in alto o al centro, lascia passare il tocco agli altri componenti
            if (distance < buttonAreaRadius || y < topThreshold) {
                return false
            }
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                performClick()
                path.moveTo(x, y)
                strokeBuilder = Ink.Stroke.builder()
                strokeBuilder.addPoint(Ink.Point.create(x, y, t))
            }
            MotionEvent.ACTION_MOVE -> {
                path.lineTo(x, y)
                strokeBuilder.addPoint(Ink.Point.create(x, y, t))
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                strokeBuilder.addPoint(Ink.Point.create(x, y, t))
                inkBuilder.addStroke(strokeBuilder.build())
                onStrokeFinished?.invoke(inkBuilder.build())
                
                postDelayed({
                    path.reset()
                    inkBuilder = Ink.builder()
                    invalidate()
                }, 1000)
            }
        }
        return true
    }
}