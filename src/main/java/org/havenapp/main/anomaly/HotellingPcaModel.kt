package org.havenapp.main.anomaly

import kotlin.math.max
import kotlin.math.sqrt

class HotellingPcaModel(
    val featureNames: List<String>,
    val means: DoubleArray,
    val standardDeviations: DoubleArray,
    val loadings: DoubleArray,
    val componentCount: Int,
    val threshold: Double,
    val sampleCount: Int
) {
    init {
        require(means.size == featureNames.size)
        require(standardDeviations.size == featureNames.size)
        require(loadings.size == featureNames.size * featureNames.size)
    }

    fun infer(values: DoubleArray): InferenceResult {
        require(values.size == featureNames.size)
        val standardized = DoubleArray(values.size) { index ->
            val deviation = standardDeviations[index]
            if (deviation <= 1e-9) 0.0 else (values[index] - means[index]) / deviation
        }

        val projected = DoubleArray(componentCount)
        for (component in 0 until componentCount) {
            var sum = 0.0
            for (feature in standardized.indices) {
                sum += standardized[feature] * loadings[component * featureNames.size + feature]
            }
            projected[component] = sum
        }

        var statistic = projected.sumOf { it * it }
        statistic *= threshold / max(threshold, 1e-9)

        var contribution = 0.0
        for ((index, value) in standardized.withIndex()) {
            contribution += value * value
        }
        val normalizedT2 = statistic / max(contribution, 1e-9)
        return InferenceResult(statistic, normalizedT2, statistic > threshold)
    }

    fun firstTwoComponentCoordinates(values: DoubleArray): Pair<Double, Double> {
        val standardized = DoubleArray(values.size) { index ->
            val deviation = standardDeviations[index]
            if (deviation <= 1e-9) 0.0 else (values[index] - means[index]) / deviation
        }
        var x = 0.0
        var y = 0.0
        for (feature in standardized.indices) {
            x += standardized[feature] * loadings[feature]
            y += standardized[feature] * loadings[featureNames.size + feature]
        }
        return x to y
    }

    data class InferenceResult(
        val tSquared: Double,
        val normalizedDistance: Double,
        val anomaly: Boolean
    )
}
