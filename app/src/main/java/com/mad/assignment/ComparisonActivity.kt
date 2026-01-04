package com.mad.assignment

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

/**
 * Comparison Activity - Display aggregate metrics for all models.
 *
 * Shows side-by-side comparison of model performance when all 200
 * predictions have been completed for a model.
 */
class ComparisonActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ComparisonActivity"
    }

    // Repositories
    private lateinit var firestoreRepository: FirestoreRepository

    // UI Components
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var btnRefresh: Button
    private lateinit var progressLoading: ProgressBar
    private lateinit var tvLoadingText: TextView
    private lateinit var scrollViewModels: ScrollView
    private lateinit var modelCardsContainer: LinearLayout
    private lateinit var emptyStateContainer: ConstraintLayout

    // Number formatters
    private val numberFormat = NumberFormat.getNumberInstance(Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_comparison)

        firestoreRepository = FirestoreRepository()

        initUI()
        setupBottomNavigation()

        // Fetch data on screen open
        fetchModelMetrics()
    }

    private fun initUI() {
        bottomNavigation = findViewById(R.id.bottomNavigation)
        btnRefresh = findViewById(R.id.btnRefresh)
        progressLoading = findViewById(R.id.progressLoading)
        tvLoadingText = findViewById(R.id.tvLoadingText)
        scrollViewModels = findViewById(R.id.scrollViewModels)
        modelCardsContainer = findViewById(R.id.modelCardsContainer)
        emptyStateContainer = findViewById(R.id.emptyStateContainer)

        btnRefresh.setOnClickListener {
            fetchModelMetrics()
        }
    }

    private fun setupBottomNavigation() {
        bottomNavigation.selectedItemId = R.id.nav_comparison

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    overridePendingTransition(0, 0)
                    true
                }
                R.id.nav_comparison -> true
                else -> false
            }
        }
    }

    /**
     * Fetch aggregate metrics for all models from Firebase
     */
    private fun fetchModelMetrics() {
        setLoadingState(true)

        lifecycleScope.launch(Dispatchers.Main) {
            try {
                Log.d(TAG, "Fetching metrics for all models...")

                val metrics = withContext(Dispatchers.IO) {
                    firestoreRepository.getAllModelMetrics(ModelType.values())
                }

                Log.d(TAG, "Fetched ${metrics.size} model metrics")

                // Check if any models have data
                val hasAnyData = metrics.any { it.predictionCount > 0 }

                if (hasAnyData) {
                    showModelCards(metrics)
                } else {
                    showEmptyState()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error fetching metrics: ${e.message}", e)
                showError("Error loading data: ${e.message}")
            } finally {
                setLoadingState(false)
            }
        }
    }

    /**
     * Display model cards with aggregate metrics
     */
    private fun showModelCards(metricsList: List<ModelAggregateMetrics>) {
        modelCardsContainer.removeAllViews()
        scrollViewModels.visibility = View.VISIBLE
        emptyStateContainer.visibility = View.GONE

        metricsList.forEach { metrics ->
            val cardView = LayoutInflater.from(this)
                .inflate(R.layout.item_model_comparison, modelCardsContainer, false)

            populateModelCard(cardView, metrics)
            modelCardsContainer.addView(cardView)
        }
    }

    /**
     * Populate a single model card with metrics data
     */
    private fun populateModelCard(cardView: View, metrics: ModelAggregateMetrics) {
        // Header elements
        val tvModelName = cardView.findViewById<TextView>(R.id.tvModelName)
        val tvPredictionCount = cardView.findViewById<TextView>(R.id.tvPredictionCount)
        val tvStatus = cardView.findViewById<TextView>(R.id.tvStatus)
        val modelIndicator = cardView.findViewById<View>(R.id.modelIndicator)

        // Metrics section
        val metricsSection = cardView.findViewById<LinearLayout>(R.id.metricsSection)
        val incompleteSection = cardView.findViewById<ConstraintLayout>(R.id.incompleteSection)

        // Metric values
        val tvAccuracy = cardView.findViewById<TextView>(R.id.tvAccuracy)
        val tvLatency = cardView.findViewById<TextView>(R.id.tvLatency)
        val tvTtft = cardView.findViewById<TextView>(R.id.tvTtft)
        val tvItps = cardView.findViewById<TextView>(R.id.tvItps)
        val tvOtps = cardView.findViewById<TextView>(R.id.tvOtps)
        val tvOet = cardView.findViewById<TextView>(R.id.tvOet)
        val tvJavaHeap = cardView.findViewById<TextView>(R.id.tvJavaHeap)
        val tvNativeHeap = cardView.findViewById<TextView>(R.id.tvNativeHeap)
        val tvPss = cardView.findViewById<TextView>(R.id.tvPss)

        // Set model name
        tvModelName.text = metrics.modelDisplayName

        // Set prediction count
        val countText = "${metrics.predictionCount}/${FirestoreRepository.REQUIRED_PREDICTIONS}"
        tvPredictionCount.text = countText

        val isComplete = metrics.predictionCount == FirestoreRepository.REQUIRED_PREDICTIONS

        if (isComplete) {
            // Complete state - show full metrics
            tvPredictionCount.setTextColor(Color.parseColor("#375534")) // forest_green
            tvStatus.text = "COMPLETE"
            tvStatus.setTextColor(Color.parseColor("#375534")) // forest_green
            modelIndicator.setBackgroundResource(R.drawable.bg_indicator_dot)

            metricsSection.visibility = View.VISIBLE
            incompleteSection.visibility = View.GONE

            // Populate metrics
            tvAccuracy.text = String.format("%.1f%%", metrics.averageAccuracy)
            tvLatency.text = formatNumber(metrics.averageLatencyMs) + " ms"
            tvTtft.text = formatNumber(metrics.averageTtftMs) + " ms"
            tvItps.text = formatNumber(metrics.averageItps) + " tok/s"
            tvOtps.text = formatNumber(metrics.averageOtps) + " tok/s"
            tvOet.text = formatNumber(metrics.averageOetMs) + " ms"
            tvJavaHeap.text = formatNumber(metrics.averageJavaHeapKb) + " KB"
            tvNativeHeap.text = formatNumber(metrics.averageNativeHeapKb) + " KB"
            tvPss.text = formatNumber(metrics.averageTotalPssKb) + " KB"

        } else {
            // Incomplete state - show progress only
            tvPredictionCount.setTextColor(Color.parseColor("#6B9071")) // sage_green
            tvStatus.text = if (metrics.predictionCount == 0) "NO DATA" else "IN PROGRESS"
            tvStatus.setTextColor(Color.parseColor("#6B9071")) // sage_green

            // Gray out the indicator
            modelIndicator.alpha = 0.4f

            metricsSection.visibility = View.GONE
            incompleteSection.visibility = View.VISIBLE

            // Card appears slightly faded
            (cardView as MaterialCardView).alpha = 0.85f
        }
    }

    /**
     * Format number with thousands separators
     */
    private fun formatNumber(value: Double): String {
        return numberFormat.format(value.toLong())
    }

    /**
     * Show empty state when no predictions exist
     */
    private fun showEmptyState() {
        scrollViewModels.visibility = View.GONE
        emptyStateContainer.visibility = View.VISIBLE
        modelCardsContainer.removeAllViews()
    }

    /**
     * Show error message
     */
    private fun showError(message: String) {
        scrollViewModels.visibility = View.GONE
        emptyStateContainer.visibility = View.VISIBLE

        // Update empty state text to show error
        findViewById<TextView>(R.id.tvEmptyTitle)?.text = "Error Loading Data"
        findViewById<TextView>(R.id.tvEmptySubtitle)?.text = message
    }

    /**
     * Set loading state
     */
    private fun setLoadingState(loading: Boolean) {
        progressLoading.visibility = if (loading) View.VISIBLE else View.GONE
        tvLoadingText.visibility = if (loading) View.VISIBLE else View.GONE
        btnRefresh.isEnabled = !loading

        if (loading) {
            scrollViewModels.visibility = View.GONE
            emptyStateContainer.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        // Ensure correct nav item is selected when returning
        bottomNavigation.selectedItemId = R.id.nav_comparison
    }
}
