package com.example.menu

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView

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

    // Distanza dal centro dove vogliamo centrare l'immagine (in DP)
    // 140dp corrisponde alla posizione delle icone flottanti
    var centerRadiusDp: Float = 140f
        set(value) {
            field = value
            invalidate()
        }

    private val clipPath = Path()
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return
        
        val centerX = width / 2f
        val centerY = height / 2f
        
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        
        // Definiamo lo spicchio per il ritaglio
        clipPath.reset()
        clipPath.moveTo(centerX, centerY)
        clipPath.arcTo(rect, startAngle, sweepAngle)
        clipPath.lineTo(centerX, centerY)
        clipPath.close()
        
        canvas.save()
        // 1. Applichiamo il ritaglio a forma di spicchio
        canvas.clipPath(clipPath)
        
        // 2. Calcoliamo l'offset per centrare l'immagine nello spicchio
        // L'angolo centrale dello spicchio è startAngle + sweepAngle/2
        val midAngleRad = Math.toRadians((startAngle + sweepAngle / 2f).toDouble())
        val distancePx = centerRadiusDp * resources.displayMetrics.density
        val offsetX = (Math.cos(midAngleRad) * distancePx).toFloat()
        val offsetY = (Math.sin(midAngleRad) * distancePx).toFloat()
        
        // 3. Trasliamo il canvas in modo che il centro dell'immagine (che super.onDraw 
        // mette al centro della View) finisca sulla posizione dell'icona
        canvas.translate(offsetX, offsetY)
        
        // 4. Ingrandiamo leggermente l'immagine per compensare la traslazione
        // e garantire che copra ancora tutto lo spicchio (vertex e bordi)
        canvas.scale(1.5f, 1.5f, centerX, centerY)
        
        super.onDraw(canvas)
        canvas.restore()
    }
}