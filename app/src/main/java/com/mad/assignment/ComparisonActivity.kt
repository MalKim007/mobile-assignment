package com.mad.assignment

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.IndexedColors
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
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

    // ════════════════════════════════════════════════════════════════════════
    // RANKING DATA CLASSES - For comparative model insights
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Represents a strength or weakness with comparative ranking
     */
    data class RankedMetric(
        val metricName: String,     // "Recall", "TTFT", etc.
        val rank: Int,              // 1-7 (1 = best)
        val totalModels: Int,       // Total models compared
        val displayValue: String,   // "0.83", "450ms", "5%"
        val isStrength: Boolean     // true = strength, false = weakness
    )

    /**
     * Holds all ranking data for a single model
     */
    data class ModelRankings(
        val modelKey: String,
        val badges: List<String>,           // e.g., ["🥇 Highest Accuracy", "🥈 Fastest Inference"]
        val strengths: List<RankedMetric>,  // Top 2 strengths
        val weaknesses: List<RankedMetric>, // Top 2 weaknesses
        val correctPredictions: Int,
        val totalPredictions: Int
    )

    // Repositories
    private lateinit var firestoreRepository: FirestoreRepository

    // UI Components
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var btnRefresh: Button
    private lateinit var progressLoading: ProgressBar
    private lateinit var tvLoadingText: TextView
    private lateinit var emptyStateContainer: ConstraintLayout

    // Stacked tables UI (3 sections: Quality, Safety, Efficiency)
    // Each section has frozen Model column + scrollable Metrics columns
    private lateinit var tablesScrollView: ScrollView

    // Quality table (Table 2)
    private lateinit var qualityModelColumn: LinearLayout
    private lateinit var qualityMetricsBody: LinearLayout

    // Safety table (Table 3)
    private lateinit var safetyModelColumn: LinearLayout
    private lateinit var safetyMetricsBody: LinearLayout

    // Efficiency table (Table 4)
    private lateinit var efficiencyModelColumn: LinearLayout
    private lateinit var efficiencyMetricsBody: LinearLayout

    private lateinit var modelCardsContainer: LinearLayout
    private lateinit var btnExportCsv: Button
    private lateinit var btnExportExcel: Button

    // Store metrics for export
    private var currentMetricsList: List<ModelAggregateMetrics> = emptyList()

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
        emptyStateContainer = findViewById(R.id.emptyStateContainer)

        // Stacked tables components (frozen Model column + scrollable Metrics)
        tablesScrollView = findViewById(R.id.tablesScrollView)

        // Quality table (Table 2) - frozen + scrollable
        qualityModelColumn = findViewById(R.id.qualityModelColumn)
        qualityMetricsBody = findViewById(R.id.qualityMetricsBody)

        // Safety table (Table 3) - frozen + non-scroll (fits on screen)
        safetyModelColumn = findViewById(R.id.safetyModelColumn)
        safetyMetricsBody = findViewById(R.id.safetyMetricsBody)

        // Efficiency table (Table 4) - frozen + scrollable
        efficiencyModelColumn = findViewById(R.id.efficiencyModelColumn)
        efficiencyMetricsBody = findViewById(R.id.efficiencyMetricsBody)

        modelCardsContainer = findViewById(R.id.modelCardsContainer)
        btnExportCsv = findViewById(R.id.btnExportCsv)
        btnExportExcel = findViewById(R.id.btnExportExcel)

        btnRefresh.setOnClickListener {
            fetchModelMetrics()
        }

        btnExportCsv.setOnClickListener {
            exportMetricsToCsv()
        }

        btnExportExcel.setOnClickListener {
            exportPredictionRecordsToExcel()
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
     * Display model cards with aggregate metrics and comparative rankings
     */
    private fun showModelCards(metricsList: List<ModelAggregateMetrics>) {
        modelCardsContainer.removeAllViews()
        tablesScrollView.visibility = View.VISIBLE
        emptyStateContainer.visibility = View.GONE

        // Show comparison tables (3 stacked sections)
        showComparisonTables(metricsList)

        // Calculate rankings for comparative insights
        val rankings = calculateModelRankings(metricsList)

        metricsList.forEach { metrics ->
            val cardView = LayoutInflater.from(this)
                .inflate(R.layout.item_model_comparison, modelCardsContainer, false)

            // Pass rankings for complete models, null for incomplete
            val modelRankings = rankings[metrics.modelKey]
            populateModelCard(cardView, metrics, modelRankings)
            modelCardsContainer.addView(cardView)
        }
    }

    /**
     * Populate the 3 stacked comparison tables with metrics for all models.
     * Each table has frozen Model column + scrollable Metrics columns.
     * Section 1: Quality Metrics (Table 2)
     * Section 2: Safety Metrics (Table 3)
     * Section 3: Efficiency Metrics (Table 4)
     * Only shows models with complete predictions.
     */
    private fun showComparisonTables(metricsList: List<ModelAggregateMetrics>) {
        // Filter to models with enough predictions to display
        val completeModels = metricsList.filter {
            it.predictionCount >= FirestoreRepository.MINIMUM_FOR_DISPLAY
        }

        if (completeModels.isEmpty()) {
            // Hide table sections but keep scroll view for model cards
            return
        }

        // Store for export
        currentMetricsList = completeModels

        // Clear existing rows in all tables (both model columns and metrics bodies)
        qualityModelColumn.removeAllViews()
        qualityMetricsBody.removeAllViews()
        safetyModelColumn.removeAllViews()
        safetyMetricsBody.removeAllViews()
        efficiencyModelColumn.removeAllViews()
        efficiencyMetricsBody.removeAllViews()

        // Populate each table with frozen model names + scrollable metrics
        completeModels.forEach { metrics ->
            // === QUALITY TABLE (Table 2) ===
            qualityModelColumn.addView(createModelNameCell(metrics.modelDisplayName))
            qualityMetricsBody.addView(createQualityMetricsRow(metrics))

            // === SAFETY TABLE (Table 3) ===
            safetyModelColumn.addView(createModelNameCell(metrics.modelDisplayName))
            safetyMetricsBody.addView(createSafetyMetricsRow(metrics))

            // === EFFICIENCY TABLE (Table 4) ===
            efficiencyModelColumn.addView(createModelNameCell(metrics.modelDisplayName))
            efficiencyMetricsBody.addView(createEfficiencyMetricsRow(metrics))
        }
    }

    /**
     * Create metrics row for the Quality table (Table 2) - without model name
     */
    private fun createQualityMetricsRow(metrics: ModelAggregateMetrics): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dpToPx(4) }

            // Precision (64dp), Recall (56dp), F1-Micro (64dp), F1-Macro (64dp)
            addView(createDataCell(String.format("%.2f", metrics.precision), widthDp = 64))
            addView(createDataCell(String.format("%.2f", metrics.recall), widthDp = 56))
            addView(createDataCell(String.format("%.2f", metrics.f1Micro), widthDp = 64))
            addView(createDataCell(String.format("%.2f", metrics.f1Macro), widthDp = 64))

            // Exact Match (80dp) - percentage
            addView(createDataCell(String.format("%.0f%%", metrics.averageAccuracy), widthDp = 80))

            // Hamming Loss (80dp) - decimal
            addView(createDataCell(String.format("%.3f", metrics.hammingLoss), widthDp = 80))

            // False Neg Rate (80dp) - percentage
            addView(createDataCell(String.format("%.0f%%", metrics.fnr * 100), widthDp = 80))
        }
    }

    /**
     * Create metrics row for the Safety table (Table 3) - without model name
     */
    private fun createSafetyMetricsRow(metrics: ModelAggregateMetrics): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dpToPx(4) }

            // Safety metrics (all percentages) - use weight for even distribution
            addView(createDataCell(String.format("%.0f%%", metrics.hallucinationRate), useWeight = true))
            addView(createDataCell(String.format("%.0f%%", metrics.overPredictionRate), useWeight = true))
            addView(createDataCell(String.format("%.0f%%", metrics.abstentionAccuracy), useWeight = true))
        }
    }

    /**
     * Create metrics row for the Efficiency table (Table 4) - without model name
     */
    private fun createEfficiencyMetricsRow(metrics: ModelAggregateMetrics): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dpToPx(4) }

            // Latency, TTFT, OET (ms - whole numbers)
            addView(createDataCell(String.format("%.0f", metrics.averageLatencyMs)))
            addView(createDataCell(String.format("%.0f", metrics.averageTtftMs)))

            // ITPS, OTPS (tok/s - 1 decimal)
            addView(createDataCell(String.format("%.1f", metrics.averageItps)))
            addView(createDataCell(String.format("%.1f", metrics.averageOtps)))

            // OET (ms)
            addView(createDataCell(String.format("%.0f", metrics.averageOetMs)))

            // Memory (KB - whole numbers)
            addView(createDataCell(String.format("%.0f", metrics.averageJavaHeapKb)))
            addView(createDataCell(String.format("%.0f", metrics.averageNativeHeapKb)))
            addView(createDataCell(String.format("%.0f", metrics.averageTotalPssKb)))
        }
    }

    /**
     * Create a model name cell for frozen column.
     * Row height matches metrics row for proper alignment.
     */
    private fun createModelNameCell(name: String): TextView {
        return TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(120),
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dpToPx(4) }
            text = name
            textSize = 11f
            setTextColor(Color.parseColor("#1A3A1A")) // deep_forest
            typeface = android.graphics.Typeface.create("serif", android.graphics.Typeface.NORMAL)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
    }

    /**
     * Create a data cell for table rows
     */
    private fun createDataCell(value: String, useWeight: Boolean = false, widthDp: Int = 56): TextView {
        return TextView(this).apply {
            layoutParams = if (useWeight) {
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            } else {
                LinearLayout.LayoutParams(dpToPx(widthDp), LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            text = value
            textSize = 11f
            setTextColor(Color.parseColor("#1A3A1A")) // deep_forest
            typeface = android.graphics.Typeface.MONOSPACE
            gravity = android.view.Gravity.CENTER
        }
    }

    /**
     * Export current metrics to CSV file and share
     */
    private fun exportMetricsToCsv() {
        if (currentMetricsList.isEmpty()) {
            Snackbar.make(tablesScrollView, "No data to export", Snackbar.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val fileName = "model_comparison_$timestamp.csv"
                val file = File(getExternalFilesDir(null), fileName)

                file.bufferedWriter().use { writer ->
                    // Header row - Quality Metrics (Table 2)
                    writer.write("Model,Precision,Recall,F1_Micro,F1_Macro,EMR,Hamming_Loss,FNR,")
                    // Safety Metrics (Table 3)
                    writer.write("Hallucination_Rate,Over_Prediction_Rate,Abstention_Accuracy,")
                    // Confusion Matrix
                    writer.write("TP,FP,FN,TN,")
                    // Efficiency Metrics (Table 4)
                    writer.write("Avg_Latency_ms,Avg_TTFT_ms,Avg_ITPS,Avg_OTPS,Avg_OET_ms,")
                    writer.write("Avg_Java_Heap_KB,Avg_Native_Heap_KB,Avg_PSS_KB")
                    writer.newLine()

                    // Data rows
                    currentMetricsList.forEach { m ->
                        writer.write(buildString {
                            // Model name
                            append("\"${m.modelDisplayName}\",")
                            // Quality Metrics (Table 2)
                            append("${String.format("%.4f", m.precision)},")
                            append("${String.format("%.4f", m.recall)},")
                            append("${String.format("%.4f", m.f1Micro)},")
                            append("${String.format("%.4f", m.f1Macro)},")
                            append("${String.format("%.4f", m.averageAccuracy / 100)},")
                            append("${String.format("%.4f", m.hammingLoss)},")
                            append("${String.format("%.4f", m.fnr)},")
                            // Safety Metrics (Table 3)
                            append("${String.format("%.2f", m.hallucinationRate)},")
                            append("${String.format("%.2f", m.overPredictionRate)},")
                            append("${String.format("%.2f", m.abstentionAccuracy)},")
                            // Confusion Matrix
                            append("${m.totalTp},${m.totalFp},${m.totalFn},${m.totalTn},")
                            // Efficiency Metrics (Table 4)
                            append("${String.format("%.1f", m.averageLatencyMs)},")
                            append("${String.format("%.1f", m.averageTtftMs)},")
                            append("${String.format("%.1f", m.averageItps)},")
                            append("${String.format("%.1f", m.averageOtps)},")
                            append("${String.format("%.1f", m.averageOetMs)},")
                            append("${String.format("%.1f", m.averageJavaHeapKb)},")
                            append("${String.format("%.1f", m.averageNativeHeapKb)},")
                            append("${String.format("%.1f", m.averageTotalPssKb)}")
                        })
                        writer.newLine()
                    }
                }

                withContext(Dispatchers.Main) {
                    // Share the file
                    val uri = FileProvider.getUriForFile(
                        this@ComparisonActivity,
                        "${packageName}.fileprovider",
                        file
                    )

                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/csv"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }

                    startActivity(Intent.createChooser(shareIntent, "Export Metrics CSV"))
                }

            } catch (e: Exception) {
                Log.e(TAG, "CSV export failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Snackbar.make(tablesScrollView, "Export failed: ${e.message}", Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Export prediction records to Excel file with separate sheets per model.
     * Section 4 requirement: Individual predictions in Excel with tabs per model.
     */
    private fun exportPredictionRecordsToExcel() {
        Snackbar.make(tablesScrollView, "Fetching prediction records...", Snackbar.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Fetch all prediction records from Firestore
                val allRecords = firestoreRepository.getAllModelPredictionRecords(ModelType.values())

                // Check if there's any data to export
                val hasData = allRecords.values.any { it.isNotEmpty() }
                if (!hasData) {
                    withContext(Dispatchers.Main) {
                        Snackbar.make(tablesScrollView, "No prediction records to export", Snackbar.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // Create Excel workbook
                val workbook = XSSFWorkbook()

                // Create header style
                val headerStyle = workbook.createCellStyle().apply {
                    fillForegroundColor = IndexedColors.LIGHT_GREEN.index
                    fillPattern = FillPatternType.SOLID_FOREGROUND
                    val headerFont = workbook.createFont().apply {
                        bold = true
                    }
                    setFont(headerFont)
                }

                // Create match style (green background)
                val matchStyle = workbook.createCellStyle().apply {
                    fillForegroundColor = IndexedColors.LIGHT_GREEN.index
                    fillPattern = FillPatternType.SOLID_FOREGROUND
                }

                // Create mismatch style (red background)
                val mismatchStyle = workbook.createCellStyle().apply {
                    fillForegroundColor = IndexedColors.ROSE.index
                    fillPattern = FillPatternType.SOLID_FOREGROUND
                }

                // Create a sheet for each model
                ModelType.values().forEach { modelType ->
                    val records = allRecords[modelType.firestoreKey] ?: emptyList()

                    // Create sheet with model name (sanitize for Excel sheet name restrictions)
                    val sheetName = modelType.displayName
                        .replace(Regex("[\\[\\]\\*\\?/\\\\:]"), "_")
                        .take(31) // Excel sheet name max 31 chars
                    val sheet = workbook.createSheet(sheetName)

                    // Create header row
                    val headerRow = sheet.createRow(0)
                    val headers = listOf(
                        "Data ID",
                        "Food Name",
                        "Ingredients",
                        "Ground Truth Allergens",
                        "Predicted Allergens",
                        "Outcome"
                    )
                    headers.forEachIndexed { index, header ->
                        val cell = headerRow.createCell(index)
                        cell.setCellValue(header)
                        cell.cellStyle = headerStyle
                    }

                    // Add data rows
                    records.forEachIndexed { index, record ->
                        val row = sheet.createRow(index + 1)

                        row.createCell(0).setCellValue(record.dataId.toDouble())
                        row.createCell(1).setCellValue(record.name)
                        row.createCell(2).setCellValue(record.ingredients)
                        row.createCell(3).setCellValue(record.groundTruthAllergens)
                        row.createCell(4).setCellValue(record.predictedAllergens)

                        // Outcome cell with conditional formatting
                        val outcomeCell = row.createCell(5)
                        if (record.isMatch) {
                            outcomeCell.setCellValue("Match")
                            outcomeCell.cellStyle = matchStyle
                        } else {
                            outcomeCell.setCellValue("Mismatch")
                            outcomeCell.cellStyle = mismatchStyle
                        }
                    }

                    // Auto-size columns (except ingredients which can be very long)
                    sheet.setColumnWidth(0, 3000)  // Data ID
                    sheet.setColumnWidth(1, 8000)  // Food Name
                    sheet.setColumnWidth(2, 15000) // Ingredients
                    sheet.setColumnWidth(3, 8000)  // Ground Truth
                    sheet.setColumnWidth(4, 8000)  // Predicted
                    sheet.setColumnWidth(5, 3500)  // Outcome
                }

                // Save the workbook
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val fileName = "prediction_records_$timestamp.xlsx"
                val file = File(getExternalFilesDir(null), fileName)

                FileOutputStream(file).use { outputStream ->
                    workbook.write(outputStream)
                }
                workbook.close()

                withContext(Dispatchers.Main) {
                    // Share the file
                    val uri = FileProvider.getUriForFile(
                        this@ComparisonActivity,
                        "${packageName}.fileprovider",
                        file
                    )

                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }

                    startActivity(Intent.createChooser(shareIntent, "Export Prediction Records"))
                }

            } catch (e: Exception) {
                Log.e(TAG, "Excel export failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Snackbar.make(tablesScrollView, "Export failed: ${e.message}", Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Convert dp to pixels
     */
    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    // ════════════════════════════════════════════════════════════════════════
    // RANKING CALCULATION - Compare models and determine rankings
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Calculate rankings for all complete models.
     * Returns a map of modelKey -> ModelRankings
     */
    private fun calculateModelRankings(
        metricsList: List<ModelAggregateMetrics>
    ): Map<String, ModelRankings> {
        // Filter to models with enough predictions to display
        val completeModels = metricsList.filter {
            it.predictionCount >= FirestoreRepository.MINIMUM_FOR_DISPLAY
        }

        if (completeModels.isEmpty()) return emptyMap()

        val totalModels = completeModels.size

        // Define metrics with their extraction functions and display formatters
        // Higher is better: accuracy, precision, recall, otps, abstention
        // Lower is better: latency, ttft, memory, hallucination, fnr, overPrediction
        data class MetricDef(
            val name: String,
            val displayName: String,
            val extract: (ModelAggregateMetrics) -> Double,
            val format: (Double) -> String,
            val higherIsBetter: Boolean,
            val badgeCategory: String? // null = not badge-worthy
        )

        val metricDefinitions = listOf(
            MetricDef("accuracy", "Accuracy", { it.averageAccuracy }, { String.format("%.1f%%", it) }, true, "Highest Accuracy"),
            MetricDef("precision", "Precision", { it.precision }, { String.format("%.2f", it) }, true, null),
            MetricDef("recall", "Recall", { it.recall }, { String.format("%.2f", it) }, true, null),
            MetricDef("latency", "Latency", { it.averageLatencyMs }, { String.format("%.0fms", it) }, false, "Fastest Inference"),
            MetricDef("ttft", "TTFT", { it.averageTtftMs }, { String.format("%.0fms", it) }, false, "Fastest TTFT"),
            MetricDef("otps", "OTPS", { it.averageOtps }, { String.format("%.1f tok/s", it) }, true, null),
            MetricDef("memory", "Memory", { it.averageTotalPssKb }, { String.format("%.0f KB", it) }, false, "Lowest Memory"),
            MetricDef("hallucination", "Hallucination", { it.hallucinationRate }, { String.format("%.0f%%", it) }, false, "Lowest Hallucination"),
            MetricDef("fnr", "False Neg Rate", { it.fnr * 100 }, { String.format("%.0f%%", it) }, false, null),
            MetricDef("overPrediction", "Over-Prediction", { it.overPredictionRate }, { String.format("%.0f%%", it) }, false, null)
        )

        // Calculate ranks for each metric
        // Map: metricName -> (modelKey -> rank)
        val ranksByMetric = mutableMapOf<String, Map<String, Int>>()

        metricDefinitions.forEach { metricDef ->
            val sorted = completeModels.sortedBy { model ->
                val value = metricDef.extract(model)
                if (metricDef.higherIsBetter) -value else value // Negate for descending sort
            }

            val ranks = sorted.mapIndexed { index, model ->
                model.modelKey to (index + 1)
            }.toMap()

            ranksByMetric[metricDef.name] = ranks
        }

        // Build ModelRankings for each model
        return completeModels.associate { model ->
            val modelKey = model.modelKey

            // Collect all ranked metrics for this model
            val allRankedMetrics = metricDefinitions.map { metricDef ->
                val rank = ranksByMetric[metricDef.name]?.get(modelKey) ?: totalModels
                val value = metricDef.extract(model)
                RankedMetric(
                    metricName = metricDef.displayName,
                    rank = rank,
                    totalModels = totalModels,
                    displayValue = metricDef.format(value),
                    isStrength = rank <= 3 // Top 3 = strength territory
                )
            }

            // Generate badges (rank 1 or 2 in badge-worthy categories)
            val badges = mutableListOf<String>()
            metricDefinitions.filter { it.badgeCategory != null }.forEach { metricDef ->
                val rank = ranksByMetric[metricDef.name]?.get(modelKey) ?: totalModels
                if (rank == 1) {
                    badges.add("🥇 ${metricDef.badgeCategory}")
                } else if (rank == 2) {
                    badges.add("🥈 2nd ${metricDef.badgeCategory}")
                }
            }

            // Select top 2 strengths (lowest rank numbers, excluding ties with weaknesses)
            val strengths = allRankedMetrics
                .filter { it.rank <= 3 } // Only consider top 3 positions
                .sortedBy { it.rank }
                .take(2)

            // Select top 2 weaknesses (highest rank numbers)
            val weaknesses = allRankedMetrics
                .filter { it.rank >= totalModels - 2 } // Bottom 3 positions
                .sortedByDescending { it.rank }
                .take(2)
                .map { it.copy(isStrength = false) }

            // Calculate correct predictions from accuracy
            val correctPredictions = (model.averageAccuracy / 100.0 * model.predictionCount).toInt()

            modelKey to ModelRankings(
                modelKey = modelKey,
                badges = badges.take(2), // Max 2 badges
                strengths = strengths,
                weaknesses = weaknesses,
                correctPredictions = correctPredictions,
                totalPredictions = model.predictionCount
            )
        }
    }

    /**
     * Populate a single model card with comparative rankings data
     */
    private fun populateModelCard(
        cardView: View,
        metrics: ModelAggregateMetrics,
        rankings: ModelRankings?
    ) {
        // Header elements
        val tvModelName = cardView.findViewById<TextView>(R.id.tvModelName)
        val tvPredictionCount = cardView.findViewById<TextView>(R.id.tvPredictionCount)
        val tvStatus = cardView.findViewById<TextView>(R.id.tvStatus)
        val modelIndicator = cardView.findViewById<View>(R.id.modelIndicator)

        // Metrics section
        val metricsSection = cardView.findViewById<LinearLayout>(R.id.metricsSection)
        val incompleteSection = cardView.findViewById<ConstraintLayout>(R.id.incompleteSection)

        // New UI elements
        val tvAccuracy = cardView.findViewById<TextView>(R.id.tvAccuracy)
        val tvMatchCount = cardView.findViewById<TextView>(R.id.tvMatchCount)
        val badgesContainer = cardView.findViewById<LinearLayout>(R.id.badgesContainer)
        val tvBadge1 = cardView.findViewById<TextView>(R.id.tvBadge1)
        val tvBadge2 = cardView.findViewById<TextView>(R.id.tvBadge2)
        val tvStrength1 = cardView.findViewById<TextView>(R.id.tvStrength1)
        val tvStrength2 = cardView.findViewById<TextView>(R.id.tvStrength2)
        val tvWeakness1 = cardView.findViewById<TextView>(R.id.tvWeakness1)
        val tvWeakness2 = cardView.findViewById<TextView>(R.id.tvWeakness2)
        val labelStrengths = cardView.findViewById<TextView>(R.id.labelStrengths)
        val labelWeaknesses = cardView.findViewById<TextView>(R.id.labelWeaknesses)
        val strengthsContainer = cardView.findViewById<LinearLayout>(R.id.strengthsContainer)
        val weaknessesContainer = cardView.findViewById<LinearLayout>(R.id.weaknessesContainer)

        // Set model name
        tvModelName.text = metrics.modelDisplayName

        // Set prediction count (always show X/200)
        val countText = "${metrics.predictionCount}/${FirestoreRepository.TOTAL_PREDICTIONS}"
        tvPredictionCount.text = countText

        // Two separate thresholds:
        // - canDisplayMetrics: has enough data to show detailed metrics (>= 20)
        // - isFullyComplete: reached target prediction count (>= 200)
        val canDisplayMetrics = metrics.predictionCount >= FirestoreRepository.MINIMUM_FOR_DISPLAY
        val isFullyComplete = metrics.predictionCount >= FirestoreRepository.TOTAL_PREDICTIONS

        if (canDisplayMetrics && rankings != null) {
            // Has enough data to show detailed metrics
            metricsSection.visibility = View.VISIBLE
            incompleteSection.visibility = View.GONE
            modelIndicator.setBackgroundResource(R.drawable.bg_indicator_dot)

            if (isFullyComplete) {
                // Fully complete (200/200) - green styling
                tvPredictionCount.setTextColor(Color.parseColor("#375534")) // forest_green
                tvStatus.text = "COMPLETE"
                tvStatus.setTextColor(Color.parseColor("#375534")) // forest_green
            } else {
                // In progress but displayable (20-199) - sage styling
                tvPredictionCount.setTextColor(Color.parseColor("#6B9071")) // sage_green
                tvStatus.text = "IN PROGRESS"
                tvStatus.setTextColor(Color.parseColor("#6B9071")) // sage_green
            }

            // Accuracy + Match Count
            tvAccuracy.text = String.format("%.1f%%", metrics.averageAccuracy)
            tvMatchCount.text = "Correct: ${rankings.correctPredictions} / ${rankings.totalPredictions}"

            // === BADGES ===
            if (rankings.badges.isNotEmpty()) {
                badgesContainer.visibility = View.VISIBLE
                tvBadge1.text = rankings.badges[0]
                tvBadge1.visibility = View.VISIBLE

                if (rankings.badges.size > 1) {
                    tvBadge2.text = rankings.badges[1]
                    tvBadge2.visibility = View.VISIBLE
                } else {
                    tvBadge2.visibility = View.GONE
                }
            } else {
                badgesContainer.visibility = View.GONE
            }

            // === STRENGTHS ===
            if (rankings.strengths.isNotEmpty()) {
                labelStrengths.visibility = View.VISIBLE
                strengthsContainer.visibility = View.VISIBLE

                val s1 = rankings.strengths[0]
                tvStrength1.text = "• #${s1.rank} ${s1.metricName} (${s1.displayValue})"
                tvStrength1.visibility = View.VISIBLE

                if (rankings.strengths.size > 1) {
                    val s2 = rankings.strengths[1]
                    tvStrength2.text = "• #${s2.rank} ${s2.metricName} (${s2.displayValue})"
                    tvStrength2.visibility = View.VISIBLE
                } else {
                    tvStrength2.visibility = View.GONE
                }
            } else {
                labelStrengths.visibility = View.GONE
                strengthsContainer.visibility = View.GONE
            }

            // === WEAKNESSES ===
            if (rankings.weaknesses.isNotEmpty()) {
                labelWeaknesses.visibility = View.VISIBLE
                weaknessesContainer.visibility = View.VISIBLE

                val w1 = rankings.weaknesses[0]
                tvWeakness1.text = "• #${w1.rank} ${w1.metricName} (${w1.displayValue})"
                tvWeakness1.visibility = View.VISIBLE

                if (rankings.weaknesses.size > 1) {
                    val w2 = rankings.weaknesses[1]
                    tvWeakness2.text = "• #${w2.rank} ${w2.metricName} (${w2.displayValue})"
                    tvWeakness2.visibility = View.VISIBLE
                } else {
                    tvWeakness2.visibility = View.GONE
                }
            } else {
                labelWeaknesses.visibility = View.GONE
                weaknessesContainer.visibility = View.GONE
            }

        } else {
            // Not enough data to display (<20 predictions)
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
        tablesScrollView.visibility = View.GONE
        emptyStateContainer.visibility = View.VISIBLE
        modelCardsContainer.removeAllViews()
    }

    /**
     * Show error message
     */
    private fun showError(message: String) {
        tablesScrollView.visibility = View.GONE
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
            tablesScrollView.visibility = View.GONE
            emptyStateContainer.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        // Ensure correct nav item is selected when returning
        bottomNavigation.selectedItemId = R.id.nav_comparison
    }
}
