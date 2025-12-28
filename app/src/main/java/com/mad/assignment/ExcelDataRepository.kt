package com.mad.assignment

import android.content.Context
import android.util.Log
import org.apache.poi.xssf.usermodel.XSSFWorkbook

/**
 * Repository for reading food data from the Excel file (foodpreprocessed.xlsx).
 * Uses Apache POI library to parse .xlsx format.
 *
 * Data is cached after first load for performance.
 */
class ExcelDataRepository(private val context: Context) {

    companion object {
        private const val TAG = "ExcelDataRepository"
        private const val EXCEL_FILE = "foodpreprocessed.xlsx"
        private const val ITEMS_PER_DATASET = 10
        private const val TOTAL_DATASETS = 20
    }

    // Cached food items - loaded once, reused
    private var foodItems: List<FoodItem>? = null

    /**
     * Load all food items from Excel file.
     * Results are cached after first load.
     *
     * @return List of all 200 FoodItem objects
     * @throws Exception if file cannot be read or parsed
     */
    fun loadFoodItems(): List<FoodItem> {
        // Return cached data if available
        foodItems?.let { return it }

        Log.d(TAG, "Loading food items from $EXCEL_FILE")

        val items = mutableListOf<FoodItem>()

        context.assets.open(EXCEL_FILE).use { inputStream ->
            XSSFWorkbook(inputStream).use { workbook ->
                val sheet = workbook.getSheetAt(0)
                val lastRowNum = sheet.lastRowNum

                Log.d(TAG, "Excel file has ${lastRowNum + 1} rows (including header)")

                // Skip header row (row 0), read data rows (1 to lastRowNum)
                for (rowIndex in 1..lastRowNum) {
                    val row = sheet.getRow(rowIndex) ?: continue

                    try {
                        val item = FoodItem(
                            id = getCellValueAsInt(row.getCell(0), rowIndex),
                            name = getCellValueAsString(row.getCell(1)),
                            link = getCellValueAsString(row.getCell(2)),
                            ingredients = getCellValueAsString(row.getCell(3)),
                            allergensRaw = getCellValueAsString(row.getCell(4)),
                            allergensMapped = getCellValueAsString(row.getCell(5))
                        )
                        items.add(item)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing row $rowIndex: ${e.message}")
                    }
                }
            }
        }

        Log.d(TAG, "Loaded ${items.size} food items")
        foodItems = items
        return items
    }

    /**
     * Get a specific dataset by number (1-20).
     * Each dataset contains 10 items.
     *
     * Dataset 1 = IDs 1-10 (rows 1-10)
     * Dataset 2 = IDs 11-20 (rows 11-20)
     * ...
     * Dataset 20 = IDs 191-200 (rows 191-200)
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
     * Clear cached data (useful for memory management)
     */
    fun clearCache() {
        foodItems = null
        Log.d(TAG, "Cache cleared")
    }

    // Helper function to safely get cell value as String
    private fun getCellValueAsString(cell: org.apache.poi.ss.usermodel.Cell?): String {
        if (cell == null) return ""

        return when (cell.cellType) {
            org.apache.poi.ss.usermodel.CellType.STRING -> cell.stringCellValue ?: ""
            org.apache.poi.ss.usermodel.CellType.NUMERIC -> cell.numericCellValue.toString()
            org.apache.poi.ss.usermodel.CellType.BOOLEAN -> cell.booleanCellValue.toString()
            org.apache.poi.ss.usermodel.CellType.FORMULA -> {
                try {
                    cell.stringCellValue ?: ""
                } catch (e: Exception) {
                    cell.numericCellValue.toString()
                }
            }
            else -> ""
        }
    }

    // Helper function to safely get cell value as Int
    private fun getCellValueAsInt(cell: org.apache.poi.ss.usermodel.Cell?, defaultValue: Int): Int {
        if (cell == null) return defaultValue

        return when (cell.cellType) {
            org.apache.poi.ss.usermodel.CellType.NUMERIC -> cell.numericCellValue.toInt()
            org.apache.poi.ss.usermodel.CellType.STRING -> {
                cell.stringCellValue?.toIntOrNull() ?: defaultValue
            }
            else -> defaultValue
        }
    }
}
