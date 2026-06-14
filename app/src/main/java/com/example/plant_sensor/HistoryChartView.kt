package com.example.plant_sensor

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import java.text.SimpleDateFormat
import java.util.*

data class HistoryPoint(val timestamp: Long, val value: Float)

class HistoryChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var dataPoints: List<HistoryPoint> = emptyList()
    private var label: String = ""
    private var color: Int = Color.parseColor("#4CAF50")

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0E4E0")
        strokeWidth = 2f
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9EAC9E")
        textSize = 28f
    }

    private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val path = Path()
    private val fillPath = Path()

    fun setData(points: List<HistoryPoint>, label: String, color: Int) {
        this.dataPoints = points.sortedBy { it.timestamp }
        this.label = label
        this.color = color
        linePaint.color = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (dataPoints.size < 2) return

        val paddingLeft = 100f
        val paddingRight = 40f
        val paddingTop = 80f
        val paddingBottom = 80f
        
        val chartWidth = width - paddingLeft - paddingRight
        val chartHeight = height - paddingTop - paddingBottom

        val minTime = dataPoints.first().timestamp
        val maxTime = dataPoints.last().timestamp
        val timeRange = (maxTime - minTime).coerceAtLeast(1).toFloat()

        val minValue = dataPoints.minOf { it.value } * 0.95f
        val maxValue = dataPoints.maxOf { it.value } * 1.05f
        val valueRange = (maxValue - minValue).coerceAtLeast(1f)

        // Draw horizontal grid lines
        val gridLines = 4
        for (i in 0..gridLines) {
            val y = paddingTop + chartHeight - (i.toFloat() / gridLines) * chartHeight
            canvas.drawLine(paddingLeft, y, width - paddingRight, y, gridPaint)
            val valLabel = "%.1f".format(minValue + (i.toFloat() / gridLines) * valueRange)
            canvas.drawText(valLabel, 10f, y + 10f, textPaint)
        }

        // Prepare path (Smooth curve)
        path.reset()
        fillPath.reset()

        val points = dataPoints.map { point ->
            PointF(
                paddingLeft + ((point.timestamp - minTime) / timeRange) * chartWidth,
                paddingTop + chartHeight - ((point.value - minValue) / valueRange) * chartHeight
            )
        }

        if (points.isNotEmpty()) {
            path.moveTo(points[0].x, points[0].y)
            fillPath.moveTo(points[0].x, paddingTop + chartHeight)
            fillPath.lineTo(points[0].x, points[0].y)

            for (i in 0 until points.size - 1) {
                val p1 = points[i]
                val p2 = points[i + 1]
                val controlPointX = (p1.x + p2.x) / 2
                path.cubicTo(controlPointX, p1.y, controlPointX, p2.y, p2.x, p2.y)
                fillPath.cubicTo(controlPointX, p1.y, controlPointX, p2.y, p2.x, p2.y)
            }

            fillPath.lineTo(points.last().x, paddingTop + chartHeight)
            fillPath.close()
        }

        // Draw fill with gradient
        val gradient = LinearGradient(
            0f, paddingTop, 0f, paddingTop + chartHeight,
            adjustAlpha(color, 0.2f), adjustAlpha(color, 0.0f),
            Shader.TileMode.CLAMP
        )
        fillPaint.shader = gradient
        canvas.drawPath(fillPath, fillPaint)

        // Draw line
        canvas.drawPath(path, linePaint)
        
        // Draw Label
        textPaint.color = color
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(label, paddingLeft, paddingTop - 30f, textPaint)
        
        // Time labels
        textPaint.color = Color.parseColor("#9EAC9E")
        textPaint.typeface = Typeface.DEFAULT
        val startTimeStr = dateFormat.format(Date(minTime))
        val endTimeStr = dateFormat.format(Date(maxTime))
        canvas.drawText(startTimeStr, paddingLeft, height - 20f, textPaint)
        val endWidth = textPaint.measureText(endTimeStr)
        canvas.drawText(endTimeStr, width - paddingRight - endWidth, height - 20f, textPaint)
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = Math.round(Color.alpha(color) * factor)
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        return Color.argb(alpha, red, green, blue)
    }
}
