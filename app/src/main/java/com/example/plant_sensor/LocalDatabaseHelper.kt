package com.example.plant_sensor

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class LocalDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "plants.db"
        private const val DATABASE_VERSION = 1

        const val TABLE_NAME = "Data_sensor"
        const val COLUMN_ID = "id"
        const val COLUMN_NAME = "plant_name"
        const val COLUMN_SPECIES = "species_id"
        const val COLUMN_ROOM = "room"
        const val COLUMN_MAC = "sensor_mac"
        const val COLUMN_IMAGE = "image_uri"
        const val COLUMN_LAST_WATERED = "last_watered"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = ("CREATE TABLE " + TABLE_NAME + "("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_NAME + " TEXT,"
                + COLUMN_SPECIES + " TEXT,"
                + COLUMN_ROOM + " TEXT,"
                + COLUMN_MAC + " TEXT,"
                + COLUMN_IMAGE + " TEXT,"
                + COLUMN_LAST_WATERED + " TEXT" + ")")
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun addPlant(plant: Plant): Long {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_NAME, plant.name)
            put(COLUMN_SPECIES, plant.speciesId)
            put(COLUMN_ROOM, plant.room)
            put(COLUMN_MAC, plant.sensorMac)
            put(COLUMN_IMAGE, plant.imageUri)
            put(COLUMN_LAST_WATERED, plant.lastWatered)
        }
        val id = db.insert(TABLE_NAME, null, values)
        db.close()
        return id
    }

    fun updatePlant(plant: Plant): Int {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_NAME, plant.name)
            put(COLUMN_SPECIES, plant.speciesId)
            put(COLUMN_ROOM, plant.room)
            put(COLUMN_MAC, plant.sensorMac)
            put(COLUMN_IMAGE, plant.imageUri)
            put(COLUMN_LAST_WATERED, plant.lastWatered)
        }
        val result = db.update(TABLE_NAME, values, "$COLUMN_ID = ?", arrayOf(plant.id.toString()))
        db.close()
        return result
    }

    fun getPlant(id: Long): Plant? {
        val db = this.readableDatabase
        val cursor = db.query(TABLE_NAME, null, "$COLUMN_ID = ?", arrayOf(id.toString()), null, null, null)
        var plant: Plant? = null
        if (cursor.moveToFirst()) {
            plant = Plant(
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
        cursor.close()
        db.close()
        return plant
    }

    fun getAllPlants(): List<Plant> {
        val plantList = mutableListOf<Plant>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_NAME", null)

        if (cursor.moveToFirst()) {
            do {
                val plant = Plant(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                    name = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NAME)),
                    room = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ROOM)),
                    speciesId = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SPECIES)),
                    lastWatered = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LAST_WATERED)),
                    careHints = "",
                    sensorMac = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MAC)),
                    imageUri = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_IMAGE))
                )
                plantList.add(plant)
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return plantList
    }
}
