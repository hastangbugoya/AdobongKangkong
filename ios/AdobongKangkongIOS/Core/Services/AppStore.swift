import Foundation
import SwiftUI

@MainActor
final class AppStore: ObservableObject {
    static let currentSchemaVersion = 2

    @Published private(set) var foods: [Food] = []
    @Published private(set) var recipes: [Recipe] = []
    @Published private(set) var logs: [LogEntry] = []
    @Published private(set) var plannedMeals: [PlannedMeal] = []

    private let fileURL: URL

    init() {
        let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first
            ?? URL(fileURLWithPath: NSTemporaryDirectory())
        self.fileURL = documents.appendingPathComponent("adobongkangkong-ios-store.json")

        if !load() {
            seedDefaults()
            save()
        }
    }

    var foodById: [UUID: Food] {
        Dictionary(uniqueKeysWithValues: foods.map { ($0.id, $0) })
    }

    var recipeById: [UUID: Recipe] {
        Dictionary(uniqueKeysWithValues: recipes.map { ($0.id, $0) })
    }

    func food(matchingBarcode barcode: String) -> Food? {
        let normalized = normalizeBarcode(barcode)
        guard !normalized.isEmpty else { return nil }

        return foods.first { food in
            food.barcodes.contains { normalizeBarcode($0) == normalized }
        }
    }

    func assignBarcode(_ barcode: String, toFoodId foodId: UUID) {
        let normalized = normalizeBarcode(barcode)
        guard !normalized.isEmpty else { return }

        for idx in foods.indices {
            foods[idx].barcodes.removeAll { normalizeBarcode($0) == normalized }
        }

        guard let targetIdx = foods.firstIndex(where: { $0.id == foodId }) else {
            save()
            return
        }

        if !foods[targetIdx].barcodes.contains(where: { normalizeBarcode($0) == normalized }) {
            foods[targetIdx].barcodes.append(normalized)
            foods[targetIdx].barcodes.sort()
        }

        save()
    }

    func importUSDAFood(_ candidate: USDAFoodCandidate, preferredBarcode: String? = nil) {
        var barcodes = candidate.barcodes.map(normalizeBarcode).filter { !$0.isEmpty }
        if let preferredBarcode {
            let normalized = normalizeBarcode(preferredBarcode)
            if !normalized.isEmpty {
                barcodes.append(normalized)
            }
        }
        barcodes = Array(Set(barcodes)).sorted()

        let food = Food(
            id: UUID(),
            name: candidate.description,
            servingSize: max(candidate.servingSize ?? 1, 0.0001),
            servingUnit: candidate.servingSizeUnit ?? "serving",
            gramsPerServingUnit: nil,
            mlPerServingUnit: nil,
            basis: .usdaReportedServing,
            nutrientsPerCanonical100: candidate.nutrients,
            barcodes: barcodes,
            isFavorite: false
        )

        upsertFood(food)
    }

