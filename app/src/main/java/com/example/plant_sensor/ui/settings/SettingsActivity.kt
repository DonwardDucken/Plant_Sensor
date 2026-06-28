package com.example.plant_sensor.ui.settings

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.plant_sensor.R
import com.example.plant_sensor.util.SettingsManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputEditText

class SettingsActivity : AppCompatActivity() {

    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        settingsManager = SettingsManager(this)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbarSettings)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        val editIp = findViewById<TextInputEditText>(R.id.editServerIp)
        val editPort = findViewById<TextInputEditText>(R.id.editServerPort)
        val buttonSave = findViewById<Button>(R.id.buttonSaveSettings)

        editIp.setText(settingsManager.serverIp)
        editPort.setText(settingsManager.serverPort)

        buttonSave.setOnClickListener {
            val ip = editIp.text.toString().trim()
            val port = editPort.text.toString().trim()

            if (ip.isNotEmpty() && port.isNotEmpty()) {
                settingsManager.serverIp = ip
                settingsManager.serverPort = port
                Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
