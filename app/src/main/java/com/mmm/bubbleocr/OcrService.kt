package com.mmm.bubbleocr

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class OcrService : AccessibilityService() {

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    private var bubble: View? = null
    private var selectionRoot: View? = null
    private var resultRoot: View? = null
    private var resultTv: TextView? = null
    private var currentText = ""
    private var capturing = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        addBubble()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        removeSelection()
        removeResult()
        bubble?.let { runCatching { wm.removeView(it) } }
        bubble = null
        recognizer.close()
        super.onDestroy()
    }

    // ---------- Bubble ----------

    private fun addBubble() {
        val size = dp(52)
        val lp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = dp(220)
        }
        val dm = resources.displayMetrics
        val v = BubbleView(
            this,
            onTap = { startCapture() },
            onDrag = { dx, dy ->
                lp.x = (lp.x + dx).coerceIn(0, dm.widthPixels - size)
                lp.y = (lp.y + dy).coerceIn(0, dm.heightPixels - size)
                bubble?.let { runCatching { wm.updateViewLayout(it, lp) } }
            }
        )
        bubble = v
        wm.addView(v, lp)
    }

    // ---------- Capture ----------

    private fun startCapture() {
        if (capturing || selectionRoot != null) return
        capturing = true
        removeResult()
        bubble?.visibility = View.INVISIBLE
        handler.postDelayed({
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    val hw = result.hardwareBuffer
                    val hwBmp = Bitmap.wrapHardwareBuffer(hw, result.colorSpace)
                    val bmp = hwBmp?.copy(Bitmap.Config.ARGB_8888, false)
                    hwBmp?.recycle()
                    hw.close()
                    capturing = false
                    if (bmp == null) {
                        bubble?.visibility = View.VISIBLE
                        toast("Could not read the screenshot")
                    } else {
                        showSelection(bmp)
                    }
                }

                override fun onFailure(errorCode: Int) {
                    capturing = false
                    bubble?.visibility = View.VISIBLE
                    toast("Screenshot failed (code $errorCode). Try again in a second.")
                }
            })
        }, 200)
    }

    // ---------- Selection overlay ----------

    private fun showSelection(bmp: Bitmap) {
        val root = FrameLayout(this)
        val readBtn = Button(this).apply {
            text = "Read text"
            isEnabled = false
        }
        val sel = SelectionView(this, bmp) { has -> readBtn.isEnabled = has }
        root.addView(sel, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        val hint = TextView(this).apply {
            text = "Drag to select the text area"
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = GradientDrawable().apply {
                setColor(0xB3000000.toInt())
                cornerRadius = dpf(20f)
            }
        }
        root.addView(
            hint,
            FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
                .apply { topMargin = dp(72) }
        )

        val cancel = Button(this).apply {
            text = "Cancel"
            setOnClickListener { removeSelection(); bmp.recycle() }
        }
        readBtn.setOnClickListener {
            val r = sel.selectionInBitmap()
            if (r != null) {
                removeSelection()
                runOcr(bmp, r)
            }
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(cancel)
            addView(readBtn, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { leftMargin = dp(12) })
        }
        root.addView(
            bar,
            FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
                .apply { bottomMargin = dp(88) }
        )

        val lp = WindowManager.LayoutParams(
            MATCH_PARENT, MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        selectionRoot = root
        wm.addView(root, lp)
    }

    private fun removeSelection() {
        selectionRoot?.let { runCatching { wm.removeView(it) } }
        selectionRoot = null
        bubble?.visibility = View.VISIBLE
    }

    // ---------- OCR ----------

    private fun runOcr(full: Bitmap, sel: Rect) {
        var crop = Bitmap.createBitmap(full, sel.left, sel.top, sel.width(), sel.height())
        if (crop !== full) full.recycle()
        val scale = when {
            crop.height < 64 -> 3
            crop.height < 160 -> 2
            else -> 1
        }
        if (scale > 1) {
            crop = Bitmap.createScaledBitmap(crop, crop.width * scale, crop.height * scale, true)
        }
        showResult("Reading...", canCopy = false)
        recognizer.process(InputImage.fromBitmap(crop, 0))
            .addOnSuccessListener { r ->
                val t = r.text.trim()
                showResult(if (t.isEmpty()) "No text found." else t, canCopy = t.isNotEmpty())
            }
            .addOnFailureListener { e ->
                showResult("OCR failed: ${e.message}", canCopy = false)
            }
    }

    // ---------- Result card ----------

    private fun showResult(text: String, canCopy: Boolean) {
        currentText = if (canCopy) text else ""
        if (resultRoot == null) buildResult()
        resultTv?.text = text
    }

    private fun buildResult() {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(12))
            background = GradientDrawable().apply {
                setColor(0xFF1E1E1E.toInt())
                val r = dpf(20f)
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
        }
        val tv = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 16f
        }
        val maxH = dp(260)
        val scroll = object : ScrollView(this) {
            override fun onMeasure(w: Int, h: Int) {
                super.onMeasure(w, MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.AT_MOST))
            }
        }
        scroll.addView(tv)
        card.addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        fun btn(label: String, onClick: () -> Unit) = Button(this).apply {
            text = label
            setOnClickListener { onClick() }
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val w = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        row.addView(btn("Copy") { copyText() }, w)
        row.addView(btn("New scan") { startCapture() }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        row.addView(btn("Close") { removeResult() }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        card.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })

        val lp = WindowManager.LayoutParams(
            MATCH_PARENT, WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.BOTTOM }
        resultTv = tv
        resultRoot = card
        wm.addView(card, lp)
    }

    private fun removeResult() {
        resultRoot?.let { runCatching { wm.removeView(it) } }
        resultRoot = null
        resultTv = null
    }

    private fun copyText() {
        if (currentText.isEmpty()) {
            toast("Nothing to copy")
            return
        }
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("OCR", currentText))
        toast("Copied")
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
