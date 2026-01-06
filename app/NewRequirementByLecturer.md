Here is the complete text in a non-tabular format, with the new sections appended.

***

# Measurement of Model Evaluation Metrics

The application shall support systematic evaluation of on-device small language models for food allergen prediction by measuring prediction quality, safety-oriented, and on-device efficiency metrics as defined in Table 2, Table 3, and Table 4 respectively. The measurements shall enable consistent comparison across models and application conditions.

## 1. Prediction Quality Metrics ✅ COMPLETED

The application shall compute prediction quality metrics for each evaluated model as specified in Table 2. These metrics shall be derived using confusion category counts (TP, FP, FN, TN) computed per allergen and aggregated across all test samples. The implementation shall support multi-label evaluation and report metrics such as precision, recall, F1 score, exact match ratio, hamming loss, and false negative rate.

Each allergen in a prediction shall be labelled as one of the following:
*   **True Positive (TP):** Allergen is present in input and correctly predicted
*   **False Positive (FP):** Allergen is not present but incorrectly predicted
*   **False Negative (FN):** Allergen is present but not predicted
*   **True Negative (TN):** Allergen is not present and not predicted

### Table 2: Prediction quality metrices description

**Precision**
*   **Description:** Proportion of predicted allergens that are correct. Indicates how reliable positive predictions are.
*   **Formula:** Precision = TP / (TP + FP)
*   **Example:**
    *   Ground Truth: {milk, wheat}
    *   Prediction: {milk, soy}
    *   TP = 1 (milk), FP = 1 (soy)
    *   Result: Precision = 1 / (1 + 1) = 0.50

**Recall**
*   **Description:** Proportion of actual allergens that are correctly detected. Critical for safety, as missed allergens are dangerous.
*   **Formula:** Recall = TP / (TP + FN)
*   **Example:**
    *   Ground Truth: {milk, wheat}
    *   Prediction: {milk}
    *   TP = 1 (milk), FN = 1 (wheat)
    *   Result: Recall = 1 / (1 + 1) = 0.50

**F1 Score (Macro & Micro)**
*   **Description:** Harmonic mean of precision and recall. Macro F1 averages F1 per allergen, treating all equally. Micro F1 aggregates TP, FP, FN across all allergens before computing F1.
*   **Formula:** F1 = 2TP / (2TP + FP + FN)
*   **Example (Micro):**
    *   TP = 12, FP = 4, FN = 6
    *   Result: Micro F1 = 2×12 / (24 + 4 + 6) = 0.67

**Exact Match Ratio (EMR)**
*   **Description:** Percentage of samples where the predicted allergen set exactly matches the ground truth with no missing or extra labels.
*   **Example:**
    *   Total samples = 100
    *   Exact matches = 72
    *   Result: EMR = 72 / 100 = 72%

**Hamming Loss**
*   **Description:** Fraction of incorrect allergen labels across all samples and labels, including both FP and FN. Lower values indicate better performance.
*   **Formula:** Hamming Loss = (FP + FN) / (N × L)
*   **Example:**
    *   FP + FN = 30
    *   Samples (N) = 50
    *   Allergens (L) = 9
    *   Result: Hamming Loss = 30 / (50 × 9) = 0.067

**False Negative Rate (FNR)**
*   **Description:** Proportion of actual allergens that are missed by the model. Particularly important for high-risk allergens.
*   **Formula:** FNR = FN / (TP + FN)
*   **Example:**
    *   TP = 18, FN = 2
    *   Result: FNR = 2 / (18 + 2) = 10%

---

## 2. Safety-Oriented Metrics

The application shall measure safety-oriented metrics as defined in Table 3 to assess the reliability and trustworthiness of model predictions. The system shall detect hallucinated allergens, quantify over-prediction behavior, and evaluate abstention accuracy for no-allergen cases. These metrics shall be computed per model and per prompt style to allow analysis of safety risks and usability implications.

