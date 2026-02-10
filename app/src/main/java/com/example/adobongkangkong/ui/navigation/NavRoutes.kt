package com.example.adobongkangkong.ui.navigation

import java.time.LocalDate

object NavRoutes {
    object Dashboard {
        const val route: String = "dashboard"
        // Dashboard -> DayLog uses DayLog.dayLog(date)
    }

    object Heatmap {
        const val route: String = "heatmap"
        // Heatmap tap shows bottom sheet (UI state), but button navigates to DayLog.
    }

    object DayLog {
        private const val BASE = "daylog"
        private const val ARG_DATE = "date" // yyyy-MM-dd

        // Pattern
        const val route: String = "$BASE/{$ARG_DATE}"

        // Builder
        fun dayLog(date: LocalDate): String = "$BASE/${date}" // LocalDate.toString() => yyyy-MM-dd
    }

    object Foods {
        private const val BASE = "food"
        private const val ARG_FOOD_ID = "foodId"
        private const val ARG_NAME = "name"
        private const val ARG_BARCODE = "barcode"

        // Picker result is returned via SavedStateHandle; barcode is passed via route arg.
        private const val PICK_BASE = "food/pickBarcode"
        const val pickBarcode: String = "$PICK_BASE?$ARG_BARCODE={$ARG_BARCODE}"

        const val list: String = BASE
        const val details: String = "$BASE/{$ARG_FOOD_ID}"
        const val edit: String = "$BASE/edit/{$ARG_FOOD_ID}"

        // New food supports optional prefill name + optional prefill barcode
        const val new: String = "$BASE/new?$ARG_NAME={$ARG_NAME}&$ARG_BARCODE={$ARG_BARCODE}"

        fun details(foodId: Long): String = "$BASE/$foodId"
        fun edit(foodId: Long): String = "$BASE/edit/$foodId"

        fun new(prefillName: String? = null, prefillBarcode: String? = null): String {
            fun enc(s: String?): String {
                val v = s.orEmpty()
                return java.net.URLEncoder.encode(
                    v,
                    java.nio.charset.StandardCharsets.UTF_8.toString()
                )
            }
            return "$BASE/new?$ARG_NAME=${enc(prefillName)}&$ARG_BARCODE=${enc(prefillBarcode)}"
        }

        fun pickBarcode(barcode: String): String {
            val encoded = java.net.URLEncoder.encode(
                barcode,
                java.nio.charset.StandardCharsets.UTF_8.toString()
            )
            return "$PICK_BASE?$ARG_BARCODE=$encoded"
        }
    }

    object Recipes {
        const val route: String = "recipe"
        private const val BASE = "recipe"
        private const val ARG_RECIPE_ID = "recipeId"
        private const val ARG_EDIT_FOOD_ID = "editFoodId"

        // Route *pattern* registered in the NavGraph
        const val builder: String =
            "$BASE/builder?$ARG_RECIPE_ID={$ARG_RECIPE_ID}&$ARG_EDIT_FOOD_ID={$ARG_EDIT_FOOD_ID}"

        // Route *string* used by navigate(...)
        fun builder(recipeId: Long? = null, editFoodId: Long? = null): String {
            val r = recipeId?.toString().orEmpty()
            val f = editFoodId?.toString().orEmpty()
            // ✅ Always include BOTH query params, even if blank.
            return "$BASE/builder?$ARG_RECIPE_ID=$r&$ARG_EDIT_FOOD_ID=$f"
        }
    }

    object Debug {
        const val meowLogs: String = "debug/meowLogs"
    }

    object Planner {
        const val plannerDay = "planner/{dateIso}"
        fun plannerDay(dateIso: String): String = "planner/$dateIso"
    }
}


