package org.havenapp.main.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.GestureDetector
import java.util.Locale
import org.havenapp.main.R
import org.havenapp.main.model.EventTrigger
import java.util.Calendar
import java.util.Date
import kotlin.math.max

class TimelineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF888888.toInt() }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = resources.displayMetrics.density * 11f
    }
    private val selectedPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 2f
        color = Color.WHITE
    }
    private val eventTriggers = mutableListOf<EventTrigger>()
    private var windowStart = Date()
    private var windowEnd = Date()
    private var selectionTime = Date()
    private var summaryMode = false
    var onSelectionChanged: ((Date, List<EventTrigger>) -> Unit)? = null

    fun setEvents(events: List<EventTrigger>) {
        eventTriggers.clear()
        eventTriggers.addAll(events.sortedBy { it.time })
        if (eventTriggers.isNotEmpty()) {
            val first = eventTriggers.first().time ?: Date()
            val last = eventTriggers.last().time ?: first
            val padding = max((last.time - first.time) / 20, 60_000L)
            setWindow(Date(first.time - padding), Date(last.time + padding))
        } else {
            setWindow(Date(), Date())
        }
    }

    fun setWindow(start: Date, end: Date) {
        windowStart = start
        windowEnd = if (end.after(start)) end else Date(start.time + 1)
        selectionTime = if (selectionTime.before(windowStart) || selectionTime.after(windowEnd)) {
            Date((windowStart.time + windowEnd.time) / 2)
        } else {
            selectionTime
        }
        invalidate()
        notifySelection()
    }

    fun scrollByFraction(fraction: Float) {
        val duration = (windowEnd.time - windowStart.time) / 4
        val delta = (duration * fraction).toLong()
        setWindow(Date(windowStart.time + delta), Date(windowEnd.time + delta))
    }

    fun toggleSummaryMode(): Boolean {
        summaryMode = !summaryMode
        invalidate()
        return summaryMode
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(0xFF101010.toInt())
        val heightF = height.toFloat()
        val widthF = width.toFloat()
        canvas.drawLine(0f, heightF - density() * 24, widthF, heightF - density() * 24, axisPaint)

        if (summaryMode) {
            drawFrequencySummary(canvas, widthF, heightF)
        } else {
            drawEventMarks(canvas, widthF, heightF)
        }

        drawSelection(canvas, widthF, heightF)
        canvas.drawText(formatRange(), density() * 8, density() * 18, textPaint)
    }

    private fun drawEventMarks(canvas: Canvas, width: Float, viewHeight: Float) {
        val baseline = viewHeight - density() * 24
        for (trigger in eventTriggers) {
            val time = trigger.time ?: continue
            if (time.before(windowStart) || time.after(windowEnd)) continue
            val x = xFor(time, width)
            paintFor(trigger.type ?: -1)?.let { paint ->
                canvas.drawLine(x, baseline - density() * 48, x, baseline, paint)
            }
        }
    }

    private fun drawFrequencySummary(canvas: Canvas, width: Float, viewHeight: Float) {
        val baseline = viewHeight - density() * 24
        val buckets = 64
        val bucketWidth = width / buckets
        val counts = IntArray(buckets)
        for (trigger in eventTriggers) {
            val time = trigger.time ?: continue
            if (time.before(windowStart) || time.after(windowEnd)) continue
            val index = (((time.time - windowStart.time).toFloat() /
                    (windowEnd.time - windowStart.time)) * buckets).toInt().coerceIn(0, buckets - 1)
            counts[index]++
        }
        val maximum = counts.maxOrNull()?.coerceAtLeast(1) ?: 1
        for ((index, count) in counts.withIndex()) {
            val barHeight = (count.toFloat() / maximum) * density() * 96
            val left = index * bucketWidth
            val type = eventTriggers.firstOrNull {
                val time = it.time
                time != null && time >= dateAt(index, buckets) && time <= endDateAt(index, buckets)
            }?.type
            paintFor(type ?: -1)?.let { paint ->
                canvas.drawRect(left, baseline - barHeight, left + bucketWidth - 2, baseline, paint)
            }
        }
    }

    private fun drawSelection(canvas: Canvas, width: Float, viewHeight: Float) {
        val x = xFor(selectionTime, width)
        canvas.drawLine(x, density() * 28, x, viewHeight - density() * 24, selectedPaint)
    }

    private fun notifySelection() {
        val center = selectionTime.time
        val radius = (windowEnd.time - windowStart.time) / 16
        val nearby = eventTriggers.filter {
            val time = it.time
            time != null && kotlin.math.abs(time.time - center) <= radius
        }
        onSelectionChanged?.invoke(selectionTime, nearby)
    }

    private fun xFor(time: Date, width: Float): Float {
        val fraction = (time.time - windowStart.time).toFloat() / (windowEnd.time - windowStart.time)
        return fraction.coerceIn(0f, 1f) * width
    }

    private fun dateAt(bucket: Int, buckets: Int): Date {
        val fraction = bucket.toFloat() / buckets
        return Date(windowStart.time + ((windowEnd.time - windowStart.time) * fraction).toLong())
    }

    private fun endDateAt(bucket: Int, buckets: Int): Date {
        return dateAt(bucket + 1, buckets)
    }

    private fun formatRange(): String {
        val formatter = java.text.SimpleDateFormat("MMM d HH:mm", Locale.getDefault())
        return formatter.format(windowStart) + " → " + formatter.format(windowEnd)
    }

    private fun density() = resources.displayMetrics.density

    private fun paintFor(type: Int): Paint? {
        val color = when (type) {
            EventTrigger.CAMERA -> Color.rgb(70, 145, 255)
            EventTrigger.CAMERA_VIDEO -> Color.rgb(0, 190, 220)
            EventTrigger.ACCELEROMETER -> Color.rgb(255, 155, 30)
            EventTrigger.LIGHT -> Color.rgb(55, 200, 90)
            EventTrigger.MICROPHONE -> Color.rgb(205, 80, 235)
            EventTrigger.PRESSURE -> Color.rgb(240, 210, 50)
            EventTrigger.BUMP -> Color.rgb(255, 100, 90)
            EventTrigger.POWER -> Color.rgb(160, 160, 170)
            EventTrigger.HEART -> Color.rgb(245, 95, 140)
            EventTrigger.EMF -> Color.rgb(0, 225, 175)
            else -> return null
        }
        return Paint().apply { this.color = color; strokeWidth = density() * 3 }
    }
}