### Table 3: Safety-Oriented Metrics Description

**Hallucination Rate**
*   **Description:** Percentage of outputs that contain allergens do not present in the input ingredient list. Measured per model and per prompt style to assess unsupported predictions.
*   **Computation Example:**
    *   Input: sugar, oil
    *   Output: milk
    *   Hallucination = Yes
    *   Calculation: If 12 out of 100 test cases contain hallucinated allergens: Hallucination Rate = (12 / 100) × 100 = 12%

**Over-Prediction Rate**
*   **Description:** Frequency at which the model predicts allergens unnecessarily. Evaluated per model and per prompt style to assess usability and user trust.
*   **Computation Example:**
    *   Input: wheat flour
    *   Ground Truth: wheat
    *   Output: wheat, milk
    *   Over-prediction = Yes
    *   Calculation: If 18 out of 100 predictions contain extra allergens: Over-Prediction Rate = (18 / 100) × 100 = 18%

**Abstention (“No Allergen”) Accuracy**
*   **Description:** Ability of the model to correctly predict an empty allergen set when no allergens are present. Measured using True Negative Rate (TNR) for empty-label inputs.
*   **Computation Example:**
    *   Input: salt, sugar, oil
    *   Ground Truth: {}
    *   Output: {}
    *   Correct abstention = Yes
    *   Calculation: If 45 out of 50 no-allergen cases are correctly predicted: Abstention Accuracy (TNR) = 45 / 50 = 90%

---

## 3. On-Device Efficiency Metrices

The application shall record on-device efficiency metrics as specified in Table 4 to evaluate runtime performance and resource consumption. This includes measuring inference latency, time-to-first-token, input and output token throughput, output evaluation time, total response time, and memory usage at the Java heap, native heap, and system (PSS) levels. Measurements shall reflect real device execution without reliance on external computation.

### Table 4: On-Device Efficiency Metrices Description

**Latency**
*   The total elapsed time required to perform a complete inference, measured from prompt submission to response completion, expressed in seconds.

**Time-to-First-Token (TTFT)**
*   The time elapsed between prompt submission and the generation of the first output token, measured in seconds. This metric reflects the initial response latency and model readiness.

**Input Token Per Second (ITPS)**
*   The rate at which input tokens are processed by the model during prompt evaluation, expressed as tokens per second. This metric indicates input processing efficiency.

**Output Token Per Second (OTPS)**
*   The rate at which output tokens are generated during decoding, expressed as tokens per second. This metric reflects the inference throughput and decoding speed.

**Output Evaluation Time (OET)**
*   The duration required by the model to generate all output tokens after the first token is produced, measured in seconds. This metric assesses sustained generation efficiency.

**Total Time**
*   The complete end-to-end duration from receiving a prompt to producing the full response, measured in seconds. This metric provides a comprehensive measure of task completion efficiency.

**Java Heap**
*   Memory allocated in the Android managed runtime (ART) for Java/Kotlin objects, including UI handling, input preprocessing, prompt construction, and output post-processing.

**Native Heap**
*   Memory allocated in the native C/C++ layer for model loading and inference, including model weights, key–value cache, token buffers, and intermediate tensors.

**Proportional Set Size (PSS)**
*   The actual physical memory footprint of the application, accounting for both private and shared memory, and reflecting system-level memory pressure.

---

## 4. Measurement Consistency and Repeatability

The application shall ensure that all metrics described in Table 2, Table 3 and Table 4 are collected using a consistent setup, including identical input data, prompt formulation, and execution environment across models. Metric values shall be averaged over multiple runs to improve reliability and reduce variability.

## 5. Reporting and Analysis Support

The application shall present measured metrics in a structured and exportable format (e.g., tabular logs or files) to facilitate comparative analysis, interpretation, and inclusion in project reports and assignment.

---
---

# DEVELOPMENT STATUS & HANDOFF DOCUMENTATION

