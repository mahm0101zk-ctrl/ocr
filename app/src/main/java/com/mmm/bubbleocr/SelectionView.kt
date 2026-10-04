package com.mmm.bubbleocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Shows the frozen screenshot, dimmed, with an adjustable selection rectangle. */
class SelectionView(
    context: Context,
    private val bmp: Bitmap,
    private val onChange: (Boolean) -> Unit
) : View(context) {

    private companion object {
        const val LEFT = 1
        const val TOP = 2
        const val RIGHT = 4
        const val BOTTOM = 8
        const val MOVE = 16
        const val NEW = 32
    }

    private var rect: RectF? = null
    private val src = Rect(0, 0, bmp.width, bmp.height)
    private val dst = RectF()
    private val dim = Paint().apply { color = 0x99000000.toInt() }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dpf(2f)
        color = 0xFF4FC3F7.toInt()
    }
    private val handleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val handleRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dpf(2f)
        color = 0xFF4FC3F7.toInt()
    }
    private val touch = context.dpf(28f)
    private val minSize = context.dpf(24f)
    private val handleR = context.dpf(7f)

    private var mode = NEW
    private var startX = 0f
    private var startY = 0f
    private var lastX = 0f
    private var lastY = 0f

    override fun onDraw(canvas: Canvas) {
        dst.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawBitmap(bmp, src, dst, null)
        val r = rect
        if (r == null) {
            canvas.drawRect(dst, dim)
            return
        }
        canvas.save()
        canvas.clipOutRect(r)
        canvas.drawRect(dst, dim)
        canvas.restore()
        canvas.drawRect(r, border)
        val xs = floatArrayOf(r.left, r.centerX(), r.right)
        val ys = floatArrayOf(r.top, r.centerY(), r.bottom)
        for (i in 0..2) for (j in 0..2) {
            if (i == 1 && j == 1) continue
            canvas.drawCircle(xs[i], ys[j], handleR, handleFill)
            canvas.drawCircle(xs[i], ys[j], handleR, handleRing)
        }
    }

    private fun hit(x: Float, y: Float): Int {
        val r = rect ?: return NEW
        var m = 0
        val inY = y > r.top - touch && y < r.bottom + touch
        val inX = x > r.left - touch && x < r.right + touch
        if (abs(x - r.left) < touch && inY) m = m or LEFT
        if (abs(x - r.right) < touch && inY) m = m or RIGHT
        if (abs(y - r.top) < touch && inX) m = m or TOP
        if (abs(y - r.bottom) < touch && inX) m = m or BOTTOM
        if (m != 0) return m
        return if (r.contains(x, y)) MOVE else NEW
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val x = e.x.coerceIn(0f, width.toFloat())
        val y = e.y.coerceIn(0f, height.toFloat())
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mode = hit(x, y)
                lastX = x; lastY = y
                if (mode == NEW) {
                    startX = x; startY = y
                    rect = RectF(x, y, x, y)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val r = rect ?: return true
                if (mode == NEW) {
                    r.set(min(startX, x), min(startY, y), max(startX, x), max(startY, y))
                } else if (mode == MOVE) {
                    val dx = (x - lastX).coerceIn(-r.left, width - r.right)
                    val dy = (y - lastY).coerceIn(-r.top, height - r.bottom)
                    r.offset(dx, dy)
                } else {
                    if (mode and LEFT != 0) r.left = min(x, r.right - minSize)
                    if (mode and RIGHT != 0) r.right = max(x, r.left + minSize)
                    if (mode and TOP != 0) r.top = min(y, r.bottom - minSize)
                    if (mode and BOTTOM != 0) r.bottom = max(y, r.top + minSize)
                }
                lastX = x; lastY = y
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val r = rect
                if (r != null && mode == NEW && (r.width() < minSize || r.height() < minSize)) rect = null
                onChange(rect != null)
                invalidate()
            }
        }
        return true
    }

    /** Selection mapped to bitmap pixel coordinates. */
    fun selectionInBitmap(): Rect? {
        val r = rect ?: return null
        if (width == 0 || height == 0) return null
        val sx = bmp.width / width.toFloat()
        val sy = bmp.height / height.toFloat()
        val l = (r.left * sx).toInt().coerceIn(0, bmp.width - 1)
        val t = (r.top * sy).toInt().coerceIn(0, bmp.height - 1)
        val rr = (r.right * sx).toInt().coerceIn(l + 1, bmp.width)
        val b = (r.bottom * sy).toInt().coerceIn(t + 1, bmp.height)
        return Rect(l, t, rr, b)
    }
}
