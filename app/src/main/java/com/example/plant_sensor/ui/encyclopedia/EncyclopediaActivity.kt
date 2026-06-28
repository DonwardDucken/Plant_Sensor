package com.example.plant_sensor.ui.encyclopedia

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.example.plant_sensor.R
import com.example.plant_sensor.data.model.PlantReference
import com.example.plant_sensor.data.remote.PlantDatabase
import com.example.plant_sensor.ui.detail.PlantDetailActivity
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

/**
 * Shows the plant encyclopedia.
 */
class EncyclopediaActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: EncyclopediaAdapter

    private val allReferences = mutableListOf<PlantReference>()

    private var isLoading = false
    private var isLastPage = false
    private var currentOffset = 0
    private var isSearching = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_encyclopedia)

        setupToolbar()
        setupRecyclerView()
        setupSearchField()
        loadNextPage()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbarEncyclopedia)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.recyclerViewEncyclopedia)
        progressBar = findViewById(R.id.progressBarEncyclopedia)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = EncyclopediaAdapter(
            plants = emptyList(),
            canLoadImages = hasInternetPermission(),
            onClick = { reference -> openPlantDetail(reference) }
        )
        recyclerView.adapter = adapter

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(view: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(view, dx, dy)
                if (shouldLoadMoreItems()) loadNextPage()
            }
        })
    }

    private fun setupSearchField() {
        val editSearch = findViewById<TextInputEditText>(R.id.editSearchEncyclopedia)
        editSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
                filterPlants(text.toString())
            }
            override fun afterTextChanged(text: Editable?) {}
        })
    }

    private fun shouldLoadMoreItems(): Boolean {
        if (isLoading || isLastPage || isSearching) return false
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return false
        val visibleItemCount = layoutManager.childCount
        val totalItemCount = layoutManager.itemCount
        val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
        return (visibleItemCount + firstVisiblePosition) >= totalItemCount && firstVisiblePosition >= 0 && totalItemCount >= PAGE_SIZE
    }

    private fun loadNextPage() {
        if (isLoading || isLastPage) return
        isLoading = true
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val nextBatch = PlantDatabase.getPlantReferencesPage(this@EncyclopediaActivity, PAGE_SIZE, currentOffset)
                if (nextBatch.isEmpty()) {
                    isLastPage = true
                } else {
                    allReferences.addAll(nextBatch)
                    adapter.updateData(allReferences)
                    currentOffset += PAGE_SIZE
                }
            } catch (exception: Exception) {
                Log.e(LOG_TAG, "Error loading plant references", exception)
            } finally {
                isLoading = false
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun filterPlants(query: String) {
        lifecycleScope.launch {
            try {
                if (query.isBlank()) {
                    isSearching = false
                    adapter.updateData(allReferences)
                    return@launch
                }
                isSearching = true
                progressBar.visibility = View.VISIBLE
                val results = PlantDatabase.searchPlants(this@EncyclopediaActivity, query)
                adapter.updateData(results)
            } catch (exception: Exception) {
                Log.e(LOG_TAG, "Error searching plants", exception)
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun openPlantDetail(reference: PlantReference) {
        val intent = Intent(this, PlantDetailActivity::class.java).apply {
            putExtra("name", reference.displayPid)
            putExtra("isEncyclopedia", true)
            putExtra("speciesId", reference.pid)
            putExtra("imageUri", formatImageUrl(reference.image, DETAIL_IMAGE_SIZE))
            putExtra("displayPid", reference.displayPid)
            putExtra("category", reference.category)
            putExtra("origin", reference.origin)
            putExtra("sunlight", reference.sunlight)
            putExtra("watering", reference.watering)
            putExtra("soil", reference.soil)
            putExtra("fertilization", reference.fertilization)
            putExtra("pruning", reference.pruning)
            putExtra("floralLanguage", reference.floralLanguage)
        }
        startActivity(intent)
    }

    private fun hasInternetPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED
    }

    class EncyclopediaAdapter(
        private var plants: List<PlantReference>,
        private val canLoadImages: Boolean,
        private val onClick: (PlantReference) -> Unit
    ) : RecyclerView.Adapter<EncyclopediaAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.textPlantName)
            val category: TextView = view.findViewById(R.id.textPlantCategory)
            val image: ImageView = view.findViewById(R.id.imagePlant)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_encyclopedia_plant, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val plant = plants[position]
            val context = holder.itemView.context
            holder.name.text = plant.displayPid ?: context.getString(R.string.placeholder_unknown)
            holder.category.text = plant.category ?: context.getString(R.string.nav_plant)
            if (canLoadImages) {
                holder.image.load(formatImageUrl(plant.image, THUMBNAIL_IMAGE_SIZE)) {
                    placeholder(R.drawable.ic_plant_placeholder)
                    error(R.drawable.ic_plant_placeholder)
                    crossfade(true)
                    transformations(RoundedCornersTransformation(16f))
                }
            } else {
                holder.image.setImageResource(R.drawable.ic_plant_placeholder)
            }
            holder.itemView.setOnClickListener { onClick(plant) }
        }

        override fun getItemCount(): Int = plants.size
        fun updateData(newPlants: List<PlantReference>) {
            plants = newPlants
            notifyDataSetChanged()
        }
    }

    companion object {
        private const val LOG_TAG = "EncyclopediaActivity"
        private const val PAGE_SIZE = 10
        private const val DETAIL_IMAGE_SIZE = 600
        private const val THUMBNAIL_IMAGE_SIZE = 150
        fun formatImageUrl(imageUrl: String?, size: Int): String? {
            return imageUrl?.replace("%d", size.toString())
        }
    }
}
