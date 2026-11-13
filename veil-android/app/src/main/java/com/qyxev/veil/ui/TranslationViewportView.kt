package com.qyxev.veil.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs

class TranslationViewportView(
    context: Context,
    private val windowManager: WindowManager,
    private val layoutParams: WindowManager.LayoutParams,
    private val screenWidth: Int,
    private val screenHeight: Int,
    private val onCaptureRequested: (Rect) -> Unit,
    private val onDismissRequested: () -> Unit
) : View(context) {

    // Drawer & Viewport State
    private var isExpanded = false
    private var isSnappedLeft = false
    private var translatedText: String = "Scanning region..."
    private var isTranslated = false

    // Saved Dimensions for Expanded State
    private var expandedWidth = 820
    private var expandedHeight = 320
    private var expandedX = (screenWidth - 820) / 2
    private var expandedY = screenHeight / 3

    // Compact Notch Dimensions
    private val notchWidth = 54
    private val notchHeight = 150
    private var notchY = screenHeight / 3

    // Glassmorphic Paints
    private val bgPaint = Paint().apply {
        color = Color.parseColor("#A6000000") // Translucent pure black glass
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val borderPaint = Paint().apply {
        color = Color.parseColor("#80FFFFFF") // Frosted glass rim
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        isAntiAlias = true
    }

    private val notchIndicatorPaint = Paint().apply {
        color = Color.parseColor("#CCFFFFFF")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 38f
        isAntiAlias = true
        setShadowLayer(6f, 0f, 0f, Color.BLACK)
    }

    private val controlPaint = Paint().apply {
        color = Color.parseColor("#E6FFFFFF")
        textSize = 44f
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
    }

    // Touch & Gesture Tracking
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var initialWidth = 0
    private var initialHeight = 0
    private var isResizing = false

    private val closeButtonRect = RectF()
    private val collapseButtonRect = RectF()

    init {
        // Start docked cleanly on the right edge
        layoutParams.width = notchWidth
        layoutParams.height = notchHeight
        layoutParams.x = screenWidth - notchWidth
        layoutParams.y = notchY
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = layoutParams.x
                initialY = layoutParams.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                initialWidth = layoutParams.width
                initialHeight = layoutParams.height

                if (isExpanded) {
                    if (closeButtonRect.contains(event.x, event.y)) {
                        onDismissRequested()
                        return true
                    }
                    if (collapseButtonRect.contains(event.x, event.y)) {
                        collapseToNotch()
                        return true
                    }
                    isResizing = event.x >= width - 85f && event.y >= height - 85f
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - initialTouchX).toInt()
                val dy = (event.rawY - initialTouchY).toInt()

                if (!isExpanded) {
                    // Drag notch freely; will snap to nearest edge on release
                    layoutParams.x = (initialX + dx).coerceIn(0, screenWidth - notchWidth)
                    layoutParams.y = (initialY + dy).coerceIn(60, screenHeight - notchHeight - 60)
                } else if (isResizing) {
                    expandedWidth = (initialWidth + dx).coerceIn(360, screenWidth - 40)
                    expandedHeight = (initialHeight + dy).coerceIn(180, screenHeight / 2)
                    layoutParams.width = expandedWidth
                    layoutParams.height = expandedHeight
                } else {
                    expandedX = (initialX + dx).coerceIn(0, screenWidth - layoutParams.width)
                    expandedY = (initialY + dy).coerceIn(40, screenHeight - layoutParams.height - 40)
                    layoutParams.x = expandedX
                    layoutParams.y = expandedY
                }

                windowManager.updateViewLayout(this, layoutParams)
                requestLayout()
                return true
            }

            MotionEvent.ACTION_UP -> {
                val isClick = abs(event.rawX - initialTouchX) < 15 && abs(event.rawY - initialTouchY) < 15

                if (!isExpanded) {
                    if (isClick) {
                        expandDrawer()
                    } else {
                        snapNotchToNearestEdge()
                    }
                } else {
                    if (isClick && !isResizing) {
                        triggerRegionScan()
                    }
                    isResizing = false
                }
                return true
            }
        }
        return false
    }

    private fun snapNotchToNearestEdge() {
        val centerX = layoutParams.x + (notchWidth / 2)
        isSnappedLeft = centerX < screenWidth / 2
        val targetX = if (isSnappedLeft) 0 else (screenWidth - notchWidth)
        notchY = layoutParams.y

        ValueAnimator.ofInt(layoutParams.x, targetX).apply {
            duration = 200
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                layoutParams.x = anim.animatedValue as Int
                windowManager.updateViewLayout(this@TranslationViewportView, layoutParams)
            }
            start()
        }
    }

    private fun expandDrawer() {
        isExpanded = true
        translatedText = "Detecting text..."
        isTranslated = false

        val startX = layoutParams.x
        val startY = layoutParams.y
        val startW = layoutParams.width
        val startH = layoutParams.height

        // Keep vertical alignment near the notch if possible
        val targetX = ((screenWidth - expandedWidth) / 2).coerceIn(20, screenWidth - expandedWidth - 20)
        val targetY = notchY.coerceIn(80, screenHeight - expandedHeight - 80)
        expandedX = targetX
        expandedY = targetY

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 260
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val f = anim.animatedFraction
                layoutParams.x = (startX + (targetX - startX) * f).toInt()
                layoutParams.y = (startY + (targetY - startY) * f).toInt()
                layoutParams.width = (startW + (expandedWidth - startW) * f).toInt()
                layoutParams.height = (startH + (expandedHeight - startH) * f).toInt()
                windowManager.updateViewLayout(this@TranslationViewportView, layoutParams)
                invalidate()
            }
            start()
        }

        // Automatically detect text immediately after expansion animation completes
        postDelayed({
            if (isExpanded) triggerRegionScan()
        }, 300)
    }

    private fun collapseToNotch() {
        isExpanded = false
        val startX = layoutParams.x
        val startY = layoutParams.y
        val startW = layoutParams.width
        val startH = layoutParams.height

        val targetX = if (isSnappedLeft) 0 else (screenWidth - notchWidth)
        val targetY = startY.coerceIn(60, screenHeight - notchHeight - 60)
        notchY = targetY

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 220
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val f = anim.animatedFraction
                layoutParams.x = (startX + (targetX - startX) * f).toInt()
                layoutParams.y = (startY + (targetY - startY) * f).toInt()
                layoutParams.width = (startW + (notchWidth - startW) * f).toInt()
                layoutParams.height = (startH + (notchHeight - startH) * f).toInt()
                windowManager.updateViewLayout(this@TranslationViewportView, layoutParams)
                invalidate()
            }
            start()
        }
    }

    private fun triggerRegionScan() {
        val location = IntArray(2)
        getLocationOnScreen(location)
        val captureRect = Rect(location[0], location[1], location[0] + width, location[1] + height)
        onCaptureRequested(captureRect)
    }

    fun setTranslatedResult(text: String) {
        translatedText = text
        isTranslated = true
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        if (!isExpanded) {
            // Draw Sleek Edge Drawer Pill
            val radius = w / 2f
            canvas.drawRoundRect(0f, 0f, w, h, radius, radius, bgPaint)
            canvas.drawRoundRect(2f, 2f, w - 2f, h - 2f, radius, radius, borderPaint)

            // Draw Vertical Handle Bar inside Notch
            val barX = if (isSnappedLeft) w * 0.65f else w * 0.35f
            canvas.drawRoundRect(barX - 3f, h * 0.28f, barX + 3f, h * 0.72f, 4f, 4f, notchIndicatorPaint)
        } else {
            // Draw Expanded Glassmorphic Overlay
            canvas.drawRoundRect(0f, 0f, w, h, 28f, 28f, bgPaint)
            canvas.drawRoundRect(2f, 2f, w - 2f, h - 2f, 28f, 28f, borderPaint)

            // Draw Detected/Translated Text (Multi-line support)
            drawWrappedText(canvas, translatedText, 36f, 84f, w - 150f)

            // Minimize to Notch ('—') Button
            collapseButtonRect.set(w - 135f, 12f, w - 75f, 72f)
            canvas.drawText("—", w - 118f, 52f, controlPaint)

            // Close Service ('×') Button
            closeButtonRect.set(w - 70f, 12f, w - 10f, 72f)
            canvas.drawText("×", w - 52f, 54f, controlPaint)

            // Bottom-Right Resize Grip Lines
            canvas.drawLine(w - 32f, h - 12f, w - 12f, h - 32f, borderPaint)
            canvas.drawLine(w - 20f, h - 12f, w - 12f, h - 20f, borderPaint)
        }
    }

    private fun drawWrappedText(canvas: Canvas, text: String, startX: Float, startY: Float, maxWidth: Float) {
        val lines = text.split("\n")
        var currentY = startY
        for (paragraph in lines) {
            var remaining = paragraph
            while (remaining.isNotEmpty() && currentY < height - 30f) {
                val count = textPaint.breakText(remaining, true, maxWidth, null)
                canvas.drawText(remaining.substring(0, count), startX, currentY, textPaint)
                remaining = remaining.substring(count).trimStart()
                currentY += textPaint.textSize + 12f
            }
        }
    }
}