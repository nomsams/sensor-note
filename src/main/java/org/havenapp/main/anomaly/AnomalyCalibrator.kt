package org.havenapp.main.anomaly

import kotlin.math.max

class AnomalyCalibrator(private val featureNames: List<String>) {
    private val samples = mutableListOf<DoubleArray>()

    fun clear() {
        samples.clear()
    }

    @Synchronized
    fun add(values: DoubleArray) {
        require(values.size == featureNames.size)
        if (samples.size >= CALIBRATION_SAMPLES) samples.removeAt(0)
        samples.add(values.copyOf())
    }

    fun isReady(): Boolean = samples.size >= MINIMUM_CALIBRATION_SAMPLES

    @JvmOverloads
    @Synchronized
    fun calibrate(confidenceScale: Double = 9.21): HotellingPcaModel? {
        if (!isReady()) return null
        val dimensions = featureNames.size
        val means = DoubleArray(dimensions)
        for (sample in samples) for (index in 0 until dimensions) means[index] += sample[index]
        for (index in 0 until dimensions) means[index] /= samples.size

        val deviations = DoubleArray(dimensions)
        for (sample in samples) for (index in 0 until dimensions) {
            deviations[index] += (sample[index] - means[index]) * (sample[index] - means[index])
        }
        for (index in 0 until dimensions) {
            deviations[index] = sqrt(deviations[index] / max(1, samples.size - 1))
        }

        val covariance = Array(dimensions) { DoubleArray(dimensions) }
        for (sample in samples) {
            val centered = DoubleArray(dimensions) { index ->
                if (deviations[index] <= 1e-12) 0.0 else (sample[index] - means[index]) / deviations[index]
            }
            for (row in 0 until dimensions) for (column in 0 until dimensions) {
                covariance[row][column] += centered[row] * centered[column]
            }
        }
        for (row in 0 until dimensions) for (column in 0 until dimensions) {
            covariance[row][column] /= max(1, samples.size - 1)
        }

        val eigenvectors = powerIteration(covariance)
        val retainedVariance = eigenvectors.map { abs(it.eigenvalue) }.sum().coerceAtLeast(1e-12)
        var cumulative = 0.0
        var componentCount = 1
        for (value in eigenvectors.sortedByDescending { abs(it.eigenvalue) }) {
            cumulative += abs(value.eigenvalue)
            if (cumulative / retainedVariance >= 0.95 || componentCount == dimensions) break
            componentCount++
        }

        val loadings = DoubleArray(dimensions * dimensions)
        eigenvectors.sortedByDescending { abs(it.eigenvalue) }.forEachIndexed { component, eigenpair ->
            for (feature in 0 until dimensions) {
                loadings[component * dimensions + feature] = eigenpair.vector[feature]
            }
        }

        val distances = samples.map { sample ->
            projectStatistic(means, deviations, loadings, sample)
        }
        val distanceMean = distances.average()
        val distanceSpread = sqrt(distances.sumOf { (it - distanceMean) * (it - distanceMean) } /
                max(1, distances.size - 1))
        val adaptiveThreshold = distanceMean + confidenceScale * distanceSpread

        return HotellingPcaModel(
            featureNames = featureNames,
            means = means,
            standardDeviations = deviations,
            loadings = loadings,
            componentCount = componentCount,
            threshold = adaptiveThreshold.coerceIn(1e-6, 1e6),
            sampleCount = samples.size
        )
    }

    private fun projectStatistic(
        means: DoubleArray,
        deviations: DoubleArray,
        loadings: DoubleArray,
        sample: DoubleArray
    ): Double {
        val temporaryModel = HotellingPcaModel(
            featureNames, means, deviations, loadings, featureNames.size, 1.0, samples.size
        )
        return temporaryModel.infer(sample).tSquared
    }

    private data class EigenPair(val eigenvalue: Double, val vector: DoubleArray)

    private fun powerIteration(matrix: Array<DoubleArray>): List<EigenPair> {
        val dimensions = matrix.size
        val vectors = mutableListOf<EigenPair>()
        val deflated = Array(dimensions) { matrix[it].copyOf() }
        repeat(dimensions) {
            var vector = DoubleArray(dimensions) { index -> if (index % 2 == 0) 1.0 else 0.7 }
            repeat(24) {
                val next = DoubleArray(dimensions)
                for (row in 0 until dimensions) {
                    next[row] = deflated[row].indices.sumOf { column -> deflated[row][column] * vector[column] }
                }
                val norm = sqrt(next.sumOf { it * it })
                if (norm <= 1e-15) return@repeat
                vector = next.map { it / norm }.toDoubleArray()
            }
            val rayleigh = vector.indices.sumOf { row ->
                vector.indices.sumOf { column -> vector[row] * deflated[row][column] * vector[column] }
            }
            vectors.add(EigenPair(rayleigh, vector.copyOf()))
            for (row in 0 until dimensions) for (column in 0 until dimensions) {
                deflated[row][column] -= rayleigh * vector[row] * vector[column]
            }
        }
        return vectors
    }

    companion object {
        const val MINIMUM_CALIBRATION_SAMPLES = 40
        const val CALIBRATION_SAMPLES = 240
    }
}

private fun sqrt(value: Double): Double = kotlin.math.sqrt(value.coerceAtLeast(0.0))

private fun abs(value: Double): Double = kotlin.math.abs(value)
