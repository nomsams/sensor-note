package org.havenapp.main.sensors

import org.havenapp.main.anomaly.SensorFeatureWindow
import org.junit.Assert.*
import org.junit.Test

class SensorFeatureWindowTest {

    @Test
    fun testFeatureWindowInitialization() {
        val window = SensorFeatureWindow(4, minimumSamples = 1)
        
        assertEquals(4, window.names.size / 4) // 4 features per sensor
        assertEquals(16, window.names.size) // 4 sensors * 4 features
    }

    @Test
    fun testObserveBeforeMinimumSamples() {
        val window = SensorFeatureWindow(4, minimumSamples = 8)
        
        // Add fewer than minimum samples
        for (i in 0..5) {
            val vector = window.observe(System.currentTimeMillis(), 0, 1.0)
            assertNull("Should return null before minimum samples", vector)
        }
    }

    @Test
    fun testObserveAfterMinimumSamples() {
        val window = SensorFeatureWindow(4, minimumSamples = 8)
        
        // Add minimum samples to all channels
        for (channel in 0..3) {
            for (i in 0..7) {
                window.observe(System.currentTimeMillis() + i * 100, channel, 1.0)
            }
        }
        
        // Now observe should return a feature vector
        val vector = window.observe(System.currentTimeMillis() + 800, 0, 1.0)
        assertNotNull("Should return vector after minimum samples", vector)
        assertEquals(16, vector!!.values.size)
    }

    @Test
    fun testAddMethod() {
        val window = SensorFeatureWindow(4, minimumSamples = 1)
        
        // Add values to specific channels
        window.add(0, 1.0)
        window.add(1, 2.0)
        window.add(2, 3.0)
        window.add(3, 4.0)
        
        val vector = window.observe(System.currentTimeMillis(), 0, 1.0)
        assertNotNull(vector)
    }

    @Test
    fun testWindowExpiration() {
        val window = SensorFeatureWindow(4, windowMillis = 100, minimumSamples = 2)
        
        // Add samples
        val time1 = System.currentTimeMillis()
        window.observe(time1, 0, 1.0)
        window.observe(time1, 1, 1.0)
        window.observe(time1, 2, 1.0)
        window.observe(time1, 3, 1.0)
        
        // Wait for window to expire
        Thread.sleep(150)
        
        // Add new samples - old ones should be expired
        val time2 = System.currentTimeMillis()
        window.observe(time2, 0, 2.0)
        window.observe(time2, 1, 2.0)
        window.observe(time2, 2, 2.0)
        window.observe(time2, 3, 2.0)
        
        val vector = window.observe(time2 + 10, 0, 2.0)
        assertNotNull(vector)
    }
}
