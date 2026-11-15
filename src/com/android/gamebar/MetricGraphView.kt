/*
 * SPDX-FileCopyrightText: 2025 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.gamebar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Grafik historis generik untuk metrik dashboard (suhu, frame time, CPU, RAM, daya).
 * Sumbu X selalu memakai timestamp sampel sehingga semua metrik sejajar waktunya.
 */
class MetricGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class Config(
        val yAxisLabel: String,
        val unit: String,
        val lineColor: Int,
        val baselineZero: Boolean = false,
        val maxFloor: Double? = null
    )

    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity
    private val timestampFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4CAF50")
        strokeWidth = 3f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#30FFFFFF")
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B0B0B0")
        textSize = 10f * scaledDensity
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
    }

    private val avgLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFB74D")
        strokeWidth = 2f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    private var data: List<Pair<Long, Double>> = emptyList()
    private var avgValue: Double = 0.0
    private var config = Config("", "", Color.parseColor("#4CAF50"))
    private var yMin = 0.0
    private var yMax = 100.0

    private val padding = 46f * density
    private val topPadding = 10f * density
    private val bottomPadding = 46f * density

    fun setData(data: List<Pair<Long, Double>>, avg: Double, config: Config) {
        this.data = data
        this.avgValue = avg
        this.config = config
        linePaint.color = config.lineColor
        computeRange()

        post {
            updateFillShader()
            invalidate()
        }
        invalidate()
    }

    private fun computeRange() {
        if (data.isEmpty()) {
            yMin = 0.0
            yMax = 1.0
            return
        }

        val values = data.map { it.second }
        val dataMin = values.min()
        val dataMax = values.max()
        val floor = config.maxFloor

        if (config.baselineZero) {
            yMin = 0.0
            yMax = max(floor ?: 1.0, niceCeil(dataMax))
        } else {
            val span = (dataMax - dataMin).let {
                if (it < 0.000001) max(kotlin.math.abs(dataMax) * 0.1, 1.0) else it
            }
            yMin = (dataMin - span * 0.1).coerceAtLeast(0.0)
            yMax = dataMax + span * 0.1
            if (floor != null && yMax < floor) yMax = floor
        }

        if (yMax <= yMin) yMax = yMin + 1.0
    }

    private fun updateFillShader() {
        val bottom = (height - bottomPadding).coerceAtLeast(topPadding + 1f)
        fillPaint.shader = LinearGradient(
            0f, topPadding, 0f, bottom,
            colorWithAlpha(config.lineColor, 0x40),
            colorWithAlpha(config.lineColor, 0x05),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (data.isEmpty()) {
            drawEmptyState(canvas)
            return
        }

        val graphWidth = width - 2 * padding
        val graphHeight = height - topPadding - bottomPadding
        if (graphWidth <= 0f || graphHeight <= 0f) return

        drawGrid(canvas, graphWidth, graphHeight)
        drawAverageLine(canvas, graphWidth, graphHeight)
        drawGraph(canvas, graphWidth, graphHeight)
    }

    private fun drawEmptyState(canvas: Canvas) {
        val message = "No data available"
        val textWidth = textPaint.measureText(message)
        canvas.drawText(message, (width - textWidth) / 2f, height / 2f, textPaint)
    }

    private fun drawGrid(canvas: Canvas, graphWidth: Float, graphHeight: Float) {
        val range = yMax - yMin
        for (i in 0..4) {
            val fraction = i / 4.0
            val value = yMin + range * fraction
            val y = (topPadding + graphHeight * (1.0 - fraction)).toFloat()

            canvas.drawLine(padding, y, padding + graphWidth, y, gridPaint)

            val label = formatAxis(value)
            canvas.drawText(
                label,
                padding - textPaint.measureText(label) - 8f * density,
                y + 4f * density,
                textPaint
            )
        }

        canvas.save()
        canvas.rotate(-90f, 10f * density, height / 2f)
        val yLabel = config.yAxisLabel
        val yLabelWidth = textPaint.measureText(yLabel)
        canvas.drawText(yLabel, (width - yLabelWidth) / 2f, 14f * density, textPaint)
        canvas.restore()

        val startTime = data.first().first
        val endTime = data.last().first
        val duration = (endTime - startTime).coerceAtLeast(1L)

        val xTickY = height - bottomPadding + 16f * density
        val startLabel = formatTimestamp(startTime)
        canvas.drawText(startLabel, padding, xTickY, textPaint)
        val middleLabel = formatTimestamp(startTime + duration / 2)
        canvas.drawText(
            middleLabel,
            padding + graphWidth / 2 - textPaint.measureText(middleLabel) / 2f,
            xTickY,
            textPaint
        )
        val endLabel = formatTimestamp(endTime)
        canvas.drawText(
            endLabel,
            padding + graphWidth - textPaint.measureText(endLabel),
            xTickY,
            textPaint
        )

        val xLabel = "Time"
        canvas.drawText(
            xLabel,
            (width - textPaint.measureText(xLabel)) / 2f,
            height - bottomPadding + 34f * density,
            textPaint
        )
    }

    private fun drawAverageLine(canvas: Canvas, graphWidth: Float, graphHeight: Float) {
        val value = avgValue.coerceIn(yMin, yMax)
        val y = valueToY(value, graphHeight)
        canvas.drawLine(padding, y, padding + graphWidth, y, avgLinePaint)
    }

    private fun drawGraph(canvas: Canvas, graphWidth: Float, graphHeight: Float) {
        val startTime = data.first().first
        val endTime = data.last().first
        val timeDuration = max(endTime - startTime, 1L)

        val path = Path()
        val fillPath = Path()

        data.forEachIndexed { index, (timestamp, value) ->
            val x = padding + ((timestamp - startTime).toFloat() / timeDuration) * graphWidth
            val y = valueToY(value, graphHeight)

            if (index == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, height - bottomPadding)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }

        if (data.size == 1) {
            canvas.drawCircle(padding, valueToY(data.first().second, graphHeight), 6f, linePaint)
            return
        }

        fillPath.lineTo(padding + graphWidth, height - bottomPadding)
        fillPath.close()

        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(path, linePaint)
    }

    private fun valueToY(value: Double, graphHeight: Float): Float {
        val clamped = value.coerceIn(yMin, yMax)
        val normalized = (clamped - yMin) / (yMax - yMin)
        return (topPadding + graphHeight * (1.0 - normalized)).toFloat()
    }

    private fun formatAxis(value: Double): String {
        val range = yMax - yMin
        return if (range >= 10.0) {
            String.format(Locale.getDefault(), "%.0f", value)
        } else {
            String.format(Locale.getDefault(), "%.1f", value)
        }
    }

    private fun formatTimestamp(timestampMs: Long): String =
        timestampFormat.format(Date(timestampMs))

    private fun niceCeil(value: Double): Double {
        if (value <= 0.0) return 1.0
        val exponent = floor(log10(value))
        val magnitude = 10.0.pow(exponent)
        val fraction = value / magnitude
        val nice = when {
            fraction <= 1.0 -> 1.0
            fraction <= 2.0 -> 2.0
            fraction <= 5.0 -> 5.0
            else -> 10.0
        }
        return nice * magnitude
    }

    private fun colorWithAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha shl 24)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = 800
        val desiredHeight = 480

        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val measuredWidth = when (widthMode) {
            MeasureSpec.EXACTLY -> widthSize
            MeasureSpec.AT_MOST -> min(desiredWidth, widthSize)
            else -> desiredWidth
        }
        val measuredHeight = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> min(desiredHeight, heightSize)
            else -> desiredHeight
        }

        setMeasuredDimension(measuredWidth, measuredHeight)
    }
}
