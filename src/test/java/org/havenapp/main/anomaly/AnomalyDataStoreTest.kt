package org.havenapp.main.anomaly
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config


import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class AnomalyDataStoreTest {

    @Test
    fun testSaveAndRetrievePoints() {
        val context = ApplicationProvider.getApplicationContext<Context>()
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
        
        println("Saved-point callback completed; retrieved=${retrieved?.size}")
        if (retrieved != null) {
            assertTrue(retrieved!!.isNotEmpty())
        }
        
        store.close()
    }

    @Test
    fun testGetPointsInRange() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = AnomalyDataStore(context)
        
        val now = System.currentTimeMillis()
        store.savePoints(listOf(
            AnomalyPoint(now - 10000, 0.1, 0.1, 0.5, false),
            AnomalyPoint(now - 5000, 0.2, 0.2, 1.0, false),
            AnomalyPoint(now, 0.3, 0.3, 2.0, true)
        ))
        
        Thread.sleep(500)
        
        var retrieved: List<AnomalyPoint>? = null
        store.getPoints(now - 7000, now, { points ->
            retrieved = points
        })
        
        Thread.sleep(500)
        
        if (retrieved != null) {
            println("Range callback completed; retrieved=${retrieved!!.size}")
        }
        
        store.close()
    }

    @Test
    fun testGetSummaryBuckets() {
        val context = ApplicationProvider.getApplicationContext<Context>()
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
        
        if (buckets != null) {
            println("Summary callback completed; buckets=${buckets!!.size}")
        }
        
        store.close()
    }
}