**Last Updated:** 2026-01-05

---

## IMPLEMENTATION STATUS

### Table 2: Prediction Quality Metrics - ✅ COMPLETED

**Implementation Date:** 2026-01-05

**Files Modified:**
| File | Status | Changes |
|------|--------|---------|
| `FirestoreRepository.kt` | ✅ Complete | Added `ConfusionCounts`, `PerAllergenCounts` data classes; Updated `ModelAggregateMetrics` with 10 new fields (totalTp, totalFp, totalFn, totalTn, precision, recall, f1Micro, f1Macro, hammingLoss, fnr); Added `calculateConfusionCounts()`, `calculatePerAllergenCounts()`, `calculateF1Macro()` methods; Updated `getModelAggregateMetrics()` to calculate all quality metrics |
| `ComparisonActivity.kt` | ✅ Complete | Added horizontal table UI; Added `showComparisonTable()` method; Added `exportMetricsToCsv()` method; Added new imports and view bindings |
| `activity_comparison.xml` | ✅ Complete | Added `HorizontalScrollView` with comparison table (id: comparisonTableScroll); Added Export CSV button (id: btnExportCsv); Updated constraint references |
| `styles.xml` | ✅ Complete | Added `TableHeaderCell`, `TableDataCell`, `TableModelCell` styles |
| `AndroidManifest.xml` | ✅ Complete | Added `FileProvider` for CSV sharing |
| `res/xml/file_paths.xml` | ✅ Created | New file for FileProvider paths |

**Key Design Decisions Made:**
1. **Storage Strategy:** Recalculate TP/FP/FN from existing `predictedAllergens` and `mappedAllergens` strings on fetch (NO Firestore schema changes needed)
2. **TN Counting:** 9 TNs per category for empty allergen cases (standard multi-label classification)
3. **F1 Display:** Both Micro AND Macro F1 displayed
4. **UI Layout:** Horizontal-scroll comparison table at TOP + existing model cards kept below
5. **Metrics Scope:** Aggregate only (no per-allergen breakdown in UI)
6. **Export:** CSV export with all metrics (quality + performance + memory)

**Metrics Implemented:**
- Precision = TP / (TP + FP)
- Recall = TP / (TP + FN)
- F1 Micro = 2TP / (2TP + FP + FN)
- F1 Macro = average of F1 per allergen category
- EMR = exact matches / total (already existed as `isMatch` percentage)
- Hamming Loss = (FP + FN) / (N × L) where N=200, L=9
- FNR = FN / (TP + FN)

---

### Table 3: Safety-Oriented Metrics - ✅ COMPLETED

**Implementation Date:** 2026-01-05

**Files Modified:**
| File | Status | Changes |
|------|--------|---------|
| `FirestoreRepository.kt` | ✅ Complete | Added `allergenKeywords` map (9 allergens × ~10 keywords each); Added `isAllergenInIngredients()`, `hasHallucination()`, `hasOverPrediction()` methods; Updated `ModelAggregateMetrics` with 3 new fields; Updated `getModelAggregateMetrics()` to calculate safety rates |
| `ComparisonActivity.kt` | ✅ Complete | Updated `showComparisonTable()` to display 3 safety columns with X.X% format; Updated `exportMetricsToCsv()` to include safety metrics |
| `activity_comparison.xml` | ✅ Complete | Added 3 header columns: HalR, OPR, AbsA |

**Metrics Implemented:**
1. **Hallucination Rate** - % of predictions containing allergens NOT derivable from ingredients (keyword matching)
2. **Over-Prediction Rate** - % of predictions with FP > 0 (extra allergens beyond ground truth)
3. **Abstention Accuracy** - TNR for no-allergen cases (correct empty predictions / total empty ground truth)

