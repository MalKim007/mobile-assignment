package com.mad.assignment

/**
 * Represents a food item from the Excel dataset (foodpreprocessed.xlsx)
 *
 * @property id Unique identifier (1-200)
 * @property name Product name
 * @property link Source URL from Open Food Facts
 * @property ingredients Full ingredient list
 * @property allergensRaw Original allergen text from source (e.g., "Crustaceans", "Gluten")
 * @property allergensMapped Normalized allergens - ground truth for comparison (e.g., "shellfish", "wheat")
 */
data class FoodItem(
    val id: Int,
    val name: String,
    val link: String,
    val ingredients: String,
    val allergensRaw: String,
    val allergensMapped: String
)
