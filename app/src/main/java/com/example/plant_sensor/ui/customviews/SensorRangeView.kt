package com.example.plant_sensor.ui.customviews

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import com.example.plant_sensor.R

/**
 * Custom view for displaying one sensor value as a colored range bar.
 */
class SensorRangeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var label: String = "VALUE"
    private var value: Float = 0f
    private var minValue: Float = NO_LIMIT
    private var maxValue: Float = NO_LIMIT
    private var scaleMax: Float = DEFAULT_SCALE_MAX
    private var unit: String = ""

    private val density = resources.displayMetrics.density

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_main)
        textSize = VALUE_TEXT_SIZE_SP * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_secondary)
        textSize = LABEL_TEXT_SIZE_SP * density
    }

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EEEEEE")
    }

    private val okPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.status_ok)
    }

    private val warningPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.status_warning)
    }

    private val criticalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.status_critical)
    }

    init {
        isClickable = true
        isFocusable = true
        setDefaultRippleBackground()
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val horizontalPadding = HORIZONTAL_PADDING_DP * density
        val barHeight = BAR_HEIGHT_DP * density
        val barRadius = barHeight / 2f
        val barWidth = width - 2 * horizontalPadding

        drawTexts(canvas, horizontalPadding)
        drawBarBackground(canvas, horizontalPadding, barHeight, barRadius)
        drawCurrentValue(canvas, horizontalPadding, barWidth, barHeight, barRadius)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val measuredWidth = MeasureSpec.getSize(widthMeasureSpec)
        val measuredHeight = (VIEW_HEIGHT_DP * density).toInt()
        setMeasuredDimension(measuredWidth, measuredHeight)
    }

    fun setData(
        label: String,
        value: Float,
        min: Float,
        max: Float,
        scaleMax: Float,
        unit: String
    ) {
        this.label = label
        this.value = value
        this.minValue = min
        this.maxValue = max
        this.scaleMax = scaleMax
        this.unit = unit
        invalidate()
    }

    private fun setDefaultRippleBackground() {
        val outValue = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
        setBackgroundResource(outValue.resourceId)
    }

    private fun drawTexts(canvas: Canvas, horizontalPadding: Float) {
        canvas.drawText(label, horizontalPadding, 28f * density, labelPaint)
        val valueText = "${formatValue(value)}$unit"
        val valueTextWidth = textPaint.measureText(valueText)
        canvas.drawText(valueText, width - horizontalPadding - valueTextWidth, 30f * density, textPaint)
    }

    private fun drawBarBackground(canvas: Canvas, horizontalPadding: Float, barHeight: Float, barRadius: Float) {
        val barTop = height - 20f * density - barHeight
        val barBottom = height - 20f * density
        canvas.drawRoundRect(RectF(horizontalPadding, barTop, width - horizontalPadding, barBottom), barRadius, barRadius, backgroundPaint)
    }

    private fun drawCurrentValue(canvas: Canvas, horizontalPadding: Float, barWidth: Float, barHeight: Float, barRadius: Float) {
        val progressFactor = (value / scaleMax).coerceIn(0f, 1f)
        val valueX = horizontalPadding + progressFactor * barWidth
        if (valueX <= horizontalPadding) return
        val barTop = height - 20f * density - barHeight
        val barBottom = height - 20f * density
        canvas.drawRoundRect(RectF(horizontalPadding, barTop, valueX, barBottom), barRadius, barRadius, getValuePaint())
    }

    private fun getValuePaint(): Paint {
        return when {
            minValue != NO_LIMIT && value < minValue -> warningPaint
            maxValue != NO_LIMIT && value > maxValue -> criticalPaint
            else -> okPaint
        }
    }

    private fun formatValue(value: Float): String {
        return if (value % 1f == 0f) value.toInt().toString() else "%.1f".format(value)
    }

    companion object {
        private const val NO_LIMIT = -1f
        private const val DEFAULT_SCALE_MAX = 100f
        private const val VIEW_HEIGHT_DP = 72
        private const val HORIZONTAL_PADDING_DP = 16f
        private const val BAR_HEIGHT_DP = 8f
        private const val VALUE_TEXT_SIZE_SP = 16f
        private const val LABEL_TEXT_SIZE_SP = 13f
    }
}
