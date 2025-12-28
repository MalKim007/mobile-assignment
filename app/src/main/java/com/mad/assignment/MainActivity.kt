package com.mad.assignment

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.Timestamp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * BITP 3453 Mobile Application Development
 *
 * Enhanced Food Allergen Prediction App
 * - Reads from foodpreprocessed.xlsx (200 items, 20 datasets)
 * - User can select specific items to predict
 * - Batch processing with coroutines (no UI freeze)
 * - Stores results to Firebase Firestore
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val ITEMS_PER_DATASET = 10

        // Load native libraries for LLM inference
        init {
            System.loadLibrary("native-lib")
            System.loadLibrary("ggml-base")
            System.loadLibrary("ggml-cpu")
            System.loadLibrary("llama")
        }
    }

    // Native JNI function declaration
    external fun inferAllergens(input: String): String

    // Repositories
    private lateinit var excelRepository: ExcelDataRepository
    private lateinit var firestoreRepository: FirestoreRepository

    // UI Components
    private lateinit var rootLayout: ConstraintLayout
    private lateinit var spinnerDataset: Spinner
    private lateinit var btnLoadDataset: Button
    private lateinit var tvSelectionInfo: TextView
    private lateinit var selectionButtonsRow: LinearLayout
    private lateinit var btnSelectAll: Button
    private lateinit var btnDeselectAll: Button
    private lateinit var btnStartPrediction: Button
    private lateinit var progressSection: ConstraintLayout
    private lateinit var progressOverall: ProgressBar
    private lateinit var tvProgress: TextView
    private lateinit var btnCancel: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmptyState: TextView
    private lateinit var summarySection: MaterialCardView
    private lateinit var tvSummaryAccuracy: TextView
    private lateinit var tvSummaryMetrics: TextView
    private lateinit var tvSummaryFirestore: TextView

    // Adapter
    private lateinit var adapter: FoodItemAdapter

    // State
    private var currentJob: Job? = null
    private var isProcessing = false
    private var currentDatasetNumber = 1
    private var loadedFoodItems: List<FoodItem> = emptyList()
    private val currentStates = mutableListOf<FoodItemState>()
    private val currentResults = mutableListOf<PredictionResult>()
    private var batchStartTime: Long = 0

    // Allowed allergens for filtering LLM output
    private val allowedAllergens = setOf(
        "milk", "egg", "peanut", "tree nut",
        "wheat", "soy", "fish", "shellfish", "sesame"
    )

    // Map raw model output terms to standardized allergen names
    // Based on dataset analysis: allergensraw → allergensmapped patterns
    private val allergenMapping = mapOf(
        // Direct matches
        "milk" to "milk",
        "egg" to "egg",
        "eggs" to "egg",
        "peanut" to "peanut",
        "peanuts" to "peanut",
        "wheat" to "wheat",
        "soy" to "soy",
        "fish" to "fish",
        "shellfish" to "shellfish",
        "sesame" to "sesame",
        "tree nut" to "tree nut",
        "tree nuts" to "tree nut",
        "treenut" to "tree nut",
        "treenuts" to "tree nut",

        // Raw allergen names from dataset → mapped names
        "crustaceans" to "shellfish",
        "crustacean" to "shellfish",
        "molluscs" to "shellfish",
        "mollusc" to "shellfish",
        "mollusk" to "shellfish",
        "mollusks" to "shellfish",
        "prawn" to "shellfish",
        "prawns" to "shellfish",
        "shrimp" to "shellfish",
        "crab" to "shellfish",
        "lobster" to "shellfish",
        "oyster" to "shellfish",
        "oysters" to "shellfish",

        "gluten" to "wheat",
        "flour" to "wheat",
        "barley" to "wheat",
        "rye" to "wheat",
        "oat" to "wheat",
        "oats" to "wheat",

        "nuts" to "tree nut",
        "nut" to "tree nut",
        "almond" to "tree nut",
        "almonds" to "tree nut",
        "hazelnut" to "tree nut",
        "hazelnuts" to "tree nut",
        "cashew" to "tree nut",
        "cashews" to "tree nut",
        "walnut" to "tree nut",
        "walnuts" to "tree nut",
        "pecan" to "tree nut",
        "pecans" to "tree nut",
        "pistachio" to "tree nut",
        "pistachios" to "tree nut",
        "macadamia" to "tree nut",
        "brazil nut" to "tree nut",

        "soybeans" to "soy",
        "soybean" to "soy",
        "soya" to "soy",
        "lecithin" to "soy",
        "soy lecithin" to "soy",
        "soya lecithin" to "soy",

        "tuna" to "fish",
        "salmon" to "fish",
        "sardine" to "fish",
        "sardines" to "fish",
        "anchovy" to "fish",
        "anchovies" to "fish",
        "pollock" to "fish",
        "cod" to "fish",
        "trout" to "fish",

        "sesame seeds" to "sesame",
        "tahini" to "sesame",

        "dairy" to "milk",
        "cream" to "milk",
        "butter" to "milk",
        "cheese" to "milk",
        "whey" to "milk",
        "lactose" to "milk",
        "casein" to "milk"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Copy model file if needed
        copyModelIfNeeded(this)

        // Initialize
        initRepositories()
        initUI()
        setupSpinner()
        setupRecyclerView()
        setupClickListeners()

        Log.d(TAG, "MainActivity created")
    }

    private fun initRepositories() {
        excelRepository = ExcelDataRepository(this)
        firestoreRepository = FirestoreRepository()
        Log.d(TAG, "Repositories initialized")
    }

    private fun initUI() {
        rootLayout = findViewById(R.id.rootLayout)
        spinnerDataset = findViewById(R.id.spinnerDataset)
        btnLoadDataset = findViewById(R.id.btnLoadDataset)
        tvSelectionInfo = findViewById(R.id.tvSelectionInfo)
        selectionButtonsRow = findViewById(R.id.selectionButtonsRow)
        btnSelectAll = findViewById(R.id.btnSelectAll)
        btnDeselectAll = findViewById(R.id.btnDeselectAll)
        btnStartPrediction = findViewById(R.id.btnStartPrediction)
        progressSection = findViewById(R.id.progressSection)
        progressOverall = findViewById(R.id.progressOverall)
        tvProgress = findViewById(R.id.tvProgress)
        btnCancel = findViewById(R.id.btnCancel)
        recyclerView = findViewById(R.id.recyclerView)
        tvEmptyState = findViewById(R.id.tvEmptyState)
        summarySection = findViewById(R.id.summarySection)
        tvSummaryAccuracy = findViewById(R.id.tvSummaryAccuracy)
        tvSummaryMetrics = findViewById(R.id.tvSummaryMetrics)
        tvSummaryFirestore = findViewById(R.id.tvSummaryFirestore)
    }

    private fun setupSpinner() {
        // Create dataset options: "Dataset 1 (Items 1-10)", "Dataset 2 (Items 11-20)", etc.
        val datasets = (1..20).map { datasetNum ->
            val startId = (datasetNum - 1) * ITEMS_PER_DATASET + 1
            val endId = datasetNum * ITEMS_PER_DATASET
            "Dataset $datasetNum (Items $startId-$endId)"
        }

        val spinnerAdapter = ArrayAdapter(
            this,
            R.layout.spinner_item,
            datasets
        )
        spinnerAdapter.setDropDownViewResource(R.layout.spinner_item)
        spinnerDataset.adapter = spinnerAdapter

        Log.d(TAG, "Spinner setup with ${datasets.size} datasets")
    }

    private fun setupRecyclerView() {
        adapter = FoodItemAdapter(
            onItemClick = { completedState ->
                showDetailDialog(completedState)
            },
            onSelectionChanged = { selectedCount ->
                updateSelectionUI(selectedCount)
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        Log.d(TAG, "RecyclerView setup complete")
    }

    private fun setupClickListeners() {
        btnLoadDataset.setOnClickListener {
            loadDataset()
        }

        btnSelectAll.setOnClickListener {
            adapter.selectAll()
        }

        btnDeselectAll.setOnClickListener {
            adapter.deselectAll()
        }

        btnStartPrediction.setOnClickListener {
            startBatchPrediction()
        }

        btnCancel.setOnClickListener {
            cancelPrediction()
        }
    }

    /**
     * Update UI based on selection count
     */
    private fun updateSelectionUI(selectedCount: Int) {
        btnStartPrediction.isEnabled = selectedCount > 0
        btnStartPrediction.text = "Predict ($selectedCount)"
        tvSelectionInfo.text = "$selectedCount of ${loadedFoodItems.size} items selected"
    }

    /**
     * Load dataset items for selection
     */
    private fun loadDataset() {
        currentDatasetNumber = spinnerDataset.selectedItemPosition + 1
        Log.d(TAG, "Loading dataset $currentDatasetNumber")

        lifecycleScope.launch(Dispatchers.Main) {
            try {
                btnLoadDataset.isEnabled = false
                btnLoadDataset.text = "Loading..."

                // Load from Excel
                loadedFoodItems = withContext(Dispatchers.IO) {
                    excelRepository.getDataset(currentDatasetNumber)
                }

                Log.d(TAG, "Loaded ${loadedFoodItems.size} items")

                // Clear previous state
                adapter.clearSelection()
                currentStates.clear()
                currentResults.clear()
                summarySection.visibility = View.GONE

                // Show items as Selectable
                currentStates.addAll(loadedFoodItems.map { FoodItemState.Selectable(it, false) })
                adapter.submitList(currentStates.toList())

                // Show selection UI
                tvEmptyState.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
                tvSelectionInfo.visibility = View.VISIBLE
                selectionButtonsRow.visibility = View.VISIBLE
                tvSelectionInfo.text = "0 of ${loadedFoodItems.size} items selected"
                btnStartPrediction.isEnabled = false
                btnStartPrediction.text = "Predict (0)"

                showSnackbar("Dataset $currentDatasetNumber loaded - select items to predict", isSuccess = true)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to load dataset: ${e.message}", e)
                showSnackbar("Error loading dataset: ${e.message}", isSuccess = false)
            } finally {
                btnLoadDataset.isEnabled = true
                btnLoadDataset.text = "Load"
            }
        }
    }

    /**
     * Start batch prediction for selected items only
     */
    private fun startBatchPrediction() {
        val selectedIds = adapter.getSelectedIds()
        if (selectedIds.isEmpty()) {
            showSnackbar("Please select at least one item", isSuccess = false)
            return
        }

        // Get selected food items
        val selectedItems = loadedFoodItems.filter { it.id in selectedIds }
        Log.d(TAG, "Starting prediction for ${selectedItems.size} selected items")

        // Cancel any existing job
        currentJob?.cancel()
        currentResults.clear()

        // Update UI state
        setProcessingState(true)
        summarySection.visibility = View.GONE
        batchStartTime = System.currentTimeMillis()

        // Update states: selected items become Pending, others stay Selectable
        currentStates.clear()
        currentStates.addAll(loadedFoodItems.map { item ->
            if (item.id in selectedIds) {
                FoodItemState.Pending(item)
            } else {
                FoodItemState.Selectable(item, false)
            }
        })
        adapter.submitList(currentStates.toList())

        currentJob = lifecycleScope.launch(Dispatchers.Main) {
            try {
                progressOverall.max = selectedItems.size
                progressOverall.progress = 0

                // Process only selected items
                selectedItems.forEachIndexed { index, foodItem ->
                    // Check if cancelled
                    if (!isActive) {
                        Log.d(TAG, "Job cancelled, stopping at index $index")
                        return@launch
                    }

                    // Find position in full list
                    val listPosition = loadedFoodItems.indexOfFirst { it.id == foodItem.id }

                    // Update to Processing state
                    currentStates[listPosition] = FoodItemState.Processing(foodItem)
                    adapter.submitList(currentStates.toList())
                    updateProgress(index, selectedItems.size, foodItem.name)

                    try {
                        // Run inference on background thread
                        val (predictedAllergens, metrics) = withContext(Dispatchers.Default) {
                            performInference(foodItem)
                        }

                        // Compare prediction with ground truth
                        val isMatch = compareAllergens(predictedAllergens, foodItem.allergensMapped)

                        // Create PredictionResult for Firestore
                        val result = PredictionResult(
                            dataId = foodItem.id,
                            name = foodItem.name,
                            ingredients = foodItem.ingredients,
                            allergens = foodItem.allergensRaw,
                            mappedAllergens = foodItem.allergensMapped,
                            predictedAllergens = predictedAllergens,
                            timestamp = Timestamp.now(),
                            metrics = metrics,
                            datasetNumber = currentDatasetNumber,
                            isMatch = isMatch
                        )
                        currentResults.add(result)

                        // Update to Completed state
                        currentStates[listPosition] = FoodItemState.Completed(
                            foodItem = foodItem,
                            predictedAllergens = predictedAllergens,
                            metrics = metrics,
                            isMatch = isMatch
                        )

                        Log.d(TAG, "Item ${foodItem.id} completed: predicted='$predictedAllergens', expected='${foodItem.allergensMapped}', match=$isMatch")

                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing item ${foodItem.id}: ${e.message}")
                        currentStates[listPosition] = FoodItemState.Failed(
                            foodItem = foodItem,
                            errorMessage = e.message ?: "Unknown error"
                        )
                    }

                    // Update list
                    adapter.submitList(currentStates.toList())
                    progressOverall.progress = index + 1
                }

                // Store results to Firestore
                if (currentResults.isNotEmpty()) {
                    tvProgress.text = "Saving to Firebase..."
                    withContext(Dispatchers.IO) {
                        try {
                            firestoreRepository.storeBatchPredictions(currentResults, currentDatasetNumber)
                            Log.d(TAG, "Stored ${currentResults.size} results to Firestore")
                        } catch (e: Exception) {
                            Log.e(TAG, "Firestore error: ${e.message}")
                            throw e
                        }
                    }
                }

                // Show summary
                showSummary(currentDatasetNumber, currentResults)
                showSnackbar("Completed! ${currentResults.size} predictions saved to Firestore", isSuccess = true)

            } catch (e: CancellationException) {
                Log.d(TAG, "Prediction cancelled")
                showSnackbar("Prediction cancelled", isSuccess = false)
            } catch (e: Exception) {
                Log.e(TAG, "Batch prediction failed: ${e.message}", e)
                showSnackbar("Error: ${e.message}", isSuccess = false)
            } finally {
                setProcessingState(false)
            }
        }
    }

    /**
     * Perform LLM inference for a single food item
     */
    private fun performInference(foodItem: FoodItem): Pair<String, InferenceMetrics> {
        val prompt = buildPrompt(foodItem.ingredients)

        // Memory measurements before
        val javaBefore = MemoryReader.javaHeapKb()
        val nativeBefore = MemoryReader.nativeHeapKb()
        val pssBefore = MemoryReader.totalPssKb()

        // Run inference
        val startNs = System.nanoTime()
        val rawResult = inferAllergens(prompt)
        val latencyMs = (System.nanoTime() - startNs) / 1_000_000

        // Memory measurements after
        val javaAfter = MemoryReader.javaHeapKb()
        val nativeAfter = MemoryReader.nativeHeapKb()
        val pssAfter = MemoryReader.totalPssKb()

        // Parse result: TTFT_MS=<val>;ITPS=<val>;OTPS=<val>;OET_MS=<val>|<output>
        val parts = rawResult.split("|", limit = 2)
        val meta = parts[0]
        val rawOutput = if (parts.size > 1) parts[1] else ""

        var ttftMs = -1L
        var itps = -1L
        var otps = -1L
        var oetMs = -1L

        meta.split(";").forEach {
            when {
                it.startsWith("TTFT_MS=") -> ttftMs = it.removePrefix("TTFT_MS=").toLongOrNull() ?: -1L
                it.startsWith("ITPS=") -> itps = it.removePrefix("ITPS=").toLongOrNull() ?: -1L
                it.startsWith("OTPS=") -> otps = it.removePrefix("OTPS=").toLongOrNull() ?: -1L
                it.startsWith("OET_MS=") -> oetMs = it.removePrefix("OET_MS=").toLongOrNull() ?: -1L
            }
        }

        val metrics = InferenceMetrics(
            latencyMs = latencyMs,
            javaHeapKb = javaAfter - javaBefore,
            nativeHeapKb = nativeAfter - nativeBefore,
            totalPssKb = pssAfter - pssBefore,
            ttft = ttftMs,
            itps = itps,
            otps = otps,
            oet = oetMs
        )

        Log.i("SLM_METRICS", "Item ${foodItem.id}: Latency=${metrics.latencyMs}ms | TTFT=${ttftMs}ms | OTPS=${otps} tok/s")

        // Clean raw output
        val cleaned = rawOutput
            .replace("Ġ", "")
            .replace("_", " ")
            .replace("-", " ")
            .lowercase()

        Log.d(TAG, "Raw LLM output for item ${foodItem.id}: $rawOutput")
        Log.d(TAG, "Cleaned output: $cleaned")

        // Extract words/phrases and map to standardized allergens
        val tokens = cleaned
            .split(",", "\n", ";", "and", "&", ":", ".", "(", ")")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val mappedAllergens = mutableSetOf<String>()

        for (token in tokens) {
            // Try direct mapping first
            allergenMapping[token]?.let { mappedAllergens.add(it) }

            // Also check if any mapping key is contained in the token
            for ((key, value) in allergenMapping) {
                if (token.contains(key) && key.length >= 3) {
                    mappedAllergens.add(value)
                }
            }
        }

        // Filter to only allowed allergens (should already be, but safety check)
        val allergens = mappedAllergens
            .filter { it in allowedAllergens }
            .sorted()
            .joinToString(", ")

        Log.d(TAG, "Mapped allergens for item ${foodItem.id}: $allergens")

        return Pair(allergens.ifEmpty { "none" }, metrics)
    }

    /**
     * Build the prompt for allergen detection
     * ULTRA-SIMPLE prompt - small models work better with minimal text
     */
    private fun buildPrompt(ingredients: String): String {
        // Ultra-minimal prompt for small LLM
        return "List allergens in: $ingredients\nAllergens:"
    }

    /**
     * Compare predicted allergens with ground truth
     */
    private fun compareAllergens(predicted: String, groundTruth: String): Boolean {
        val predSet = predicted.lowercase()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "none" }
            .toSet()

        val truthSet = groundTruth.lowercase()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

        return predSet == truthSet
    }

    /**
     * Cancel ongoing prediction
     */
    private fun cancelPrediction() {
        Log.d(TAG, "Cancelling prediction")
        currentJob?.cancel()
        setProcessingState(false)
        showSnackbar("Prediction cancelled", isSuccess = false)
    }

    /**
     * Update UI for processing/idle state
     */
    private fun setProcessingState(processing: Boolean) {
        isProcessing = processing
        btnLoadDataset.isEnabled = !processing
        spinnerDataset.isEnabled = !processing
        btnSelectAll.isEnabled = !processing
        btnDeselectAll.isEnabled = !processing
        btnStartPrediction.isEnabled = !processing
        selectionButtonsRow.visibility = if (processing) View.GONE else View.VISIBLE
        progressSection.visibility = if (processing) View.VISIBLE else View.GONE
    }

    /**
     * Update progress text
     */
    private fun updateProgress(currentIndex: Int, total: Int, itemName: String) {
        tvProgress.text = "Processing item ${currentIndex + 1} of $total\n$itemName"
    }

    /**
     * Show summary statistics after batch completion
     */
    private fun showSummary(datasetNumber: Int, results: List<PredictionResult>) {
        if (results.isEmpty()) return

        val totalTime = System.currentTimeMillis() - batchStartTime
        val matchCount = results.count { it.isMatch }
        val accuracy = if (results.isNotEmpty()) (matchCount * 100.0 / results.size) else 0.0

        val avgLatency = results.map { it.metrics.latencyMs }.average()
        val avgTtft = results.map { it.metrics.ttft }.filter { it > 0 }.average()
        val avgOtps = results.map { it.metrics.otps }.filter { it > 0 }.average()

        tvSummaryAccuracy.text = "Accuracy: $matchCount/${results.size} (${String.format("%.1f", accuracy)}%) correct predictions"

        tvSummaryMetrics.text = String.format(
            "Avg Latency: %.0fms | Avg TTFT: %.0fms | Total Time: %.1fs",
            avgLatency,
            if (avgTtft.isNaN()) 0.0 else avgTtft,
            totalTime / 1000.0
        )

        tvSummaryFirestore.text = "Results saved to Firebase Firestore (Dataset $datasetNumber)"
        tvSummaryFirestore.setTextColor(Color.parseColor("#E3EED4")) // Cream mint for dark background

        summarySection.visibility = View.VISIBLE

        Log.d(TAG, "Summary: accuracy=$accuracy%, avgLatency=$avgLatency, totalTime=$totalTime")
    }

    /**
     * Show detail dialog for a completed prediction
     */
    private fun showDetailDialog(state: FoodItemState.Completed) {
        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_prediction_detail, null)

        val item = state.foodItem
        val metrics = state.metrics

        // Forest theme colors
        val forestGreen = Color.parseColor("#375534")
        val deepForest = Color.parseColor("#0F2A1D")
        val snowWhite = Color.parseColor("#FFFAFA")

        // Populate dialog views
        dialogView.findViewById<TextView>(R.id.tvDetailId).text = "#${item.id}"
        dialogView.findViewById<TextView>(R.id.tvDetailName).text = item.name
        dialogView.findViewById<TextView>(R.id.tvDetailIngredients).text = item.ingredients
        dialogView.findViewById<TextView>(R.id.tvDetailAllergensRaw).text = item.allergensRaw.ifEmpty { "none" }
        dialogView.findViewById<TextView>(R.id.tvDetailAllergensMapped).text = item.allergensMapped.ifEmpty { "none" }

        val tvPredicted = dialogView.findViewById<TextView>(R.id.tvDetailPredicted)
        tvPredicted.text = state.predictedAllergens.ifEmpty { "none" }

        val tvMatchStatus = dialogView.findViewById<TextView>(R.id.tvDetailMatchStatus)
        if (state.isMatch) {
            tvMatchStatus.text = "MATCH"
            tvMatchStatus.setTextColor(snowWhite)
            tvMatchStatus.setBackgroundResource(R.drawable.bg_status_match)
            tvPredicted.setTextColor(forestGreen)
        } else {
            tvMatchStatus.text = "MISMATCH"
            tvMatchStatus.setTextColor(snowWhite)
            tvMatchStatus.setBackgroundResource(R.drawable.bg_status_mismatch)
            tvPredicted.setTextColor(deepForest)
        }

        // Metrics
        val metricsText = """
            Latency:      ${metrics.latencyMs} ms
            TTFT:         ${metrics.ttft} ms
            ITPS:         ${metrics.itps} tok/s
            OTPS:         ${metrics.otps} tok/s
            OET:          ${metrics.oet} ms

            Java Heap:    ${metrics.javaHeapKb} KB
            Native Heap:  ${metrics.nativeHeapKb} KB
            Total PSS:    ${metrics.totalPssKb} KB
        """.trimIndent()
        dialogView.findViewById<TextView>(R.id.tvDetailMetrics).text = metricsText

        // Timestamp
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        dialogView.findViewById<TextView>(R.id.tvDetailTimestamp).text =
            "Analyzed: ${dateFormat.format(System.currentTimeMillis())}"

        // Create dialog without title
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        // Make dialog background transparent to show our rounded corners
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Wire up the close button
        dialogView.findViewById<Button>(R.id.btnClose).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    /**
     * Show snackbar message from top
     */
    private fun showSnackbar(message: String, isSuccess: Boolean) {
        val snackbar = Snackbar.make(rootLayout, message, Snackbar.LENGTH_LONG)
        if (isSuccess) {
            snackbar.setBackgroundTint(Color.parseColor("#375534")) // Forest green
        } else {
            snackbar.setBackgroundTint(Color.parseColor("#0F2A1D")) // Deep forest
        }
        snackbar.setTextColor(Color.parseColor("#FFFAFA")) // Snow white text

        // Position at top
        val view = snackbar.view
        val params = view.layoutParams as android.widget.FrameLayout.LayoutParams
        params.gravity = android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
        params.topMargin = 100
        view.layoutParams = params

        snackbar.show()
    }

    /**
     * Copy model file from assets to internal storage if needed
     */
    private fun copyModelIfNeeded(context: Context) {
        val modelName = "qwen2.5-1.5b-instruct-q4_k_m.gguf"
        val outFile = File(context.filesDir, modelName)

        if (outFile.exists()) {
            Log.d(TAG, "Model already exists: ${outFile.absolutePath}")
            return
        }

        Log.d(TAG, "Copying model to: ${outFile.absolutePath}")
        context.assets.open(modelName).use { input ->
            outFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        Log.d(TAG, "Model copied successfully")
    }

    override fun onDestroy() {
        super.onDestroy()
        currentJob?.cancel()
        excelRepository.clearCache()
        Log.d(TAG, "MainActivity destroyed")
    }
}
