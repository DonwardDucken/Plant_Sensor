package com.example.plant_sensor.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.plant_sensor.data.model.Plant

/**
 * Local SQLite helper for storing plants on the Android device.
 *
 * This class is only needed if plants are stored locally in the app.
 * If the app uses the Python server as the main database, this helper may no longer be required.
 */
class LocalDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_PLANTS_TABLE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    /**
     * Inserts a new plant into the local database.
     *
     * @return database id of the inserted plant or -1 if inserting failed.
     */
    fun addPlant(plant: Plant): Long {
        val database = writableDatabase
        val id = database.insert(TABLE_NAME, null, plantToContentValues(plant))
        database.close()

        return id
    }

    /**
     * Updates an existing plant by its id.
     *
     * @return number of changed rows.
     */
    fun updatePlant(plant: Plant): Int {
        val database = writableDatabase

        val changedRows = database.update(
            TABLE_NAME,
            plantToContentValues(plant),
            "$COLUMN_ID = ?",
            arrayOf(plant.id.toString())
        )

        database.close()
        return changedRows
    }

    /**
     * Loads one plant by id.
     */
    fun getPlant(id: Long): Plant? {
        val database = readableDatabase

        val cursor = database.query(
            TABLE_NAME,
            null,
            "$COLUMN_ID = ?",
            arrayOf(id.toString()),
            null,
            null,
            null
        )

        val plant = if (cursor.moveToFirst()) {
            cursorToPlant(cursor)
        } else {
            null
        }

        cursor.close()
        database.close()

        return plant
    }

    /**
     * Loads all locally stored plants.
     */
    fun getAllPlants(): List<Plant> {
        val plants = mutableListOf<Plant>()
        val database = readableDatabase
        val cursor = database.rawQuery("SELECT * FROM $TABLE_NAME", null)

        if (cursor.moveToFirst()) {
            do {
                plants.add(cursorToPlant(cursor))
            } while (cursor.moveToNext())
        }

        cursor.close()
        database.close()

        return plants
    }

    private fun plantToContentValues(plant: Plant): ContentValues {
        return ContentValues().apply {
            put(COLUMN_NAME, plant.name)
            put(COLUMN_SPECIES, plant.speciesId)
            put(COLUMN_ROOM, plant.room)
            put(COLUMN_MAC, plant.sensorMac)
            put(COLUMN_IMAGE, plant.imageUri)
            put(COLUMN_LAST_WATERED, plant.lastWatered)
        }
    }

    private fun cursorToPlant(cursor: android.database.Cursor): Plant {
        return Plant(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)),
            name = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NAME)),
            room = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ROOM)),
            speciesId = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SPECIES)),
            lastWatered = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LAST_WATERED)),
            careHints = "",
            sensorMac = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MAC)),
            imageUri = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_IMAGE))
        )
    }

    companion object {
        private const val DATABASE_NAME = "plants.db"
        private const val DATABASE_VERSION = 1

        private const val TABLE_NAME = "Data_sensor"

        private const val COLUMN_ID = "id"
        private const val COLUMN_NAME = "plant_name"
        private const val COLUMN_SPECIES = "species_id"
        private const val COLUMN_ROOM = "room"
        private const val COLUMN_MAC = "sensor_mac"
        private const val COLUMN_IMAGE = "image_uri"
        private const val COLUMN_LAST_WATERED = "last_watered"

        private const val CREATE_PLANTS_TABLE = """
            CREATE TABLE Data_sensor (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                plant_name TEXT,
                species_id TEXT,
                room TEXT,
                sensor_mac TEXT,
                image_uri TEXT,
                last_watered TEXT
            )
        """
    }
}