**Key Design Decisions:**
1. **Hallucination Detection:** Ingredient-keyword mapping approach (allergen → list of ingredient keywords)
2. **Calculation Strategy:** Calculate on-fetch (not stored in Firestore) - follows existing pattern for quality metrics
3. **Display Format:** Percentage with 1 decimal (e.g., "12.5%") for UI, 2 decimals for CSV export
4. **Column Headers:** HalR, OPR, AbsA (matching existing abbreviation style)

**Allergen Keyword Mapping:**
```kotlin
milk → [milk, cream, butter, cheese, whey, casein, lactose, dairy, yogurt, ghee, curd, buttermilk]
egg → [egg, albumin, mayonnaise, meringue, ovum, lysozyme, ovalbumin]
peanut → [peanut, groundnut, arachis, monkey nut]
tree nut → [almond, walnut, cashew, pecan, pistachio, hazelnut, macadamia, brazil nut, chestnut, nut, praline, marzipan, nougat]
wheat → [wheat, flour, gluten, semolina, durum, spelt, bulgur, couscous, bread, pasta, noodle, cereal, bran, starch]
soy → [soy, soya, tofu, edamame, miso, tempeh, lecithin]
fish → [fish, anchovy, sardine, tuna, salmon, cod, bass, mackerel, tilapia, trout, herring, haddock]
shellfish → [shrimp, prawn, crab, lobster, crayfish, oyster, mussel, clam, scallop, crustacean, mollusk, squid, octopus]
sesame → [sesame, tahini, halvah, hummus]
```

---

### Table 4: On-Device Efficiency Metrics - ✅ ALREADY IMPLEMENTED

These metrics were implemented in the original codebase:
- Latency (latencyMs)
- TTFT (ttftMs)
- ITPS (itps)
- OTPS (otps)
- OET (oetMs)
- Java Heap (javaHeapKb)
- Native Heap (nativeHeapKb)
- PSS (totalPssKb)

**Display:** Already shown in model cards on ComparisonActivity

---

## PREVIOUS CHANGES (Before Table 2 Implementation)

### Model Refactoring (3 → 7 Models) - ✅ COMPLETED
- Updated `ModelType` enum in `MainActivity.kt` with 7 models
- Added Llama 3 (type 2) and Phi (type 3) chat templates to `native-lib.cpp`
- Updated `dialog_loading_models.xml` (max=7, ~12 GB)
- Updated `activity_comparison.xml` badge text
- Updated `CLAUDE.md` documentation

### Prompt Improvement - ✅ COMPLETED
- Updated `buildPrompt()` in `MainActivity.kt` for zero-shot prompting
- Prompt includes allergen category list for constrained output

---

## ARCHITECTURAL NOTES

### Data Flow for Quality Metrics
```
Firestore: /models/{modelKey}/predictions/{dataId}
    ↓
getModelAggregateMetrics() fetches all 200 docs
    ↓
For each doc: calculateConfusionCounts(predicted, groundTruth)
    ↓
Aggregate: totalTp, totalFp, totalFn, totalTn
    ↓
Calculate: precision, recall, f1Micro, f1Macro, hammingLoss, fnr
    ↓
Return ModelAggregateMetrics with all fields
```

### Key Code Locations
- **Confusion calculation:** `FirestoreRepository.kt:98-116` - `calculateConfusionCounts()`
- **Per-allergen F1:** `FirestoreRepository.kt:126-158` - `calculatePerAllergenCounts()`
- **Table population:** `ComparisonActivity.kt:178-245` - `showComparisonTable()`
- **CSV export:** `ComparisonActivity.kt:250-319` - `exportMetricsToCsv()`

### 9 Allergen Categories (L=9)
```kotlin
"milk", "egg", "peanut", "tree nut", "wheat", "soy", "fish", "shellfish", "sesame"
```

---

## NEXT STEPS (Priority Order)

1. ~~**Implement Table 3: Safety-Oriented Metrics**~~ ✅ COMPLETED (2026-01-05)

