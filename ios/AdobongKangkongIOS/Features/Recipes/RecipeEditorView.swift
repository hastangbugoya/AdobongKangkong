import SwiftUI

private struct IngredientDraft: Identifiable {
    let id: UUID
    var foodId: UUID
    var quantity: String
    var unit: QuantityUnit
}

struct RecipeEditorView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss

    @State private var name: String
    @State private var servings: String
    @State private var ingredients: [IngredientDraft]

    let recipeId: UUID
    let onSave: (Recipe) -> Void
    let onDelete: (() -> Void)?

    init(recipe: Recipe, onSave: @escaping (Recipe) -> Void, onDelete: (() -> Void)? = nil) {
        _name = State(initialValue: recipe.name)
        _servings = State(initialValue: Fmt.num(recipe.servings, digits: 2))
        _ingredients = State(initialValue: recipe.ingredientItems.map {
            IngredientDraft(id: $0.id, foodId: $0.foodId, quantity: Fmt.num($0.quantity, digits: 2), unit: $0.unit)
        })
        self.recipeId = recipe.id
        self.onSave = onSave
        self.onDelete = onDelete
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Recipe") {
                    TextField("Name", text: $name)
                    TextField("Servings", text: $servings)
                        .keyboardType(.decimalPad)
                }

                Section("Ingredients") {
                    ForEach($ingredients) { $ingredient in
                        Picker("Food", selection: $ingredient.foodId) {
                            ForEach(store.foods) { food in
                                Text(food.name).tag(food.id)
                            }
                        }

                        TextField("Quantity", text: $ingredient.quantity)
                            .keyboardType(.decimalPad)

                        Picker("Unit", selection: $ingredient.unit) {
                            ForEach(QuantityUnit.allCases) { unit in
                                Text(unit.label).tag(unit)
                            }
                        }
                    }
                    .onDelete { ingredients.remove(atOffsets: $0) }

                    Button("Add Ingredient") {
                        guard let food = store.foods.first else { return }
                        ingredients.append(
                            IngredientDraft(id: UUID(), foodId: food.id, quantity: "1", unit: .servings)
                        )
                    }
                }

                if let onDelete {
                    Section {
                        Button("Delete Recipe", role: .destructive) {
                            onDelete()
                            dismiss()
                        }
                    }
                }
            }
            .navigationTitle("Recipe Editor")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        onSave(makeRecipe())
                        dismiss()
                    }
                    .disabled(name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || store.foods.isEmpty)
                }
            }
        }
    }

    private func makeRecipe() -> Recipe {
        Recipe(
            id: recipeId,
            name: name.trimmingCharacters(in: .whitespacesAndNewlines),
            servings: max(parse(servings, fallback: 1), 0.1),
            ingredientItems: ingredients.map {
                RecipeIngredient(
                    id: $0.id,
                    foodId: $0.foodId,
                    quantity: max(parse($0.quantity, fallback: 0), 0),
                    unit: $0.unit
                )
            }
        )
    }

    private func parse(_ value: String, fallback: Double = 0) -> Double {
        Double(value.replacingOccurrences(of: ",", with: "")) ?? fallback
    }
}
