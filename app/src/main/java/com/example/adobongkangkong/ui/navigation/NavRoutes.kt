package com.example.adobongkangkong.ui.navigation

import java.time.LocalDate

object NavRoutes {

    object Dashboard {
        private const val BASE = "dashboard"
        private const val ARG_DATE = "date" // yyyy-MM-dd

        // Route pattern supports optional query
        const val route: String = "$BASE?$ARG_DATE={$ARG_DATE}"

        fun dashboard(date: LocalDate? = null): String {
            return if (date == null) {
                BASE
            } else {
                "$BASE?$ARG_DATE=${date}"
            }
        }
    }

    object Calendar {
        const val route: String = "calendar"
    }

    object DayLog {
        private const val BASE = "dayLog"
        private const val ARG_DATE = "date" // yyyy-MM-dd

        const val route: String = "$BASE/{$ARG_DATE}"

        fun dayLog(date: LocalDate): String {
            return "$BASE/${date}"
        }
    }

    object QuickAdd {
        private const val BASE = "quickAdd"
        private const val ARG_DATE = "date" // yyyy-MM-dd

        const val route: String = "$BASE/{$ARG_DATE}"

        fun quickAdd(date: LocalDate): String {
            return "$BASE/${date}"
        }
    }

    object Foods {
        const val list: String = "foods"

        // Food picker mode (returns a selected foodId to the caller via SavedStateHandle)
        const val pickFood: String = "foods/pickFood"

        const val pickBarcode: String = "foods/pickBarcode?barcode={barcode}"
        fun pickBarcode(barcode: String): String = "foods/pickBarcode?barcode=$barcode"

        const val edit: String = "foods/edit/{foodId}?barcode={barcode}"
        fun edit(foodId: Long, barcode: String? = null): String {
            val b = barcode.orEmpty()
            return "foods/edit/$foodId?barcode=$b"
        }

        const val new: String = "foods/new?name={name}&barcode={barcode}"
        fun new(prefillName: String? = null, prefillBarcode: String? = null): String {
            val n = prefillName.orEmpty()
            val b = prefillBarcode.orEmpty()
            return "foods/new?name=$n&barcode=$b"
        }
    }

    object Recipes {
        const val route: String = "recipes"

        const val builder: String = "recipes/builder?recipeId={recipeId}&editFoodId={editFoodId}"

        fun builder(recipeId: Long? = null, editFoodId: Long? = null): String {
            val r = recipeId?.toString().orEmpty()
            val e = editFoodId?.toString().orEmpty()
            return "recipes/builder?recipeId=$r&editFoodId=$e"
        }
    }

    object Debug {
        const val meowLogs: String = "debug/meowLogs"
    }

    object Planner {
        private const val KEY_TEMPLATE_PICK_TEMPLATE_ID = "template_pick_template_id"
        private const val KEY_TEMPLATE_PICK_OVERRIDE_SLOT = "template_pick_override_slot"

        const val plannerDay = "planner/{dateIso}"
        fun plannerDay(dateIso: String): String = "planner/$dateIso"

        private const val ARG_MEAL_ID = "mealId"
        const val plannedMealEditor: String = "planner/mealEditor/{$ARG_MEAL_ID}"
        fun plannedMealEditor(mealId: Long): String = "planner/mealEditor/$mealId"

        private const val ARG_DATE_ISO = "dateIso"
        private const val ARG_SLOT = "slot"
        const val plannedMealEditorNew: String = "planner/mealEditor/new/{$ARG_DATE_ISO}?$ARG_SLOT={$ARG_SLOT}"
        fun plannedMealEditorNew(dateIso: String, slot: String): String =
            "planner/mealEditor/new/$dateIso?$ARG_SLOT=$slot"

        // Template Picker
        const val templatePicker: String = "planner/templatePicker/{$ARG_DATE_ISO}?$ARG_SLOT={$ARG_SLOT}"
        fun templatePicker(dateIso: String, slot: String = ""): String =
            "planner/templatePicker/$dateIso?$ARG_SLOT=$slot"

        const val templates: String = "planner/templates"

        private const val ARG_TEMPLATE_ID = "templateId"
        const val templateEditor: String = "planner/templateEditor/{$ARG_TEMPLATE_ID}"
        fun templateEditor(templateId: Long): String = "planner/templateEditor/$templateId"

        const val templateEditorNew: String = "planner/templateEditorNew"
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