package com.mad.assignment

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Data class to hold aggregate metrics for a model.
 * Used for comparison screen to display average performance across all predictions.
 * Includes prediction quality metrics (Precision, Recall, F1, etc.) per lecturer requirements.
 */
data class ModelAggregateMetrics(
    val modelKey: String,
    val modelDisplayName: String,
    val predictionCount: Int,
    val averageAccuracy: Double,        // EMR (Exact Match Ratio) as percentage
    val averageLatencyMs: Double,
    val averageTtftMs: Double,
    val averageItps: Double,
    val averageOtps: Double,
    val averageOetMs: Double,
    val averageJavaHeapKb: Double,
    val averageNativeHeapKb: Double,
    val averageTotalPssKb: Double,
    // Prediction Quality Metrics (Table 2)
    val totalTp: Int = 0,               // Aggregate True Positives
    val totalFp: Int = 0,               // Aggregate False Positives
    val totalFn: Int = 0,               // Aggregate False Negatives
    val totalTn: Int = 0,               // Aggregate True Negatives
    val precision: Double = 0.0,        // TP / (TP + FP)
    val recall: Double = 0.0,           // TP / (TP + FN)
    val f1Micro: Double = 0.0,          // 2TP / (2TP + FP + FN)
    val f1Macro: Double = 0.0,          // Average F1 per allergen
    val hammingLoss: Double = 0.0,      // (FP + FN) / (N * L)
    val fnr: Double = 0.0,              // FN / (TP + FN) = 1 - Recall
    // Safety-Oriented Metrics (Table 3)
    val hallucinationRate: Double = 0.0,   // % predictions with hallucinated allergens (0-100)
    val overPredictionRate: Double = 0.0,  // % predictions with FP > 0 (0-100)
    val abstentionAccuracy: Double = 0.0   // TNR for no-allergen cases (0-100)
)

/**
 * Confusion matrix counts for a single prediction.
 * Used for calculating aggregate quality metrics.
 */
data class ConfusionCounts(
    val tp: Int,    // Allergens correctly predicted (in both predicted AND ground truth)
    val fp: Int,    // Allergens incorrectly predicted (in predicted but NOT in ground truth)
    val fn: Int,    // Allergens missed (in ground truth but NOT in predicted)
    val tn: Int     // Allergens correctly not predicted (in NEITHER)
)

/**
 * Per-allergen confusion counts for F1 Macro calculation.
 * Tracks TP/FP/FN/TN for each of the 9 allergen categories.
 */
data class PerAllergenCounts(
    val allergen: String,
    var tp: Int = 0,
    var fp: Int = 0,
    var fn: Int = 0,
    var tn: Int = 0
)

/**
 * Individual prediction record for Excel export (Section 4 requirements).
 * Each record represents one food item's prediction result.
 */
data class PredictionRecord(
    val dataId: Int,
    val name: String,
    val ingredients: String,
    val groundTruthAllergens: String,
    val predictedAllergens: String,
    val isMatch: Boolean
)

/**
 * Repository for Firebase Firestore operations.
 *
 * New structure: /models/{modelKey}/predictions/{dataId}
 * - Each model has its own subcollection with max 200 documents
 * - Document ID = dataId (1-200) for upsert behavior
 * - Re-running same IDs overwrites previous data (no duplicates)
 */
class FirestoreRepository {

    companion object {
        private const val TAG = "FirestoreRepository"
        private const val COLLECTION_MODELS = "models"
        private const val SUBCOLLECTION_PREDICTIONS = "predictions"

        // Minimum predictions needed to display detailed metrics (for testing)
        const val MINIMUM_FOR_DISPLAY = 20

        // Total predictions required for "COMPLETE" status (always 200)
        const val TOTAL_PREDICTIONS = 200
    }

