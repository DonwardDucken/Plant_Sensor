package com.example.plant_sensor

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat

/**
 * Custom View to display a sensor value within a range bar.
 * English version.
 */
class SensorRangeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    init {
        // Make the view explicitly clickable
        isClickable = true
        isFocusable = true
        // Add default ripple effect
        val outValue = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
        setBackgroundResource(outValue.resourceId)
    }

    var label: String = "VALUE"
    var value: Float = 0f
    var minValue: Float = -1f
    var maxValue: Float = -1f
    var scaleMax: Float = 100f
    var unit: String = ""

    private val density = resources.displayMetrics.density

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_main)
        textSize = 16f * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_secondary)
        textSize = 13f * density
    }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EEEEEE")
    }

    private val okPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.status_ok) }
    private val warnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.status_warning) }
    private val badPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.status_critical) }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val paddingH = 16f * density
        val barHeight = 8f * density
        val radius = barHeight / 2f
        
        // Draw label (centered on top half)
        canvas.drawText(label, paddingH, 28f * density, labelPaint)

        // Draw value (top right)
        val valueText = "${formatValue(value)}$unit"
        val valWidth = textPaint.measureText(valueText)
        canvas.drawText(valueText, width - paddingH - valWidth, 30f * density, textPaint)

        // Bar coordinates (bottom half)
        val barTop = height - 20f * density - barHeight
        val barBottom = height - 20f * density
        val barWidth = width - 2 * paddingH
        
        // Draw background
        canvas.drawRoundRect(RectF(paddingH, barTop, width - paddingH, barBottom), radius, radius, bgPaint)

        // Calculate progress
        val valueX = paddingH + (value / scaleMax).coerceIn(0f, 1f) * barWidth
        
        // Determine color
        val colorPaint = when {
            minValue != -1f && value < minValue -> warnPaint
            maxValue != -1f && value > maxValue -> badPaint
            else -> okPaint
        }

        if (valueX > paddingH) {
            canvas.drawRoundRect(RectF(paddingH, barTop, valueX, barBottom), radius, radius, colorPaint)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (72 * density).toInt())
    }

    private fun formatValue(v: Float) = if (v % 1f == 0f) v.toInt().toString() else "%.1f".format(v)

    fun setData(label: String, value: Float, min: Float, max: Float, scaleMax: Float, unit: String) {
        this.label = label; this.value = value; this.minValue = min; this.maxValue = max; this.scaleMax = scaleMax; this.unit = unit
        invalidate()
    }
}
