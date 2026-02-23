package com.homeobjectcontroller

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

data class OverlayObject(
    val boundingBox: Rect,
    val label: String,
    val isCustomLabel: Boolean,
    val trackingId: Int?,
    val detectedInfo: DetectedObjectInfo
)

class ObjectOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var objects: List<OverlayObject> = emptyList()
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1
    private var onObjectTapped: ((OverlayObject) -> Unit)? = null

    private val boxPaintDefault = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    private val boxPaintCustom = Paint().apply {
        color = Color.parseColor("#4CAF50")
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    private val fillPaintDefault = Paint().apply {
        color = Color.parseColor("#33FFFFFF")
        style = Paint.Style.FILL
    }

    private val fillPaintCustom = Paint().apply {
        color = Color.parseColor("#334CAF50")
        style = Paint.Style.FILL
    }

    private val labelBackgroundPaint = Paint().apply {
        color = Color.parseColor("#CC000000")
        style = Paint.Style.FILL
    }

    private val labelTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 36f
        isAntiAlias = true
        isFakeBoldText = true
    }

    fun setOnObjectTappedListener(listener: (OverlayObject) -> Unit) {
        onObjectTapped = listener
    }

    fun updateObjects(detectedObjects: List<OverlayObject>, imgWidth: Int, imgHeight: Int) {
        objects = detectedObjects
        imageWidth = imgWidth
        imageHeight = imgHeight
        invalidate()
    }

    fun clear() {
        objects = emptyList()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (obj in objects) {
            val mappedRect = mapRect(obj.boundingBox)
            val boxPaint = if (obj.isCustomLabel) boxPaintCustom else boxPaintDefault
            val fillPaint = if (obj.isCustomLabel) fillPaintCustom else fillPaintDefault

            // Draw fill
            canvas.drawRect(mappedRect, fillPaint)
            // Draw border
            canvas.drawRect(mappedRect, boxPaint)

            // Draw label
            val label = obj.label
            val textWidth = labelTextPaint.measureText(label)
            val textHeight = labelTextPaint.textSize

            val labelLeft = mappedRect.left
            val labelTop = mappedRect.top - textHeight - 12f
            val labelRight = labelLeft + textWidth + 16f
            val labelBottom = mappedRect.top

            // Ensure label stays on screen
            val adjustedTop = if (labelTop < 0) mappedRect.top else labelTop
            val adjustedBottom = if (labelTop < 0) mappedRect.top + textHeight + 12f else labelBottom

            canvas.drawRect(labelLeft, adjustedTop, labelRight, adjustedBottom, labelBackgroundPaint)
            canvas.drawText(label, labelLeft + 8f, adjustedBottom - 4f, labelTextPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val x = event.x
            val y = event.y

            for (obj in objects) {
                val mappedRect = mapRect(obj.boundingBox)
                if (mappedRect.contains(x, y)) {
                    onObjectTapped?.invoke(obj)
                    performClick()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    private fun mapRect(rect: Rect): RectF {
        val scaleX = width.toFloat() / imageWidth
        val scaleY = height.toFloat() / imageHeight

        return RectF(
            rect.left * scaleX,
            rect.top * scaleY,
            rect.right * scaleX,
            rect.bottom * scaleY
        )
    }
}
