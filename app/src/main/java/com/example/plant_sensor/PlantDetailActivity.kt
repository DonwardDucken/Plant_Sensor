package com.example.plant_sensor

import android.os.Bundle
import android.view.MotionEvent
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PlantDetailActivity : AppCompatActivity() {

    private var x1 = 0f
    private var x2 = 0f
    private val minDistance = 150

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_plant_detail)

        val name = intent.getStringExtra("name") ?: "Unbekannt"
        val room = intent.getStringExtra("room") ?: "Unbekannt"
        val moisture = intent.getStringExtra("moisture") ?: "Unbekannt"
        val lastWatered = intent.getStringExtra("lastWatered") ?: "Unbekannt"
        val temperature = intent.getStringExtra("temperature") ?: "Unbekannt"
        val careHints = intent.getStringExtra("careHints") ?: "Keine Pflegehinweise vorhanden."

        val textPlantName = findViewById<TextView>(R.id.textPlantName)
        val textPlantRoom = findViewById<TextView>(R.id.textPlantRoom)
        val textMoisture = findViewById<TextView>(R.id.textMoisture)
        val textTemperature = findViewById<TextView>(R.id.textTemperature)
        val textLastWatered = findViewById<TextView>(R.id.textLastWatered)
        val textCareHints = findViewById<TextView>(R.id.textCareHints)
        val buttonWaterNow = findViewById<Button>(R.id.buttonWaterNow)

        textPlantName.text = name
        textPlantRoom.text = "Raum: $room"
        textMoisture.text = "Feuchtigkeit: $moisture"
        textTemperature.text = "Temperatur: $temperature"
        textLastWatered.text = "Zuletzt gegossen: $lastWatered"
        textCareHints.text = careHints

        buttonWaterNow.setOnClickListener {
            val currentDate = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
            textLastWatered.text = "Zuletzt gegossen: $currentDate"

            Toast.makeText(this, "$name wurde gegossen", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        when (event?.action) {
            MotionEvent.ACTION_DOWN -> x1 = event.x
            MotionEvent.ACTION_UP -> {
                x2 = event.x
                val deltaX = x2 - x1

                if (deltaX < -minDistance) {
                    finish()
                    overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                }
            }
        }
        return true
    }
}