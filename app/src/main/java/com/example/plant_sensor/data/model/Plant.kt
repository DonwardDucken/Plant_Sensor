package com.example.plant_sensor.data.model

import com.google.gson.annotations.SerializedName

/**
 * Represents one plant that is stored on the server.
 */
data class Plant(

    /** Database id of the plant. */
    val id: Long? = null,

    /** User-defined plant name. */
    @SerializedName("plant_name")
    val name: String,

    /** Room in which the plant is located. */
    val room: String,

    /** Botanical species id. */
    @SerializedName("species_id")
    val speciesId: String,

    /** Date of the last watering. */
    @SerializedName("last_watered")
    val lastWatered: String? = null,

    /** User notes or care instructions. */
    @SerializedName("care_hints")
    val careHints: String? = null,

    /** MAC address of the assigned MiFlora sensor. */
    @SerializedName("MAC")
    val sensorMac: String? = null,

    /** URI of the plant image. */
    @SerializedName("image_uri")
    val imageUri: String? = null
)
