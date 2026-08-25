package org.havenapp.main.anomaly

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class AnomalyCalibratorTest {

    @Test
    fun testCalibratorInitialization() {
        val featureNames = listOf(\"sensor1_mean\", \"sensor1_std\", \"sensor1_max\", \"sensor1_min\",
                                   \"sensor2_mean\", \"sensor2_std\", \"sensor2_max\", \"sensor2_min\",
                                   \"sensor3_mean\", \"sensor3_std\", \"sensor3_max\", \"sensor3_min\",
                                   \"sensor4_mean\", \"sensor4_std\", \"sensor4_max\", \"sensor4_min\")
        val calibrator = AnomalyCalibrator(featureNames)
        
        assertFalse(\"Calibrator should not be ready initially\", calibrator.isReady())
        
        // Add minimum samples
        for (i in 0 until AnomalyCalibrator.MINIMUM_CALIBRATION_SAMPLES) {
            val values = DoubleArray(16) { (Math.random() * 10).toDouble() }
            calibrator.add(values)
        }
        
        assertTrue(\"Calibrator should be ready after minimum samples\", calibrator.isReady())
    }

    @Test
    fun testCalibratorProducesModel() {
        val featureNames = listOf(\"sensor1_mean\", \"sensor1_std\", \"sensor1_max\", \"sensor1_min\",
                                   \"sensor2_mean\", \"sensor2_std\", \"sensor2_max\", \"sensor2_min\",
                                   \"sensor3_mean\", \"sensor3_std\", \"sensor3_max\", \"sensor3_min\",
                                   \"sensor4_mean\", \"sensor4_std\", \"sensor4_max\", \"sensor4_min\")
        val calibrator = AnomalyCalibrator(featureNames)
        
        // Add enough samples
        for (i in 0 until AnomalyCalibrator.CALIBRATION_SAMPLES) {
            val values = DoubleArray(16) { (Math.random() * 10).toDouble() }
            calibrator.add(values)
        }
        
        val model = calibrator.calibrate()
        assertNotNull(\"Model should be produced after calibration\", model)
        assertEquals(\"Feature names should match\", featureNames, model.featureNames)
        assertEquals(\"Means array size\", featureNames.size, model.means.size)
        assertEquals(\"Std deviations array size\", featureNames.size, model.standardDeviations.size)
        assertEquals(\"Loadings array size\", featureNames.size * featureNames.size, model.loadings.size)
        assertTrue(\"Threshold should be positive\", model.threshold > 0)
        assertEquals(\"Sample count\", AnomalyCalibrator.CALIBRATION_SAMPLES, model.sampleCount)
    }

    @Test
    fun testCalibratorWithConstantValues() {
        val featureNames = listOf(\"sensor1_mean\", \"sensor1_std\", \"sensor1_max\", \"sensor1_min\")
        val calibrator = AnomalyCalibrator(featureNames)
        
        // Add constant values - should handle gracefully
        for (i in 0 until AnomalyCalibrator.MINIMUM_CALIBRATION_SAMPLES) {
            calibrator.add(doubleArrayOf(5.0, 0.0, 5.0, 5.0))
        }
        
        val model = calibrator.calibrate()
        assertNotNull(model)
        // With zero variance, std should be near zero
        assertTrue(model.standardDeviations[1] < 1e-6)
    }

    @Test
    fun testCalibratorRejectsWrongSize() {
        val featureNames = listOf(\"a\", \"b\")
        val calibrator = AnomalyCalibrator(featureNames)
        
        try {
            calibrator.add(doubleArrayOf(1.0, 2.0, 3.0)) // Wrong size
            fail(\"Should throw IllegalArgumentException\")
        } catch (e: IllegalArgumentException) {
            // Expected
        }
    }
}
