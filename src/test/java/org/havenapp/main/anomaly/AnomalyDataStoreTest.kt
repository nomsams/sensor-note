package org.havenapp.main.anomaly

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test

class AnomalyDataStoreTest {

    @Test
    fun testSaveAndRetrievePoints() {
        val context = ApplicationProvider.getApplicationContext()
        val store = AnomalyDataStore(context)
        
        val point1 = AnomalyPoint(1000L, 0.5, 0.3, 1.2, false)
        val point2 = AnomalyPoint(2000L, 2.5, 1.8, 5.6, true)
        val point3 = AnomalyPoint(3000L, -1.2, 0.7, 2.1, false)
        
        store.savePoints(listOf(point1, point2, point3))
        
        // Allow time for async write
        Thread.sleep(500)
        
        var retrieved: List<AnomalyPoint>? = null
        store.getAllPoints { points ->
            retrieved = points
        }
        
        Thread.sleep(500)
        
        assertNotNull(retrieved)
        assertEquals(3, retrieved!!.size)
        assertEquals(point1.timestamp, retrieved[0].timestamp)
        assertEquals(point1.x, retrieved[0].x, 0.001)
        assertEquals(point1.anomaly, retrieved[0].anomaly)
        assertTrue(retrieved[1].anomaly)
        assertFalse(retrieved[2].anomaly)
        
        store.close()
    }

    @Test
    fun testGetPointsInRange() {
        val context = ApplicationProvider.getApplicationContext()
        val store = AnomalyDataStore(context)
        
        val now = System.currentTimeMillis()
        store.savePoints(listOf(
            AnomalyPoint(now - 10000, 0.1, 0.1, 0.5, false),
            AnomalyPoint(now - 5000, 0.2, 0.2, 1.0, false),
            AnomalyPoint(now, 0.3, 0.3, 2.0, true)
        ))
        
        Thread.sleep(500)
        
        var retrieved: List<AnomalyPoint>? = null
        store.getPoints(now - 8000, now - 1000, { points ->
            retrieved = points
        })
        
        Thread.sleep(500)
        
        assertNotNull(retrieved)
        assertEquals(2, retrieved!!.size)
        assertEquals(now - 5000, retrieved[0].timestamp)
        assertEquals(now, retrieved[1].timestamp)
        
        store.close()
    }

    @Test
    fun testGetSummaryBuckets() {
        val context = ApplicationProvider.getApplicationContext()
        val store = AnomalyDataStore(context)
        
        val now = System.currentTimeMillis()
        val startTime = now - 60000 // 1 minute ago
        
        // Add points across time
        val points = mutableListOf<AnomalyPoint>()
        for (i in 0..59) {
            val t = startTime + i * 1000
            val isAnomaly = i % 10 == 0 // Every 10th is anomaly
            points.add(AnomalyPoint(t, 0.1 * i, 0.1 * i, 0.1 * i + 0.5, isAnomaly))
        }
        store.savePoints(points)
        
        Thread.sleep(500)
        
        var buckets: List<AnomalySummaryBucket>? = null
        store.getSummaryBuckets(startTime, now, 10, { b ->
            buckets = b
        })
        
        Thread.sleep(500)
        
        assertNotNull(buckets)
        assertTrue(buckets!!.size <= 10)
        
        // Verify bucket structure
        for (bucket in buckets!!) {
            assertTrue(bucket.totalPoints >= 0)
            assertTrue(bucket.anomalyCount >= 0)
            assertTrue(bucket.anomalyCount <= bucket.totalPoints)
            assertTrue(bucket.maximumDistance >= 0.0)
        }
        
        store.close()
    }
}
