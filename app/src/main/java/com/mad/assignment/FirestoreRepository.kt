package com.mad.assignment

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Repository for Firebase Firestore operations.
 * Handles storing prediction results to the cloud database.
 */
class FirestoreRepository {

    companion object {
        private const val TAG = "FirestoreRepository"
        private const val COLLECTION_PREDICTIONS = "predictions"
    }

    private val db: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance()
    }

    /**
     * Store a single prediction result to Firestore.
     *
     * @param result The PredictionResult to store
     * @return Document ID of the created document
     * @throws Exception if write fails
     */
    suspend fun storePrediction(result: PredictionResult): String {
        Log.d(TAG, "Storing prediction for item ${result.dataId}: ${result.name}")

        val docRef = db.collection(COLLECTION_PREDICTIONS)
            .add(result.toFirestoreMap())
            .await()

        Log.d(TAG, "Prediction stored with ID: ${docRef.id}")
        return docRef.id
    }

    /**
     * Store multiple prediction results in a batch operation.
     * More efficient than storing individually for multiple items.
     *
     * @param results List of PredictionResult objects to store
     * @param datasetNumber The dataset number (for logging)
     * @throws Exception if batch write fails
     */
    suspend fun storeBatchPredictions(
        results: List<PredictionResult>,
        datasetNumber: Int
    ) {
        if (results.isEmpty()) {
            Log.w(TAG, "No results to store for dataset $datasetNumber")
            return
        }

        Log.d(TAG, "Storing ${results.size} predictions for dataset $datasetNumber")

        val batch = db.batch()
        val collection = db.collection(COLLECTION_PREDICTIONS)

        results.forEach { result ->
            val docRef = collection.document()
            batch.set(docRef, result.toFirestoreMap())
        }

        batch.commit().await()

        Log.d(TAG, "Successfully stored ${results.size} predictions for dataset $datasetNumber")
    }

    /**
     * Delete all predictions for a specific dataset.
     * Useful for re-running predictions.
     *
     * @param datasetNumber The dataset number to clear
     * @throws Exception if delete fails
     */
    suspend fun clearDatasetPredictions(datasetNumber: Int) {
        Log.d(TAG, "Clearing predictions for dataset $datasetNumber")

        val querySnapshot = db.collection(COLLECTION_PREDICTIONS)
            .whereEqualTo("datasetNumber", datasetNumber)
            .get()
            .await()

        if (querySnapshot.isEmpty) {
            Log.d(TAG, "No existing predictions for dataset $datasetNumber")
            return
        }

        val batch = db.batch()
        querySnapshot.documents.forEach { doc ->
            batch.delete(doc.reference)
        }
        batch.commit().await()

        Log.d(TAG, "Deleted ${querySnapshot.size()} predictions for dataset $datasetNumber")
    }

    /**
     * Get count of predictions stored for a dataset.
     *
     * @param datasetNumber The dataset number to count
     * @return Number of predictions stored
     */
    suspend fun getDatasetPredictionCount(datasetNumber: Int): Int {
        val querySnapshot = db.collection(COLLECTION_PREDICTIONS)
            .whereEqualTo("datasetNumber", datasetNumber)
            .get()
            .await()

        return querySnapshot.size()
    }
}
