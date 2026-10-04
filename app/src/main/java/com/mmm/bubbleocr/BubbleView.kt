package com.mmm.bubbleocr

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

class BubbleView(
    context: Context,
    private val onTap: () -> Unit,
    private val onDrag: (Int, Int) -> Unit
) : View(context) {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE6E53935.toInt() }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = context.dpf(2.5f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var moved = false

    override fun onDraw(canvas: Canvas) {
        val c = width / 2f
        canvas.drawCircle(c, c, c - 2f, fill)
        val m = width * 0.30f
        val l = width * 0.14f
        val e = width - m
        path.reset()
        path.moveTo(m, m + l); path.lineTo(m, m); path.lineTo(m + l, m)
        path.moveTo(e - l, m); path.lineTo(e, m); path.lineTo(e, m + l)
        path.moveTo(e, e - l); path.lineTo(e, e); path.lineTo(e - l, e)
        path.moveTo(m + l, e); path.lineTo(m, e); path.lineTo(m, e - l)
        canvas.drawPath(path, stroke)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.rawX; downY = e.rawY
                lastX = downX; lastY = downY
                moved = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(e.rawX - downX) > slop || abs(e.rawY - downY) > slop) moved = true
                if (moved) {
                    val dx = (e.rawX - lastX).toInt()
                    val dy = (e.rawY - lastY).toInt()
                    if (dx != 0 || dy != 0) {
                        onDrag(dx, dy)
                        lastX += dx; lastY += dy
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!moved) {
                    performClick()
                    onTap()
                }
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
