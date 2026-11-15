/*
 * SPDX-FileCopyrightText: 2025 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */


package com.android.gamebar

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class FpsGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

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
        shader = LinearGradient(
            0f, 0f, 0f, 800f,
            Color.parseColor("#804CAF50"),
            Color.parseColor("#00000000"),
            Shader.TileMode.CLAMP
        )
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
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }

    private val avgLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4CAF50")
        strokeWidth = 2f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    private val lowLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F44336")
        strokeWidth = 2f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    private var fpsData: List<Pair<Long, Double>> = emptyList()
    private var avgFps: Double = 0.0
    private var fps1PercentLow: Double = 0.0
    private var yMax: Double = 60.0
    
    private val padding = 46f * density
    private val topPadding = 22f * density
    private val bottomPadding = 46f * density

    fun setData(data: List<Pair<Long, Double>>, avg: Double, low1Percent: Double) {
        this.fpsData = data
        this.avgFps = avg
        this.fps1PercentLow = low1Percent
        updateYMax()
        
        // Update gradient shader with actual view height
        post {
            fillPaint.shader = LinearGradient(
                0f, topPadding, 0f, height - bottomPadding,
                Color.parseColor("#404CAF50"),
                Color.parseColor("#054CAF50"),
                Shader.TileMode.CLAMP
            )
            invalidate()
        }
        
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (fpsData.isEmpty()) {
            drawEmptyState(canvas)
            return
        }

        val graphWidth = width - 2 * padding
        val graphHeight = height - topPadding - bottomPadding

        // Draw grid and labels
        drawGrid(canvas, graphWidth, graphHeight)

        // Draw average line
        drawAverageLine(canvas, graphWidth, graphHeight)

        // Draw 1% low line
        draw1PercentLowLine(canvas, graphWidth, graphHeight)

        // Draw FPS graph
        drawGraph(canvas, graphWidth, graphHeight)

        // Draw legend
        drawLegend(canvas)
    }

    private fun drawEmptyState(canvas: Canvas) {
        val message = "No data to display"
        val textWidth = textPaint.measureText(message)
        canvas.drawText(
            message,
            (width - textWidth) / 2,
            height / 2f,
            textPaint
        )
    }

    private fun drawGrid(canvas: Canvas, graphWidth: Float, graphHeight: Float) {
        // Draw horizontal grid lines with a dynamic FPS range.
        for (i in 0..4) {
            val fraction = i / 4.0
            val fps = yMax * fraction
            val y = (topPadding + graphHeight * (1.0 - fraction)).toFloat()

            canvas.drawLine(padding, y, padding + graphWidth, y, gridPaint)

            val label = String.format(Locale.getDefault(), "%.0f", fps)
            canvas.drawText(
                label,
                padding - textPaint.measureText(label) - 8f * density,
                y + 4f * density,
                textPaint
            )
        }

        // Draw Y-axis label (far left, separate from numbers)
        canvas.save()
        canvas.rotate(-90f, 10f * density, height / 2f)
        val yLabel = "FPS"
        val yLabelWidth = textPaint.measureText(yLabel)
        canvas.drawText(yLabel, (width - yLabelWidth) / 2, 14f * density, textPaint)
        canvas.restore()

        // Draw time labels on X-axis (higher up - first line)
        if (fpsData.isNotEmpty()) {
            val startTime = fpsData.first().first
            val endTime = fpsData.last().first
            val duration = endTime - startTime

            val xTickY = height - bottomPadding + 16f * density

            val startLabel = formatTimestamp(startTime)
            canvas.drawText(startLabel, padding, xTickY, textPaint)

            val middleLabel = formatTimestamp(startTime + duration / 2)
            val middleX = padding + graphWidth / 2
            canvas.drawText(
                middleLabel,
                middleX - textPaint.measureText(middleLabel) / 2f,
                xTickY,
                textPaint
            )

            val endLabel = formatTimestamp(endTime)
            val endX = padding + graphWidth - textPaint.measureText(endLabel)
            canvas.drawText(endLabel, endX, xTickY, textPaint)
        }

        // Draw X-axis label (second line - below time values)
        val xLabel = "Time"
        val xLabelWidth = textPaint.measureText(xLabel)
        canvas.drawText(
            xLabel,
            (width - xLabelWidth) / 2,
            height - bottomPadding + 34f * density,
            textPaint
        )
    }

    private fun drawGraph(canvas: Canvas, graphWidth: Float, graphHeight: Float) {
        if (fpsData.size < 2) return

        val startTime = fpsData.first().first
        val endTime = fpsData.last().first
        val timeDuration = max(endTime - startTime, 1L)

        val path = Path()
        val fillPath = Path()

        fpsData.forEachIndexed { index, (timestamp, fps) ->
            val x = padding + ((timestamp - startTime).toFloat() / timeDuration) * graphWidth
            
            val y = fpsToY(fps, graphHeight)

            if (index == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, height - bottomPadding)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }

        // Complete fill path
        val lastX = padding + graphWidth
        fillPath.lineTo(lastX, height - bottomPadding)
        fillPath.close()

        // Draw fill
        canvas.drawPath(fillPath, fillPaint)

        // Draw line
        canvas.drawPath(path, linePaint)
    }

    private fun drawAverageLine(canvas: Canvas, graphWidth: Float, graphHeight: Float) {
        val y = fpsToY(avgFps, graphHeight)
        canvas.drawLine(padding, y, padding + graphWidth, y, avgLinePaint)
    }

    private fun draw1PercentLowLine(canvas: Canvas, graphWidth: Float, graphHeight: Float) {
        val y = fpsToY(fps1PercentLow, graphHeight)
        canvas.drawLine(padding, y, padding + graphWidth, y, lowLinePaint)
    }

    private fun fpsToY(fps: Double, graphHeight: Float): Float {
        val normalized = (fps.coerceIn(0.0, yMax)) / yMax
        return (topPadding + graphHeight * (1.0 - normalized)).toFloat()
    }

    private fun updateYMax() {
        val peak = max(
            fpsData.maxOfOrNull { it.second } ?: 0.0,
            max(avgFps, fps1PercentLow)
        )
        if (peak <= 0.0) {
            yMax = 60.0
            return
        }

        val padded = peak * 1.1
        val step = when {
            padded <= 30.0 -> 5.0
            padded <= 60.0 -> 10.0
            padded <= 180.0 -> 20.0
            padded <= 360.0 -> 30.0
            else -> 60.0
        }
        yMax = ceil(padded / step) * step
        if (yMax < 30.0) yMax = 30.0
    }

    private fun drawLegend(canvas: Canvas) {
        val legendX = padding
        val legendY = 11f * density
        val lineLength = 13f * density
        val spacing = 60f * density
        val textOffset = 4f * density

        // FPS line
        canvas.drawLine(legendX, legendY, legendX + lineLength, legendY, linePaint)
        canvas.drawText("FPS", legendX + lineLength + textOffset, legendY + 4f * density, textPaint)

        // Average line
        canvas.drawLine(legendX + spacing, legendY, legendX + spacing + lineLength, legendY, avgLinePaint)
        canvas.drawText("Avg", legendX + spacing + lineLength + textOffset, legendY + 4f * density, textPaint)

        // 1% Low line
        canvas.drawLine(legendX + spacing * 2, legendY, legendX + spacing * 2 + lineLength, legendY, lowLinePaint)
        canvas.drawText("1% Low", legendX + spacing * 2 + lineLength + textOffset, legendY + 4f * density, textPaint)
    }

    private fun formatTimestamp(timestampMs: Long): String =
        timestampFormat.format(Date(timestampMs))

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = 800
        val desiredHeight = 600

        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val width = when (widthMode) {
            MeasureSpec.EXACTLY -> widthSize
            MeasureSpec.AT_MOST -> min(desiredWidth, widthSize)
            else -> desiredWidth
        }

        val height = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> min(desiredHeight, heightSize)
            else -> desiredHeight
        }

        setMeasuredDimension(width, height)
    }
}
