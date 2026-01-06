package com.mad.assignment

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.checkbox.MaterialCheckBox
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

/**
 * Sealed class representing the different states of a food item in the prediction list.
 */
sealed class FoodItemState {
    abstract val foodItem: FoodItem

    /** Item is loaded and available for selection */
    data class Selectable(
        override val foodItem: FoodItem,
        val isSelected: Boolean = false
    ) : FoodItemState()

    /** Item is waiting to be processed (selected but not yet started) */
    data class Pending(override val foodItem: FoodItem) : FoodItemState()

    /** Item is currently being processed by the LLM */
    data class Processing(override val foodItem: FoodItem) : FoodItemState()

    /** Item processing completed successfully */
    data class Completed(
        override val foodItem: FoodItem,
        val predictedAllergens: String,
        val metrics: InferenceMetrics,
        val isMatch: Boolean
    ) : FoodItemState()

    /** Item processing failed with an error */
    data class Failed(
        override val foodItem: FoodItem,
        val errorMessage: String
    ) : FoodItemState()
}

/**
 * RecyclerView adapter for displaying food items with their prediction states.
 * Uses ListAdapter with DiffUtil for efficient updates.
 *
 * @param onItemClick Callback when user taps on a completed item
 * @param onSelectionChanged Callback when selection changes (returns count of selected items)
 */
