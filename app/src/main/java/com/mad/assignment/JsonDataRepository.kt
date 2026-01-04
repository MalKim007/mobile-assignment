package com.mad.assignment

import android.content.Context
import android.util.Log
import kotlinx.serialization.json.Json

/**
 * Repository for reading food data from JSON file (food_preprocessed.json).
 * Uses kotlinx.serialization for fast, type-safe parsing.
 *
 * Data is cached after first load for performance.
 * Drop-in replacement for ExcelDataRepository.
 */
class JsonDataRepository(private val context: Context) {

    companion object {
        private const val TAG = "JsonDataRepository"
        private const val JSON_FILE = "food_preprocessed.json"
        private const val ITEMS_PER_DATASET = 10
        private const val TOTAL_DATASETS = 20
    }

    // JSON parser with lenient settings for robustness
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // Cached food items - loaded once, reused
    private var foodItems: List<FoodItem>? = null

    /**
     * Load all food items from JSON file.
     * Results are cached after first load.
     *
     * @return List of all 200 FoodItem objects
     * @throws IllegalStateException if file cannot be read or parsed
     */
    fun loadFoodItems(): List<FoodItem> {
        // Return cached data if available
        foodItems?.let { return it }

        Log.d(TAG, "Loading food items from $JSON_FILE")

        val items: List<FoodItem> = try {
            context.assets.open(JSON_FILE).use { inputStream ->
                val jsonString = inputStream.bufferedReader().use { it.readText() }
                json.decodeFromString<List<FoodItem>>(jsonString)
            }
        } catch (e: kotlinx.serialization.SerializationException) {
            Log.e(TAG, "JSON parsing error: ${e.message}", e)
            throw IllegalStateException("Failed to parse $JSON_FILE: ${e.message}", e)
        } catch (e: java.io.IOException) {
            Log.e(TAG, "File I/O error: ${e.message}", e)
            throw IllegalStateException("Failed to read $JSON_FILE: ${e.message}", e)
        }

        Log.d(TAG, "Loaded ${items.size} food items")
        foodItems = items
        return items
    }

    /**
     * Get a specific dataset by number (1-20).
     * Each dataset contains 10 items.
     *
     * Dataset 1 = IDs 1-10 (indices 0-9)
     * Dataset 2 = IDs 11-20 (indices 10-19)
     * ...
     * Dataset 20 = IDs 191-200 (indices 190-199)
     *
     * @param datasetNumber Dataset number (1-20)
     * @return List of 10 FoodItem objects for that dataset
     * @throws IllegalArgumentException if datasetNumber is not in range 1-20
     */
    fun getDataset(datasetNumber: Int): List<FoodItem> {
        require(datasetNumber in 1..TOTAL_DATASETS) {
            "Dataset number must be between 1 and $TOTAL_DATASETS, got $datasetNumber"
        }

        val allItems = loadFoodItems()

        // Calculate start and end indices
        val startIndex = (datasetNumber - 1) * ITEMS_PER_DATASET
        val endIndex = minOf(startIndex + ITEMS_PER_DATASET, allItems.size)

        Log.d(TAG, "Getting dataset $datasetNumber: indices $startIndex to ${endIndex - 1}")

        return allItems.subList(startIndex, endIndex)
    }

    /**
     * Get the ID range for a dataset.
     *
     * @param datasetNumber Dataset number (1-20)
     * @return Pair of (startId, endId) inclusive
     */
    fun getDatasetIdRange(datasetNumber: Int): Pair<Int, Int> {
        require(datasetNumber in 1..TOTAL_DATASETS)
        val startId = (datasetNumber - 1) * ITEMS_PER_DATASET + 1
        val endId = datasetNumber * ITEMS_PER_DATASET
        return Pair(startId, endId)
    }

    /**
     * Get all 200 food items.
     * Convenience method for Run All functionality.
     *
     * @return List of all 200 FoodItem objects
     */
    fun getAllItems(): List<FoodItem> {
        return loadFoodItems()
    }

    /**
     * Clear cached data (useful for memory management)
     */
    fun clearCache() {
        foodItems = null
        Log.d(TAG, "Cache cleared")
    }
}
