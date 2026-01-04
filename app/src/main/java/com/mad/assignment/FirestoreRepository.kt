package com.mad.assignment

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Data class to hold aggregate metrics for a model.
 * Used for comparison screen to display average performance across all predictions.
 */
data class ModelAggregateMetrics(
    val modelKey: String,
    val modelDisplayName: String,
    val predictionCount: Int,
    val averageAccuracy: Double,
    val averageLatencyMs: Double,
    val averageTtftMs: Double,
    val averageItps: Double,
    val averageOtps: Double,
    val averageOetMs: Double,
    val averageJavaHeapKb: Double,
    val averageNativeHeapKb: Double,
    val averageTotalPssKb: Double
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
        const val REQUIRED_PREDICTIONS = 200
    }

    private val db: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance()
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
     * Used by comparison screen to show average performance.
     *
     * @param modelKey The Firestore key for the model
     * @param displayName The display name for the model
     * @return ModelAggregateMetrics with all averages calculated
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
            )
        }

        var matchCount = 0
        var totalLatency = 0.0
        var totalTtft = 0.0
        var totalItps = 0.0
        var totalOtps = 0.0
        var totalOet = 0.0
        var totalJavaHeap = 0.0
        var totalNativeHeap = 0.0
        var totalPss = 0.0

        querySnapshot.documents.forEach { doc ->
            if (doc.getBoolean("isMatch") == true) matchCount++
            totalLatency += doc.getLong("latencyMs")?.toDouble() ?: 0.0
            totalTtft += doc.getLong("ttftMs")?.toDouble() ?: 0.0
            totalItps += doc.getLong("itps")?.toDouble() ?: 0.0
            totalOtps += doc.getLong("otps")?.toDouble() ?: 0.0
            totalOet += doc.getLong("oetMs")?.toDouble() ?: 0.0
            totalJavaHeap += doc.getLong("javaHeapKb")?.toDouble() ?: 0.0
            totalNativeHeap += doc.getLong("nativeHeapKb")?.toDouble() ?: 0.0
            totalPss += doc.getLong("totalPssKb")?.toDouble() ?: 0.0
        }

        val metrics = ModelAggregateMetrics(
            modelKey = modelKey,
            modelDisplayName = displayName,
            predictionCount = count,
            averageAccuracy = matchCount.toDouble() / count * 100,
            averageLatencyMs = totalLatency / count,
            averageTtftMs = totalTtft / count,
            averageItps = totalItps / count,
            averageOtps = totalOtps / count,
            averageOetMs = totalOet / count,
            averageJavaHeapKb = totalJavaHeap / count,
            averageNativeHeapKb = totalNativeHeap / count,
            averageTotalPssKb = totalPss / count
        )

        Log.d(TAG, "Aggregate metrics for $modelKey: count=$count, accuracy=${metrics.averageAccuracy}%")
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
}
