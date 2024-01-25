package com.example.driverAppPrototype

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

internal class paintResults {
    private val mPaintRectangle: Paint = Paint()
    private val mPaintText: Paint

    init {
        mPaintRectangle.color = Color.RED
        mPaintText = Paint()
        mPaintText.color = Color.RED
    }

    fun draw(canvas: Canvas, results: ArrayList<Result>) {
        for (result in results) {
            mPaintRectangle.strokeWidth = 5f
            mPaintRectangle.style = Paint.Style.STROKE
            canvas.drawRect(result.rect, mPaintRectangle)
            mPaintText.strokeWidth = 0f
            mPaintText.style = Paint.Style.FILL
            mPaintText.textSize = 32f
            //Locale.setDefault(new Locale("eng", "US"));
            canvas.drawText(
                String.format(
                    "%s %.2f",
                    postProcessor.mClasses[result.classIndex],
                    result.score
                ), result.rect.left.toFloat(), result.rect.top.toFloat(), mPaintText
            )
        }
    }

    companion object {
        private const val TEXT_WIDTH = 260
        private const val TEXT_HEIGHT = 50
    }
}