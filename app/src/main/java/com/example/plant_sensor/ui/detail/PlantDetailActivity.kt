package com.example.plant_sensor.ui.detail

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.plant_sensor.R
import com.example.plant_sensor.data.model.HistoryPoint
import com.example.plant_sensor.data.model.Plant
import com.example.plant_sensor.data.model.PlantReference
import com.example.plant_sensor.data.remote.PlantDatabase
import com.example.plant_sensor.ui.customviews.HistoryChartView
import com.example.plant_sensor.ui.customviews.SensorRangeView
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
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
import com.example.plant_sensor.BuildConfig

/**
 * Shows details for one plant.
 */
class PlantDetailActivity : AppCompatActivity() {

    private var historyData: SensorHistory? = null
    private var plantRef: PlantReference? = null

    private var plantId: Long = INVALID_ID
    private var currentName: String = ""
    private var currentRoom: String = ""
    private var currentCareHints: String? = null

    private val rooms = mutableListOf("Living Room", "Kitchen", "Balcony")
    private val gson = GsonBuilder().create()

    private lateinit var imageHeader: ImageView
    private lateinit var chart: HistoryChartView
    private lateinit var cardHistory: View
    private lateinit var textHistoryLabel: TextView

    private lateinit var textPlantName: TextView
    private lateinit var textPlantRoom: TextView
    private lateinit var textCareHints: TextView
    private lateinit var textLastWatered: TextView

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        handleSelectedImage(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_plant_detail)

        setupToolbar()
        bindViews()
        readIntentValues()
        loadRoomsFromServer()
        loadPlantReferenceAndSetupUi()
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

    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun bindViews() {
        imageHeader = findViewById(R.id.imagePlantHeader)
        cardHistory = findViewById(R.id.cardHistory)
        chart = findViewById(R.id.chartHistory)
        textHistoryLabel = findViewById(R.id.textHistoryLabel)
        textPlantName = findViewById(R.id.textPlantName)
        textPlantRoom = findViewById(R.id.textPlantRoom)
        textCareHints = findViewById(R.id.textCareHints)
        textLastWatered = findViewById(R.id.textLastWatered)
    }

    private fun readIntentValues() {
        plantId = intent.getLongExtra("plantId", INVALID_ID)
        currentName = intent.getStringExtra("name") ?: getString(R.string.placeholder_unknown)
        currentRoom = intent.getStringExtra("room") ?: ""
        currentCareHints = intent.getStringExtra("careHints")
    }

    private fun loadPlantReferenceAndSetupUi() {
        val speciesId = intent.getStringExtra("speciesId")
        val lastWatered = intent.getStringExtra("lastWatered") ?: getString(R.string.placeholder_never)
        val isEncyclopedia = intent.getBooleanExtra("isEncyclopedia", false)
        val sensorMac = intent.getStringExtra("sensorMac")
        val imageUriString = intent.getStringExtra("imageUri")

        lifecycleScope.launch {
            plantRef = PlantDatabase.getByPidFromServer(speciesId)
            setupUi(currentName, currentRoom, lastWatered, isEncyclopedia, imageUriString)
            updateRangeBars(null)
            if (!isEncyclopedia) {
                loadPlantDataFromServer()
                if (!sensorMac.isNullOrEmpty()) refreshData(sensorMac)
            }
        }
    }