    func upsertFood(_ food: Food) {
        var normalizedFood = food
        normalizedFood.barcodes = Array(Set(food.barcodes.map(normalizeBarcode).filter { !$0.isEmpty })).sorted()

        if let idx = foods.firstIndex(where: { $0.id == normalizedFood.id }) {
            foods[idx] = normalizedFood
        } else {
            foods.append(normalizedFood)
        }
        foods.sort { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
        save()
    }

    func deleteFood(_ id: UUID) {
        foods.removeAll { $0.id == id }
        recipes = recipes.map { recipe in
            var updated = recipe
            updated.ingredientItems.removeAll { $0.foodId == id }
            return updated
        }
        logs.removeAll { $0.foodId == id }
        save()
    }

    func upsertRecipe(_ recipe: Recipe) {
        if let idx = recipes.firstIndex(where: { $0.id == recipe.id }) {
            recipes[idx] = recipe
        } else {
            recipes.append(recipe)
        }
        recipes.sort { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
        save()
    }

    func deleteRecipe(_ id: UUID) {
        recipes.removeAll { $0.id == id }
        plannedMeals.removeAll { $0.recipeId == id }
        save()
    }

    func addLog(foodId: UUID, date: Date, quantity: Double, unit: QuantityUnit) {
        let item = LogEntry(id: UUID(), date: date, foodId: foodId, quantity: quantity, unit: unit)
        logs.append(item)
        save()
    }

    func deleteLog(_ id: UUID) {
        logs.removeAll { $0.id == id }
        save()
    }

    func planMeal(date: Date, slot: MealSlot, recipeId: UUID, servings: Double) {
        if let idx = plannedMeals.firstIndex(where: {
            Calendar.iso8601Local.isDate($0.date, inSameDayAs: date) && $0.slot == slot
        }) {
            plannedMeals[idx].recipeId = recipeId
            plannedMeals[idx].servings = servings
        } else {
            plannedMeals.append(
                PlannedMeal(id: UUID(), date: date, slot: slot, recipeId: recipeId, servings: servings)
            )
        }
        save()
    }

    func removePlannedMeal(date: Date, slot: MealSlot) {
        plannedMeals.removeAll {
            Calendar.iso8601Local.isDate($0.date, inSameDayAs: date) && $0.slot == slot
        }
        save()
    }

    func dailyNutrition(for date: Date) -> DailyNutritionSummary {
        let filtered = logs.filter { Calendar.iso8601Local.isDate($0.date, inSameDayAs: date) }

        var totals: [NutrientKey: Double] = [:]
        for log in filtered {
            guard let food = foodById[log.foodId], let servings = servingsFor(food: food, quantity: log.quantity, unit: log.unit) else {
                continue
            }

            for key in NutrientKey.allCases {
                let valuePerServing = nutrientPerServing(food: food, nutrient: key)
                totals[key, default: 0] += valuePerServing * servings
            }
        }

        return DailyNutritionSummary(totals: totals)
    }

    func shoppingList(startDate: Date, days: Int) -> [ShoppingItem] {
        guard days > 0 else { return [] }

        let end = Calendar.iso8601Local.date(byAdding: .day, value: days - 1, to: startDate) ?? startDate

        let plansInWindow = plannedMeals.filter { plan in
            (plan.date >= startDate && plan.date <= end)
        }

        var aggregate: [String: ShoppingItem] = [:]

        for plan in plansInWindow {
            guard let recipe = recipeById[plan.recipeId] else { continue }
            let ratio = plan.servings / max(recipe.servings, 0.0001)

            for ingredient in recipe.ingredientItems {
                guard let food = foodById[ingredient.foodId] else { continue }

                let key = "\(food.id.uuidString)-\(ingredient.unit.rawValue)"
                let qty = ingredient.quantity * ratio

                if let current = aggregate[key] {
                    aggregate[key] = ShoppingItem(
                        id: current.id,
                        foodName: current.foodName,
                        quantity: current.quantity + qty,
                        unit: current.unit
                    )
                } else {
                    aggregate[key] = ShoppingItem(
                        id: UUID(),
                        foodName: food.name,
                        quantity: qty,
                        unit: ingredient.unit
                    )
                }
            }
        }

        return aggregate.values.sorted { lhs, rhs in
            if lhs.foodName == rhs.foodName {
                return lhs.unit.rawValue < rhs.unit.rawValue
            }
            return lhs.foodName.localizedCaseInsensitiveCompare(rhs.foodName) == .orderedAscending
        }
    }

    func recipeNutrition(recipe: Recipe) -> [NutrientKey: Double] {
        var totals: [NutrientKey: Double] = [:]

        for ingredient in recipe.ingredientItems {
            guard let food = foodById[ingredient.foodId], let servings = servingsFor(food: food, quantity: ingredient.quantity, unit: ingredient.unit) else {
                continue
            }

            for key in NutrientKey.allCases {
                totals[key, default: 0] += nutrientPerServing(food: food, nutrient: key) * servings
            }
        }

        return totals
    }

    func resetToSeedData() {
        seedDefaults()
        save()
    }

    func exportBackupData() throws -> Data {
        let envelope = SnapshotEnvelope(
            schemaVersion: Self.currentSchemaVersion,
            snapshot: Snapshot(foods: foods, recipes: recipes, logs: logs, plannedMeals: plannedMeals)
        )
        return try JSONEncoder().encode(envelope)
    }

    func importBackupData(_ data: Data) throws {
        if let envelope = try? JSONDecoder().decode(SnapshotEnvelope.self, from: data) {
            applySnapshot(envelope.snapshot, fromSchemaVersion: envelope.schemaVersion)
            save()
            return
        }

        if let legacy = try? JSONDecoder().decode(Snapshot.self, from: data) {
            applySnapshot(legacy, fromSchemaVersion: 1)
            save()
            return
        }

        throw AppStoreBackupError.invalidFormat
    }

    private func nutrientPerServing(food: Food, nutrient: NutrientKey) -> Double {
        let canonical = food.nutrientsPerCanonical100[nutrient] ?? 0

        switch food.basis {
        case .per100g:
            let grams = food.gramsPerServingUnit ?? (food.servingUnit.lowercased() == "g" ? food.servingSize : nil)
            guard let grams else { return 0 }
            return canonical * (grams / 100.0)
        case .per100ml:
            let ml = food.mlPerServingUnit ?? (food.servingUnit.lowercased() == "ml" ? food.servingSize : nil)
            guard let ml else { return 0 }
            return canonical * (ml / 100.0)
        case .usdaReportedServing:
            return canonical
        }
    }

    private func servingsFor(food: Food, quantity: Double, unit: QuantityUnit) -> Double? {
        guard quantity > 0 else { return nil }

        switch unit {
        case .servings:
            return quantity
        case .grams:
            guard let gramsPerServing = food.gramsPerServingUnit, gramsPerServing > 0 else { return nil }
            return quantity / gramsPerServing
        case .milliliters:
            guard let mlPerServing = food.mlPerServingUnit, mlPerServing > 0 else { return nil }
            return quantity / mlPerServing
        }
    }

    private func load() -> Bool {
        guard let data = try? Data(contentsOf: fileURL) else { return false }

        if let envelope = try? JSONDecoder().decode(SnapshotEnvelope.self, from: data) {
            applySnapshot(envelope.snapshot, fromSchemaVersion: envelope.schemaVersion)
            return true
        }

        if let legacy = try? JSONDecoder().decode(Snapshot.self, from: data) {
            applySnapshot(legacy, fromSchemaVersion: 1)
            return true
        }

        return false
    }

    private func save() {
        let envelope = SnapshotEnvelope(
            schemaVersion: Self.currentSchemaVersion,
            snapshot: Snapshot(foods: foods, recipes: recipes, logs: logs, plannedMeals: plannedMeals)
        )
        guard let data = try? JSONEncoder().encode(envelope) else { return }
        try? data.write(to: fileURL, options: [.atomic])
    }

    private func applySnapshot(_ snapshot: Snapshot, fromSchemaVersion schemaVersion: Int) {
        var migrated = snapshot

        if schemaVersion < 2 {
            migrated.foods = migrated.foods.map { food in
                var copy = food
                copy.barcodes = Array(Set(food.barcodes.map(normalizeBarcode).filter { !$0.isEmpty })).sorted()
                return copy
            }
        }

        foods = migrated.foods
        recipes = migrated.recipes
        logs = migrated.logs
        plannedMeals = migrated.plannedMeals
    }

    private func normalizeBarcode(_ value: String) -> String {
        value.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func seedDefaults() {
        let rice = Food(
            id: UUID(),
            name: "Cooked Rice",
            servingSize: 1,
            servingUnit: "cup",
            gramsPerServingUnit: 158,
            mlPerServingUnit: nil,
            basis: .per100g,
            nutrientsPerCanonical100: [.kcal: 130, .protein: 2.7, .carbs: 28.2, .fat: 0.3],
            barcodes: [],
            isFavorite: true
        )

        let chickenAdobo = Food(
            id: UUID(),
            name: "Chicken Adobo",
            servingSize: 1,
            servingUnit: "serving",
            gramsPerServingUnit: 180,
            mlPerServingUnit: nil,
            basis: .per100g,
            nutrientsPerCanonical100: [.kcal: 210, .protein: 20.4, .carbs: 4.2, .fat: 12.3],
            barcodes: [],
            isFavorite: true
        )

        let kangkong = Food(
            id: UUID(),
            name: "Kangkong",
            servingSize: 1,
            servingUnit: "cup",
            gramsPerServingUnit: 56,
            mlPerServingUnit: nil,
            basis: .per100g,
            nutrientsPerCanonical100: [.kcal: 19, .protein: 2.6, .carbs: 3.1, .fat: 0.2],
            barcodes: [],
            isFavorite: false
        )

        foods = [rice, chickenAdobo, kangkong]

        let adoboPlate = Recipe(
            id: UUID(),
            name: "Adobo Plate",
            servings: 2,
            ingredientItems: [
                RecipeIngredient(id: UUID(), foodId: chickenAdobo.id, quantity: 2, unit: .servings),
                RecipeIngredient(id: UUID(), foodId: rice.id, quantity: 2, unit: .servings),
                RecipeIngredient(id: UUID(), foodId: kangkong.id, quantity: 1, unit: .servings)
            ]
        )

        recipes = [adoboPlate]

        let today = Date()
        logs = [
            LogEntry(id: UUID(), date: today, foodId: rice.id, quantity: 1, unit: .servings),
            LogEntry(id: UUID(), date: today, foodId: chickenAdobo.id, quantity: 1, unit: .servings)
        ]

        plannedMeals = [
            PlannedMeal(id: UUID(), date: today, slot: .lunch, recipeId: adoboPlate.id, servings: 2)
        ]
    }
}

private struct Snapshot: Codable {
    var foods: [Food]
    var recipes: [Recipe]
    var logs: [LogEntry]
    var plannedMeals: [PlannedMeal]
}

private struct SnapshotEnvelope: Codable {
    var schemaVersion: Int
    var snapshot: Snapshot
}

enum AppStoreBackupError: Error {
    case invalidFormat
}
