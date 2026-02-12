package com.example.menu

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.random.Random

class DrawingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 30f
    }

    private var items: List<MainActivity.AppListItem> = emptyList()
    private val rects = mutableListOf<RectF>()
    private var onItemClick: ((MainActivity.AppListItem) -> Unit)? = null

    fun setData(items: List<MainActivity.AppListItem>, onItemClick: (MainActivity.AppListItem) -> Unit) {
        this.items = items.take(12)
        this.onItemClick = onItemClick
        generateRects()
        invalidate()
    }

    private fun generateRects() {
        rects.clear()
        if (width == 0 || height == 0) return

        val size = Math.min(width, height).toFloat()
        val startX = (width - size) / 2
        val startY = (height - size) / 2
        val c = size / 4 // base della griglia 4x4

        // 1. Prima riga (4 rettangoli)
        rects.add(RectF(startX, startY, startX + c, startY + c))
        rects.add(RectF(startX + c, startY, startX + 2*c, startY + c))
        rects.add(RectF(startX + 2*c, startY, startX + 3*c, startY + c))
        rects.add(RectF(startX + 3*c, startY, startX + 4*c, startY + c))

        // 2. Quinta icona (indice 4) - GRANDE QUADRATO CENTRALE 2x2
        rects.add(RectF(startX + c, startY + c, startX + 3*c, startY + 3*c))

        // 3. Icone laterali al quadrato centrale
        rects.add(RectF(startX, startY + c, startX + c, startY + 2*c))       // Sinistra 1
        rects.add(RectF(startX + 3*c, startY + c, startX + 4*c, startY + 2*c)) // Destra 1
        rects.add(RectF(startX, startY + 2*c, startX + c, startY + 3*c))       // Sinistra 2
        rects.add(RectF(startX + 3*c, startY + 2*c, startX + 4*c, startY + 3*c)) // Destra 2

        // 4. Ultima riga (3 rettangoli per arrivare a 12 totali, uno più largo)
        rects.add(RectF(startX, startY + 3*c, startX + c, startY + 4*c))
        rects.add(RectF(startX + c, startY + 3*c, startX + 2.5f*c, startY + 4*c))
        rects.add(RectF(startX + 2.5f*c, startY + 3*c, startX + 4*c, startY + 4*c))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        generateRects()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (i in 0 until 12) {
            if (i >= rects.size) break
            val rect = rects[i]
            paint.color = Color.BLACK
            canvas.drawRect(rect, paint)

            if (i < items.size) {
                val item = items[i]
                val icon = getIconForItem(item)
                icon?.let {
                    canvas.save()
                    canvas.clipRect(rect)
                    val iconW = it.intrinsicWidth.takeIf { it > 0 } ?: 100
                    val iconH = it.intrinsicHeight.takeIf { it > 0 } ?: 100
                    
                    // Applichiamo uno zoom extra (1.4f) per eliminare i bordi vuoti tipici delle icone
                    val baseScale = Math.max(rect.width() / iconW, rect.height() / iconH)
                    val scale = baseScale * 1.4f

                    val drawW = iconW * scale
                    val drawH = iconH * scale
                    val left = rect.centerX() - drawW / 2
                    val top = rect.centerY() - drawH / 2
                    it.setBounds(left.toInt(), top.toInt(), (left + drawW).toInt(), (top + drawH).toInt())
                    it.draw(canvas)
                    canvas.restore()
                }
            }
            canvas.drawRect(rect, borderPaint)
        }
    }

    private fun getIconForItem(item: MainActivity.AppListItem): Drawable? {
        return when (item) {
            is MainActivity.AppListItem.Special -> null
            is MainActivity.AppListItem.App -> {
                (context as? MainActivity)?.let { it.iconCache[item.res.activityInfo.packageName] ?: item.res.loadIcon(it.packageManager) }
            }
            is MainActivity.AppListItem.Link -> {
                context.getDrawable(android.R.drawable.ic_menu_share)
            }
            else -> null
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            for (i in 0 until rects.size.coerceAtMost(items.size)) {
                if (rects[i].contains(event.x, event.y)) {
                    onItemClick?.invoke(items[i])
                    return true
                }
            }
        }
        return true
    }
}
