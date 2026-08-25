package org.havenapp.main.anomaly

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import androidx.annotation.NonNull
import org.havenapp.main.model.EventTrigger
import java.util.concurrent.Executors

class AnomalyDataStore(context: Context) {
    private val dbHelper = DatabaseHelper(context)
    private val executor = Executors.newSingleThreadExecutor()
    private val LOG_TAG = \"AnomalyDataStore\"

    init {
        // Clean up old data periodically
        executor.execute { cleanupOldData() }
    }

    /**
     * Save an anomaly point
     */
    fun savePoint(point: AnomalyPoint) {
        executor.execute {
            val db = dbHelper.writableDatabase
            try {
                db.execSQL(
                    \"INSERT INTO anomaly_points (timestamp, x, y, t_squared, anomaly) VALUES (?, ?, ?, ?, ?)\",
                    arrayOf(
                        point.timestamp.toString(),
                        point.x.toString(),
                        point.y.toString(),
                        point.tSquared.toString(),
                        if (point.anomaly) \"1\" else \"0\"
                    )
                )
            } catch (e: Exception) {
                Log.e(LOG_TAG, \"Failed to save anomaly point\", e)
            }
        }
    }

    /**
     * Save a batch of anomaly points
     */
    fun savePoints(points: List<AnomalyPoint>) {
        executor.execute {
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                for (point in points) {
                    db.execSQL(
                        \"INSERT INTO anomaly_points (timestamp, x, y, t_squared, anomaly) VALUES (?, ?, ?, ?, ?)\",
                        arrayOf(
                            point.timestamp.toString(),
                            point.x.toString(),
                            point.y.toString(),
                            point.tSquared.toString(),
                            if (point.anomaly) \"1\" else \"0\"
                        )
                    )
                }
                db.setTransactionSuccessful()
            } catch (e: Exception) {
                Log.e(LOG_TAG, \"Failed to save anomaly points\", e)
            } finally {
                db.endTransaction()
            }
        }
    }

    /**
     * Get anomaly points within a time range
     */
    fun getPoints(startTime: Long, endTime: Long, callback: (List<AnomalyPoint>) -> Unit) {
        executor.execute {
            val db = dbHelper.readableDatabase
            val points = mutableListOf<AnomalyPoint>()
            val cursor = db.rawQuery(
                \"SELECT timestamp, x, y, t_squared, anomaly FROM anomaly_points WHERE timestamp >= ? AND timestamp <= ? ORDER BY timestamp ASC\",
                arrayOf(startTime.toString(), endTime.toString())
            )
            try {
                while (cursor.moveToNext()) {
                    points.add(AnomalyPoint(
                        timestamp = cursor.getLong(0),
                        x = cursor.getDouble(1),
                        y = cursor.getDouble(2),
                        tSquared = cursor.getDouble(3),
                        anomaly = cursor.getInt(4) == 1
                    ))
                }
            } finally {
                cursor.close()
            }
            // Post to main thread if needed
            callback(points)
        }
    }

    /**
     * Get all anomaly points (for playback)
     */
    fun getAllPoints(callback: (List<AnomalyPoint>) -> Unit) {
        executor.execute {
            val db = dbHelper.readableDatabase
            val points = mutableListOf<AnomalyPoint>()
            val cursor = db.rawQuery(
                \"SELECT timestamp, x, y, t_squared, anomaly FROM anomaly_points ORDER BY timestamp ASC\",
                null
            )
            try {
                while (cursor.moveToNext()) {
                    points.add(AnomalyPoint(
                        timestamp = cursor.getLong(0),
                        x = cursor.getDouble(1),
                        y = cursor.getDouble(2),
                        tSquared = cursor.getDouble(3),
                        anomaly = cursor.getInt(4) == 1
                    ))
                }
            } finally {
                cursor.close()
            }
            callback(points)
        }
    }

    /**
     * Get summary buckets for outside-zone vs time chart
     */
    fun getSummaryBuckets(startTime: Long, endTime: Long, bucketCount: Int, callback: (List<AnomalySummaryBucket>) -> Unit) {
        executor.execute {
            val db = dbHelper.readableDatabase
            val buckets = mutableListOf<AnomalySummaryBucket>()
            val bucketSize = (endTime - startTime) / bucketCount
            
            for (i in 0 until bucketCount) {
                val bucketStart = startTime + i * bucketSize
                val bucketEnd = startTime + (i + 1) * bucketSize
                
                val cursor = db.rawQuery(
                    \"SELECT COUNT(*), SUM(CASE WHEN anomaly = 1 THEN 1 ELSE 0 END), MAX(t_squared) \" +
                    \"FROM anomaly_points WHERE timestamp >= ? AND timestamp < ?\",
                    arrayOf(bucketStart.toString(), bucketEnd.toString())
                )
                try {
                    if (cursor.moveToNext()) {
                        val total = cursor.getInt(0)
                        val anomalyCount = cursor.getInt(1)
                        val maxDistance = cursor.getDouble(2)
                        if (total > 0) {
                            buckets.add(AnomalySummaryBucket(
                                timestamp = bucketStart + bucketSize / 2,
                                totalPoints = total,
                                anomalyCount = anomalyCount,
                                maximumDistance = maxDistance
                            ))
                        }
                    }
                } finally {
                    cursor.close()
                }
            }
            callback(buckets)
        }
    }

    /**
     * Clean up data older than 7 days
     */
    private fun cleanupOldData() {
        val cutoff = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
        val db = dbHelper.writableDatabase
        try {
            db.execSQL(\"DELETE FROM anomaly_points WHERE timestamp < ?\", arrayOf(cutoff.toString()))
        } catch (e: Exception) {
            Log.e(LOG_TAG, \"Failed to cleanup old data\", e)
        }
    }

    /**
     * Close the database
     */
    fun close() {
        dbHelper.close()
        executor.shutdown()
    }

    private class DatabaseHelper(context: Context) : SQLiteOpenHelper(
        context, \"anomaly_data.db\", null, 1
    ) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(\"\"\"
                CREATE TABLE anomaly_points (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    timestamp INTEGER NOT NULL,
                    x REAL NOT NULL,
                    y REAL NOT NULL,
                    t_squared REAL NOT NULL,
                    anomaly INTEGER NOT NULL
                )
                \"\"\".trimIndent())
            db.execSQL(\"CREATE INDEX idx_anomaly_timestamp ON anomaly_points(timestamp)\")
            db.execSQL(\"CREATE INDEX idx_anomaly_anomaly ON anomaly_points(anomaly)\")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // Handle migrations if needed
        }
    }
}
