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
    
    private var currentBgColor = Color.BLACK
    private var currentFgColor = Color.WHITE

    private val largeIndices = mutableSetOf<Int>()

    fun setData(items: List<MainActivity.AppListItem>, onItemClick: (MainActivity.AppListItem) -> Unit) {
        this.items = items
        this.onItemClick = onItemClick
        
        largeIndices.clear()
        largeIndices.add(4)
        if (items.size > 1) {
            val available = (1 until items.size).filter { it != 4 }.shuffled()
            largeIndices.addAll(available.take(5))
        }
        
        generateRects()
        invalidate()
    }

    fun setColors(bgColor: Int, fgColor: Int) {
        currentBgColor = bgColor
        currentFgColor = fgColor
        borderPaint.color = fgColor
        invalidate()
    }

    private fun generateRects() {
        rects.clear()
        if (width == 0 || height == 0) return

        if (width > height) {
            generateLandscapeRects()
        } else {
            generatePortraitRects()
        }
    }

    private fun generatePortraitRects() {
        val size = Math.min(width, height).toFloat()
        val startX = (width - size) / 2
        val startY = (height - size) / 2
        val c = size / 4

        val pRects = arrayOfNulls<RectF>(12)
        pRects[0] = RectF(startX, startY, startX + c, startY + c)
        pRects[1] = RectF(startX + c, startY, startX + 2*c, startY + c)
        pRects[2] = RectF(startX + 2*c, startY, startX + 3*c, startY + c)
        pRects[3] = RectF(startX + 3*c, startY, startX + 4*c, startY + c)
        pRects[4] = RectF(startX + c, startY + c, startX + 3*c, startY + 3*c)
        pRects[5] = RectF(startX, startY + c, startX + c, startY + 2*c)
        pRects[6] = RectF(startX + 3*c, startY + c, startX + 4*c, startY + 2*c)
        pRects[7] = RectF(startX, startY + 2*c, startX + c, startY + 3*c)
        pRects[8] = RectF(startX + 3*c, startY + 2*c, startX + 4*c, startY + 3*c)
        pRects[9] = RectF(startX, startY + 3*c, startX + c, startY + 4*c)
        pRects[10] = RectF(startX + c, startY + 3*c, startX + 2.5f*c, startY + 4*c)
        pRects[11] = RectF(startX + 2.5f*c, startY + 3*c, startX + 4*c, startY + 4*c)
        
        for (r in pRects) if (r != null) rects.add(r)
    }

    private fun generateLandscapeRects() {
        val rows = 4
        val c = height.toFloat() / rows
        val cols = (width / c).toInt()
        val startX = (width - cols * c) / 2
        
        val occupied = Array(rows) { BooleanArray(cols) { false } }
        val tempRectsMap = mutableMapOf<Int, RectF>()
        
        fun findNextFree(): Pair<Int, Int>? {
            for (r in 0 until rows) {
                for (col in 0 until cols) {
                    if (!occupied[r][col]) return r to col
                }
            }
            return null
        }

        for (i in items.indices) {
            val free = findNextFree() ?: break
            val r = free.first
            val col = free.second
            
            if (largeIndices.contains(i) && r + 1 < rows && col + 1 < cols &&
                !occupied[r+1][col] && !occupied[r][col+1] && !occupied[r+1][col+1]) {
                val rect = RectF(startX + col * c, r * c, startX + (col + 2) * c, (r + 2) * c)
                tempRectsMap[i] = rect
                occupied[r][col] = true
                occupied[r+1][col] = true
                occupied[r][col+1] = true
                occupied[r+1][col+1] = true
            } else {
                val rect = RectF(startX + col * c, r * c, startX + (col + 1) * c, (r + 1) * c)
                tempRectsMap[i] = rect
                occupied[r][col] = true
            }
        }
        
        for (i in 0 until items.size) {
            tempRectsMap[i]?.let { rects.add(it) }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        generateRects()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (i in rects.indices) {
            val rect = rects[i]
            paint.color = currentBgColor
            canvas.drawRect(rect, paint)

            if (i < items.size) {
                val item = items[i]
                val icon = getIconForItem(item)
                icon?.let {
                    canvas.save()
                    canvas.clipRect(rect)
                    val iconW = it.intrinsicWidth.takeIf { it > 0 } ?: 100
                    val iconH = it.intrinsicHeight.takeIf { it > 0 } ?: 100
                    val scale = Math.max(rect.width() / iconW, rect.height() / iconH) * 1.4f
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
            val limit = Math.min(rects.size, items.size)
            for (i in 0 until limit) {
                if (rects[i].contains(event.x, event.y)) {
                    onItemClick?.invoke(items[i])
                    return true
                }
            }
        }
        return true
    }
}