class FoodItemAdapter(
    private val onItemClick: (FoodItemState.Completed) -> Unit,
    private val onSelectionChanged: (Int) -> Unit
) : ListAdapter<FoodItemState, FoodItemAdapter.ViewHolder>(DiffCallback()) {

    // Track selected item IDs
    private val selectedIds = mutableSetOf<Int>()

    // Forest theme colors
    companion object {
        private const val COLOR_FOREST_GREEN = "#375534"
        private const val COLOR_DEEP_FOREST = "#0F2A1D"
        private const val COLOR_SAGE_GREEN = "#6B9071"
        private const val COLOR_MINT_SAGE = "#AEC3B0"
        private const val COLOR_SNOW_WHITE = "#FFFAFA"
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView = itemView.findViewById(R.id.cardView)
        val checkBox: MaterialCheckBox = itemView.findViewById(R.id.checkBox)
        val tvFoodId: TextView = itemView.findViewById(R.id.tvFoodId)
        val tvFoodName: TextView = itemView.findViewById(R.id.tvFoodName)
        val tvIngredients: TextView = itemView.findViewById(R.id.tvIngredients)
        val tvGroundTruth: TextView = itemView.findViewById(R.id.tvGroundTruth)
        val tvPredicted: TextView = itemView.findViewById(R.id.tvPredicted)
        val tvMatchStatus: TextView = itemView.findViewById(R.id.tvMatchStatus)
        val tvMetrics: TextView = itemView.findViewById(R.id.tvMetrics)
        val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        val tvTapHint: TextView = itemView.findViewById(R.id.tvTapHint)
        val progressBar: ProgressBar = itemView.findViewById(R.id.progressBar)
        val ivStatusIcon: ImageView = itemView.findViewById(R.id.ivStatusIcon)
        val labelPredicted: TextView = itemView.findViewById(R.id.labelPredicted)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_food_prediction, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        when (val state = getItem(position)) {
            is FoodItemState.Selectable -> bindSelectable(holder, state)
            is FoodItemState.Pending -> bindPending(holder, state)
            is FoodItemState.Processing -> bindProcessing(holder, state)
            is FoodItemState.Completed -> bindCompleted(holder, state)
            is FoodItemState.Failed -> bindFailed(holder, state)
        }
    }

    private fun bindSelectable(holder: ViewHolder, state: FoodItemState.Selectable) {
        val item = state.foodItem
        holder.apply {
            // Checkbox visible and enabled
            checkBox.visibility = View.VISIBLE
            checkBox.isEnabled = true
            checkBox.setOnCheckedChangeListener(null) // Remove old listener
            checkBox.isChecked = state.isSelected
            checkBox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedIds.add(item.id)
                } else {
                    selectedIds.remove(item.id)
                }
                onSelectionChanged(selectedIds.size)
            }

            // Basic info
            tvFoodId.text = "#${item.id}"
            tvFoodName.text = item.name
            tvIngredients.text = item.ingredients

            // Ground truth
            tvGroundTruth.text = item.allergensMapped.ifEmpty { "none" }

            // Hide prediction-related views
            labelPredicted.visibility = View.GONE
            tvPredicted.visibility = View.GONE
            tvMatchStatus.visibility = View.GONE
            tvMetrics.visibility = View.GONE
            tvTapHint.visibility = View.GONE

            // Status
            tvStatus.text = if (state.isSelected) "Selected" else "Tap to select"
            tvStatus.setTextColor(if (state.isSelected) Color.parseColor(COLOR_FOREST_GREEN) else Color.parseColor(COLOR_SAGE_GREEN))
            tvStatus.visibility = View.VISIBLE
            progressBar.visibility = View.GONE
            ivStatusIcon.visibility = View.GONE

            // Card styling
            cardView.alpha = 1.0f
            cardView.strokeWidth = if (state.isSelected) 2 else 0
            cardView.strokeColor = Color.parseColor(COLOR_FOREST_GREEN)
            cardView.setOnClickListener {
                checkBox.isChecked = !checkBox.isChecked
            }
        }
    }

    private fun bindPending(holder: ViewHolder, state: FoodItemState.Pending) {
        val item = state.foodItem
        holder.apply {
            // Checkbox disabled during processing
            checkBox.visibility = View.VISIBLE
            checkBox.isEnabled = false
            checkBox.isChecked = true

            // Basic info
            tvFoodId.text = "#${item.id}"
            tvFoodName.text = item.name
            tvIngredients.text = item.ingredients

            // Ground truth
            tvGroundTruth.text = item.allergensMapped.ifEmpty { "none" }

            // Hide prediction-related views
            labelPredicted.visibility = View.GONE
            tvPredicted.visibility = View.GONE
            tvMatchStatus.visibility = View.GONE
            tvMetrics.visibility = View.GONE
            tvTapHint.visibility = View.GONE

            // Status
            tvStatus.text = "Waiting..."
            tvStatus.setTextColor(Color.parseColor(COLOR_SAGE_GREEN))
            tvStatus.visibility = View.VISIBLE
            progressBar.visibility = View.GONE
            ivStatusIcon.visibility = View.GONE

            // Card styling
            cardView.alpha = 0.7f
            cardView.strokeWidth = 0
            cardView.setOnClickListener(null)
            cardView.isClickable = false
        }
    }

    private fun bindProcessing(holder: ViewHolder, state: FoodItemState.Processing) {
        val item = state.foodItem
        holder.apply {
            // Checkbox disabled
            checkBox.visibility = View.VISIBLE
            checkBox.isEnabled = false
            checkBox.isChecked = true

            // Basic info
            tvFoodId.text = "#${item.id}"
            tvFoodName.text = item.name
            tvIngredients.text = item.ingredients

            // Ground truth
            tvGroundTruth.text = item.allergensMapped.ifEmpty { "none" }

            // Hide prediction-related views
            labelPredicted.visibility = View.GONE
            tvPredicted.visibility = View.GONE
            tvMatchStatus.visibility = View.GONE
            tvMetrics.visibility = View.GONE
            tvTapHint.visibility = View.GONE

            // Status - show progress
            tvStatus.text = "Analyzing..."
            tvStatus.setTextColor(Color.parseColor(COLOR_SAGE_GREEN))
            tvStatus.visibility = View.VISIBLE
            progressBar.visibility = View.VISIBLE
            ivStatusIcon.visibility = View.GONE

            // Card styling
            cardView.alpha = 1.0f
            cardView.strokeWidth = 2
            cardView.strokeColor = Color.parseColor(COLOR_SAGE_GREEN)
            cardView.setOnClickListener(null)
            cardView.isClickable = false
        }
    }

    private fun bindCompleted(holder: ViewHolder, state: FoodItemState.Completed) {
        val item = state.foodItem

        holder.apply {
            // Checkbox hidden after completion
            checkBox.visibility = View.GONE

            // Basic info
            tvFoodId.text = "#${item.id}"
            tvFoodName.text = item.name
            tvIngredients.text = item.ingredients

            // Ground truth
            tvGroundTruth.text = item.allergensMapped.ifEmpty { "none" }

            // Prediction
            labelPredicted.visibility = View.VISIBLE
            tvPredicted.visibility = View.VISIBLE
            tvPredicted.text = state.predictedAllergens.ifEmpty { "none" }

            // Match status
            tvMatchStatus.visibility = View.VISIBLE
            if (state.isMatch) {
                tvMatchStatus.text = "MATCH"
                tvMatchStatus.setTextColor(Color.parseColor(COLOR_SNOW_WHITE))
                tvMatchStatus.setBackgroundResource(R.drawable.bg_status_match)
                tvPredicted.setTextColor(Color.parseColor(COLOR_FOREST_GREEN))
            } else {
                tvMatchStatus.text = "MISMATCH"
                tvMatchStatus.setTextColor(Color.parseColor(COLOR_SNOW_WHITE))
                tvMatchStatus.setBackgroundResource(R.drawable.bg_status_mismatch)
                tvPredicted.setTextColor(Color.parseColor(COLOR_DEEP_FOREST))
            }

            // Metrics summary
            tvMetrics.visibility = View.VISIBLE
            val m = state.metrics
            tvMetrics.text = "Latency: ${m.latencyMs}ms"

            // Tap hint
            tvTapHint.visibility = View.VISIBLE

            // Status - hide for completed items
            tvStatus.visibility = View.GONE
            progressBar.visibility = View.GONE
            ivStatusIcon.visibility = View.GONE

            // Card styling
            cardView.alpha = 1.0f
            cardView.strokeWidth = if (state.isMatch) 2 else 1
            cardView.strokeColor = if (state.isMatch) Color.parseColor(COLOR_FOREST_GREEN) else Color.parseColor(COLOR_DEEP_FOREST)
            cardView.isClickable = true
            cardView.setOnClickListener {
                onItemClick(state)
            }
        }
    }

    private fun bindFailed(holder: ViewHolder, state: FoodItemState.Failed) {
        val item = state.foodItem
        holder.apply {
            // Checkbox hidden
            checkBox.visibility = View.GONE

            // Basic info
            tvFoodId.text = "#${item.id}"
            tvFoodName.text = item.name
            tvIngredients.text = item.ingredients

            // Ground truth
            tvGroundTruth.text = item.allergensMapped.ifEmpty { "none" }

            // Show error in predicted field
            labelPredicted.visibility = View.VISIBLE
            tvPredicted.visibility = View.VISIBLE
            tvPredicted.text = "Error: ${state.errorMessage}"
            tvPredicted.setTextColor(Color.parseColor(COLOR_DEEP_FOREST))

            // Hide other views
            tvMatchStatus.visibility = View.GONE
            tvMetrics.visibility = View.GONE
            tvTapHint.visibility = View.GONE

            // Status
            tvStatus.text = "Failed"
            tvStatus.setTextColor(Color.parseColor(COLOR_DEEP_FOREST))
            tvStatus.visibility = View.VISIBLE
            progressBar.visibility = View.GONE
            ivStatusIcon.visibility = View.GONE

            // Card styling
            cardView.alpha = 0.8f
            cardView.strokeWidth = 2
            cardView.strokeColor = Color.parseColor(COLOR_DEEP_FOREST)
            cardView.setOnClickListener(null)
            cardView.isClickable = false
        }
    }

    /**
     * Get list of selected food item IDs
     */
    fun getSelectedIds(): Set<Int> = selectedIds.toSet()

    /**
     * Select all items
     */
    fun selectAll() {
        val list = currentList.toList()  // Snapshot of current list
        if (list.isEmpty()) return  // Guard against async timing issue

        list.forEach { state ->
            if (state is FoodItemState.Selectable) {
                selectedIds.add(state.foodItem.id)
            }
        }
        onSelectionChanged(selectedIds.size)
        // Refresh list to update checkboxes
        val updatedList = list.map { state ->
            if (state is FoodItemState.Selectable) {
                state.copy(isSelected = true)
            } else state
        }
        submitList(null)  // Force reset
        submitList(updatedList)  // Then submit new list
    }

    /**
     * Deselect all items
     */
    fun deselectAll() {
        val list = currentList.toList()  // Snapshot of current list
        if (list.isEmpty()) return  // Guard against async timing issue

        selectedIds.clear()
        onSelectionChanged(0)
        // Refresh list to update checkboxes
        val updatedList = list.map { state ->
            if (state is FoodItemState.Selectable) {
                state.copy(isSelected = false)
            } else state
        }
        submitList(null)  // Force reset
        submitList(updatedList)  // Then submit new list
    }

    /**
     * Clear selection tracking (call when loading new dataset)
     */
    fun clearSelection() {
        selectedIds.clear()
    }

    /**
     * Add an item ID to selection (used by MainActivity for select all)
     */
    fun addToSelection(id: Int) {
        selectedIds.add(id)
    }

    /**
     * DiffUtil callback for efficient list updates
     */
    private class DiffCallback : DiffUtil.ItemCallback<FoodItemState>() {
        override fun areItemsTheSame(oldItem: FoodItemState, newItem: FoodItemState): Boolean {
            return oldItem.foodItem.id == newItem.foodItem.id
        }

        override fun areContentsTheSame(oldItem: FoodItemState, newItem: FoodItemState): Boolean {
            return oldItem == newItem
        }
    }
}