    private val db: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance()
    }

    /**
     * The 9 standard allergen categories for multi-label classification.
     */
    private val allAllergens = listOf(
        "milk", "egg", "peanut", "tree nut",
        "wheat", "soy", "fish", "shellfish", "sesame"
    )

    /**
     * Keyword mapping for hallucination detection (Table 3).
     * Maps each allergen to ingredient keywords that indicate its presence.
     */
    private val allergenKeywords = mapOf(
        "milk" to listOf("milk", "cream", "butter", "cheese", "whey", "casein", "lactose", "dairy", "yogurt", "ghee", "curd", "buttermilk"),
        "egg" to listOf("egg", "albumin", "mayonnaise", "meringue", "ovum", "lysozyme", "ovalbumin"),
        "peanut" to listOf("peanut", "groundnut", "arachis", "monkey nut"),
        "tree nut" to listOf("almond", "walnut", "cashew", "pecan", "pistachio", "hazelnut", "macadamia", "brazil nut", "chestnut", "nut", "praline", "marzipan", "nougat"),
        "wheat" to listOf("wheat", "flour", "gluten", "semolina", "durum", "spelt", "bulgur", "couscous", "bread", "pasta", "noodle", "cereal", "bran", "starch"),
        "soy" to listOf("soy", "soya", "tofu", "edamame", "miso", "tempeh", "lecithin"),
        "fish" to listOf("fish", "anchovy", "sardine", "tuna", "salmon", "cod", "bass", "mackerel", "tilapia", "trout", "herring", "haddock"),
        "shellfish" to listOf("shrimp", "prawn", "crab", "lobster", "crayfish", "oyster", "mussel", "clam", "scallop", "crustacean", "mollusk", "squid", "octopus"),
        "sesame" to listOf("sesame", "tahini", "halvah", "hummus")
    )

    /**
     * Calculate confusion matrix counts for a single prediction.
     * Compares predicted allergens against ground truth to get TP, FP, FN, TN.
     *
     * @param predicted Comma-separated predicted allergens string
     * @param groundTruth Comma-separated ground truth allergens string
     * @return ConfusionCounts with TP, FP, FN, TN values
     */
    private fun calculateConfusionCounts(predicted: String, groundTruth: String): ConfusionCounts {
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

        val tp = predSet.intersect(truthSet).size
        val fp = (predSet - truthSet).size
        val fn = (truthSet - predSet).size
        val tn = 9 - tp - fp - fn  // 9 allergen categories total

        return ConfusionCounts(tp, fp, fn, tn)
    }

    /**
     * Calculate per-allergen confusion counts across all predictions.
     * Used for F1 Macro calculation which averages F1 scores per allergen category.
     *
     * @param predictions List of (predicted, groundTruth) string pairs
     * @return Map of allergen name to PerAllergenCounts
     */
    private fun calculatePerAllergenCounts(
        predictions: List<Pair<String, String>>
    ): Map<String, PerAllergenCounts> {
        val counts = allAllergens.associateWith { PerAllergenCounts(it) }.toMutableMap()

        predictions.forEach { (predicted, groundTruth) ->
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

            // For each allergen, determine if it's TP, FP, FN, or TN
            allAllergens.forEach { allergen ->
                val inPred = allergen in predSet
                val inTruth = allergen in truthSet
                when {
                    inPred && inTruth -> counts[allergen]!!.tp++
                    inPred && !inTruth -> counts[allergen]!!.fp++
                    !inPred && inTruth -> counts[allergen]!!.fn++
                    else -> counts[allergen]!!.tn++
                }
            }
        }

        return counts
    }

    /**
     * Calculate F1 Macro: average of per-allergen F1 scores.
     * Treats all allergen categories equally regardless of frequency.
     *
     * @param perAllergenCounts Map of allergen to PerAllergenCounts
     * @return F1 Macro score (0.0 to 1.0)
     */
    private fun calculateF1Macro(perAllergenCounts: Map<String, PerAllergenCounts>): Double {
        val f1Scores = perAllergenCounts.values.map { c ->
            val precision = if (c.tp + c.fp > 0) c.tp.toDouble() / (c.tp + c.fp) else 0.0
            val recall = if (c.tp + c.fn > 0) c.tp.toDouble() / (c.tp + c.fn) else 0.0
            if (precision + recall > 0) 2 * precision * recall / (precision + recall) else 0.0
        }
        return if (f1Scores.isNotEmpty()) f1Scores.average() else 0.0
    }

    // ==================== SAFETY-ORIENTED METRICS (Table 3) ====================

    /**
     * Check if an allergen can be derived from the ingredient list.
     * Uses keyword matching against the allergenKeywords map.
     *
     * @param allergen The allergen to check (e.g., "milk")
     * @param ingredients The raw ingredients text
     * @return true if any keyword for this allergen is found in ingredients
     */
    private fun isAllergenInIngredients(allergen: String, ingredients: String): Boolean {
        val keywords = allergenKeywords[allergen.lowercase()] ?: return false
        val ingredientsLower = ingredients.lowercase()
        return keywords.any { ingredientsLower.contains(it) }
    }

    /**
     * Check if a prediction contains hallucinated allergens.
     * Hallucination = predicted allergen that cannot be derived from ingredients.
     *
     * @param predictedAllergens Comma-separated predicted allergens
     * @param ingredients Raw ingredients text
     * @return true if ANY predicted allergen is not derivable from ingredients
     */
    private fun hasHallucination(predictedAllergens: String, ingredients: String): Boolean {
        if (predictedAllergens.isBlank()) return false
        val predicted = predictedAllergens.lowercase()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "none" }
        return predicted.any { !isAllergenInIngredients(it, ingredients) }
    }

    /**
     * Check if a prediction has over-prediction (extra allergens beyond ground truth).
     * Over-prediction = FP > 0 (predicted allergens not in ground truth).
     *
     * @param predictedAllergens Comma-separated predicted allergens
     * @param groundTruth Comma-separated ground truth allergens
     * @return true if there are any false positives
     */
    private fun hasOverPrediction(predictedAllergens: String, groundTruth: String): Boolean {
        val predSet = predictedAllergens.lowercase()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "none" }
            .toSet()
        val truthSet = groundTruth.lowercase()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        return (predSet - truthSet).isNotEmpty()
    }

    /**
     * Store multiple prediction results in a batch operation.
     * Uses model-specific subcollections with document ID = dataId for upsert behavior.
     *
     * Path: /models/{modelKey}/predictions/{dataId}
     * Using set() instead of add() - creates OR replaces (no duplicates)
     *
     * @param results List of PredictionResult objects to store
     * @param modelKey The Firestore key for the model (e.g., "qwen_2_5_1_5b")
     * @throws Exception if batch write fails
     */
    suspend fun storeBatchPredictions(
        results: List<PredictionResult>,
        modelKey: String
    ) {
        if (results.isEmpty()) {
            Log.w(TAG, "No results to store for model $modelKey")
            return
        }

        Log.d(TAG, "Storing ${results.size} predictions for model $modelKey")

        val batch = db.batch()

        results.forEach { result ->
            // Path: /models/{modelKey}/predictions/{dataId}
            val docRef = db.collection(COLLECTION_MODELS)
                .document(modelKey)
                .collection(SUBCOLLECTION_PREDICTIONS)
                .document(result.dataId.toString())

            // set() creates OR replaces - no duplicates
            batch.set(docRef, result.toFirestoreMap())
        }

        batch.commit().await()

        Log.d(TAG, "Successfully stored ${results.size} predictions for model $modelKey")
    }

    /**
     * Get count of predictions stored for a model.
     *
     * @param modelKey The Firestore key for the model
     * @return Number of predictions stored (max 200)
     */
    suspend fun getModelPredictionCount(modelKey: String): Int {
        val querySnapshot = db.collection(COLLECTION_MODELS)
            .document(modelKey)
            .collection(SUBCOLLECTION_PREDICTIONS)
            .get()
            .await()

        return querySnapshot.size()
    }

    /**
     * Fetch all predictions for a model and calculate aggregate metrics.
     * Includes prediction quality metrics (Precision, Recall, F1, etc.) per lecturer requirements.
     *
     * @param modelKey The Firestore key for the model
     * @param displayName The display name for the model
     * @return ModelAggregateMetrics with all averages and quality metrics calculated
     */
    suspend fun getModelAggregateMetrics(
        modelKey: String,
        displayName: String
    ): ModelAggregateMetrics {
        Log.d(TAG, "Fetching aggregate metrics for model $modelKey")

        val querySnapshot = db.collection(COLLECTION_MODELS)
            .document(modelKey)
            .collection(SUBCOLLECTION_PREDICTIONS)
            .get()
            .await()

        val count = querySnapshot.size()

        if (count == 0) {
            Log.d(TAG, "No predictions found for model $modelKey")
            return ModelAggregateMetrics(
                modelKey = modelKey,
                modelDisplayName = displayName,
                predictionCount = 0,
                averageAccuracy = 0.0,
                averageLatencyMs = 0.0,
                averageTtftMs = 0.0,
                averageItps = 0.0,
                averageOtps = 0.0,
                averageOetMs = 0.0,
                averageJavaHeapKb = 0.0,
                averageNativeHeapKb = 0.0,
                averageTotalPssKb = 0.0
                // New quality metrics default to 0 via default parameter values
            )
        }

        // Existing accumulators
        var matchCount = 0
        var totalLatency = 0.0
        var totalTtft = 0.0
        var totalItps = 0.0
        var totalOtps = 0.0
        var totalOet = 0.0
        var totalJavaHeap = 0.0
        var totalNativeHeap = 0.0
        var totalPss = 0.0

        // NEW: Confusion matrix accumulators for quality metrics
        var totalTp = 0
        var totalFp = 0
        var totalFn = 0
        var totalTn = 0

        // Collect prediction pairs for F1 Macro calculation
        val predictionPairs = mutableListOf<Pair<String, String>>()

        // Safety-Oriented Metrics (Table 3) accumulators
        var hallucinationCount = 0      // Predictions with hallucinated allergens
        var overPredictionCount = 0     // Predictions with FP > 0
        var abstentionTotal = 0         // No-allergen ground truth cases
        var abstentionCorrect = 0       // Correctly predicted empty for no-allergen cases

        querySnapshot.documents.forEach { doc ->
            // Existing performance metric accumulation
            if (doc.getBoolean("isMatch") == true) matchCount++
            totalLatency += doc.getLong("latencyMs")?.toDouble() ?: 0.0
            totalTtft += doc.getLong("ttftMs")?.toDouble() ?: 0.0
            totalItps += doc.getLong("itps")?.toDouble() ?: 0.0
            totalOtps += doc.getLong("otps")?.toDouble() ?: 0.0
            totalOet += doc.getLong("oetMs")?.toDouble() ?: 0.0
            totalJavaHeap += doc.getLong("javaHeapKb")?.toDouble() ?: 0.0
            totalNativeHeap += doc.getLong("nativeHeapKb")?.toDouble() ?: 0.0
            totalPss += doc.getLong("totalPssKb")?.toDouble() ?: 0.0

            // NEW: Calculate confusion counts per prediction
            val predicted = doc.getString("predictedAllergens") ?: ""
            val groundTruth = doc.getString("mappedAllergens") ?: ""
            val ingredients = doc.getString("ingredients") ?: ""

            val counts = calculateConfusionCounts(predicted, groundTruth)
            totalTp += counts.tp
            totalFp += counts.fp
            totalFn += counts.fn
            totalTn += counts.tn

            predictionPairs.add(Pair(predicted, groundTruth))

            // Safety metrics calculation
            if (hasHallucination(predicted, ingredients)) {
                hallucinationCount++
            }
            if (hasOverPrediction(predicted, groundTruth)) {
                overPredictionCount++
            }
            // Abstention: check no-allergen cases
            val groundTruthEmpty = groundTruth.isBlank() ||
                groundTruth.lowercase().split(",").all { it.trim().isEmpty() || it.trim() == "none" }
            if (groundTruthEmpty) {
                abstentionTotal++
                val predictedEmpty = predicted.isBlank() ||
                    predicted.lowercase().split(",").all { it.trim().isEmpty() || it.trim() == "none" }
                if (predictedEmpty) {
                    abstentionCorrect++
                }
            }
        }

        // Calculate quality metrics
        val precision = if (totalTp + totalFp > 0)
            totalTp.toDouble() / (totalTp + totalFp) else 0.0
        val recall = if (totalTp + totalFn > 0)
            totalTp.toDouble() / (totalTp + totalFn) else 0.0
        val f1Micro = if (2 * totalTp + totalFp + totalFn > 0)
            (2.0 * totalTp) / (2 * totalTp + totalFp + totalFn) else 0.0

        // F1 Macro - requires per-allergen calculation
        val perAllergenCounts = calculatePerAllergenCounts(predictionPairs)
        val f1Macro = calculateF1Macro(perAllergenCounts)

        // Hamming Loss: (FP + FN) / (N * L) where L=9 allergen categories
        val hammingLoss = (totalFp + totalFn).toDouble() / (count * 9)

        // FNR (False Negative Rate) = FN / (TP + FN) = 1 - Recall
        val fnr = if (totalTp + totalFn > 0)
            totalFn.toDouble() / (totalTp + totalFn) else 0.0

        // Safety-Oriented Metrics (Table 3)
        val hallucinationRate = (hallucinationCount.toDouble() / count) * 100
        val overPredictionRate = (overPredictionCount.toDouble() / count) * 100
        val abstentionAccuracy = if (abstentionTotal > 0)
            (abstentionCorrect.toDouble() / abstentionTotal) * 100 else 100.0

        val metrics = ModelAggregateMetrics(
            modelKey = modelKey,
            modelDisplayName = displayName,
            predictionCount = count,
            averageAccuracy = matchCount.toDouble() / count * 100,  // EMR as percentage
            averageLatencyMs = totalLatency / count,
            averageTtftMs = totalTtft / count,
            averageItps = totalItps / count,
            averageOtps = totalOtps / count,
            averageOetMs = totalOet / count,
            averageJavaHeapKb = totalJavaHeap / count,
            averageNativeHeapKb = totalNativeHeap / count,
            averageTotalPssKb = totalPss / count,
            // Quality metrics
            totalTp = totalTp,
            totalFp = totalFp,
            totalFn = totalFn,
            totalTn = totalTn,
            precision = precision,
            recall = recall,
            f1Micro = f1Micro,
            f1Macro = f1Macro,
            hammingLoss = hammingLoss,
            fnr = fnr,
            // Safety metrics
            hallucinationRate = hallucinationRate,
            overPredictionRate = overPredictionRate,
            abstentionAccuracy = abstentionAccuracy
        )

        Log.d(TAG, "Aggregate metrics for $modelKey: count=$count, EMR=${metrics.averageAccuracy}%, " +
                "Precision=${String.format("%.3f", precision)}, Recall=${String.format("%.3f", recall)}, " +
                "F1-Micro=${String.format("%.3f", f1Micro)}, F1-Macro=${String.format("%.3f", f1Macro)}, " +
                "HalR=${String.format("%.1f", hallucinationRate)}%, OPR=${String.format("%.1f", overPredictionRate)}%, " +
                "AbsA=${String.format("%.1f", abstentionAccuracy)}%")
        return metrics
    }

    /**
     * Fetch aggregate metrics for all models.
     * Convenience method for comparison screen.
     *
     * @param models List of ModelType to fetch metrics for
     * @return List of ModelAggregateMetrics for all models
     */
    suspend fun getAllModelMetrics(models: Array<ModelType>): List<ModelAggregateMetrics> {
        return models.map { model ->
            getModelAggregateMetrics(model.firestoreKey, model.displayName)
        }
    }

    /**
     * Fetch all individual prediction records for a model.
     * Used for Excel export (Section 4 requirements).
     *
     * @param modelKey The Firestore key for the model
     * @return List of PredictionRecord sorted by dataId
     */
    suspend fun getModelPredictionRecords(modelKey: String): List<PredictionRecord> {
        Log.d(TAG, "Fetching prediction records for model $modelKey")

        val querySnapshot = db.collection(COLLECTION_MODELS)
            .document(modelKey)
            .collection(SUBCOLLECTION_PREDICTIONS)
            .get()
            .await()

        if (querySnapshot.isEmpty) {
            Log.d(TAG, "No prediction records found for model $modelKey")
            return emptyList()
        }

        val records = querySnapshot.documents.mapNotNull { doc ->
            try {
                PredictionRecord(
                    dataId = doc.getLong("dataId")?.toInt() ?: 0,
                    name = doc.getString("name") ?: "",
                    ingredients = doc.getString("ingredients") ?: "",
                    groundTruthAllergens = doc.getString("mappedAllergens") ?: "",
                    predictedAllergens = doc.getString("predictedAllergens") ?: "",
                    isMatch = doc.getBoolean("isMatch") ?: false
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing prediction record: ${e.message}")
                null
            }
        }.sortedBy { it.dataId }

        Log.d(TAG, "Fetched ${records.size} prediction records for model $modelKey")
        return records
    }

    /**
     * Fetch all prediction records for all models.
     * Used for Excel export with separate sheets per model.
     *
     * @param models List of ModelType to fetch records for
     * @return Map of modelKey to list of PredictionRecords
     */
    suspend fun getAllModelPredictionRecords(models: Array<ModelType>): Map<String, List<PredictionRecord>> {
        return models.associate { model ->
            model.firestoreKey to getModelPredictionRecords(model.firestoreKey)
        }
    }
}
