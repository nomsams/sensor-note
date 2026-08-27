package org.havenapp.main.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import org.havenapp.main.anomaly.AnomalyPoint
import kotlin.math.max

class AnomalyEllipseView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val gridPaint = Paint().apply { color = 0xFF333333.toInt(); strokeWidth = density() }
    private val ellipsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.GREEN
        strokeWidth = density() * 2f
    }
    private val normalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.CYAN }
    private val anomalyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.RED }
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density() * 2f
        color = Color.WHITE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = density() * 11f
    }

    private var points = listOf<AnomalyPoint>()
    private var selectedIndex = -1
    private var thresholdScale = 1f
    private var playbackIndex = 0
    @JvmField
    var onPointSelected: java.util.function.Consumer<AnomalyPoint?>? = null

    fun setPoints(points: List<AnomalyPoint>) {
        this.points = points.sortedBy { it.timestamp }
        playbackIndex = points.lastIndex.coerceAtLeast(0)
        invalidate()
    }

    fun setThresholdScale(scale: Float) {
        thresholdScale = scale.coerceIn(0.25f, 5f)
        invalidate()
    }

    fun seek(index: Int) {
        if (points.isEmpty()) return
        playbackIndex = index.coerceIn(0, points.lastIndex)
        selectedIndex = playbackIndex
            onPointSelected?.accept(points[playbackIndex])
        invalidate()
    }

    fun step(delta: Int) = seek(playbackIndex + delta)

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN && points.isNotEmpty()) {
            val nearest = points.indices.minByOrNull { index ->
                val coordinate = coordinateFor(points[index])
                val dx = coordinate.first - event.x
                val dy = coordinate.second - event.y
                dx * dx + dy * dy
            } ?: return false
            selectedIndex = nearest
            playbackIndex = nearest
            onPointSelected?.accept(points[nearest])
            invalidate()
            performClick()
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(0xFF101010.toInt())
        canvas.drawLine(width / 2f, 0f, width / 2f, height.toFloat(), gridPaint)
        canvas.drawLine(0f, height / 2f, width.toFloat(), height / 2f, gridPaint)

        val radiusX = max(width, height) * 0.28f * thresholdScale
        val radiusY = max(height, width) * 0.20f * thresholdScale
        canvas.drawOval(
            width / 2f - radiusX, height / 2f - radiusY,
            width / 2f + radiusX, height / 2f + radiusY,
            ellipsePaint
        )

        points.forEachIndexed { index, point ->
            val (x, y) = coordinateFor(point)
            canvas.drawCircle(x, y, density() * (if (index == selectedIndex) 6f else 4f),
                    if (point.anomaly || outsideEllipse(x, y, radiusX, radiusY)) anomalyPaint else normalPaint)
            if (index == selectedIndex) canvas.drawCircle(x, y, density() * 9f, selectedPaint)
        }

        canvas.drawText("T² playback ${playbackIndex + 1}/${points.size}", density() * 8,
                density() * 16, textPaint)
    }

    private fun outsideEllipse(x: Float, y: Float, rx: Float, ry: Float): Boolean {
        val dx = (x - width / 2f) / rx
        val dy = (y - height / 2f) / ry
        return dx * dx + dy * dy > 1f
    }

    private fun coordinateFor(point: AnomalyPoint): Pair<Float, Float> {
        val maximum = max(1e-9, points.maxOf { max(abs(it.x), abs(it.y)) })
        val scale = minOf(width, height) / 2f / maximum.toFloat() * 0.85f
        return width / 2f + (point.x.toFloat() * scale) to
                height / 2f - (point.y.toFloat() * scale)
    }

    private fun abs(value: Double) = kotlin.math.abs(value)
    private fun density() = resources.displayMetrics.density
}
