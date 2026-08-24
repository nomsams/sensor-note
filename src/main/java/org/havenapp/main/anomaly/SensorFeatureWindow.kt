package org.havenapp.main.anomaly

import java.util.ArrayDeque
import kotlin.math.abs

data class FeatureVector(
    val timestamp: Long,
    val values: DoubleArray
)

class SensorFeatureWindow(
    private val featureCount: Int,
    private val windowMillis: Long = 1_500L,
    private val minimumSamples: Int = 8,
    private val maximumSamples: Int = 512
) {
    data class Sample(val timestamp: Long, val values: DoubleArray)

    private class ChannelWindow {
        private val samples = ArrayDeque<Sample>()

        fun add(sample: Sample, cutoff: Long) {
            samples.addLast(sample)
            while (samples.isNotEmpty() && samples.first().timestamp < cutoff) samples.removeFirst()
        }

        fun features(timestamp: Long): List<Double> {
            if (samples.size < minimumSamples) return emptyList()
            val values = samples.map { abs(it.values[0]) }
            val mean = values.average()
            val variance = if (values.size > 1) {
                values.sumOf { (it - mean) * (it - mean) } / (values.size - 1)
            } else 0.0
            return listOf(mean, kotlin.math.sqrt(variance), values.maxOrNull() ?: 0.0,
                    values.minOrNull() ?: 0.0)
        }
    }

    private val channels = List(featureCount) { ChannelWindow() }
    val names: List<String> = List(featureCount) { channel ->
        listOf("mean", "std", "max", "min").map { suffix -> "sensor${channel + 1}_$suffix" }
    }.flatten()

    @Synchronized
    fun observe(timestamp: Long, sensorIndex: Int, value: Double): FeatureVector? {
        require(sensorIndex in channels.indices)
        val cutoff = timestamp - windowMillis
        channels.forEach { it.add(Sample(timestamp, doubleArrayOf(value)), cutoff) }
        val all = channels.map { it.features(timestamp) }
        if (all.any { it.isEmpty() }) return null
        return FeatureVector(timestamp, all.flatten().toDoubleArray())
    }
}
