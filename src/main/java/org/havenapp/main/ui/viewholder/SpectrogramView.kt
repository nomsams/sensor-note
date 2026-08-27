package org.havenapp.main.ui.viewholder

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class SpectrogramView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint()
    private var spectrogram: FloatArray? = null
    private val frequencyBins = 48
    private var sampleRate = 1
    private var durationSeconds = 1f

    fun setAudioData(samples: ShortArray, audioSampleRate: Int, audioDurationSeconds: Float) {
        spectrogram = computeSpectrogram(samples)
        sampleRate = audioSampleRate.coerceAtLeast(1)
        durationSeconds = audioDurationSeconds.coerceAtLeast(0.01f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK)
        val samples = spectrogram ?: return
        if (samples.isEmpty()) return

        val columnCount = samples.size / frequencyBins
        val columnWidth = width.toFloat() / columnCount
        val rowHeight = height.toFloat() / frequencyBins

        for (column in 0 until columnCount) {
            for (bin in 0 until frequencyBins) {
                val magnitude = samples[column * frequencyBins + bin]
                if (magnitude > 0.04f) {
                    paint.alpha = 48 + (magnitude * 207).toInt()
                    paint.color = when {
                        magnitude > 0.82f -> Color.WHITE
                        magnitude > 0.62f -> Color.rgb(255, 226, 90)
                        magnitude > 0.38f -> Color.rgb(255, 124, 40)
                        else -> Color.rgb(52, 145, 255)
                    }
                    val top = height - ((bin + 1) * rowHeight)
                    canvas.drawRect(
                        column * columnWidth,
                        top,
                        ((column + 1) * columnWidth),
                        top + rowHeight,
                        paint
                    )
                }
            }
        }
    }

    private fun computeSpectrogram(samples: ShortArray): FloatArray {
        val columns = min(width / 3, 180).coerceAtLeast(24)
        val frequencyBins = 48
        val result = FloatArray(columns * frequencyBins)
        if (samples.isEmpty()) return result

        val windowSize = max(64, samples.size / columns)
        for (column in 0 until columns) {
            val start = column * windowSize
            if (start >= samples.size) break
            val end = min(start + windowSize, samples.size)
            val size = end - start
            if (size < 8) continue

            val real = DoubleArray(size)
            val imaginary = DoubleArray(size)
            for (index in 0 until size) {
                real[index] = samples[start + index] / 32768.0 *
                        (0.54 - 0.46 * kotlin.math.cos((2.0 * Math.PI * index) / (size - 1)))
            }
            fft(real, imaginary)

            for (bin in 0 until frequencyBins) {
                val sourceBin = ((bin + 1) * size / 2 / frequencyBins).coerceIn(1, size / 2 - 1)
                val magnitude = kotlin.math.sqrt(real[sourceBin] * real[sourceBin] +
                        imaginary[sourceBin] * imaginary[sourceBin]).toFloat()
                result[column * frequencyBins + bin] = magnitude.coerceIn(0f, 1f)
            }
        }
        return result
    }

    private fun fft(realInput: DoubleArray, imaginaryInput: DoubleArray) {
        val count = realInput.size
        var bits = 0
        while (1 shl bits < count) bits++
        for (index in 0 until count) {
            val reversed = Integer.reverse(index) ushr (32 - bits)
            if (reversed > index) {
                var temp = realInput[index]; realInput[index] = realInput[reversed]; realInput[reversed] = temp
                temp = imaginaryInput[index]; imaginaryInput[index] = imaginaryInput[reversed]; imaginaryInput[reversed] = temp
            }
        }

        var size = 2
        while (size <= count) {
            val halfSize = size / 2
            val tableStep = Math.PI / halfSize
            var start = 0
            while (start < count) {
                var index = start
                var step = 0
                while (index < start + halfSize) {
                    val angle = tableStep * step
                    val cosine = kotlin.math.cos(angle)
                    val sine = kotlin.math.sin(angle)
                    val evenIndex = index + halfSize
                    val evenReal = realInput[evenIndex] * cosine + imaginaryInput[evenIndex] * sine
                    val evenImaginary = -realInput[evenIndex] * sine + imaginaryInput[evenIndex] * cosine
                    realInput[evenIndex] = realInput[index] - evenReal
                    imaginaryInput[evenIndex] = imaginaryInput[index] - evenImaginary
                    realInput[index] += evenReal
                    imaginaryInput[index] += evenImaginary
                    index++; step++
                }
                start += size
            }
            size *= 2
        }
    }
}
