package com.example.plant_sensor

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import coil.load
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class PlantDetailActivity : AppCompatActivity() {

    private var historyData: SensorHistory? = null
    private lateinit var chart: HistoryChartView
    private lateinit var cardHistory: View
    private lateinit var textHistoryLabel: TextView
    private lateinit var imageHeader: ImageView

    private lateinit var textPlantName: TextView
    private lateinit var textPlantRoom: TextView
    private lateinit var textCareHints: TextView
    private lateinit var textLastWateredView: TextView

    private var plantId: Long = -1
    private var currentName: String = ""
    private var currentRoom: String = ""
    private var currentCareHints: String? = null
    private val rooms = mutableListOf("Living Room", "Kitchen", "Balcony")
    private var plantRef: PlantReference? = null

    private val SERVER_URL = "http://192.168.0.16:8080"
    private val gson = GsonBuilder().setLenient().create()

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            imageHeader.load(it) { crossfade(true) }
            if (plantId != -1L) {
                updatePlantImageOnServer(it.toString())
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_plant_detail)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener { finish() }

        // Initialize views
        imageHeader = findViewById(R.id.imagePlantHeader)
        cardHistory = findViewById(R.id.cardHistory)
        chart = findViewById(R.id.chartHistory)
        textHistoryLabel = findViewById(R.id.textHistoryLabel)
        textPlantName = findViewById(R.id.textPlantName)
        textPlantRoom = findViewById(R.id.textPlantRoom)
        textCareHints = findViewById(R.id.textCareHints)
        textLastWateredView = findViewById(R.id.textLastWatered)

        plantId = intent.getLongExtra("plantId", -1)
        currentName = intent.getStringExtra("name") ?: getString(R.string.placeholder_unknown)
        currentRoom = intent.getStringExtra("room") ?: ""
        currentCareHints = intent.getStringExtra("careHints")

        loadRoomsFromServer()

        val speciesId = intent.getStringExtra("speciesId")
        val lastWatered = intent.getStringExtra("lastWatered") ?: getString(R.string.placeholder_never)
        val isEncyclopedia = intent.getBooleanExtra("isEncyclopedia", false)
        val sensorMac = intent.getStringExtra("sensorMac")
        val imageUriString = intent.getStringExtra("imageUri")

        lifecycleScope.launch {
            val ref = PlantDatabase.getByPidFromServer(speciesId)
            plantRef = ref

            setupUI(currentName, currentRoom, lastWatered, isEncyclopedia, ref, imageUriString)
            updateRangeBars(null)

            if (!isEncyclopedia) {
                loadPlantDataFromServer()
                if (!sensorMac.isNullOrEmpty()) {
                    refreshData(sensorMac)
                }
            }
        }
    }

    private fun loadPlantDataFromServer() {
        if (plantId == -1L) return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val jsonResponse = fetchUrl("$SERVER_URL/plants")
                val type = object : TypeToken<List<Plant>>() {}.type
                val loadedPlants: List<Plant> = gson.fromJson(jsonResponse, type) ?: emptyList()
                val currentPlant = loadedPlants.find { it.id == plantId }

                withContext(Dispatchers.Main) {
                    currentPlant?.let {
                        currentCareHints = it.careHints
                        updateCareHintsDisplay()

                        currentName = it.name
                        currentRoom = it.room
                        textPlantName.text = it.name
                        textPlantRoom.text = it.room
                        textLastWateredView.text = it.lastWatered ?: getString(R.string.placeholder_never)
                    }
                }
            } catch (e: Exception) {
                Log.e("PlantSensor", "Error loading plant data", e)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val isEncyclopedia = intent.getBooleanExtra("isEncyclopedia", false)
        if (!isEncyclopedia) {
            menuInflater.inflate(R.menu.menu_plant_detail, menu)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_edit -> {
                showEditDialog()
                true
            }
            R.id.action_delete -> {
                showDeleteConfirmation()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showEditDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 10)
        }

        val editName = EditText(this).apply {
            hint = getString(R.string.hint_plant_name)
            setText(currentName)
        }

        val textRoomLabel = TextView(this).apply {
            text = getString(R.string.label_room_select)
            setPadding(0, 24, 0, 8)
        }

        val spinnerRoom = Spinner(this)
        val roomAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, rooms)
        roomAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerRoom.adapter = roomAdapter

        if (currentRoom.isNotEmpty() && !rooms.contains(currentRoom)) {
            rooms.add(currentRoom)
            roomAdapter.notifyDataSetChanged()
        }
        spinnerRoom.setSelection(rooms.indexOf(currentRoom))

        val btnAddRoom = Button(this, null, android.R.attr.borderlessButtonStyle).apply {
            text = getString(R.string.button_add_room)
            setOnClickListener {
                showAddRoomDialog { newRoom ->
                    if (!rooms.contains(newRoom)) {
                        rooms.add(newRoom)
                        roomAdapter.notifyDataSetChanged()
                    }
                    spinnerRoom.setSelection(rooms.indexOf(newRoom))
                }
            }
        }

        layout.addView(editName)
        layout.addView(textRoomLabel)
        layout.addView(spinnerRoom)
        layout.addView(btnAddRoom)

        AlertDialog.Builder(this)
            .setTitle("Pflanze bearbeiten")
            .setView(layout)
            .setPositiveButton(R.string.button_save) { _, _ ->
                val newName = editName.text.toString().trim()
                val newRoom = spinnerRoom.selectedItem?.toString() ?: ""
                if (newName.isNotEmpty() && newRoom.isNotEmpty()) {
                    updatePlantDetails(newName, newRoom)
                } else {
                    Toast.makeText(this, getString(R.string.toast_fill_all), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    private fun loadRoomsFromServer() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val jsonResponse = fetchUrl("$SERVER_URL/plants")
                val type = object : TypeToken<List<Plant>>() {}.type
                val loadedPlants: List<Plant> = gson.fromJson(jsonResponse, type) ?: emptyList()

                withContext(Dispatchers.Main) {
                    loadedPlants.forEach {
                        if (!rooms.contains(it.room)) rooms.add(it.room)
                    }
                    if (currentRoom.isNotEmpty() && !rooms.contains(currentRoom)) {
                        rooms.add(currentRoom)
                    }
                }
            } catch (e: Exception) {
                Log.e("PlantSensor", "Error loading rooms", e)
            }
        }
    }

    private fun showAddRoomDialog(onRoomAdded: (String) -> Unit) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_room, null)
        val editRoom = view.findViewById<TextInputEditText>(R.id.editRoomName)

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_new_room)
            .setView(view)
            .setPositiveButton(R.string.button_save) { _, _ ->
                val name = editRoom.text.toString().trim()
                if (name.isNotEmpty()) onRoomAdded(name)
            }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    private fun updatePlantDetails(newName: String, newRoom: String) {
        if (plantId == -1L) return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("id", plantId)
                    put("plant_name", newName)
                    put("room", newRoom)
                }
                postUrl("$SERVER_URL/update_plant", json)
                withContext(Dispatchers.Main) {
                    currentName = newName
                    currentRoom = newRoom
                    textPlantName.text = newName
                    textPlantRoom.text = newRoom
                    Toast.makeText(this@PlantDetailActivity, "Änderungen gespeichert", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PlantDetailActivity, "Fehler beim Speichern: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showDeleteConfirmation() {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_delete_plant_title)
            .setMessage(getString(R.string.dialog_delete_plant_message, currentName))
            .setPositiveButton(R.string.button_delete) { _, _ ->
                deletePlantFromServer()
            }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    private fun deletePlantFromServer() {
        if (plantId == -1L) return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject().apply { put("id", plantId) }
                postUrl("$SERVER_URL/delete_plant", json)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PlantDetailActivity, getString(R.string.toast_plant_deleted), Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PlantDetailActivity, getString(R.string.toast_error_deleting, e.localizedMessage), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setupUI(name: String, room: String, lastWatered: String, isEncyclopedia: Boolean, ref: PlantReference?, imageUriString: String?) {
        val toggleGroup = findViewById<com.google.android.material.button.MaterialButtonToggleGroup>(R.id.toggleGroup)
        val layoutData = findViewById<LinearLayout>(R.id.layoutData)
        val layoutInfo = findViewById<LinearLayout>(R.id.layoutInfo)
        val cardLastWatered = findViewById<View>(R.id.cardLastWatered)
        val btnEditImage = findViewById<View>(R.id.buttonEditImage)
        val btnEditNotes = findViewById<ImageButton>(R.id.buttonEditCareHints)

        textPlantName.text = name
        textPlantRoom.text = if (isEncyclopedia) getString(R.string.label_encyclopedia_entry) else room
        textLastWateredView.text = lastWatered

        if (isEncyclopedia) {
            cardLastWatered.visibility = View.GONE
            toggleGroup.visibility = View.GONE
            layoutData.visibility = View.GONE
            layoutInfo.visibility = View.VISIBLE
            btnEditImage.visibility = View.GONE
            btnEditNotes.visibility = View.GONE
        } else {
            cardLastWatered.visibility = View.VISIBLE
            toggleGroup.visibility = View.VISIBLE
            layoutData.visibility = View.VISIBLE
            layoutInfo.visibility = View.GONE
            btnEditImage.visibility = View.VISIBLE
            btnEditNotes.visibility = View.VISIBLE
            btnEditNotes.setOnClickListener { showEditNotesDialog() }
        }

        if (!imageUriString.isNullOrEmpty()) {
            imageHeader.load(Uri.parse(imageUriString)) {
                crossfade(true)
                placeholder(R.drawable.ic_plant_placeholder)
                error(R.drawable.ic_plant_placeholder)
            }
        } else {
            imageHeader.load(ref?.image?.replace("%d", "800")) {
                crossfade(true)
                placeholder(R.drawable.ic_plant_placeholder)
                error(R.drawable.ic_plant_placeholder)
            }
        }

        setFact(R.id.cardSpecies, getString(R.string.label_species), ref?.displayPid)
        setFact(R.id.cardCategory, getString(R.string.label_category), ref?.category)
        setFact(R.id.cardOrigin, getString(R.string.label_origin), ref?.origin)

        setMaintenanceText(R.id.textInfoSunlight, ref?.sunlight, getString(R.string.label_sunlight))
        setMaintenanceText(R.id.textInfoWatering, ref?.watering, getString(R.string.label_watering))
        setMaintenanceText(R.id.textInfoFertilizer, ref?.fertilization, getString(R.string.label_fertilizer))

        updateCareHintsDisplay()

        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                layoutData.visibility = if (checkedId == R.id.btnData) View.VISIBLE else View.GONE
                layoutInfo.visibility = if (checkedId == R.id.btnInfo) View.VISIBLE else View.GONE
                if (checkedId == R.id.btnInfo) cardHistory.visibility = View.GONE
            }
        }

        findViewById<Button>(R.id.buttonWaterNow).setOnClickListener {
            val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
            textLastWateredView.text = now
            if (plantId != -1L) updateLastWateredOnServer(now)
            Toast.makeText(this, getString(R.string.toast_watered, currentName), Toast.LENGTH_SHORT).show()
        }

        btnEditImage.setOnClickListener { pickImageLauncher.launch("image/*") }
        setupRangeClickListeners()
    }

    private fun updateCareHintsDisplay() {
        textCareHints.text = if (currentCareHints.isNullOrEmpty() || currentCareHints == "null") {
            getString(R.string.placeholder_no_hints)
        } else {
            currentCareHints
        }
    }

    private fun showEditNotesDialog() {
        val currentHints = if (currentCareHints.isNullOrEmpty() || currentCareHints == "null") "" else currentCareHints!!
        val editText = EditText(this).apply {
            setText(currentHints)
            hint = getString(R.string.hint_notes)
            gravity = android.view.Gravity.TOP
            minLines = 3
        }

        val container = FrameLayout(this)
        val params = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(48, 24, 48, 24)
        }
        editText.layoutParams = params
        container.addView(editText)

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_edit_notes_title)
            .setView(container)
            .setPositiveButton(R.string.button_save) { _, _ ->
                val newHints = editText.text.toString().trim()
                currentCareHints = newHints
                updateCareHintsDisplay()
                if (plantId != -1L) updateCareHintsOnServer(newHints)
            }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    private fun updateCareHintsOnServer(hints: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("id", plantId)
                    put("care_hints", hints)
                }
                postUrl("$SERVER_URL/update_plant", json)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PlantDetailActivity, getString(R.string.toast_notes_updated), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PlantDetailActivity, getString(R.string.toast_error_updating_notes), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateLastWateredOnServer(date: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("id", plantId)
                    put("last_watered", date)
                }
                postUrl("$SERVER_URL/update_plant", json)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PlantDetailActivity, getString(R.string.toast_error_updating_watered), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updatePlantImageOnServer(uri: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("id", plantId)
                    put("image_uri", uri)
                }
                postUrl("$SERVER_URL/update_plant", json)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PlantDetailActivity, getString(R.string.toast_image_updated), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PlantDetailActivity, getString(R.string.toast_error_updating_image), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupRangeClickListeners() {
        findViewById<View>(R.id.rangeSoilMoisture).setOnClickListener { showChart("Moisture") }
        findViewById<View>(R.id.rangeTemperature).setOnClickListener { showChart("Temperature") }
        findViewById<View>(R.id.rangeLight).setOnClickListener { showChart("Light") }
        findViewById<View>(R.id.rangeConductivity).setOnClickListener { showChart("Conductivity") }
    }

    private fun refreshData(mac: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val latestJson = fetchUrl("$SERVER_URL/sensor?mac=$mac")
                val latest = JSONObject(latestJson)
                val historyJson = fetchUrl("$SERVER_URL/history?mac=$mac")
                val type = object : TypeToken<List<HistoryRawPoint>>() {}.type
                val rawPoints: List<HistoryRawPoint> = gson.fromJson(historyJson, type) ?: emptyList()
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val mPoints = mutableListOf<HistoryPoint>()
                val tPoints = mutableListOf<HistoryPoint>()
                val lPoints = mutableListOf<HistoryPoint>()
                val cPoints = mutableListOf<HistoryPoint>()

                for (p in rawPoints) {
                    val date = p.timestamp?.let { sdf.parse(it) } ?: continue
                    mPoints.add(HistoryPoint(date.time, p.moisture))
                    tPoints.add(HistoryPoint(date.time, p.temp))
                    lPoints.add(HistoryPoint(date.time, p.light))
                    cPoints.add(HistoryPoint(date.time, p.conductivity))
                }
                mPoints.sortBy { it.timestamp }; tPoints.sortBy { it.timestamp }; lPoints.sortBy { it.timestamp }; cPoints.sortBy { it.timestamp }
                historyData = SensorHistory(tPoints, mPoints, lPoints, cPoints)
                withContext(Dispatchers.Main) { updateRangeBars(latest) }
            } catch (e: Exception) { Log.e("PlantSensor", "Error refreshing data", e) }
        }
    }

    private fun updateRangeBars(data: JSONObject?) {
        val ref = plantRef
        val currentT = data?.optDouble("temp", 0.0)?.toFloat() ?: 0f
        findViewById<SensorRangeView>(R.id.rangeTemperature).setData(getString(R.string.label_history_temp), currentT, ref?.minTemp?.toFloat() ?: 18f, ref?.maxTemp?.toFloat() ?: 28f, 50f, getString(R.string.unit_temp))
        val currentM = data?.optDouble("moisture", 0.0)?.toFloat() ?: 0f
        findViewById<SensorRangeView>(R.id.rangeSoilMoisture).setData(getString(R.string.label_history_moisture), currentM, ref?.minSoilMoist?.toFloat() ?: 20f, ref?.maxSoilMoist?.toFloat() ?: 60f, 100f, getString(R.string.unit_moisture))
        val currentL = data?.optDouble("light", 0.0)?.toFloat() ?: 0f
        findViewById<SensorRangeView>(R.id.rangeLight).setData(getString(R.string.label_history_light), currentL, ref?.minLightLux?.toFloat() ?: 500f, ref?.maxLightLux?.toFloat() ?: 5000f, 20000f, getString(R.string.unit_light))
        val currentC = data?.optDouble("conductivity", 0.0)?.toFloat() ?: 0f
        findViewById<SensorRangeView>(R.id.rangeConductivity).setData(getString(R.string.label_history_conductivity), currentC, ref?.minSoilEc?.toFloat() ?: 300f, ref?.maxSoilEc?.toFloat() ?: 1500f, 3000f, getString(R.string.unit_conductivity))
    }

    private fun showChart(type: String) {
        val data = historyData ?: return
        cardHistory.visibility = View.VISIBLE
        when (type) {
            "Moisture" -> { textHistoryLabel.text = getString(R.string.label_history_moisture); chart.setData(data.moisture, getString(R.string.label_history_moisture) + " " + getString(R.string.unit_moisture), Color.parseColor("#2196F3")) }
            "Temperature" -> { textHistoryLabel.text = getString(R.string.label_history_temp); chart.setData(data.temp, getString(R.string.label_history_temp) + " " + getString(R.string.unit_temp), Color.parseColor("#FF5722")) }
            "Light" -> { textHistoryLabel.text = getString(R.string.label_history_light); chart.setData(data.light, getString(R.string.label_history_light) + " " + getString(R.string.unit_light), Color.parseColor("#FFC107")) }
            "Conductivity" -> { textHistoryLabel.text = getString(R.string.label_history_conductivity); chart.setData(data.conductivity, getString(R.string.label_history_conductivity) + " " + getString(R.string.unit_conductivity), Color.parseColor("#9C27B0")) }
        }
        findViewById<NestedScrollView>(R.id.nestedScrollView).post { findViewById<NestedScrollView>(R.id.nestedScrollView).smoothScrollTo(0, cardHistory.top) }
    }

    private fun fetchUrl(urlString: String): String {
        val url = URL(urlString); val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 8000; conn.readTimeout = 8000; conn.setRequestProperty("Connection", "close")
        return try {
            if (conn.responseCode in 200..299) conn.inputStream.bufferedReader().use { it.readText() }
            else throw Exception("HTTP ${conn.responseCode}")
        } finally { conn.disconnect() }
    }

    private fun postUrl(urlString: String, json: JSONObject) {
        val url = URL(urlString); val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"; conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.doOutput = true
        try {
            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(json.toString()); it.flush() }
            if (conn.responseCode !in 200..299) throw Exception("HTTP ${conn.responseCode}")
        } finally { conn.disconnect() }
    }

    private fun setFact(cardId: Int, label: String, value: String?) {
        val card = findViewById<View>(cardId) ?: return
        if (value.isNullOrEmpty() || value == "null") card.visibility = View.GONE
        else { card.visibility = View.VISIBLE; card.findViewById<TextView>(R.id.textFactLabel).text = label; card.findViewById<TextView>(R.id.textFactValue).text = value }
    }

    private fun setMaintenanceText(viewId: Int, text: String?, label: String) {
        val view = findViewById<TextView>(viewId) ?: return
        if (text.isNullOrEmpty() || text == "n/a") view.visibility = View.GONE
        else { view.visibility = View.VISIBLE; view.text = getString(R.string.template_maintenance, label, text) }
    }

    data class SensorHistory(val temp: List<HistoryPoint>, val moisture: List<HistoryPoint>, val light: List<HistoryPoint>, val conductivity: List<HistoryPoint>)
    private data class HistoryRawPoint(val timestamp: String?, val temp: Float = 0f, val moisture: Float = 0f, val light: Float = 0f, val conductivity: Float = 0f)
}
