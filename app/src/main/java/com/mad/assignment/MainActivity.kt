package com.mad.assignment

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
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
import com.google.android.material.bottomnavigation.BottomNavigationView
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
 * Enum representing available LLM models with their configurations.
 * @property displayName User-friendly name shown in UI
 * @property fileName The GGUF file name in assets/filesDir
 * @property templateType 0 = ChatML (Qwen, SmolLM2), 1 = Gemma format
 * @property firestoreKey Key used for Firestore collection path
 */
enum class ModelType(
    val displayName: String,
    val fileName: String,
    val templateType: Int,
    val firestoreKey: String
) {
    QWEN_2_5("Qwen 2.5 1.5B", "qwen2.5-1.5b-instruct-q4_k_m.gguf", 0, "qwen_2_5_1_5b"),
    GEMMA_3("Gemma 3 1B", "gemma-3-1b-it-Q4_K_M.gguf", 1, "gemma_3_1b"),
    SMOLLM2("SmolLM2 1.7B", "smollm2-1.7b-instruct-q4_k_m.gguf", 0, "smollm2_1_7b")
}

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
    external fun inferAllergens(input: String, modelPath: String, templateType: Int): String

    // Repositories
    private lateinit var dataRepository: JsonDataRepository
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

    // Model selection UI
    private lateinit var spinnerModel: Spinner
    private lateinit var tvModelStatus: TextView
    private lateinit var btnRunAll: Button

    // Bottom Navigation
    private lateinit var bottomNavigation: BottomNavigationView

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

    // Model selection state
    private var selectedModelType: ModelType? = null
    private var loadingDialog: AlertDialog? = null

    // Allowed allergens for filtering LLM output
    private val allowedAllergens = setOf(
        "milk", "egg", "peanut", "tree nut",
        "wheat", "soy", "fish", "shellfish", "sesame"
    )

    // Basic normalization only - model must output correct category names
    // Only handles plurals and common formatting variations
    private val allergenMapping = mapOf(
        // Identity mappings (for completeness)
        "milk" to "milk",
        "egg" to "egg",
        "peanut" to "peanut",
        "wheat" to "wheat",
        "soy" to "soy",
        "fish" to "fish",
        "shellfish" to "shellfish",
        "sesame" to "sesame",
        "tree nut" to "tree nut",

        // Plural handling only
        "eggs" to "egg",
        "peanuts" to "peanut",
        "tree nuts" to "tree nut",

        // Common formatting variations
        "treenut" to "tree nut",
        "treenuts" to "tree nut",
        "tree-nut" to "tree nut",
        "tree-nuts" to "tree nut"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize
        initRepositories()
        initUI()
        setupSpinner()
        setupModelSpinner()
        setupBottomNavigation()
        setupRecyclerView()
        setupClickListeners()

        // Copy all models on first launch with loading dialog
        showLoadingDialogAndCopyModels()

        Log.d(TAG, "MainActivity created")
    }

    private fun initRepositories() {
        dataRepository = JsonDataRepository(this)
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

        // Model selection UI
        spinnerModel = findViewById(R.id.spinnerModel)
        tvModelStatus = findViewById(R.id.tvModelStatus)
        btnRunAll = findViewById(R.id.btnRunAll)

        // Bottom Navigation
        bottomNavigation = findViewById(R.id.bottomNavigation)
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

    /**
     * Setup model selection spinner with placeholder and model options
     */
    private fun setupModelSpinner() {
        // Create adapter with placeholder + model names
        val modelOptions = mutableListOf("-- Select Model --")
        modelOptions.addAll(ModelType.values().map { it.displayName })

        val modelAdapter = ArrayAdapter(
            this,
            R.layout.spinner_item,
            modelOptions
        )
        modelAdapter.setDropDownViewResource(R.layout.spinner_item)
        spinnerModel.adapter = modelAdapter

        spinnerModel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == 0) {
                    // Placeholder selected
                    selectedModelType = null
                    tvModelStatus.text = "No model selected"
                    tvModelStatus.setTextColor(Color.parseColor("#C86464")) // status_error
                } else {
                    // Actual model selected
                    selectedModelType = ModelType.values()[position - 1]
                    tvModelStatus.text = "Ready: ${selectedModelType?.displayName}"
                    tvModelStatus.setTextColor(Color.parseColor("#375534")) // forest_green
                    Log.d(TAG, "Model selected: ${selectedModelType?.displayName}")
                }
                updateButtonStates()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedModelType = null
                updateButtonStates()
            }
        }

        Log.d(TAG, "Model spinner setup complete")
    }

    /**
     * Setup bottom navigation for switching between screens
     */
    private fun setupBottomNavigation() {
        bottomNavigation.selectedItemId = R.id.nav_home

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_comparison -> {
                    startActivity(Intent(this, ComparisonActivity::class.java))
                    overridePendingTransition(0, 0)
                    true
                }
                else -> false
            }
        }
    }

    /**
     * Show loading dialog and copy all models on first launch
     */
    private fun showLoadingDialogAndCopyModels() {
        val models = ModelType.values()
        val allModelsExist = models.all { File(filesDir, it.fileName).exists() }

        if (allModelsExist) {
            Log.d(TAG, "All models already exist, skipping copy")
            setUIEnabled(true)
            return
        }

        // Show loading dialog
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_loading_models, null)
        val progressLoading = dialogView.findViewById<ProgressBar>(R.id.progressLoading)
        val tvLoadingProgress = dialogView.findViewById<TextView>(R.id.tvLoadingProgress)

        progressLoading.max = models.size
        progressLoading.progress = 0

        loadingDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        loadingDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        loadingDialog?.show()

        // Disable UI while copying
        setUIEnabled(false)

        // Copy models in background
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                models.forEachIndexed { index, modelType ->
                    withContext(Dispatchers.Main) {
                        tvLoadingProgress.text = "Loading ${modelType.displayName} (${index + 1}/${models.size})..."
                        progressLoading.progress = index
                    }

                    val outFile = File(filesDir, modelType.fileName)
                    if (!outFile.exists()) {
                        Log.d(TAG, "Copying model: ${modelType.fileName}")
                        assets.open(modelType.fileName).use { input ->
                            outFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        Log.d(TAG, "Model copied: ${modelType.fileName}")
                    } else {
                        Log.d(TAG, "Model already exists: ${modelType.fileName}")
                    }
                }

                withContext(Dispatchers.Main) {
                    progressLoading.progress = models.size
                    tvLoadingProgress.text = "All models ready!"
                    kotlinx.coroutines.delay(500) // Brief pause to show completion
                    loadingDialog?.dismiss()
                    loadingDialog = null
                    setUIEnabled(true)
                    Log.d(TAG, "All models ready")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error copying models: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    loadingDialog?.dismiss()
                    loadingDialog = null
                    showSnackbar("Error loading models: ${e.message}", isSuccess = false)
                    tvModelStatus.text = "Error loading models"
                }
            }
        }
    }

    /**
     * Enable/disable UI during model loading
     */
    private fun setUIEnabled(enabled: Boolean) {
        spinnerDataset.isEnabled = enabled
        spinnerModel.isEnabled = enabled
        btnLoadDataset.isEnabled = enabled && selectedModelType != null
        btnStartPrediction.isEnabled = enabled && selectedModelType != null && adapter.getSelectedIds().isNotEmpty()
    }

    /**
     * Update button states based on model selection
     */
    private fun updateButtonStates() {
        val modelSelected = selectedModelType != null
        val itemsSelected = if (::adapter.isInitialized) adapter.getSelectedIds().isNotEmpty() else false
        val datasetLoaded = loadedFoodItems.isNotEmpty()

        // Load button requires model to be selected
        btnLoadDataset.isEnabled = modelSelected && !isProcessing

        // Predict button requires model AND items selected
        btnStartPrediction.isEnabled = modelSelected && itemsSelected && !isProcessing

        // Run All button requires model to be selected
        btnRunAll.isEnabled = modelSelected && !isProcessing

        // Selection buttons require dataset loaded
        if (::btnSelectAll.isInitialized) {
            btnSelectAll.isEnabled = datasetLoaded && !isProcessing
            btnDeselectAll.isEnabled = datasetLoaded && !isProcessing
        }
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

        btnRunAll.setOnClickListener {
            runAllPredictions()
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

                // Load from JSON
                loadedFoodItems = withContext(Dispatchers.IO) {
                    dataRepository.getDataset(currentDatasetNumber)
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
                            isMatch = isMatch,
                            modelName = selectedModelType?.displayName ?: "Unknown"
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

                // Store results to Firestore using model-specific collection
                if (currentResults.isNotEmpty()) {
                    tvProgress.text = "Saving to Firebase..."
                    withContext(Dispatchers.IO) {
                        try {
                            firestoreRepository.storeBatchPredictions(
                                currentResults,
                                selectedModelType!!.firestoreKey
                            )
                            Log.d(TAG, "Stored ${currentResults.size} results to Firestore for model ${selectedModelType?.firestoreKey}")
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
        val modelType = selectedModelType
            ?: throw IllegalStateException("No model selected")

        val prompt = buildPrompt(foodItem.ingredients)
        val modelPath = "${filesDir.absolutePath}/${modelType.fileName}"

        // Verify model file exists
        val modelFile = File(modelPath)
        if (!modelFile.exists()) {
            throw IllegalStateException("Model file not found: ${modelType.displayName}")
        }

        // Memory measurements before
        val javaBefore = MemoryReader.javaHeapKb()
        val nativeBefore = MemoryReader.nativeHeapKb()
        val pssBefore = MemoryReader.totalPssKb()

        // Run inference with selected model
        val startNs = System.nanoTime()
        val rawResult = inferAllergens(prompt, modelPath, modelType.templateType)
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
            oet = oetMs,
            modelName = modelType.displayName
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
            // Direct mapping only - model must output correct category names
            allergenMapping[token]?.let { mappedAllergens.add(it) }

            // Also check if token directly matches an allowed allergen
            if (token in allowedAllergens) {
                mappedAllergens.add(token)
            }
        }

        // Filter to only allowed allergens (safety check)
        val allergens = mappedAllergens
            .filter { it in allowedAllergens }
            .sorted()
            .joinToString(", ")

        Log.d(TAG, "Mapped allergens for item ${foodItem.id}: $allergens")

        return Pair(allergens.ifEmpty { "none" }, metrics)
    }

    /**
     * Build the prompt for allergen detection
     * Lists exact categories so model outputs standardized terms
     */
    private fun buildPrompt(ingredients: String): String {
        return """Categories: milk,egg,peanut,tree nut,wheat,soy,fish,shellfish,sesame
Ingredients: $ingredients
Allergens:"""
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
     * Run predictions for all 200 items at once.
     * Loads all datasets and processes every item sequentially.
     */
    private fun runAllPredictions() {
        if (selectedModelType == null) {
            showSnackbar("Please select a model first", isSuccess = false)
            return
        }

        Log.d(TAG, "Starting Run All 200 predictions for ${selectedModelType?.displayName}")

        // Cancel any existing job
        currentJob?.cancel()
        currentResults.clear()
        currentStates.clear()

        // Update UI state
        setProcessingState(true)
        summarySection.visibility = View.GONE
        batchStartTime = System.currentTimeMillis()

        // Hide selection UI elements during run all
        tvSelectionInfo.visibility = View.GONE
        recyclerView.visibility = View.GONE
        tvEmptyState.visibility = View.GONE

        currentJob = lifecycleScope.launch(Dispatchers.Main) {
            try {
                // Load all 200 items from JSON
                tvProgress.text = "Loading all 200 items..."
                val allItems = withContext(Dispatchers.IO) {
                    dataRepository.getAllItems()
                }

                Log.d(TAG, "Loaded ${allItems.size} items for Run All")

                if (allItems.isEmpty()) {
                    showSnackbar("No items found in dataset", isSuccess = false)
                    return@launch
                }

                progressOverall.max = allItems.size
                progressOverall.progress = 0

                // Process all items
                allItems.forEachIndexed { index, foodItem ->
                    // Check if cancelled
                    if (!isActive) {
                        Log.d(TAG, "Run All cancelled at index $index")
                        return@launch
                    }

                    updateProgress(index, allItems.size, foodItem.name)

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
                            datasetNumber = (foodItem.id - 1) / ITEMS_PER_DATASET + 1,
                            isMatch = isMatch,
                            modelName = selectedModelType?.displayName ?: "Unknown"
                        )
                        currentResults.add(result)

                        Log.d(TAG, "Item ${foodItem.id} completed: match=$isMatch")

                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing item ${foodItem.id}: ${e.message}")
                        // Continue with next item even if one fails
                    }

                    progressOverall.progress = index + 1
                }

                // Store all results to Firestore
                if (currentResults.isNotEmpty()) {
                    tvProgress.text = "Saving ${currentResults.size} results to Firebase..."
                    withContext(Dispatchers.IO) {
                        try {
                            firestoreRepository.storeBatchPredictions(
                                currentResults,
                                selectedModelType!!.firestoreKey
                            )
                            Log.d(TAG, "Stored ${currentResults.size} results to Firestore")
                        } catch (e: Exception) {
                            Log.e(TAG, "Firestore error: ${e.message}")
                            throw e
                        }
                    }
                }

                // Show summary
                showRunAllSummary(currentResults)
                showSnackbar("Completed! ${currentResults.size}/200 predictions saved", isSuccess = true)

            } catch (e: CancellationException) {
                Log.d(TAG, "Run All cancelled")
                showSnackbar("Run All cancelled", isSuccess = false)
            } catch (e: Exception) {
                Log.e(TAG, "Run All failed: ${e.message}", e)
                showSnackbar("Error: ${e.message}", isSuccess = false)
            } finally {
                setProcessingState(false)
            }
        }
    }

    /**
     * Show summary for Run All operation
     */
    private fun showRunAllSummary(results: List<PredictionResult>) {
        if (results.isEmpty()) return

        val totalTime = System.currentTimeMillis() - batchStartTime
        val matchCount = results.count { it.isMatch }
        val accuracy = (matchCount * 100.0 / results.size)
        val modelName = selectedModelType?.displayName ?: "Unknown"

        val avgLatency = results.map { it.metrics.latencyMs }.average()
        val avgTtft = results.map { it.metrics.ttft }.filter { it > 0 }.average()
        val avgOtps = results.map { it.metrics.otps }.filter { it > 0 }.average()

        tvSummaryAccuracy.text = "Accuracy: $matchCount/${results.size} (${String.format("%.1f", accuracy)}%) correct predictions"

        tvSummaryMetrics.text = String.format(
            "Model: %s\nAvg Latency: %.0fms | Avg TTFT: %.0fms\nTotal Time: %.1f minutes",
            modelName,
            avgLatency,
            if (avgTtft.isNaN()) 0.0 else avgTtft,
            totalTime / 60000.0
        )

        tvSummaryFirestore.text = "All 200 predictions saved to Firebase"
        tvSummaryFirestore.setTextColor(Color.parseColor("#E3EED4"))

        summarySection.visibility = View.VISIBLE

        Log.d(TAG, "Run All Summary: accuracy=$accuracy%, totalTime=${totalTime}ms")
    }

    /**
     * Update UI for processing/idle state
     */
    private fun setProcessingState(processing: Boolean) {
        isProcessing = processing
        btnLoadDataset.isEnabled = !processing && selectedModelType != null
        spinnerDataset.isEnabled = !processing
        spinnerModel.isEnabled = !processing  // Lock model selector during processing
        btnSelectAll.isEnabled = !processing
        btnDeselectAll.isEnabled = !processing
        btnStartPrediction.isEnabled = !processing && selectedModelType != null
        btnRunAll.isEnabled = !processing && selectedModelType != null
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
        val modelName = selectedModelType?.displayName ?: "Unknown"

        val avgLatency = results.map { it.metrics.latencyMs }.average()
        val avgTtft = results.map { it.metrics.ttft }.filter { it > 0 }.average()
        val avgOtps = results.map { it.metrics.otps }.filter { it > 0 }.average()

        tvSummaryAccuracy.text = "Accuracy: $matchCount/${results.size} (${String.format("%.1f", accuracy)}%) correct predictions"

        tvSummaryMetrics.text = String.format(
            "Model: %s\nAvg Latency: %.0fms | Avg TTFT: %.0fms | Total: %.1fs",
            modelName,
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

    override fun onResume() {
        super.onResume()
        // Ensure correct nav item is selected when returning
        bottomNavigation.selectedItemId = R.id.nav_home
    }

    override fun onDestroy() {
        super.onDestroy()
        currentJob?.cancel()
        dataRepository.clearCache()
        Log.d(TAG, "MainActivity destroyed")
    }
}
