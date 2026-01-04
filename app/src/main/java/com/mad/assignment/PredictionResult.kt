package com.mad.assignment

import com.google.firebase.Timestamp

/**
 * Represents a complete prediction result for Firebase Firestore storage.
 * Maps directly to assignment requirements (7a-7h).
 *
 * @property dataId a. Data id
 * @property name b. Name
 * @property ingredients c. Ingredients
 * @property allergens d. Allergens (allergensRaw from Excel)
 * @property mappedAllergens e. Mapped Allergens (ground truth)
 * @property predictedAllergens f. Predicted Allergens (from LLM)
 * @property timestamp g. Timestamp
 * @property metrics h. All inference metrics
 * @property datasetNumber Which dataset (1-20) this item belongs to
 * @property isMatch Whether prediction matches ground truth
 * @property modelName Name of the model used for prediction
 */
data class PredictionResult(
    val dataId: Int,
    val name: String,
    val ingredients: String,
    val allergens: String,
    val mappedAllergens: String,
    val predictedAllergens: String,
    val timestamp: Timestamp,
    val metrics: InferenceMetrics,
    val datasetNumber: Int,
    val isMatch: Boolean,
    val modelName: String = ""
) {
    /**
     * Converts this result to a Firestore-compatible Map.
     * Field names match assignment requirements exactly.
     */
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        // Assignment requirements a-g
        "dataId" to dataId,
        "name" to name,
        "ingredients" to ingredients,
        "allergens" to allergens,
        "mappedAllergens" to mappedAllergens,
        "predictedAllergens" to predictedAllergens,
        "timestamp" to timestamp,

        // Assignment requirement h - All inference metrics
        "latencyMs" to metrics.latencyMs,
        "javaHeapKb" to metrics.javaHeapKb,
        "nativeHeapKb" to metrics.nativeHeapKb,
        "totalPssKb" to metrics.totalPssKb,
        "ttftMs" to metrics.ttft,
        "itps" to metrics.itps,
        "otps" to metrics.otps,
        "oetMs" to metrics.oet,

        // Additional fields for app functionality
        "datasetNumber" to datasetNumber,
        "isMatch" to isMatch,
        "modelName" to modelName
    )
}