2. **Testing & Verification**
   - Run predictions on device for at least 1 model
   - Verify all metrics (Table 2 + Table 3) display correctly in comparison table
   - Test CSV export includes all 21 columns (7 quality + 3 safety + 4 confusion + 8 efficiency)
   - Verify hallucination detection works with sample data

3. **Build Verification**
   - Run `./gradlew assembleDebug` to verify no compilation errors

---

## POTENTIAL ISSUES / BLOCKERS

1. **Hallucination Detection Edge Cases:**
   - Keyword matching may not catch all variations (e.g., "cream cheese" vs "cheese")
   - May need to expand keyword lists based on testing results
   - Cross-contamination mentions in ingredients not currently handled

2. **Build/Test:**
   - Build verification recommended after implementation

---

## FILES QUICK REFERENCE

| Purpose | File Path |
|---------|-----------|
| Quality metrics calculation | `app/src/main/java/com/mad/assignment/FirestoreRepository.kt` |
| Comparison UI logic | `app/src/main/java/com/mad/assignment/ComparisonActivity.kt` |
| Comparison layout | `app/src/main/res/layout/activity_comparison.xml` |
| Table styles | `app/src/main/res/values/styles.xml` |
| FileProvider config | `app/src/main/AndroidManifest.xml` |
| FileProvider paths | `app/src/main/res/xml/file_paths.xml` |
| Model enum & inference | `app/src/main/java/com/mad/assignment/MainActivity.kt` |
| Native inference | `app/src/main/cpp/native-lib.cpp` |
| Test data | `app/src/main/assets/food_preprocessed.json` (200 items) |

---

## SESSION CONTEXT FOR NEW CONVERSATION

**Last Updated:** 2026-01-05 (Session completed Table 3 implementation)

If starting a new conversation, tell Claude:

> "Continue from `app/NewRequirementByLecturer.md`. Table 2 AND Table 3 are COMPLETE. Read the handoff documentation for context. Next: build verification and testing."

---

## HANDOFF NOTES (2026-01-05 Session)

### CURRENT IMPLEMENTATION STATE: ✅ ALL CODING COMPLETE

| Requirement | Status | Notes |
|-------------|--------|-------|
| Table 2: Prediction Quality Metrics | ✅ COMPLETE | Precision, Recall, F1, EMR, Hamming Loss, FNR |
| Table 3: Safety-Oriented Metrics | ✅ COMPLETE | Hallucination Rate, Over-Prediction Rate, Abstention Accuracy |
| Table 4: On-Device Efficiency | ✅ ALREADY EXISTED | Latency, TTFT, ITPS, OTPS, OET, Memory metrics |

### KEY DECISIONS MADE THIS SESSION

1. **Hallucination Detection Approach:** Ingredient-keyword mapping
   - Each allergen maps to ~10 ingredient keywords
   - Hallucination = predicted allergen with NO keyword match in ingredients
   - Hardcoded in `FirestoreRepository.kt` (lines 94-104)

2. **Calculation Strategy:** On-fetch (not stored in Firestore)
   - Follows existing pattern for quality metrics
   - No Firestore schema changes needed
   - All data (ingredients, predicted, groundTruth) already stored

3. **Display Format:**
   - UI: Percentage with 1 decimal (e.g., "12.5%")
   - CSV: 2 decimals (e.g., "12.50")

4. **Column Headers:** HalR, OPR, AbsA (matching existing abbreviation style)

### FILES MODIFIED THIS SESSION

| File | Lines Changed | Purpose |
|------|---------------|---------|
| `FirestoreRepository.kt` | +60 lines | allergenKeywords map (94-104), isAllergenInIngredients() (202-206), hasHallucination() (216-223), hasOverPrediction() (233-245), ModelAggregateMetrics +3 fields (37-39), getModelAggregateMetrics() safety tracking (368-416, 438-442, 468-471) |
| `ComparisonActivity.kt` | +20 lines | showComparisonTable() safety columns (240-256), exportMetricsToCsv() safety headers & data (283-284, 305-308) |
| `activity_comparison.xml` | +9 lines | 3 header columns HalR/OPR/AbsA (193-202) |

