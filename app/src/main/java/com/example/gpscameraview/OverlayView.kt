package com.example.gpscameraview

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val paintGreen: Paint = Paint().apply {
        color = Color.GREEN
        strokeWidth = 5f
    }
    private val paintYellow: Paint = Paint().apply {
        color = Color.YELLOW
        strokeWidth = 5f
    }
    private val paintRed: Paint = Paint().apply {
        color = Color.RED
        textSize = 30f
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val activity = context as MainActivity
        if (activity.debugEnabled) {
            canvas.drawText(activity.currentLocation, 10f, 30f, paintRed);
        }
        if (activity.isWithinRange) {
            // Draw two lines if within range
            canvas.drawLine(0f, 100f, width.toFloat(), 100f, paintGreen)
            canvas.drawLine(0f, 200f, width.toFloat(), 200f, paintYellow)
        }
    }
}
