package com.example.plant_sensor.data.model

/**
 * Represents one measured value at a specific point in time.
 */
data class HistoryPoint(
    val timestamp: Long,
    val value: Float
)
