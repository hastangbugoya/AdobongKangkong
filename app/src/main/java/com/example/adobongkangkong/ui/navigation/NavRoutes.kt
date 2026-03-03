package com.example.adobongkangkong.ui.navigation

import java.time.LocalDate

object NavRoutes {


    object Dashboard {
        private const val BASE = "dashboard"
        private const val ARG_DATE = "date" // yyyy-MM-dd

        // Route pattern supports optional query
        const val route: String = "$BASE?$ARG_DATE={$ARG_DATE}"

        fun dashboard(date: LocalDate? = null): String {
            return if (date == null) BASE else "$BASE?$ARG_DATE=$date"
        }
    }

    object Calendar {
        const val route: String = "calendar"
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
        private const val ARG_REQUEST_KEY = "requestKey"

        // Routes (patterns)
        const val list: String = BASE
        const val details: String = "$BASE/{$ARG_FOOD_ID}"

        // ✅ Edit supports optional barcode (for “assign existing” return)
        private const val EDIT_BASE = "$BASE/edit/{$ARG_FOOD_ID}"
        const val edit: String = "$EDIT_BASE?$ARG_BARCODE={$ARG_BARCODE}"

        // ✅ New supports optional name + optional barcode
        private const val NEW_BASE = "$BASE/new"
        const val new: String = "$NEW_BASE?$ARG_NAME={$ARG_NAME}&$ARG_BARCODE={$ARG_BARCODE}"

        // ✅ Picker supports required/optional barcode as query (we’ll treat blank as none)
        private const val PICK_BASE = "$BASE/pickBarcode"
        const val pickBarcode: String = "$PICK_BASE?$ARG_BARCODE={$ARG_BARCODE}"

        // ✅ Food picker (returns a selected foodId to the caller via SavedStateHandle)
        const val pickFood: String = "$BASE/pickFood/{$ARG_REQUEST_KEY}"

        // Builders
        fun details(foodId: Long): String = "$BASE/$foodId"

        fun edit(foodId: Long, barcode: String? = null): String {
            val encoded = enc(barcode)
            return "$BASE/edit/$foodId?$ARG_BARCODE=$encoded"
        }

        fun new(prefillName: String? = null, prefillBarcode: String? = null): String {
            return "$NEW_BASE?$ARG_NAME=${enc(prefillName)}&$ARG_BARCODE=${enc(prefillBarcode)}"
        }

        fun pickBarcode(barcode: String): String {
            return "$PICK_BASE?$ARG_BARCODE=${enc(barcode)}"
        }

        fun pickFood(requestKey: String): String {
            return "$BASE/pickFood/${enc(requestKey)}"
        }

        private fun enc(s: String?): String {
            val v = s.orEmpty()
            return java.net.URLEncoder.encode(v, java.nio.charset.StandardCharsets.UTF_8.toString())
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

        private const val ARG_MEAL_ID = "mealId"
        const val plannedMealEditor: String = "planner/mealEditor/{$ARG_MEAL_ID}"
        fun plannedMealEditor(mealId: Long): String = "planner/mealEditor/$mealId"
    }

    object Shopping {
        private const val BASE = "shopping"
        private const val ARG_START = "start" // yyyy-MM-dd
        private const val ARG_DAYS = "days"   // Int

        // Pattern
        const val route: String = "$BASE?$ARG_START={$ARG_START}&$ARG_DAYS={$ARG_DAYS}"

        // Builder
        fun shopping(startDate: LocalDate, days: Int = 7): String {
            return "$BASE?$ARG_START=${startDate}&$ARG_DAYS=$days"
        }
    }
}


