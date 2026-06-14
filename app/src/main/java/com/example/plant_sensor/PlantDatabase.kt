package com.example.plant_sensor

import android.util.Log
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Zentrale Datenbank für Pflanzenreferenzen.
 */
object PlantDatabase {

    //private const val SERVER_URL = "http://10.0.2.2:8080"
    private val SERVER_URL = "http://192.168.0.16:8080"
    private val gson = GsonBuilder().create()

    var plantReferences: List<PlantReference> = emptyList()
        private set

    private val cache = mutableMapOf<String, PlantReference>()
    private var isLoaded = false

    fun loadIfNeeded(onComplete: () -> Unit) {
        if (isLoaded && plantReferences.isNotEmpty()) {
            onComplete()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            loadAllFromServer()
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }

    /**
     * Führt einen HTTP-GET-Request aus mit Retry-Logik und robuster Fehlerbehandlung
     * für "unexpected end of stream" Fehler.
     */
    private suspend fun performRequest(urlStr: String): String? = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null

        try {
            val url = URL(urlStr)
            conn = url.openConnection() as HttpURLConnection

            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Accept-Encoding", "identity")
            conn.setRequestProperty("Connection", "close")

            if (conn.responseCode in 200..299) {
                var json = conn.inputStream
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }
                    .trim()

                if (json.startsWith("[") && !json.endsWith("]")) {
                    Log.w("PlantDatabase", "JSON array abgeschnitten, ] ergänzt")
                    json += "]"
                }

                if (json.startsWith("{") && !json.endsWith("}")) {
                    Log.w("PlantDatabase", "JSON object abgeschnitten, } ergänzt")
                    json += "}"
                }

                json
            } else {
                val error = conn.errorStream?.bufferedReader()?.use { it.readText() }
                Log.e("PlantDatabase", "HTTP ${conn.responseCode}: $error")
                null
            }
        } catch (e: Exception) {
            Log.e("PlantDatabase", "Request fehlgeschlagen: $urlStr", e)
            null
        } finally {
            conn?.disconnect()
        }
    }

    private suspend fun loadAllFromServer() {
        val json = performRequest("$SERVER_URL/plant_references")
        if (json != null) {
            try {
                val type = object : TypeToken<List<PlantReference>>() {}.type
                val list: List<PlantReference> = gson.fromJson(json, type) ?: emptyList()

                plantReferences = list
                cache.clear()
                list.forEach { ref ->
                    ref.pid?.let { cache[it] = ref }
                }

                isLoaded = true
                Log.d("PlantDatabase", "Loaded ${list.size} references")
            } catch (e: Exception) {
                Log.e("PlantDatabase", "Fehler beim Parsen der Referenzen", e)
            }
        }
    }

    suspend fun getPlantReferencesPage(limit: Int, offset: Int): List<PlantReference> {
        val json = performRequest("$SERVER_URL/plant_references_page?limit=$limit&offset=$offset")
        return if (json != null) {
            try {
                Log.d("PlantDatabase", "Page offset=$offset, size=${json.length}")
                val type = object : TypeToken<List<PlantReference>>() {}.type
                gson.fromJson(json, type) ?: emptyList()
            } catch (e: Exception) {
                Log.e("PlantDatabase", "Fehler beim Parsen von Page offset=$offset", e)
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    fun getByPid(pid: String?): PlantReference? {
        if (pid.isNullOrBlank()) return null
        return cache[pid]
    }

    suspend fun getByPidFromServer(pid: String?): PlantReference? = withContext(Dispatchers.IO) {
        if (pid.isNullOrBlank()) return@withContext null
        cache[pid]?.let { return@withContext it }

        val encodedPid = URLEncoder.encode(pid, "UTF-8")
        val json = performRequest("$SERVER_URL/plant_reference?pid=$encodedPid")
        if (json != null) {
            try {
                val ref = gson.fromJson(json, PlantReference::class.java)
                if (ref != null) cache[pid] = ref
                ref
            } catch (e: Exception) {
                Log.e("PlantDatabase", "Fehler beim Parsen der Pflanze pid=$pid", e)
                null
            }
        } else {
            null
        }
    }

    suspend fun searchPlants(query: String): List<PlantReference> = withContext(Dispatchers.IO) {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val json = performRequest("$SERVER_URL/search_plants?q=$encodedQuery")
        if (json != null) {
            try {
                val type = object : TypeToken<List<PlantReference>>() {}.type
                gson.fromJson(json, type) ?: emptyList()
            } catch (e: Exception) {
                Log.e("PlantDatabase", "Fehler beim Parsen der Suche", e)
                emptyList()
            }
        } else {
            emptyList()
        }
    }
}
