package org.havenapp.main.anomaly

import org.junit.Assert.*
import org.junit.Test

class HotellingPcaModelTest {

    @Test
    fun testModelCreation() {
        val featureNames = listOf(\"f1\", \"f2\")
        val means = doubleArrayOf(0.0, 0.0)
        val stdDevs = doubleArrayOf(1.0, 1.0)
        val loadings = doubleArrayOf(1.0, 0.0, 0.0, 1.0)
        
        val model = HotellingPcaModel(
            featureNames = featureNames,
            means = means,
            standardDeviations = stdDevs,
            loadings = loadings,
            componentCount = 2,
            threshold = 5.0,
            sampleCount = 100
        )
        
        assertNotNull(model)
        assertEquals(2, model.featureNames.size)
        assertEquals(2, model.means.size)
        assertEquals(2, model.standardDeviations.size)
        assertEquals(4, model.loadings.size)
        assertEquals(2, model.componentCount)
        assertEquals(5.0, model.threshold, 0.001)
        assertEquals(100, model.sampleCount)
    }

    @Test
    fun testInferWithinThreshold() {
        val featureNames = listOf(\"f1\", \"f2\")
        val means = doubleArrayOf(0.0, 0.0)
        val stdDevs = doubleArrayOf(1.0, 1.0)
        val loadings = doubleArrayOf(1.0, 0.0, 0.0, 1.0)
        
        val model = HotellingPcaModel(
            featureNames = featureNames,
            means = means,
            standardDeviations = stdDevs,
            loadings = loadings,
            componentCount = 2,
            threshold = 5.0,
            sampleCount = 100
        )
        
        // Input within threshold
        val result = model.infer(doubleArrayOf(0.5, 0.5))
        
        assertFalse(\"Should not be anomaly\", result.anomaly)
        assertTrue(\"T² should be positive\", result.tSquared > 0)
    }

    @Test
    fun testInferOutsideThreshold() {
        val featureNames = listOf(\"f1\", \"f2\")
        val means = doubleArrayOf(0.0, 0.0)
        val stdDevs = doubleArrayOf(1.0, 1.0)
        val loadings = doubleArrayOf(1.0, 0.0, 0.0, 1.0)
        
        val model = HotellingPcaModel(
            featureNames = featureNames,
            means = means,
            standardDeviations = stdDevs,
            loadings = loadings,
            componentCount = 2,
            threshold = 5.0,
            sampleCount = 100
        )
        
        // Input far outside threshold
        val result = model.infer(doubleArrayOf(10.0, 10.0))
        
        assertTrue(\"Should be anomaly\", result.anomaly)
    }

    @Test
    fun testInferZeroStdDev() {
        val featureNames = listOf(\"f1\", \"f2\")
        val means = doubleArrayOf(0.0, 0.0)
        val stdDevs = doubleArrayOf(0.0, 1.0) // First feature has zero std dev
        val loadings = doubleArrayOf(1.0, 0.0, 0.0, 1.0)
        
        val model = HotellingPcaModel(
            featureNames = featureNames,
            means = means,
            standardDeviations = stdDevs,
            loadings = loadings,
            componentCount = 2,
            threshold = 5.0,
            sampleCount = 100
        )
        
        // Should handle zero std dev gracefully
        val result = model.infer(doubleArrayOf(5.0, 0.5))
        assertNotNull(result)
    }

    @Test
    fun testFirstTwoComponentCoordinates() {
        val featureNames = listOf(\"f1\", \"f2\", \"f3\", \"f4\")
        val means = doubleArrayOf(0.0, 0.0, 0.0, 0.0)
        val stdDevs = doubleArrayOf(1.0, 1.0, 1.0, 1.0)
        val loadings = doubleArrayOf(
            1.0, 0.0, 0.0, 0.0,
            0.0, 1.0, 0.0, 0.0,
            0.0, 0.0, 1.0, 0.0,
            0.0, 0.0, 0.0, 1.0
        )
        
        val model = HotellingPcaModel(
            featureNames = featureNames,
            means = means,
            standardDeviations = stdDevs,
            loadings = loadings,
            componentCount = 2,
            threshold = 5.0,
            sampleCount = 100
        )
        
        val (x, y) = model.firstTwoComponentCoordinates(doubleArrayOf(1.0, 2.0, 3.0, 4.0))
        assertEquals(1.0, x, 0.001)
        assertEquals(2.0, y, 0.001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInferWrongSize() {
        val featureNames = listOf(\"f1\", \"f2\")
        val means = doubleArrayOf(0.0, 0.0)
        val stdDevs = doubleArrayOf(1.0, 1.0)
        val loadings = doubleArrayOf(1.0, 0.0, 0.0, 1.0)
        
        val model = HotellingPcaModel(
            featureNames = featureNames,
            means = means,
            standardDeviations = stdDevs,
            loadings = loadings,
            componentCount = 2,
            threshold = 5.0,
            sampleCount = 100
        )
        
        model.infer(doubleArrayOf(1.0)) // Wrong size
    }
}
