package com.example.plant_sensor

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val rooms = mutableListOf("Wohnzimmer", "Küche")

    private val plants = mutableListOf(
        Plant(
            "Monstera",
            "Wohnzimmer",
            "45%",
            "02.04.2026",
            "22°C",
            "Hell stellen, aber keine direkte Mittagssonne. Erde leicht feucht halten."
        ),
        Plant(
            "Ficus",
            "Wohnzimmer",
            "38%",
            "01.04.2026",
            "21°C",
            "Mag es warm und hell. Nicht zu oft umstellen."
        ),
        Plant(
            "Basilikum",
            "Küche",
            "60%",
            "02.04.2026",
            "23°C",
            "Regelmäßig gießen, viel Licht, Erde nicht austrocknen lassen."
        )
    )

    private lateinit var roomsContainer: LinearLayout
    private lateinit var buttonAddPlant: Button

    private val expandedRooms = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        roomsContainer = findViewById(R.id.roomsContainer)
        buttonAddPlant = findViewById(R.id.buttonAddPlant)

        renderRooms()

        buttonAddPlant.setOnClickListener {
            showAddPlantDialog()
        }
    }

    private fun renderRooms() {
        roomsContainer.removeAllViews()

        for (room in rooms) {
            val roomCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.room_card_bg)
                setPadding(32, 32, 32, 32)

                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.bottomMargin = 24
                layoutParams = params
            }

            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            val title = TextView(this).apply {
                text = room
                textSize = 20f
                setTextColor(Color.parseColor("#223322"))
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            val arrow = TextView(this).apply {
                text = if (expandedRooms.contains(room)) "▲" else "▼"
                textSize = 18f
                setTextColor(Color.parseColor("#5A6B5C"))
            }

            header.addView(title)
            header.addView(arrow)

            val subtitle = TextView(this).apply {
                text = "Pflanzen in diesem Raum"
                textSize = 13f
                setTextColor(Color.parseColor("#7A857B"))
                setPadding(0, 8, 0, 16)
            }

            val plantsLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                visibility = if (expandedRooms.contains(room)) View.VISIBLE else View.GONE
            }

            val plantsInRoom = plants.filter { it.room == room }

            for (plant in plantsInRoom) {
                val plantView = TextView(this).apply {
                    text = plant.name
                    textSize = 16f
                    setTextColor(Color.parseColor("#223322"))
                    setPadding(24, 20, 24, 20)
                    setBackgroundResource(R.drawable.plant_item_bg)

                    val params = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    params.bottomMargin = 12
                    layoutParams = params
                }

                plantView.setOnClickListener {
                    val intent = Intent(this, PlantDetailActivity::class.java)
                    intent.putExtra("name", plant.name)
                    intent.putExtra("room", plant.room)
                    intent.putExtra("moisture", plant.moisture)
                    intent.putExtra("lastWatered", plant.lastWatered)
                    intent.putExtra("temperature", plant.temperature)
                    intent.putExtra("careHints", plant.careHints)
                    startActivity(intent)
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                }

                plantsLayout.addView(plantView)
            }

            header.setOnClickListener {
                if (expandedRooms.contains(room)) {
                    expandedRooms.remove(room)
                } else {
                    expandedRooms.add(room)
                }
                renderRooms()
            }

            roomCard.addView(header)
            roomCard.addView(subtitle)
            roomCard.addView(plantsLayout)

            roomsContainer.addView(roomCard)
        }
    }

    private fun showAddPlantDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_plant, null)

        val editPlantName = dialogView.findViewById<EditText>(R.id.editPlantName)
        val spinnerRoom = dialogView.findViewById<Spinner>(R.id.spinnerRoom)
        val buttonAddRoom = dialogView.findViewById<Button>(R.id.buttonAddRoom)

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, rooms)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerRoom.adapter = adapter

        buttonAddRoom.setOnClickListener {
            showAddRoomDialog {
                adapter.notifyDataSetChanged()
                spinnerRoom.setSelection(rooms.size - 1)
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Neue Pflanze")
            .setView(dialogView)
            .setPositiveButton("Speichern") { _, _ ->
                val plantName = editPlantName.text.toString().trim()
                val selectedRoom = spinnerRoom.selectedItem?.toString()?.trim() ?: ""

                if (plantName.isBlank() || selectedRoom.isBlank()) {
                    Toast.makeText(this, "Bitte alles ausfüllen", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                plants.add(
                    Plant(
                        name = plantName,
                        room = selectedRoom,
                        moisture = "unbekannt",
                        lastWatered = "noch nie",
                        temperature = "unbekannt",
                        careHints = "Noch keine Pflegehinweise hinterlegt."
                    )
                )

                expandedRooms.add(selectedRoom)
                renderRooms()
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun showAddRoomDialog(onRoomAdded: () -> Unit) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_room, null)
        val editRoomName = dialogView.findViewById<EditText>(R.id.editRoomName)

        AlertDialog.Builder(this)
            .setTitle("Neuen Raum anlegen")
            .setView(dialogView)
            .setPositiveButton("Speichern") { _, _ ->
                val roomName = editRoomName.text.toString().trim()

                if (roomName.isBlank()) {
                    Toast.makeText(this, "Bitte einen Raumnamen eingeben", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (rooms.contains(roomName)) {
                    Toast.makeText(this, "Dieser Raum existiert bereits", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                rooms.add(roomName)
                expandedRooms.add(roomName)
                renderRooms()
                onRoomAdded()
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }
}