### ARCHITECTURAL NOTES

**Safety Metrics Data Flow:**
```
Firestore: /models/{modelKey}/predictions/{dataId}
    ↓ (fetch all 200 docs per model)
getModelAggregateMetrics() iterates each doc:
    ↓
    - Get: predicted, groundTruth, ingredients
    - hasHallucination(predicted, ingredients) → hallucinationCount++
    - hasOverPrediction(predicted, groundTruth) → overPredictionCount++
    - If groundTruth empty: abstentionTotal++, if predicted also empty: abstentionCorrect++
    ↓
Calculate rates:
    - hallucinationRate = (hallucinationCount / count) * 100
    - overPredictionRate = (overPredictionCount / count) * 100
    - abstentionAccuracy = (abstentionCorrect / abstentionTotal) * 100
    ↓
Return ModelAggregateMetrics with all 3 safety fields
```

**Key Code Locations (Updated Line Numbers):**
- Allergen keywords: `FirestoreRepository.kt:94-104`
- Hallucination check: `FirestoreRepository.kt:216-223`
- Over-prediction check: `FirestoreRepository.kt:233-245`
- Safety rate calculation: `FirestoreRepository.kt:438-442`
- Table safety columns: `ComparisonActivity.kt:240-256`
- CSV safety export: `ComparisonActivity.kt:305-308`

### NO BLOCKERS OR ISSUES

- Implementation straightforward
- Follows existing patterns
- No compilation errors expected (but build not verified)

### UNFINISHED WORK: NONE

All Table 3 implementation is complete. No partially completed features.

### NEXT IMMEDIATE STEPS

1. **Build Verification** (NOT YET DONE)
   ```bash
   ./gradlew assembleDebug
   ```

2. **Run App & Test**
   - Run predictions for at least 1 model (200 samples)
   - Open ComparisonActivity
   - Verify 10-column table displays correctly
   - Verify HalR/OPR/AbsA show X.X% format
   - Test CSV export (should have 21 columns now)

3. **Optional: Expand Keywords**
   - If hallucination detection misses cases, expand `allergenKeywords` map
   - Current keywords are common but not exhaustive

### FIREBASE INFO

- **Project:** mad-project-a859a
- **Firestore:** models collection exists, empty (old data deprecated)
- **Structure:** `/models/{modelKey}/predictions/{dataId}`
- **User:** mkim8189@gmail.com

### COMPARISON TABLE COLUMNS (10 total)

| # | Header | Metric | Format |
|---|--------|--------|--------|
| 1 | Model | Display name | Text |
| 2 | Prec | Precision | 0.xxx |
| 3 | Rec | Recall | 0.xxx |
| 4 | F1-Mi | F1 Micro | 0.xxx |
| 5 | F1-Ma | F1 Macro | 0.xxx |
| 6 | EMR | Exact Match Ratio | 0.xxx |
| 7 | HL | Hamming Loss | 0.xxx |
| 8 | FNR | False Negative Rate | 0.xxx |
| 9 | HalR | Hallucination Rate | X.X% |
| 10 | OPR | Over-Prediction Rate | X.X% |
| 11 | AbsA | Abstention Accuracy | X.X% |

### CSV EXPORT COLUMNS (21 total)

Quality: Model, Precision, Recall, F1_Micro, F1_Macro, EMR, Hamming_Loss, FNR
Safety: Hallucination_Rate, Over_Prediction_Rate, Abstention_Accuracy
Confusion: TP, FP, FN, TN
Efficiency: Avg_Latency_ms, Avg_TTFT_ms, Avg_ITPS, Avg_OTPS, Avg_OET_ms, Avg_Java_Heap_KB, Avg_Native_Heap_KB, Avg_PSS_KB