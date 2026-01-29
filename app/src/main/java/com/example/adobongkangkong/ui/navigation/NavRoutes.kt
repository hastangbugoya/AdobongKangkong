package com.example.adobongkangkong.ui.navigation

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object NavRoutes {

    // --- Dashboard ---
    object Dashboard {
        const val route: String = "dashboard"


        const val STARTUP: String = "startup"


    }

    // --- Foods ---
    object Foods {
        private const val BASE = "food"

        // Patterns (NavHost routes)
        const val list: String = BASE
        const val details: String = "$BASE/{foodId}"
        const val edit: String = "$BASE/edit/{foodId}"
        const val new: String = "$BASE/new?name={name}"


        // Builders (NavController.navigate destinations)
        fun details(foodId: Long): String = "$BASE/$foodId"
        fun edit(foodId: Long): String = "$BASE/edit/$foodId"

        /**
         * Optional prefill name for "Create Food".
         * Uses URL encoding so you can safely pass spaces/quotes/etc.
         */
        fun new(prefillName: String? = null): String {
            val encoded = URLEncoder.encode(prefillName.orEmpty(), StandardCharsets.UTF_8.toString())
            return "$BASE/new?name=$encoded"
        }
    }

    // --- Recipes ---
    object Recipes {
        private const val BASE = "recipe"

        // Patterns
        const val new: String = "$BASE/new"
        const val edit: String = "$BASE/edit/{foodId}" // recipes are foods (isRecipe=true)

        // Builders
        fun edit(foodId: Long): String = "$BASE/edit/$foodId"
    }

    object Heatmap {
        const val route: String = "heatmap"
        const val dayLog: String = "dayLog/{date}"

        fun dayLog(date: java.time.LocalDate): String =
            "dayLog/$date"
    }
}
