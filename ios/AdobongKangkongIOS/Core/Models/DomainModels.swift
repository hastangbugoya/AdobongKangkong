import Foundation

enum NutrientBasis: String, Codable, CaseIterable, Identifiable {
    case per100g
    case per100ml
    case usdaReportedServing

    var id: String { rawValue }

    var label: String {
        switch self {
        case .per100g: return "Per 100g"
        case .per100ml: return "Per 100ml"
        case .usdaReportedServing: return "USDA Serving"
        }
    }
}

enum NutrientKey: String, Codable, CaseIterable, Hashable, Identifiable {
    case kcal
    case protein
    case carbs
    case fat

    var id: String { rawValue }

    var unit: String {
        switch self {
        case .kcal: return "kcal"
        case .protein, .carbs, .fat: return "g"
        }
    }

    var label: String {
        rawValue.capitalized
    }
}

enum QuantityUnit: String, Codable, CaseIterable, Identifiable {
    case servings
    case grams
    case milliliters

    var id: String { rawValue }

    var label: String {
        switch self {
        case .servings: return "servings"
        case .grams: return "g"
        case .milliliters: return "ml"
        }
    }
}

enum MealSlot: String, Codable, CaseIterable, Identifiable {
    case breakfast
    case lunch
    case dinner
    case snack

    var id: String { rawValue }

    var title: String {
        rawValue.capitalized
    }
}

struct Food: Identifiable, Codable, Equatable {
    var id: UUID
    var name: String
    var servingSize: Double
    var servingUnit: String
    var gramsPerServingUnit: Double?
    var mlPerServingUnit: Double?
    var basis: NutrientBasis
    var nutrientsPerCanonical100: [NutrientKey: Double]
    var barcodes: [String]
    var isFavorite: Bool
}

struct RecipeIngredient: Identifiable, Codable, Equatable {
    var id: UUID
    var foodId: UUID
    var quantity: Double
    var unit: QuantityUnit
}

struct Recipe: Identifiable, Codable, Equatable {
    var id: UUID
    var name: String
    var servings: Double
    var ingredientItems: [RecipeIngredient]
}

struct LogEntry: Identifiable, Codable, Equatable {
    var id: UUID
    var date: Date
    var foodId: UUID
    var quantity: Double
    var unit: QuantityUnit
}

struct PlannedMeal: Identifiable, Codable, Equatable {
    var id: UUID
    var date: Date
    var slot: MealSlot
    var recipeId: UUID
    var servings: Double
}

struct DailyNutritionSummary {
    var totals: [NutrientKey: Double]

    static var empty: DailyNutritionSummary {
        DailyNutritionSummary(totals: [:])
    }
}

struct ShoppingItem: Identifiable, Equatable {
    var id: UUID
    var foodName: String
    var quantity: Double
    var unit: QuantityUnit
}
