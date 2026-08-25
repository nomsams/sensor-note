package org.havenapp.main.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.Scroller
import java.util.Locale
import org.havenapp.main.R
import org.havenapp.main.model.EventTrigger
import java.util.Calendar
import java.util.Date
import kotlin.math.max
import kotlin.math.min

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
    private val bucketPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = resources.displayMetrics.density * 10f
    }
    private val eventTriggers = mutableListOf<EventTrigger>()
    private var windowStart = Date()
    private var windowEnd = Date()
    private var selectionTime = Date()
    private var summaryMode = false
    private var scrollX = 0f
    private var maxScrollX = 0f
    private val scroller = Scroller(context)
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent?, distanceX: Float, distanceY: Float): Boolean {
            scrollBy(distanceX)
            return true
        }
        
        override fun onFling(e1: MotionEvent?, e2: MotionEvent?, velocityX: Float, velocityY: Float): Boolean {
            scroller.fling(
                scrollX.toInt(), 0,
                (-velocityX).toInt(), 0,
                0, maxScrollX.toInt(), 0, 0
            )
            invalidate()
            return true
        }
        
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            val time = timeForX(e.x)
            selectionTime = time
            invalidate()
            notifySelection()
            return true
        }
    })

    var onSelectionChanged: ((Date, List<EventTrigger>) -> Unit)? = null
    var onEventClick: ((EventTrigger) -> Unit)? = null

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
        computeMaxScroll()
    }

    fun setWindow(start: Date, end: Date) {
        windowStart = start
        windowEnd = if (end.after(start)) end else Date(start.time + 1)
        selectionTime = if (selectionTime.before(windowStart) || selectionTime.after(windowEnd)) {
            Date((windowStart.time + windowEnd.time) / 2)
        } else {
            selectionTime
        }
        scrollX = 0f
        computeMaxScroll()
        invalidate()
        notifySelection()
    }

    fun scrollBy(deltaX: Float) {
        scrollX = (scrollX + deltaX).coerceIn(0f, maxScrollX)
        invalidate()
        notifySelection()
    }

    fun scrollToFraction(fraction: Float) {
        scrollX = (maxScrollX * fraction.coerceIn(0f, 1f))
        invalidate()
        notifySelection()
    }

    fun toggleSummaryMode(): Boolean {
        summaryMode = !summaryMode
        invalidate()
        return summaryMode
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollX = scroller.currX.toFloat().coerceIn(0f, maxScrollX)
            invalidate()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(0xFF101010.toInt())
        val heightF = height.toFloat()
        val widthF = width.toFloat()
        
        // Apply scroll offset
        canvas.translate(-scrollX, 0f)
        
        val baseline = heightF - density() * 24
        canvas.drawLine(0f, baseline, widthF, baseline, axisPaint)

        if (summaryMode) {
            drawFrequencySummary(canvas, widthF, heightF)
        } else {
            drawEventMarks(canvas, widthF, heightF)
        }

        drawSelection(canvas, widthF, heightF)
        drawTimeLabels(canvas, widthF, heightF)
        
        // Draw scroll indicator
        drawScrollIndicator(canvas, widthF, heightF)
        
        canvas.drawText(formatRange(), density() * 8, density() * 18, textPaint)
    }

    private fun drawEventMarks(canvas: Canvas, width: Float, viewHeight: Float) {
        val baseline = viewHeight - density() * 24
        val markHeight = density() * 48
        
        for (trigger in eventTriggers) {
            val time = trigger.time ?: continue
            if (time.before(windowStart) || time.after(windowEnd)) continue
            val x = xFor(time, width)
            paintFor(trigger.type ?: -1)?.let { paint ->
                canvas.drawLine(x, baseline - markHeight, x, baseline, paint)
                // Draw event type indicator circle
                canvas.drawCircle(x, baseline - markHeight - density() * 8, density() * 6, paint)
            }
        }
    }

    private fun drawFrequencySummary(canvas: Canvas, width: Float, viewHeight: Float) {
        val baseline = viewHeight - density() * 24
        val buckets = 64
        val bucketWidth = width / buckets
        val counts = IntArray(buckets)
        val typeCounts = Array(buckets) { mutableMapOf<Int, Int>() }
        
        for (trigger in eventTriggers) {
            val time = trigger.time ?: continue
            if (time.before(windowStart) || time.after(windowEnd)) continue
            val index = (((time.time - windowStart.time).toFloat() / 
                    (windowEnd.time - windowStart.time)) * buckets).toInt().coerceIn(0, buckets - 1)
            counts[index]++
            val type = trigger.type ?: -1
            typeCounts[index][type] = typeCounts[index].getOrDefault(type, 0) + 1
        }
        
        val maximum = counts.maxOrNull()?.coerceAtLeast(1) ?: 1
        for ((index, count) in counts.withIndex()) {
            val barHeight = (count.toFloat() / maximum) * density() * 96
            val left = index * bucketWidth
            val right = left + bucketWidth - 2
            
            // Draw stacked bars for each event type
            var currentBottom = baseline
            for ((type, typeCount) in typeCounts[index]) {
                val typeHeight = (typeCount.toFloat() / count) * barHeight
                paintFor(type)?.let { paint ->
                    canvas.drawRect(left, currentBottom - typeHeight, right, currentBottom, paint)
                }
                currentBottom -= typeHeight
            }
        }
    }

    private fun drawSelection(canvas: Canvas, width: Float, viewHeight: Float) {
        val x = xFor(selectionTime, width)
        canvas.drawLine(x, density() * 28, x, viewHeight - density() * 24, selectedPaint)
        // Draw selection time label
        val formatter = java.text.SimpleDateFormat(\"HH:mm:ss\", Locale.getDefault())
        canvas.drawText(formatter.format(selectionTime), x + density() * 4, density() * 24, labelPaint)
    }

    private fun drawTimeLabels(canvas: Canvas, width: Float, viewHeight: Float) {
        val baseline = viewHeight - density() * 24
        val duration = windowEnd.time - windowStart.time
        val numLabels = 6
        val formatter = java.text.SimpleDateFormat(\"HH:mm\", Locale.getDefault())
        
        for (i in 0 until numLabels) {
            val fraction = i / (numLabels - 1).toFloat()
            val time = Date(windowStart.time + (duration * fraction).toLong())
            val x = xFor(time, width)
            canvas.drawLine(x, baseline, x, baseline + density() * 6, axisPaint)
            canvas.drawText(formatter.format(time), x - density() * 15, baseline + density() * 20, labelPaint)
        }
    }

    private fun drawScrollIndicator(canvas: Canvas, width: Float, viewHeight: Float) {
        if (maxScrollX <= 0) return
        val indicatorWidth = (width / (width + maxScrollX)) * width
        val indicatorX = (scrollX / maxScrollX) * (width - indicatorWidth)
        val y = viewHeight - density() * 4
        bucketPaint.color = 0xFF555555.toInt()
        canvas.drawRect(indicatorX, y, indicatorX + indicatorWidth, y + density() * 3, bucketPaint)
    }

    private fun computeMaxScroll() {
        // Calculate max scroll based on content width vs view width
        // For timeline, we allow scrolling to show events beyond the visible window
        val contentWidth = if (eventTriggers.isNotEmpty()) {
            val first = eventTriggers.first().time ?: Date()
            val last = eventTriggers.last().time ?: first
            val duration = last.time - first.time
            // Allow scrolling 2x the window width
            (width * 2f).coerceAtLeast(width)
        } else {
            0f
        }
        maxScrollX = (contentWidth - width).coerceAtLeast(0f)
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

    private fun timeForX(x: Float): Date {
        val fraction = (x + scrollX) / width
        val clamped = fraction.coerceIn(0f, 1f)
        return Date(windowStart.time + ((windowEnd.time - windowStart.time) * clamped).toLong())
    }

    private fun formatRange(): String {
        val formatter = java.text.SimpleDateFormat(\"MMM d HH:mm\", Locale.getDefault())
        return formatter.format(windowStart) + \" → \" + formatter.format(windowEnd)
    }

    private fun density() = resources.displayMetrics.density

    private fun paintFor(type: Int): Paint? {
        val color = when (type) {
            EventTrigger.CAMERA -> Color.rgb(70, 145, 255)        // Blue
            EventTrigger.CAMERA_VIDEO -> Color.rgb(0, 190, 220)    // Cyan
            EventTrigger.ACCELEROMETER -> Color.rgb(255, 155, 30)  // Orange
            EventTrigger.LIGHT -> Color.rgb(55, 200, 90)           // Green
            EventTrigger.MICROPHONE -> Color.rgb(205, 80, 235)     // Purple
            EventTrigger.PRESSURE -> Color.rgb(240, 210, 50)       // Yellow
            EventTrigger.BUMP -> Color.rgb(255, 100, 90)           // Red
            EventTrigger.POWER -> Color.rgb(160, 160, 170)         // Gray
            EventTrigger.HEART -> Color.rgb(245, 95, 140)          // Pink
            EventTrigger.EMF -> Color.rgb(0, 225, 175)             // Teal
            else -> return null
        }
        return Paint().apply { this.color = color; strokeWidth = density() * 3 }
    }
}
