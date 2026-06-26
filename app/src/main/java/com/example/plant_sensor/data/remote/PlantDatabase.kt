package com.example.plant_sensor.data.remote

import android.util.Log
import com.example.plant_sensor.BuildConfig
import com.example.plant_sensor.data.model.PlantReference
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Loads plant reference data from the Python backend.
 */
object PlantDatabase {

    private const val LOG_TAG = "PlantDatabase"
    private const val SERVER_URL =BuildConfig.SERVER_URL
    private const val CONNECTION_TIMEOUT_MS = 15000
    private const val READ_TIMEOUT_MS = 30000
    private const val CHARSET_UTF_8 = "UTF-8"
    private val HTTP_SUCCESS_RANGE = 200..299
    private val gson = GsonBuilder().create()

    var plantReferences: List<PlantReference> = emptyList()
        private set

    private val referenceCache = mutableMapOf<String, PlantReference>()
    private var isLoaded = false

    fun loadIfNeeded(onComplete: () -> Unit) {
        if (isLoaded && plantReferences.isNotEmpty()) {
            onComplete()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            loadAllReferences()
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }

    private suspend fun loadAllReferences() {
        val json = performGetRequest("$SERVER_URL/plant_references") ?: return
        try {
            val type = object : TypeToken<List<PlantReference>>() {}.type
            val references: List<PlantReference> = gson.fromJson(json, type) ?: emptyList()
            plantReferences = references
            updateCache(references)
            isLoaded = true
            Log.d(LOG_TAG, "Loaded ${references.size} plant references")
        } catch (exception: Exception) {
            Log.e(LOG_TAG, "Error parsing plant references", exception)
        }
    }

    suspend fun getPlantReferencesPage(limit: Int, offset: Int): List<PlantReference> {
        val url = "$SERVER_URL/plant_references_page?limit=$limit&offset=$offset"
        val json = performGetRequest(url) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<PlantReference>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (exception: Exception) {
            Log.e(LOG_TAG, "Error parsing reference page offset=$offset", exception)
            emptyList()
        }
    }

    fun getByPid(pid: String?): PlantReference? {
        return if (pid.isNullOrBlank()) null else referenceCache[pid]
    }

    suspend fun getByPidFromServer(pid: String?): PlantReference? = withContext(Dispatchers.IO) {
        if (pid.isNullOrBlank()) return@withContext null
        val cachedReference = referenceCache[pid]
        if (cachedReference != null && hasCareGuide(cachedReference)) {
            return@withContext cachedReference
        }
        val encodedPid = URLEncoder.encode(pid.trim(), CHARSET_UTF_8).replace("+", "%20")
        val json = performGetRequest("$SERVER_URL/plant_reference?pid=$encodedPid")
        if (json == null) return@withContext cachedReference
        try {
            val reference = gson.fromJson(json, PlantReference::class.java)
            if (reference != null && !reference.pid.isNullOrBlank()) {
                referenceCache[reference.pid] = reference
            }
            reference ?: cachedReference
        } catch (exception: Exception) {
            Log.e(LOG_TAG, "Error parsing plant reference pid=$pid", exception)
            cachedReference
        }
    }

    private fun hasCareGuide(reference: PlantReference): Boolean {
        return !reference.sunlight.isNullOrBlank() ||
                !reference.watering.isNullOrBlank() ||
                !reference.fertilization.isNullOrBlank()
    }

    suspend fun searchPlants(query: String): List<PlantReference> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val encodedQuery = URLEncoder.encode(query, CHARSET_UTF_8)
        val json = performGetRequest("$SERVER_URL/search_plants?q=$encodedQuery") ?: return@withContext emptyList()
        try {
            val type = object : TypeToken<List<PlantReference>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (exception: Exception) {
            Log.e(LOG_TAG, "Error parsing search results", exception)
            emptyList()
        }
    }

    private suspend fun performGetRequest(urlString: String): String? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = openConnection(urlString)
            if (connection.responseCode in HTTP_SUCCESS_RANGE) {
                val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }.trim()
                fixIncompleteJson(response)
            } else {
                null
            }
        } catch (exception: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun openConnection(urlString: String): HttpURLConnection {
        return (URL(urlString).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECTION_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
        }
    }

    private fun fixIncompleteJson(json: String): String {
        return when {
            json.startsWith("[") && !json.endsWith("]") -> "$json]"
            json.startsWith("{") && !json.endsWith("}") -> "$json}"
            else -> json
        }
    }

    private fun updateCache(references: List<PlantReference>) {
        referenceCache.clear()
        references.forEach { it.pid?.let { pid -> referenceCache[pid] = it } }
    }
}
