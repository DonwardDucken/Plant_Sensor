package com.example.plant_sensor.ui.main

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.plant_sensor.R
import com.example.plant_sensor.data.model.Plant
import com.example.plant_sensor.data.model.PlantReference
import com.example.plant_sensor.data.remote.PlantDatabase
import com.example.plant_sensor.ui.detail.PlantDetailActivity
import com.example.plant_sensor.ui.encyclopedia.EncyclopediaActivity
import com.example.plant_sensor.ui.settings.SettingsActivity
import com.example.plant_sensor.util.SettingsManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.plant_sensor.BuildConfig
import com.example.plant_sensor.R.layout.dialog_add_plant

/**
 * Main screen of the plant sensor app.
 */
class MainActivity : AppCompatActivity() {

    private val rooms = mutableListOf<String>()
    private val myPlants = mutableListOf<Plant>()
    private val expandedRooms = mutableSetOf<String>()

    private val gson = GsonBuilder().setLenient().create()
    private lateinit var settingsManager: SettingsManager

    private lateinit var mainAdapter: MainAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var addPlantButton: View
    private lateinit var titleView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        settingsManager = SettingsManager(this)
        bindViews()
        setupRecyclerView()
        setupAddPlantButton()
        setupBottomNavigation()
        setupSettingsButton()
    }

    override fun onResume() {
        super.onResume()
        PlantDatabase.invalidate()
        PlantDatabase.loadIfNeeded(this) {
            loadPlantsFromServer()
        }
    }

    private fun bindViews() {
        recyclerView = findViewById(R.id.recyclerViewMain)
        bottomNavigation = findViewById(R.id.bottomNavigation)
        addPlantButton = findViewById(R.id.buttonAddPlant)
        titleView = findViewById(R.id.textMainTitle)
    }

    private fun setupSettingsButton() {
        findViewById<ImageButton>(R.id.buttonSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun setupRecyclerView() {
        mainAdapter = MainAdapter(
            onClick = { item ->
                when (item) {
                    is MainItem.Header -> toggleRoom(item.name)
                    is MainItem.PlantRow -> openPlantDetail(item.plant)
                }
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = mainAdapter
    }

    private fun setupAddPlantButton() {
        addPlantButton.setOnClickListener {
            PlantDatabase.loadIfNeeded(this) {
                showAddPlantDialog()
            }
        }
    }

    private fun setupBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener { item ->
            if (item.itemId == R.id.nav_encyclopedia) {
                openEncyclopedia()
            } else {
                updateUI()
            }
            true
        }
    }

    private fun openEncyclopedia() {
        startActivity(Intent(this, EncyclopediaActivity::class.java))
        bottomNavigation.selectedItemId = R.id.nav_my_plants
    }

    /**
     * Loads all plants from the backend and updates the user interface.
     */
    private fun loadPlantsFromServer() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val jsonResponse = fetchUrl("${settingsManager.getServerUrl()}/plants")
                val type = object : TypeToken<List<Plant>>() {}.type
                val loadedPlants: List<Plant> = gson.fromJson(jsonResponse, type) ?: emptyList()

                withContext(Dispatchers.Main) {
                    myPlants.clear()
                    myPlants.addAll(loadedPlants)
                    addNewRoomsFromPlants()
                    updateUI()
                }
            } catch (exception: Exception) {
                Log.e("MainActivity", "Error loading plants", exception)
                withContext(Dispatchers.Main) {
                    myPlants.clear()
                    updateUI()
                    Toast.makeText(this@MainActivity, "Server not reachable", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun addNewRoomsFromPlants() {
        myPlants.forEach { plant ->
            if (!rooms.contains(plant.room)) {
                rooms.add(plant.room)
                expandedRooms.add(plant.room)
            }
        }
    }

    private fun updateUI() {
        val items = mutableListOf<MainItem>()
        titleView.text = getString(R.string.nav_my_plants)
        addPlantButton.visibility = View.VISIBLE

        rooms.forEach { room ->
            val isExpanded = expandedRooms.contains(room)
            items.add(MainItem.Header(room, isExpanded))
            if (isExpanded) {
                val plantsInRoom = myPlants.filter { it.room == room }
                plantsInRoom.forEach { plant ->
                    val reference = PlantDatabase.getByPid(plant.speciesId)
                    items.add(MainItem.PlantRow(plant, reference))
                }
            }
        }
        mainAdapter.submitList(items)
    }

    /**
     * Opens the dialog for creating a new plant.
     */
    private fun showAddPlantDialog() {
        val view = LayoutInflater.from(this).inflate(dialog_add_plant, null)
        val editName = view.findViewById<TextInputEditText>(R.id.editPlantName)
        val editMac = view.findViewById<TextInputEditText>(R.id.editSensorMac)
        val spinnerRoom = view.findViewById<Spinner>(R.id.spinnerRoom)
        val editSpecies = view.findViewById<AutoCompleteTextView>(R.id.editSpecies)
        val addRoomButton = view.findViewById<View>(R.id.buttonAddRoom)
        val scanButton = view.findViewById<View>(R.id.buttonScanSensor)

        val roomAdapter = createRoomAdapter()
        spinnerRoom.adapter = roomAdapter
        val speciesAdapter = createSpeciesAdapter()
        editSpecies.setAdapter(speciesAdapter)
        
        // Show all options when the field is clicked
        editSpecies.setOnClickListener { editSpecies.showDropDown() }

        addRoomButton.setOnClickListener {
            showAddRoomDialog { newRoom ->
                rooms.add(newRoom)
                expandedRooms.add(newRoom)
                roomAdapter.notifyDataSetChanged()
                spinnerRoom.setSelection(rooms.indexOf(newRoom))
            }
        }

        scanButton.setOnClickListener {
            scanForBluetoothSensor { mac -> editMac.setText(mac) }
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_new_plant)
            .setView(view)
            .setPositiveButton(R.string.button_save) { _, _ ->
                val plantJson = createPlantJson(
                    name = editName.text.toString().trim(),
                    room = spinnerRoom.selectedItem?.toString() ?: "",
                    speciesName = editSpecies.text.toString().trim(),
                    sensorMac = editMac.text.toString().trim()
                )
                if (plantJson != null) savePlantToServer(plantJson)
            }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    private fun createRoomAdapter(): ArrayAdapter<String> {
        return ArrayAdapter(this, android.R.layout.simple_spinner_item, rooms).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }

    private fun createSpeciesAdapter(): ArrayAdapter<String> {
        val speciesList = PlantDatabase.plantReferences.mapNotNull { it.displayPid }
            .ifEmpty { listOf("abelia dielsii", "abelia chinensis") }
        return ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, speciesList)
    }

    private fun createPlantJson(name: String, room: String, speciesName: String, sensorMac: String): JSONObject? {
        val species = PlantDatabase.plantReferences.find { it.displayPid == speciesName }
        val speciesId = species?.pid ?: speciesName
        if (name.isEmpty() || speciesId.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_select_name_species), Toast.LENGTH_SHORT).show()
            return null
        }

        return JSONObject().apply {
            put("plant_name", name)
            put("room", room)
            put("species_id", speciesId)
            put("MAC", if (sensorMac.isNotEmpty()) sensorMac else JSONObject.NULL)
            put("last_watered", JSONObject.NULL)
            put("image_uri", JSONObject.NULL)
        }
    }

    /**
     * Sends a new plant to the backend.
     */
    private fun savePlantToServer(json: JSONObject) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.d("MainActivity", json.toString(2))
                postUrl("${settingsManager.getServerUrl()}/add_plant", json)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, getString(R.string.toast_plant_saved), Toast.LENGTH_SHORT).show()
                    loadPlantsFromServer()
                }
            } catch (exception: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, getString(R.string.toast_error_saving, exception.localizedMessage), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showDeleteConfirmation(plant: Plant) {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_delete_plant_title)
            .setMessage(getString(R.string.dialog_delete_plant_message, plant.name))
            .setPositiveButton(R.string.button_delete) { _, _ -> deletePlantFromServer(plant) }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    private fun deletePlantFromServer(plant: Plant) {
        val plantId = plant.id ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject().apply { put("id", plantId) }
                postUrl("${settingsManager.getServerUrl()}/delete_plant", json)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, getString(R.string.toast_plant_deleted), Toast.LENGTH_SHORT).show()
                    loadPlantsFromServer()
                }
            } catch (exception: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, getString(R.string.toast_error_deleting, exception.message), Toast.LENGTH_LONG).show()
                }
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

    private fun toggleRoom(name: String) {
        if (expandedRooms.contains(name)) expandedRooms.remove(name) else expandedRooms.add(name)
        updateUI()
    }

    private fun openPlantDetail(plant: Plant) {
        val intent = Intent(this, PlantDetailActivity::class.java).apply {
            putExtra("plantId", plant.id)
            putExtra("speciesId", plant.speciesId)
            putExtra("name", plant.name)
            putExtra("room", plant.room)
            putExtra("lastWatered", plant.lastWatered)
            putExtra("careHints", plant.careHints)
            putExtra("isEncyclopedia", false)
            putExtra("sensorMac", plant.sensorMac)
            putExtra("imageUri", plant.imageUri)
        }
        startActivity(intent)
    }

    private fun scanForBluetoothSensor(onFound: (String) -> Unit) {
        if (!hasBluetoothPermissions()) {
            requestBluetoothPermissions()
            return
        }
        val bluetoothAdapter = getBluetoothAdapter()
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Toast.makeText(this, "Bluetooth is not activated", Toast.LENGTH_SHORT).show()
            return
        }
        val scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            Toast.makeText(this, "Bluetooth Scanner not available", Toast.LENGTH_SHORT).show()
            return
        }
        val discoveredDevices = mutableSetOf<String>()
        val deviceLabels = mutableListOf<String>()
        val listAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, deviceLabels)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Searching for sensors...")
            .setAdapter(listAdapter) { _, which ->
                val selected = deviceLabels[which]
                val mac = selected.substringAfterLast("\n")
                onFound(mac)
            }
            .setNegativeButton("Cancel", null)
            .create()
        val callback = createScanCallback(discoveredDevices, deviceLabels, listAdapter)
        dialog.setOnDismissListener { stopBluetoothScan(scanner, callback) }
        dialog.show()
        startBluetoothScan(scanner, callback, dialog)
        stopScanAfterTimeout(scanner, callback, dialog, discoveredDevices)
    }

    private fun hasBluetoothPermissions(): Boolean {
        return getRequiredBluetoothPermissions().all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestBluetoothPermissions() {
        ActivityCompat.requestPermissions(this, getRequiredBluetoothPermissions(), REQUEST_BLUETOOTH_PERMISSIONS)
        Toast.makeText(this, "Please allow Bluetooth permissions", Toast.LENGTH_SHORT).show()
    }

    private fun getRequiredBluetoothPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun getBluetoothAdapter(): BluetoothAdapter? {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        return bluetoothManager.adapter
    }

    private fun createScanCallback(discoveredDevices: MutableSet<String>, deviceLabels: MutableList<String>, listAdapter: ArrayAdapter<String>): ScanCallback {
        return object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val mac = device.address
                val name = device.name ?: result.scanRecord?.deviceName ?: "Unknown sensor"
                if (discoveredDevices.add(mac)) {
                    runOnUiThread {
                        deviceLabels.add("$name\n$mac")
                        listAdapter.notifyDataSetChanged()
                    }
                }
            }
        }
    }

    private fun startBluetoothScan(scanner: android.bluetooth.le.BluetoothLeScanner, callback: ScanCallback, dialog: AlertDialog) {
        try { scanner.startScan(callback) } catch (exception: SecurityException) { dialog.dismiss() }
    }

    private fun stopBluetoothScan(scanner: android.bluetooth.le.BluetoothLeScanner, callback: ScanCallback) {
        try { scanner.stopScan(callback) } catch (exception: Exception) { Log.e("MainActivity", "Error stopping scan", exception) }
    }

    private fun stopScanAfterTimeout(scanner: android.bluetooth.le.BluetoothLeScanner, callback: ScanCallback, dialog: AlertDialog, discoveredDevices: Set<String>) {
        lifecycleScope.launch {
            delay(SCAN_DURATION_MS)
            stopBluetoothScan(scanner, callback)
            if (discoveredDevices.isEmpty() && dialog.isShowing) dialog.setTitle("No sensors found")
        }
    }

    private fun fetchUrl(urlString: String): String {
        val connection = openConnection(urlString)
        return try {
            if (connection.responseCode in HTTP_SUCCESS_RANGE) {
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else {
                throw Exception("HTTP ${connection.responseCode}")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun postUrl(urlString: String, json: JSONObject) {
        val connection = openConnection(urlString)

        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true

        try {
            OutputStreamWriter(connection.outputStream).use {
                it.write(json.toString())
            }

            val responseCode = connection.responseCode
            Log.d("MainActivity", "POST Response: $responseCode")

            if (responseCode !in HTTP_SUCCESS_RANGE) {
                val error = connection.errorStream?.bufferedReader()?.readText()
                throw Exception("HTTP $responseCode\n$error")
            }

        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(urlString: String): HttpURLConnection {
        return (URL(urlString).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECTION_TIMEOUT_MS
            readTimeout = CONNECTION_TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
        }
    }

    sealed class MainItem {
        data class Header(val name: String, val isExpanded: Boolean) : MainItem()
        data class PlantRow(val plant: Plant, val ref: PlantReference?) : MainItem()
    }

    class MainAdapter(private val onClick: (MainItem) -> Unit) : ListAdapter<MainItem, RecyclerView.ViewHolder>(DiffCallback) {
        override fun getItemViewType(position: Int): Int = if (getItem(position) is MainItem.Header) VIEW_TYPE_HEADER else VIEW_TYPE_PLANT
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == VIEW_TYPE_HEADER) HeaderViewHolder(inflater.inflate(android.R.layout.simple_list_item_1, parent, false))
            else PlantViewHolder(inflater.inflate(R.layout.item_encyclopedia_plant, parent, false))
        }
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = getItem(position)
            holder.itemView.setOnClickListener { onClick(item) }
            when {
                holder is HeaderViewHolder && item is MainItem.Header -> holder.title.text = "${if (item.isExpanded) "▼" else "▶"} ${item.name}"
                holder is PlantViewHolder && item is MainItem.PlantRow -> {
                    holder.title.text = item.plant.name
                    holder.subtitle.text = item.ref?.displayPid ?: item.plant.speciesId
                    
                    val imageUri = item.plant.imageUri
                    if (!imageUri.isNullOrEmpty()) {
                        holder.image.load(imageUri.toUri()) {
                            crossfade(true)
                            placeholder(R.drawable.ic_plant_placeholder)
                            error(R.drawable.ic_plant_placeholder)
                        }
                    } else {
                        val refImage = item.ref?.image?.replace("%d", "200")
                        holder.image.load(refImage) {
                            crossfade(true)
                            placeholder(R.drawable.ic_plant_placeholder)
                            error(R.drawable.ic_plant_placeholder)
                        }
                    }
                }
            }
        }
        class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) { val title: TextView = view.findViewById(android.R.id.text1) }
        class PlantViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.textPlantName)
            val subtitle: TextView = view.findViewById(R.id.textPlantCategory)
            val image: ImageView = view.findViewById(R.id.imagePlant)
        }
        object DiffCallback : DiffUtil.ItemCallback<MainItem>() {
            override fun areItemsTheSame(oldItem: MainItem, newItem: MainItem): Boolean = oldItem == newItem
            override fun areContentsTheSame(oldItem: MainItem, newItem: MainItem): Boolean = oldItem == newItem
        }
        companion object { private const val VIEW_TYPE_HEADER = 0; private const val VIEW_TYPE_PLANT = 1 }
    }

    companion object {
        private const val REQUEST_BLUETOOTH_PERMISSIONS = 1001
        private const val SCAN_DURATION_MS = 15000L
        private const val CONNECTION_TIMEOUT_MS = 10000
        private val HTTP_SUCCESS_RANGE = 200..299
    }
}
