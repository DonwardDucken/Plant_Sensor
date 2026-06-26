package com.example.plant_sensor.ui.customviews

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.example.plant_sensor.data.model.HistoryPoint
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

/**
 * Simple custom line chart for displaying historic sensor values.
 */
class HistoryChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var dataPoints: List<HistoryPoint> = emptyList()
    private var chartLabel: String = ""
    private var chartColor: Int = Color.parseColor("#4CAF50")

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        color = chartColor
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

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val linePath = Path()
    private val fillPath = Path()

    fun setData(points: List<HistoryPoint>, label: String, color: Int) {
        dataPoints = points.sortedBy { it.timestamp }
        chartLabel = label
        chartColor = color
        linePaint.color = chartColor
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (dataPoints.size < MINIMUM_POINTS) return

        val chartArea = getChartArea()
        val minTime = dataPoints.first().timestamp
        val maxTime = dataPoints.last().timestamp
        val timeRange = (maxTime - minTime).coerceAtLeast(1).toFloat()
        val minValue = dataPoints.minOf { it.value } * VALUE_PADDING_LOWER
        val maxValue = dataPoints.maxOf { it.value } * VALUE_PADDING_UPPER
        val valueRange = (maxValue - minValue).coerceAtLeast(1f)

        drawGrid(canvas, chartArea, minValue, valueRange)
        drawChartLine(canvas, chartArea, minTime, timeRange, minValue, valueRange)
        drawTitle(canvas, chartArea.left)
        drawTimeLabels(canvas, chartArea.left, chartArea.right, minTime, maxTime)
    }

    private fun getChartArea() = ChartArea(100f, width - 40f, 80f, height - 80f)

    private fun drawGrid(canvas: Canvas, chartArea: ChartArea, minValue: Float, valueRange: Float) {
        textPaint.color = Color.parseColor("#9EAC9E")
        textPaint.typeface = Typeface.DEFAULT
        for (i in 0..GRID_LINES) {
            val factor = i.toFloat() / GRID_LINES
            val y = chartArea.bottom - factor * chartArea.height
            val valueLabel = "%.1f".format(minValue + factor * valueRange)
            canvas.drawLine(chartArea.left, y, chartArea.right, y, gridPaint)
            canvas.drawText(valueLabel, 10f, y + 10f, textPaint)
        }
    }

    private fun drawChartLine(canvas: Canvas, chartArea: ChartArea, minTime: Long, timeRange: Float, minValue: Float, valueRange: Float) {
        val points = dataPoints.map { PointF(chartArea.left + ((it.timestamp - minTime) / timeRange) * chartArea.width, chartArea.bottom - ((it.value - minValue) / valueRange) * chartArea.height) }
        if (points.isEmpty()) return
        buildSmoothPaths(points, chartArea.bottom)
        drawGradientFill(canvas, chartArea)
        canvas.drawPath(linePath, linePaint)
    }

    private fun buildSmoothPaths(points: List<PointF>, chartBottom: Float) {
        linePath.reset(); fillPath.reset()
        linePath.moveTo(points.first().x, points.first().y)
        fillPath.moveTo(points.first().x, chartBottom)
        fillPath.lineTo(points.first().x, points.first().y)

        for (i in 0 until points.size - 1) {
            val cp = points[i]; val np = points[i + 1]; val cx = (cp.x + np.x) / 2f
            linePath.cubicTo(cx, cp.y, cx, np.y, np.x, np.y)
            fillPath.cubicTo(cx, cp.y, cx, np.y, np.x, np.y)
        }
        fillPath.lineTo(points.last().x, chartBottom); fillPath.close()
    }

    private fun drawGradientFill(canvas: Canvas, chartArea: ChartArea) {
        fillPaint.shader = LinearGradient(0f, chartArea.top, 0f, chartArea.bottom, adjustAlpha(chartColor, 0.2f), adjustAlpha(chartColor, 0.0f), Shader.TileMode.CLAMP)
        canvas.drawPath(fillPath, fillPaint)
        fillPaint.shader = null
    }

    private fun drawTitle(canvas: Canvas, x: Float) {
        textPaint.color = chartColor; textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(chartLabel, x, CHART_TITLE_Y, textPaint)
    }

    private fun drawTimeLabels(canvas: Canvas, chartLeft: Float, chartRight: Float, minTime: Long, maxTime: Long) {
        textPaint.color = Color.parseColor("#9EAC9E"); textPaint.typeface = Typeface.DEFAULT
        val start = timeFormat.format(Date(minTime)); val end = timeFormat.format(Date(maxTime))
        canvas.drawText(start, chartLeft, height - 20f, textPaint)
        canvas.drawText(end, chartRight - textPaint.measureText(end), height - 20f, textPaint)
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = (Color.alpha(color) * factor).roundToInt()
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    private data class ChartArea(val left: Float, val right: Float, val top: Float, val bottom: Float) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
    }

    companion object {
        private const val MINIMUM_POINTS = 2
        private const val GRID_LINES = 4
        private const val VALUE_PADDING_LOWER = 0.95f
        private const val VALUE_PADDING_UPPER = 1.05f
        private const val CHART_TITLE_Y = 50f
    }
}
