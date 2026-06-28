# Plant Sensor

## Overview

Plant Sensor is an Android application for monitoring indoor plants using Bluetooth plant sensors and a Python backend.

The application allows users to manage plants, display current sensor values, compare measurements with recommended reference ranges and visualize historical sensor data. Plant information is retrieved from a backend database containing plant reference data.

---

## Features

- Add, edit and delete plants
- Organize plants by room
- Plant encyclopedia with search function
- Display detailed plant information
- Current sensor measurements
    - Temperature
    - Soil moisture
    - Light intensity
    - Soil conductivity
- Visualization of sensor values using custom range bars
- History charts for all sensor values
- Plant care recommendations
- Watering tracking
- Local notifications for critical sensor values
- Image support for plants

---

## Project Structure

```
com.example.plant_sensor
│
├── data
│   ├── local
│   ├── model
│   └── remote
│
├── ui
│   ├── main
│   ├── detail
│   ├── encyclopedia
│   └── customviews
│
└── util
```

The project follows a layered architecture by separating:

- data access
- user interface
- data models
- utility classes

---

## Technologies

- Kotlin
- Android SDK
- Material Design
- SQLite
- Python HTTP Server
- JSON
- Gson
- Coil
- Kotlin Coroutines
- Bluetooth Low Energy (BLE)

---

## Backend

The Android application communicates with a Python backend.

The backend is responsible for

- storing sensor measurements
- storing plant information
- providing plant reference data
- serving REST endpoints

Example endpoints:

- `/plants`
- `/latest`
- `/history`
- `/plant_reference`
- `/search_plants`

---


## Plant Reference Data

The application compares live sensor measurements with recommended values stored in the plant reference database.

Reference values include:

- temperature
- soil moisture
- light intensity
- humidity
- soil conductivity

These ranges are used to visualize whether the current sensor values are inside or outside the recommended range.

---

## Running the Project

1. Start the Python backend.
2. Make sure the backend database is available.
3. Build and run the Android application.

---

## AI Usage

Artificial intelligence tools were used during development for:

- code suggestions
- debugging
- code refactoring
- documentation
- UI improvements

The final implementation, testing and integration were performed manually.

---

