package com.example.plant_sensor

import com.google.gson.annotations.SerializedName

data class Plant(
    val id: Long? = null,

    @SerializedName("plant_name")
    val name: String,

    val room: String,

    @SerializedName("species_id")
    val speciesId: String,

    @SerializedName("last_watered")
    val lastWatered: String? = null,

    @SerializedName("care_hints")
    val careHints: String? = null,

    @SerializedName("MAC")
    val sensorMac: String? = null,

    @SerializedName("image_uri")
    val imageUri: String? = null
)
