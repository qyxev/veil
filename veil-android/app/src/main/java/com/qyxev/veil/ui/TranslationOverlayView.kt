package com.qyxev.veil.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.View

data class TranslatedBlock(
    val boundingBox: Rect,
    val translatedText: String
)

class TranslationOverlayView(context: Context, private val blocks: List<TranslatedBlock>) : View(context) {

    private val bgPaint = Paint().apply {
        color = Color.parseColor("#E6000000") // Pure black glassmorphism
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 36f
        isAntiAlias = true
        setShadowLayer(4f, 0f, 0f, Color.BLACK)
    }

    private val borderPaint = Paint().apply {
        color = Color.parseColor("#33FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (block in blocks) {
            val rect = block.boundingBox

            canvas.drawRoundRect(
                rect.left.toFloat(),
                rect.top.toFloat(),
                rect.right.toFloat(),
                rect.bottom.toFloat(),
                8f, 8f,
                bgPaint
            )

            canvas.drawRoundRect(
                rect.left.toFloat(),
                rect.top.toFloat(),
                rect.right.toFloat(),
                rect.bottom.toFloat(),
                8f, 8f,
                borderPaint
            )

            canvas.drawText(
                block.translatedText,
                rect.left.toFloat() + 8f,
                rect.bottom.toFloat() - 8f,
                textPaint
            )
        }
    }
}