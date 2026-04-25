package com.example.adobongkangkong.ui.navigation

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate

object NavRoutes {

    object Dashboard {
        private const val BASE = "dashboard"
        private const val ARG_DATE = "date"

        const val route: String = "$BASE?$ARG_DATE={$ARG_DATE}"

        fun dashboard(date: LocalDate? = null): String {
            return if (date == null) BASE else "$BASE?$ARG_DATE=${date}"
        }
    }

    object Calendar {
        const val route: String = "calendar"
    }

    object DayLog {
        private const val BASE = "dayLog"
        private const val ARG_DATE = "date"

        const val route: String = "$BASE/{$ARG_DATE}"

        fun dayLog(date: LocalDate): String = "$BASE/${date}"
    }

    object QuickAdd {
        private const val BASE = "quickAdd"
        private const val ARG_DATE = "date"

        const val route: String = "$BASE/{$ARG_DATE}"

        fun quickAdd(date: LocalDate): String = "$BASE/${date}"
    }

    object Foods {
        const val list: String = "foods"

        const val pickFood: String = "foods/pickFood"

        const val pickBarcode: String = "foods/pickBarcode?barcode={barcode}"
        fun pickBarcode(barcode: String): String = "foods/pickBarcode?barcode=${barcode.urlEncode()}"

        const val edit: String = "foods/edit/{foodId}?barcode={barcode}"
        fun edit(foodId: Long, barcode: String? = null): String {
            return "foods/edit/$foodId?barcode=${barcode.orEmpty().urlEncode()}"
        }

        const val RETURN_DEFAULT = "default"
        const val RETURN_DASHBOARD_QUICK_ADD = "dashboardQuickAdd"
        const val RETURN_DAY_LOG_QUICK_ADD = "dayLogQuickAdd"
        const val RETURN_QUICK_ADD_ROUTE = "quickAddRoute"

        const val new: String = "foods/new?name={name}&barcode={barcode}&returnTarget={returnTarget}"

        fun new(
            prefillName: String? = null,
            prefillBarcode: String? = null,
            returnTarget: String = RETURN_DEFAULT
        ): String {
            return "foods/new" +
                    "?name=${prefillName.orEmpty().urlEncode()}" +
                    "&barcode=${prefillBarcode.orEmpty().urlEncode()}" +
                    "&returnTarget=${returnTarget.urlEncode()}"
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

    object Usda {
        const val search: String = "usda/search"
    }

    object Planner {
        const val plannerDay = "planner/{dateIso}"
        fun plannerDay(dateIso: String): String = "planner/$dateIso"

        private const val ARG_MEAL_ID = "mealId"
        const val plannedMealEditor: String = "planner/mealEditor/{$ARG_MEAL_ID}"
        fun plannedMealEditor(mealId: Long): String = "planner/mealEditor/$mealId"

        private const val ARG_DATE_ISO = "dateIso"
        private const val ARG_SLOT = "slot"
        private const val ARG_TEMPLATE_ID_FOR_NEW = "templateId"
        const val plannedMealEditorNew: String =
            "planner/mealEditor/new/{$ARG_DATE_ISO}?$ARG_SLOT={$ARG_SLOT}&$ARG_TEMPLATE_ID_FOR_NEW={$ARG_TEMPLATE_ID_FOR_NEW}"

        fun plannedMealEditorNew(dateIso: String, slot: String, templateId: Long? = null): String {
            val template = templateId?.toString().orEmpty()
            return "planner/mealEditor/new/$dateIso?$ARG_SLOT=$slot&$ARG_TEMPLATE_ID_FOR_NEW=$template"
        }

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
        private const val ARG_START = "start"
        private const val ARG_DAYS = "days"

        const val route: String = "$BASE?$ARG_START={$ARG_START}&$ARG_DAYS={$ARG_DAYS}"

        fun shopping(startDate: LocalDate, days: Int = 7): String {
            return "$BASE?$ARG_START=${startDate}&$ARG_DAYS=$days"
        }
    }
}

private fun String.urlEncode(): String =
    URLEncoder.encode(this, StandardCharsets.UTF_8.toString())