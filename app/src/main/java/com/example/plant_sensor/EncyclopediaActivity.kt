package com.example.plant_sensor

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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.google.android.material.textfield.TextInputEditText

class EncyclopediaActivity : AppCompatActivity() {

    private var recyclerView: RecyclerView? = null
    private var progressBar: ProgressBar? = null
    private var adapter: EncyclopediaAdapter? = null
    
    private var allReferences: MutableList<PlantReference> = mutableListOf()
    private var isLoading = false
    private var isLastPage = false
    private var currentOffset = 0
    private val PAGE_SIZE = 10
    private var isSearching = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_encyclopedia)

            val toolbar = findViewById<Toolbar>(R.id.toolbarEncyclopedia)
            recyclerView = findViewById(R.id.recyclerViewEncyclopedia)
            progressBar = findViewById(R.id.progressBarEncyclopedia)
            val editSearch = findViewById<TextInputEditText>(R.id.editSearchEncyclopedia)

            if (toolbar != null) {
                setSupportActionBar(toolbar)
                supportActionBar?.setDisplayHomeAsUpEnabled(true)
                toolbar.setNavigationOnClickListener { finish() }
            }

            recyclerView?.layoutManager = LinearLayoutManager(this)
            
            val hasInternet = ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED
            adapter = EncyclopediaAdapter(emptyList(), hasInternet) { reference ->
                openPlantDetail(reference)
            }
            recyclerView?.adapter = adapter
            
            recyclerView?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    val layoutManager = recyclerView?.layoutManager as? LinearLayoutManager ?: return
                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                    if (!isLoading && !isLastPage && !isSearching) {
                        if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                            && firstVisibleItemPosition >= 0
                            && totalItemCount >= PAGE_SIZE
                        ) {
                            loadNextPage()
                        }
                    }
                }
            })

            editSearch?.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    filterPlants(s.toString())
                }
                override fun afterTextChanged(s: Editable?) {}
            })

            loadNextPage()
            
        } catch (e: Exception) {
            Log.e("Encyclopedia", "Error starting activity", e)
            Toast.makeText(this, getString(R.string.toast_loading), Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun loadNextPage() {
        if (isLoading || isLastPage) return
        isLoading = true
        progressBar?.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            val nextBatch = PlantDatabase.getPlantReferencesPage(PAGE_SIZE, currentOffset)
            if (nextBatch.isEmpty()) {
                isLastPage = true
            } else {
                allReferences.addAll(nextBatch)
                adapter?.updateData(allReferences.toList())
                currentOffset += PAGE_SIZE
            }
            isLoading = false
            progressBar?.visibility = View.GONE
        }
    }

    private fun filterPlants(query: String) {
        lifecycleScope.launch {
            if (query.isBlank()) {
                isSearching = false
                adapter?.updateData(allReferences)
                return@launch
            }

            isSearching = true
            progressBar?.visibility = View.VISIBLE
            val results = PlantDatabase.searchPlants(query)
            adapter?.updateData(results)
            progressBar?.visibility = View.GONE
        }
    }

    private fun openPlantDetail(reference: PlantReference) {
        try {
            val intent = Intent(this, PlantDetailActivity::class.java).apply {
                putExtra("name", reference.displayPid)
                putExtra("isEncyclopedia", true)
                putExtra("speciesId", reference.pid)
                
                val imageUrl = reference.image?.replace("%d", "600")?.replace("%d", "600")
                putExtra("imageUri", imageUrl)

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
        } catch (e: Exception) {
            Log.e("Encyclopedia", "Navigation error", e)
        }
    }

    class EncyclopediaAdapter(
        private var plants: List<PlantReference>,
        private val internetOk: Boolean,
        private val onClick: (PlantReference) -> Unit
    ) : RecyclerView.Adapter<EncyclopediaAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.textPlantName)
            val category: TextView = view.findViewById(R.id.textPlantCategory)
            val image: ImageView = view.findViewById(R.id.imagePlant)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_encyclopedia_plant, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            try {
                if (position >= plants.size) return
                val plant = plants[position]
                holder.name.text = plant.displayPid ?: holder.itemView.context.getString(R.string.placeholder_unknown)
                holder.category.text = plant.category ?: holder.itemView.context.getString(R.string.nav_plant)
                
                if (internetOk) {
                    val thumbUrl = plant.image?.replace("%d", "150")?.replace("%d", "150")
                    holder.image.load(thumbUrl) {
                        placeholder(R.drawable.ic_plant_placeholder)
                        error(R.drawable.ic_plant_placeholder)
                        crossfade(true)
                        transformations(RoundedCornersTransformation(16f))
                    }
                } else {
                    holder.image.setImageResource(R.drawable.ic_plant_placeholder)
                }
                
                holder.itemView.setOnClickListener { onClick(plant) }
            } catch (e: Exception) {
                Log.e("Adapter", "Error in onBind", e)
            }
        }

        override fun getItemCount() = plants.size

        fun updateData(newPlants: List<PlantReference>) {
            plants = newPlants
            notifyDataSetChanged()
        }
    }
}
