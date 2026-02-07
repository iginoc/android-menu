package com.example.menu

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
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
    
    // Callback per comunicare il carattere rilevato (ora basato su zona o barra laterale)
    var onLetterSelected: ((Char) -> Unit)? = null

    override fun onDraw(canvas: Canvas) {
        canvas.drawPath(path, paint)
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        val centerX = width / 2f
        val centerY = height / 2f
        val distance = sqrt(((x - centerX) * (x - centerX) + (y - centerY) * (y - centerY)).toDouble())
        
        // Area pulsanti (centrale)
        val buttonAreaRadius = 150 * resources.displayMetrics.density
        
        // Area Swipe Down (superiore, sopra l'orologio)
        val topThreshold = height * 0.15

        if (event.action == MotionEvent.ACTION_DOWN) {
            if (distance < buttonAreaRadius || y < topThreshold) {
                return false
            }
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                performClick()
                path.moveTo(x, y)
            }
            MotionEvent.ACTION_MOVE -> {
                path.lineTo(x, y)
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                // Implementeremo qui una logica di ricerca rapida laterale
                // Per ora puliamo solo il tratto
                postDelayed({
                    path.reset()
                    invalidate()
                }, 500)
            }
        }
        return true
    }
}