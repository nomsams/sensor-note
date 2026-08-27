package org.havenapp.main.anomaly

data class AnomalyPoint(
    @JvmField val timestamp: Long,
    @JvmField val x: Double,
    @JvmField val y: Double,
    @JvmField val tSquared: Double,
    @JvmField val anomaly: Boolean
)

data class AnomalySummaryBucket(
    @JvmField val timestamp: Long,
    @JvmField val totalPoints: Int,
    @JvmField val anomalyCount: Int,
    @JvmField val maximumDistance: Double
)