    private fun loadPlantDataFromServer() {
        if (plantId == INVALID_ID) return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val jsonResponse = fetchUrl("$SERVER_URL/plants")
                val type = object : TypeToken<List<Plant>>() {}.type
                val loadedPlants: List<Plant> = gson.fromJson(jsonResponse, type) ?: emptyList()
                val currentPlant = loadedPlants.find { it.id == plantId }

                withContext(Dispatchers.Main) {
                    currentPlant?.let { plant ->
                        currentName = plant.name
                        currentRoom = plant.room
                        currentCareHints = plant.careHints
                        textPlantName.text = plant.name
                        textPlantRoom.text = plant.room
                        textLastWatered.text = plant.lastWatered ?: getString(R.string.placeholder_never)
                        updateCareHintsDisplay()
                    }
                }
            } catch (exception: Exception) {
                Log.e(LOG_TAG, "Error loading plant data", exception)
            }
        }
    }

    private fun setupUi(name: String, room: String, lastWatered: String, isEncyclopedia: Boolean, imageUriString: String?) {
        val toggleGroup = findViewById<MaterialButtonToggleGroup>(R.id.toggleGroup)
        val layoutData = findViewById<LinearLayout>(R.id.layoutData)
        val layoutInfo = findViewById<LinearLayout>(R.id.layoutInfo)
        val cardLastWatered = findViewById<View>(R.id.cardLastWatered)
        val editImageButton = findViewById<View>(R.id.buttonEditImage)
        val editNotesButton = findViewById<ImageButton>(R.id.buttonEditCareHints)

        textPlantName.text = name
        textPlantRoom.text = if (isEncyclopedia) getString(R.string.label_encyclopedia_entry) else room
        textLastWatered.text = lastWatered

        setupModeVisibility(isEncyclopedia, toggleGroup, layoutData, layoutInfo, cardLastWatered, editImageButton, editNotesButton)
        loadHeaderImage(imageUriString)
        showBotanicalFacts()
        showCareGuide()
        updateCareHintsDisplay()
        setupToggleGroup(toggleGroup, layoutData, layoutInfo)
        setupButtons(editImageButton)
        setupRangeClickListeners()
    }

    private fun setupModeVisibility(isEncyclopedia: Boolean, toggleGroup: MaterialButtonToggleGroup, layoutData: LinearLayout, layoutInfo: LinearLayout, cardLastWatered: View, editImageButton: View, editNotesButton: ImageButton) {
        if (isEncyclopedia) {
            cardLastWatered.visibility = View.GONE
            toggleGroup.visibility = View.GONE
            layoutData.visibility = View.GONE
            layoutInfo.visibility = View.VISIBLE
            editImageButton.visibility = View.GONE
            editNotesButton.visibility = View.GONE
        } else {
            cardLastWatered.visibility = View.VISIBLE
            toggleGroup.visibility = View.VISIBLE
            layoutData.visibility = View.VISIBLE
            layoutInfo.visibility = View.GONE
            editImageButton.visibility = View.VISIBLE
            editNotesButton.visibility = View.VISIBLE
            editNotesButton.setOnClickListener { showEditNotesDialog() }
        }
    }

    private fun loadHeaderImage(imageUriString: String?) {
        if (!imageUriString.isNullOrEmpty()) {
            imageHeader.load(imageUriString.toUri()) {
                crossfade(true)
                placeholder(R.drawable.ic_plant_placeholder)
                error(R.drawable.ic_plant_placeholder)
            }
        } else {
            imageHeader.load(plantRef?.image?.replace("%d", "800")) {
                crossfade(true)
                placeholder(R.drawable.ic_plant_placeholder)
                error(R.drawable.ic_plant_placeholder)
            }
        }
    }

    private fun showBotanicalFacts() {
        setFact(R.id.cardSpecies, getString(R.string.label_species), getReferenceText("displayPid"))
        setFact(R.id.cardCategory, getString(R.string.label_category), getReferenceText("category"))
        setFact(R.id.cardOrigin, getString(R.string.label_origin), getReferenceText("origin"))
        setFact(R.id.cardColor, getString(R.string.label_color), getReferenceText("color"))
    }

    private fun showCareGuide() {
        val reference = plantRef
        setMaintenanceText(R.id.textInfoSunlight, reference?.sunlight ?: intent.getStringExtra("sunlight"), getString(R.string.label_sunlight))
        setMaintenanceText(R.id.textInfoWatering, reference?.watering ?: intent.getStringExtra("watering"), getString(R.string.label_watering))
        setMaintenanceText(R.id.textInfoFertilizer, reference?.fertilization ?: intent.getStringExtra("fertilization"), getString(R.string.label_fertilizer))
    }

    private fun getReferenceText(fieldName: String): String? {
        return when (fieldName) {
            "displayPid" -> plantRef?.displayPid ?: intent.getStringExtra("displayPid")
            "category" -> plantRef?.category ?: intent.getStringExtra("category")
            "origin" -> plantRef?.origin ?: intent.getStringExtra("origin")
            "color" -> plantRef?.color ?: intent.getStringExtra("color")
            else -> null
        }
    }

    private fun setupToggleGroup(toggleGroup: MaterialButtonToggleGroup, layoutData: LinearLayout, layoutInfo: LinearLayout) {
        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            layoutData.visibility = if (checkedId == R.id.btnData) View.VISIBLE else View.GONE
            layoutInfo.visibility = if (checkedId == R.id.btnInfo) View.VISIBLE else View.GONE
            if (checkedId == R.id.btnInfo) cardHistory.visibility = View.GONE
        }
    }

    private fun setupButtons(editImageButton: View) {
        findViewById<Button>(R.id.buttonWaterNow).setOnClickListener {
            val now = SimpleDateFormat(DATE_FORMAT, Locale.getDefault()).format(Date())
            textLastWatered.text = now
            if (plantId != INVALID_ID) updateLastWateredOnServer(now)
            Toast.makeText(this, getString(R.string.toast_watered, currentName), Toast.LENGTH_SHORT).show()
        }
        editImageButton.setOnClickListener { pickImageLauncher.launch("image/*") }
    }

    private fun updateCareHintsDisplay() {
        textCareHints.text = if (isEmptyValue(currentCareHints)) getString(R.string.placeholder_no_hints) else currentCareHints
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
        val roomLabel = TextView(this).apply {
            text = getString(R.string.label_room_select)
            setPadding(0, 24, 0, 8)
        }
        val roomSpinner = Spinner(this)
        val roomAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, rooms).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        roomSpinner.adapter = roomAdapter
        addCurrentRoomIfMissing(roomAdapter)
        roomSpinner.setSelection(rooms.indexOf(currentRoom))

        val addRoomButton = Button(this, null, android.R.attr.borderlessButtonStyle).apply {
            text = getString(R.string.button_add_room)
            setOnClickListener {
                showAddRoomDialog { newRoom ->
                    if (!rooms.contains(newRoom)) {
                        rooms.add(newRoom)
                        roomAdapter.notifyDataSetChanged()
                    }
                    roomSpinner.setSelection(rooms.indexOf(newRoom))
                }
            }
        }
        layout.addView(editName); layout.addView(roomLabel); layout.addView(roomSpinner); layout.addView(addRoomButton)

        AlertDialog.Builder(this)
            .setTitle("Pflanze bearbeiten")
            .setView(layout)
            .setPositiveButton(R.string.button_save) { _, _ ->
                val newName = editName.text.toString().trim()
                val newRoom = roomSpinner.selectedItem?.toString() ?: ""
                if (newName.isNotEmpty() && newRoom.isNotEmpty()) updatePlantDetails(newName, newRoom)
            }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    private fun addCurrentRoomIfMissing(roomAdapter: ArrayAdapter<String>) {
        if (currentRoom.isNotEmpty() && !rooms.contains(currentRoom)) {
            rooms.add(currentRoom)
            roomAdapter.notifyDataSetChanged()
        }
    }

    private fun loadRoomsFromServer() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val jsonResponse = fetchUrl("$SERVER_URL/plants")
                val type = object : TypeToken<List<Plant>>() {}.type
                val loadedPlants: List<Plant> = gson.fromJson(jsonResponse, type) ?: emptyList()
                withContext(Dispatchers.Main) {
                    loadedPlants.forEach { if (!rooms.contains(it.room)) rooms.add(it.room) }
                    if (currentRoom.isNotEmpty() && !rooms.contains(currentRoom)) rooms.add(currentRoom)
                }
            } catch (exception: Exception) {
                Log.e(LOG_TAG, "Error loading rooms", exception)
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
                val roomName = editRoom.text.toString().trim()
                if (roomName.isNotEmpty()) onRoomAdded(roomName)
            }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    private fun updatePlantDetails(newName: String, newRoom: String) {
        if (plantId == INVALID_ID) return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject().apply { put("id", plantId); put("plant_name", newName); put("room", newRoom) }
                postUrl("$SERVER_URL/update_plant", json)
                withContext(Dispatchers.Main) {
                    currentName = newName; currentRoom = newRoom; textPlantName.text = newName; textPlantRoom.text = newRoom
                    Toast.makeText(this@PlantDetailActivity, "Änderungen gespeichert", Toast.LENGTH_SHORT).show()
                }
            } catch (exception: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(this@PlantDetailActivity, "Fehler: ${exception.localizedMessage}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun showDeleteConfirmation() {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_delete_plant_title)
            .setMessage(getString(R.string.dialog_delete_plant_message, currentName))
            .setPositiveButton(R.string.button_delete) { _, _ -> deletePlantFromServer() }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    private fun deletePlantFromServer() {
        if (plantId == INVALID_ID) return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject().apply { put("id", plantId) }
                postUrl("$SERVER_URL/delete_plant", json)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PlantDetailActivity, getString(R.string.toast_plant_deleted), Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (exception: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(this@PlantDetailActivity, "Fehler: ${exception.localizedMessage}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun showEditNotesDialog() {
        val editText = EditText(this).apply { setText(currentCareHints ?: ""); hint = getString(R.string.hint_notes); gravity = Gravity.TOP; minLines = 3 }
        val container = FrameLayout(this).apply { setPadding(48, 24, 48, 24); addView(editText) }
        AlertDialog.Builder(this).setTitle(R.string.dialog_edit_notes_title).setView(container)
            .setPositiveButton(R.string.button_save) { _, _ ->
                val newHints = editText.text.toString().trim()
                currentCareHints = newHints
                updateCareHintsDisplay()
                if (plantId != INVALID_ID) updateCareHintsOnServer(newHints)
            }
            .setNegativeButton(R.string.button_cancel, null).show()
    }

    private fun updateCareHintsOnServer(hints: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject().apply { put("id", plantId); put("care_hints", hints) }
                postUrl("$SERVER_URL/update_plant", json)
            } catch (exception: Exception) {
                Log.e(LOG_TAG, "Error updating care hints", exception)
            }
        }
    }

    private fun updateLastWateredOnServer(date: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject().apply { put("id", plantId); put("last_watered", date) }
                postUrl("$SERVER_URL/update_plant", json)
            } catch (exception: Exception) {
                Log.e(LOG_TAG, "Error updating last watered", exception)
            }
        }
    }

    private fun handleSelectedImage(uri: Uri?) {
        if (uri == null) return
        try { applicationContext.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {}
        imageHeader.load(uri) { crossfade(true) }
        if (plantId != INVALID_ID) updatePlantImageOnServer(uri.toString())
    }

    private fun updatePlantImageOnServer(uri: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject().apply { put("id", plantId); put("image_uri", uri) }
                postUrl("$SERVER_URL/update_plant", json)
            } catch (exception: Exception) {
                Log.e(LOG_TAG, "Error updating image", exception)
            }
        }
    }

    private fun setupRangeClickListeners() {
        findViewById<View>(R.id.rangeSoilMoisture).setOnClickListener { showChart(CHART_MOISTURE) }
        findViewById<View>(R.id.rangeTemperature).setOnClickListener { showChart(CHART_TEMPERATURE) }
        findViewById<View>(R.id.rangeLight).setOnClickListener { showChart(CHART_LIGHT) }
        findViewById<View>(R.id.rangeConductivity).setOnClickListener { showChart(CHART_CONDUCTIVITY) }
    }

    private fun refreshData(mac: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val latestJson = fetchUrl("$SERVER_URL/sensor?mac=$mac")
                val historyJson = fetchUrl("$SERVER_URL/history?mac=$mac")
                val type = object : TypeToken<List<HistoryRawPoint>>() {}.type
                val rawPoints: List<HistoryRawPoint> = gson.fromJson(historyJson, type) ?: emptyList()
                historyData = parseHistoryPoints(rawPoints)
                withContext(Dispatchers.Main) { updateRangeBars(JSONObject(latestJson)) }
            } catch (exception: Exception) {
                Log.e(LOG_TAG, "Error refreshing data", exception)
            }
        }
    }

    private fun parseHistoryPoints(rawPoints: List<HistoryRawPoint>): SensorHistory {
        val dateFormat = SimpleDateFormat(SERVER_DATE_FORMAT, Locale.getDefault())
        val moisture = mutableListOf<HistoryPoint>()
        val temperature = mutableListOf<HistoryPoint>()
        val light = mutableListOf<HistoryPoint>()
        val conductivity = mutableListOf<HistoryPoint>()
        rawPoints.forEach { point ->
            val date = point.timestamp?.let { dateFormat.parse(it) } ?: return@forEach
            moisture.add(HistoryPoint(date.time, point.moisture))
            temperature.add(HistoryPoint(date.time, point.temp))
            light.add(HistoryPoint(date.time, point.light))
            conductivity.add(HistoryPoint(date.time, point.conductivity))
        }
        return SensorHistory(temperature.sortedBy { it.timestamp }, moisture.sortedBy { it.timestamp }, light.sortedBy { it.timestamp }, conductivity.sortedBy { it.timestamp })
    }

    private fun updateRangeBars(data: JSONObject?) {
        val reference = plantRef
        val temp = data?.optDouble("temp", 0.0)?.toFloat() ?: 0f
        val moist = data?.optDouble("moisture", 0.0)?.toFloat() ?: 0f
        val light = data?.optDouble("light", 0.0)?.toFloat() ?: 0f
        val cond = data?.optDouble("conductivity", 0.0)?.toFloat() ?: 0f

        findViewById<SensorRangeView>(R.id.rangeTemperature).setData(getString(R.string.label_history_temp), temp, reference?.minTemp?.toFloat() ?: DEFAULT_MIN_TEMP, reference?.maxTemp?.toFloat() ?: DEFAULT_MAX_TEMP, TEMP_SCALE_MAX, getString(R.string.unit_temp))
        findViewById<SensorRangeView>(R.id.rangeSoilMoisture).setData(getString(R.string.label_history_moisture), moist, reference?.minSoilMoist?.toFloat() ?: DEFAULT_MIN_MOISTURE, reference?.maxSoilMoist?.toFloat() ?: DEFAULT_MAX_MOISTURE, MOISTURE_SCALE_MAX, getString(R.string.unit_moisture))
        findViewById<SensorRangeView>(R.id.rangeLight).setData(getString(R.string.label_history_light), light, reference?.minLightLux?.toFloat() ?: DEFAULT_MIN_LIGHT, reference?.maxLightLux?.toFloat() ?: DEFAULT_MAX_LIGHT, LIGHT_SCALE_MAX, getString(R.string.unit_light))
        findViewById<SensorRangeView>(R.id.rangeConductivity).setData(getString(R.string.label_history_conductivity), cond, reference?.minSoilEc?.toFloat() ?: DEFAULT_MIN_CONDUCTIVITY, reference?.maxSoilEc?.toFloat() ?: DEFAULT_MAX_CONDUCTIVITY, CONDUCTIVITY_SCALE_MAX, getString(R.string.unit_conductivity))
    }

    private fun showChart(type: String) {
        val data = historyData ?: return
        cardHistory.visibility = View.VISIBLE
        when (type) {
            CHART_MOISTURE -> showChartData(data.moisture, getString(R.string.label_history_moisture), getString(R.string.unit_moisture), "#2196F3".toColorInt())
            CHART_TEMPERATURE -> showChartData(data.temp, getString(R.string.label_history_temp), getString(R.string.unit_temp), "#FF5722".toColorInt())
            CHART_LIGHT -> showChartData(data.light, getString(R.string.label_history_light), getString(R.string.unit_light), "#FFC107".toColorInt())
            CHART_CONDUCTIVITY -> showChartData(data.conductivity, getString(R.string.label_history_conductivity), getString(R.string.unit_conductivity), "#9C27B0".toColorInt())
        }
        val scrollView = findViewById<NestedScrollView>(R.id.nestedScrollView)
        scrollView.post { scrollView.smoothScrollTo(0, cardHistory.top) }
    }

    private fun showChartData(points: List<HistoryPoint>, label: String, unit: String, color: Int) {
        textHistoryLabel.text = label
        chart.setData(points, "$label $unit", color)
    }

    private fun fetchUrl(urlString: String): String {
        val connection = openConnection(urlString)
        return try {
            if (connection.responseCode in HTTP_SUCCESS_RANGE) connection.inputStream.bufferedReader().use { it.readText() }
            else throw Exception("HTTP ${connection.responseCode}")
        } finally { connection.disconnect() }
    }

    private fun postUrl(urlString: String, json: JSONObject) {
        val connection = openConnection(urlString)
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.doOutput = true
        try { OutputStreamWriter(connection.outputStream, "UTF-8").use { it.write(json.toString()); it.flush() }
        } finally { connection.disconnect() }
    }

    private fun openConnection(urlString: String): HttpURLConnection {
        return (URL(urlString).openConnection() as HttpURLConnection).apply { connectTimeout = CONNECTION_TIMEOUT_MS; readTimeout = CONNECTION_TIMEOUT_MS; setRequestProperty("Connection", "close") }
    }

    private fun setFact(cardId: Int, label: String, value: String?) {
        val card = findViewById<View>(cardId) ?: return
        if (isEmptyValue(value)) card.visibility = View.GONE
        else { card.visibility = View.VISIBLE; card.findViewById<TextView>(R.id.textFactLabel).text = label; card.findViewById<TextView>(R.id.textFactValue).text = value }
    }

    private fun setMaintenanceText(viewId: Int, text: String?, label: String) {
        val view = findViewById<TextView>(viewId) ?: return
        if (isEmptyValue(text)) view.visibility = View.GONE
        else { view.visibility = View.VISIBLE; view.text = getString(R.string.template_maintenance, label, text) }
    }

    private fun isEmptyValue(value: String?): Boolean {
        val v = value?.trim() ?: return true
        return v.isEmpty() || v.equals("null", true) || v.equals("n/a", true)
    }

    data class SensorHistory(val temp: List<HistoryPoint>, val moisture: List<HistoryPoint>, val light: List<HistoryPoint>, val conductivity: List<HistoryPoint>)
    private data class HistoryRawPoint(val timestamp: String?, val temp: Float = 0f, val moisture: Float = 0f, val light: Float = 0f, val conductivity: Float = 0f)

    companion object {
        private const val LOG_TAG = "PlantDetailActivity"
        private const val SERVER_URL = BuildConfig.SERVER_URL
        private const val INVALID_ID = -1L
        private const val CONNECTION_TIMEOUT_MS = 8000
        private const val DATE_FORMAT = "yyyy-MM-dd HH:mm"
        private const val SERVER_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss"
        private const val CHART_MOISTURE = "Moisture"
        private const val CHART_TEMPERATURE = "Temperature"
        private const val CHART_LIGHT = "Light"
        private const val CHART_CONDUCTIVITY = "Conductivity"
        private const val DEFAULT_MIN_TEMP = 18f
        private const val DEFAULT_MAX_TEMP = 28f
        private const val TEMP_SCALE_MAX = 50f
        private const val DEFAULT_MIN_MOISTURE = 20f
        private const val DEFAULT_MAX_MOISTURE = 60f
        private const val MOISTURE_SCALE_MAX = 100f
        private const val DEFAULT_MIN_LIGHT = 500f
        private const val DEFAULT_MAX_LIGHT = 5000f
        private const val LIGHT_SCALE_MAX = 20000f
        private const val DEFAULT_MIN_CONDUCTIVITY = 300f
        private const val DEFAULT_MAX_CONDUCTIVITY = 1500f
        private const val CONDUCTIVITY_SCALE_MAX = 3000f
        private val HTTP_SUCCESS_RANGE = 200..299
    }
}
