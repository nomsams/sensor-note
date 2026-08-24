package org.havenapp.main.anomaly

data class AnomalyPoint(
    val timestamp: Long,
    val x: Double,
    val y: Double,
    val tSquared: Double,
    val anomaly: Boolean
)

data class AnomalySummaryBucket(
    val timestamp: Long,
    val totalPoints: Int,
    val anomalyCount: Int,
    val maximumDistance: Double
)
