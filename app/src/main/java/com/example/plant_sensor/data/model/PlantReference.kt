package com.example.plant_sensor.data.model

import com.google.gson.annotations.SerializedName

data class PlantReference(
    @SerializedName("pid") val pid: String? = null,
    @SerializedName("display_pid") val displayPid: String? = null,
    @SerializedName("alias") val alias: String? = null,
    @SerializedName("image") val image: String? = null,
    @SerializedName("category") val category: String? = null,
    @SerializedName("blooming") val blooming: String? = null,
    @SerializedName("color") val color: String? = null,
    @SerializedName("size") val size: String? = null,
    @SerializedName("soil") val soil: String? = null,
    @SerializedName("sunlight") val sunlight: String? = null,
    @SerializedName("watering") val watering: String? = null,
    @SerializedName("fertilization") val fertilization: String? = null,
    @SerializedName("pruning") val pruning: String? = null,
    @SerializedName("origin") val origin: String? = null,
    @SerializedName("floral_language") val floralLanguage: String? = null,
    @SerializedName("production") val production: String? = null,
    @SerializedName("max_light_lux") val maxLightLux: Double? = null,
    @SerializedName("min_light_lux") val minLightLux: Double? = null,
    @SerializedName("max_temp") val maxTemp: Double? = null,
    @SerializedName("min_temp") val minTemp: Double? = null,
    @SerializedName("max_env_humid") val maxEnvHumid: Double? = null,
    @SerializedName("min_env_humid") val minEnvHumid: Double? = null,
    @SerializedName("max_soil_moist") val maxSoilMoist: Double? = null,
    @SerializedName("min_soil_moist") val minSoilMoist: Double? = null,
    @SerializedName("max_soil_ec") val maxSoilEc: Double? = null,
    @SerializedName("min_soil_ec") val minSoilEc: Double? = null
)
