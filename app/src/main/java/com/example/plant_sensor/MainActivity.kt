package com.example.plant_sensor

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
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
import java.util.Date
import java.util.Locale
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

class MainActivity : AppCompatActivity() {

    private val rooms = mutableListOf("Living Room", "Kitchen", "Balcony")
    private val myPlants = mutableListOf<Plant>()

    private val SERVER_URL = "http://192.168.0.16:8080"
    private val gson = GsonBuilder().setLenient().create()

    private lateinit var mainAdapter: MainAdapter
    private val expandedRooms = mutableSetOf("Living Room", "Kitchen", "Balcony")
    private var currentTab = R.id.nav_my_plants

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewMain)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)

        mainAdapter = MainAdapter(
            onClick = { item ->
                when (item) {
                    is MainItem.Header -> toggleRoom(item.name)
                    is MainItem.PlantRow -> openPlantDetail(item.plant)
                }
            },
            onLongClick = { item ->
                if (item is MainItem.PlantRow) {
                    showDeleteConfirmation(item.plant)
                }
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = mainAdapter

        findViewById<View>(R.id.buttonAddPlant).setOnClickListener {
            showAddPlantDialog()
        }

        bottomNav.setOnItemSelectedListener { item ->
            currentTab = item.itemId

            if (currentTab == R.id.nav_encyclopedia) {
                startActivity(Intent(this, EncyclopediaActivity::class.java))
                currentTab = R.id.nav_my_plants
                bottomNav.selectedItemId = R.id.nav_my_plants
            } else {
                updateUI()
            }

            true
        }
    }

    override fun onResume() {
        super.onResume()
        loadPlantsFromServer()
    }

    private fun loadPlantsFromServer() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val jsonResponse = fetchUrl("$SERVER_URL/plants")
                Log.d("PLANTS_JSON", jsonResponse)
                val type = object : TypeToken<List<Plant>>() {}.type
                val loadedPlants: List<Plant> = gson.fromJson(jsonResponse, type) ?: emptyList()

                withContext(Dispatchers.Main) {
                    myPlants.clear()
                    myPlants.addAll(loadedPlants)

                    myPlants.forEach {
                        if (!rooms.contains(it.room)) rooms.add(it.room)
                    }

                    updateUI()
                }
            } catch (e: Exception) {
                Log.e("PlantSensor", "Error loading plants", e)
            }
        }
    }

    private fun updateUI() {
        val items = mutableListOf<MainItem>()
        val titleView = findViewById<TextView>(R.id.textMainTitle)
        val fab = findViewById<View>(R.id.buttonAddPlant)

        titleView.text = getString(R.string.nav_my_plants)
        fab.visibility = View.VISIBLE

        rooms.forEach { room ->
            val expanded = expandedRooms.contains(room)
            items.add(MainItem.Header(room, expanded))

            if (expanded) {
                val roomPlants = myPlants.filter { it.room == room }

                roomPlants.forEach { plant ->
                    val ref = PlantDatabase.getByPid(plant.speciesId)
                    items.add(MainItem.PlantRow(plant, ref))
                }
            }
        }

        mainAdapter.submitList(items)
    }

    private fun scanForBluetoothSensor(onFound: (String) -> Unit) {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), 1001)
            Toast.makeText(this, "Bitte Bluetooth-Berechtigung erlauben", Toast.LENGTH_SHORT).show()
            return
        }

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Toast.makeText(this, "Bluetooth ist nicht aktiviert", Toast.LENGTH_SHORT).show()
            return
        }

        val scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            Toast.makeText(this, "Bluetooth Scanner nicht verfügbar", Toast.LENGTH_SHORT).show()
            return
        }

        val discoveredDevices = mutableSetOf<String>()
        val deviceStrings = mutableListOf<String>()
        val listAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, deviceStrings)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Sensoren suchen...")
            .setAdapter(listAdapter) { _, which ->
                val selected = deviceStrings[which]
                val mac = selected.substringAfterLast("\n")
                onFound(mac)
            }
            .setNegativeButton("Abbrechen", null)
            .create()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val mac = device.address
                val name = device.name ?: result.scanRecord?.deviceName ?: "Unbekannter Sensor"

                if (discoveredDevices.add(mac)) {
                    runOnUiThread {
                        deviceStrings.add("$name\n$mac")
                        listAdapter.notifyDataSetChanged()
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Scan fehlgeschlagen: $errorCode", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.setOnDismissListener {
            try {
                scanner.stopScan(callback)
            } catch (e: Exception) {
                Log.e("BLE_SCAN", "Error stopping scan", e)
            }
        }

        dialog.show()
        try {
            scanner.startScan(callback)
        } catch (e: SecurityException) {
            Toast.makeText(this, "Fehlende Berechtigung für Scan", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
            return
        }

        lifecycleScope.launch {
            delay(15000)
            try {
                scanner.stopScan(callback)
            } catch (e: Exception) {}
            
            if (discoveredDevices.isEmpty() && dialog.isShowing) {
                runOnUiThread {
                    dialog.setTitle("Keine Sensoren gefunden")
                }
            }
        }
    }

    private fun showAddPlantDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_plant, null)

        val editName = view.findViewById<TextInputEditText>(R.id.editPlantName)
        val editMac = view.findViewById<TextInputEditText>(R.id.editSensorMac)
        val spinnerRoom = view.findViewById<Spinner>(R.id.spinnerRoom)
        val spinnerSpecies = view.findViewById<Spinner>(R.id.spinnerSpecies)
        val btnAddRoom = view.findViewById<View>(R.id.buttonAddRoom)
        val btnScan = view.findViewById<View>(R.id.buttonScanSensor)

        val roomAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, rooms)
        roomAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerRoom.adapter = roomAdapter

        val speciesList = PlantDatabase.plantReferences
            .mapNotNull { it.displayPid }
            .ifEmpty { listOf("abelia dielsii", "abelia chinensis") }

        val speciesAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, speciesList)
        speciesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSpecies.adapter = speciesAdapter

        btnAddRoom.setOnClickListener {
            showAddRoomDialog { newRoom ->
                rooms.add(newRoom)
                roomAdapter.notifyDataSetChanged()
                spinnerRoom.setSelection(rooms.indexOf(newRoom))
            }
        }

        btnScan.setOnClickListener {
            scanForBluetoothSensor { mac ->
                editMac.setText(mac)
            }
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_new_plant)
            .setView(view)
            .setPositiveButton(R.string.button_save) { _, _ ->
                val name = editName.text.toString().trim()
                val manualMac = editMac.text.toString().trim()
                val room = spinnerRoom.selectedItem?.toString() ?: ""
                val speciesName = spinnerSpecies.selectedItem?.toString() ?: ""

                val species = PlantDatabase.plantReferences.find {
                    it.displayPid == speciesName
                }

                val speciesId = species?.pid ?: speciesName

                if (name.isNotEmpty() && speciesId.isNotEmpty()) {
                    val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

                    val plantJson = JSONObject().apply {
                        put("plant_name", name)
                        put("room", room)
                        put("species_id", speciesId)
                        put("MAC", if (manualMac.isNotEmpty()) manualMac else JSONObject.NULL)
                        put("last_watered", date)
                        put("image_uri", JSONObject.NULL)
                    }

                    savePlantToServer(plantJson)
                } else {
                    Toast.makeText(this, getString(R.string.toast_select_name_species), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    private fun savePlantToServer(json: JSONObject) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                postUrl("$SERVER_URL/add_plant", json)

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, getString(R.string.toast_plant_saved), Toast.LENGTH_SHORT).show()
                    loadPlantsFromServer()
                }
            } catch (e: Exception) {
                Log.e("PlantSensor", "Error saving plant", e)

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, getString(R.string.toast_error_saving, e.localizedMessage), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showDeleteConfirmation(plant: Plant) {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_delete_plant_title)
            .setMessage(getString(R.string.dialog_delete_plant_message, plant.name))
            .setPositiveButton(R.string.button_delete) { _, _ ->
                deletePlantFromServer(plant)
            }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    private fun deletePlantFromServer(plant: Plant) {
        val id = plant.id ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("id", id)
                }

                postUrl("$SERVER_URL/delete_plant", json)

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, getString(R.string.toast_plant_deleted), Toast.LENGTH_SHORT).show()
                    loadPlantsFromServer()
                }
            } catch (e: Exception) {
                Log.e("PlantSensor", "Error deleting plant", e)

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, getString(R.string.toast_error_deleting, e.message), Toast.LENGTH_LONG).show()
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
                val name = editRoom.text.toString().trim()
                if (name.isNotEmpty()) onRoomAdded(name)
            }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    private fun toggleRoom(name: String) {
        if (expandedRooms.contains(name)) {
            expandedRooms.remove(name)
        } else {
            expandedRooms.add(name)
        }

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

    private fun fetchUrl(urlString: String): String {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection

        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("Accept-Encoding", "identity")
        conn.setRequestProperty("Connection", "close")

        return try {
            val responseCode = conn.responseCode

            if (responseCode in 200..299) {
                return conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    reader.readText()
                }

            } else {
                val error = conn.errorStream?.readBytes()?.toString(Charsets.UTF_8) ?: "Server Error"
                throw Exception("HTTP $responseCode: $error")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun postUrl(urlString: String, json: JSONObject) {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection

        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("Connection", "close")
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        conn.doOutput = true

        try {
            OutputStreamWriter(conn.outputStream, "UTF-8").use {
                it.write(json.toString())
                it.flush()
            }

            if (conn.responseCode !in 200..299) {
                val error = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Server Error"
                throw Exception("HTTP ${conn.responseCode}: $error")
            }
        } finally {
            conn.disconnect()
        }
    }

    sealed class MainItem {
        data class Header(val name: String, val isExpanded: Boolean) : MainItem()
        data class PlantRow(val plant: Plant, val ref: PlantReference?) : MainItem()
    }

    class MainAdapter(
        private val onClick: (MainItem) -> Unit,
        private val onLongClick: (MainItem) -> Unit
    ) : ListAdapter<MainItem, RecyclerView.ViewHolder>(DiffCallback) {

        override fun getItemViewType(position: Int): Int {
            return when (getItem(position)) {
                is MainItem.Header -> 0
                is MainItem.PlantRow -> 1
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)

            return if (viewType == 0) {
                HeaderVH(inflater.inflate(android.R.layout.simple_list_item_1, parent, false))
            } else {
                PlantVH(inflater.inflate(R.layout.item_encyclopedia_plant, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = getItem(position)

            holder.itemView.setOnClickListener {
                onClick(item)
            }

            holder.itemView.setOnLongClickListener {
                onLongClick(item)
                true
            }

            when {
                holder is HeaderVH && item is MainItem.Header -> {
                    holder.tv.text = "${if (item.isExpanded) "▼" else "▶"} ${item.name}"
                }

                holder is PlantVH && item is MainItem.PlantRow -> {
                    holder.title.text = item.plant.name
                    holder.sub.text = item.ref?.displayPid ?: item.plant.speciesId
                }
            }
        }

        class HeaderVH(v: View) : RecyclerView.ViewHolder(v) {
            val tv: TextView = v.findViewById(android.R.id.text1)
        }

        class PlantVH(v: View) : RecyclerView.ViewHolder(v) {
            val title: TextView = v.findViewById(R.id.textPlantName)
            val sub: TextView = v.findViewById(R.id.textPlantCategory)
        }

        object DiffCallback : DiffUtil.ItemCallback<MainItem>() {
            override fun areItemsTheSame(oldItem: MainItem, newItem: MainItem): Boolean {
                return oldItem == newItem
            }

            override fun areContentsTheSame(oldItem: MainItem, newItem: MainItem): Boolean {
                return oldItem == newItem
            }
        }
    }
}
