package com.example.plant_sensor

data class Plant(
    val name: String,
    val room: String,
    val moisture: String,
    var lastWatered: String,
    val temperature: String,
    val careHints: String
)