import SwiftUI

struct RecipesView: View {
    @EnvironmentObject private var store: AppStore
    @State private var editingRecipe: Recipe?

    var body: some View {
        List {
            if store.foods.isEmpty {
                Text("Add foods first before creating recipes.")
                    .foregroundStyle(.secondary)
            }

            ForEach(store.recipes) { recipe in
                Button {
                    editingRecipe = recipe
                } label: {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(recipe.name)
                        HStack {
                            Text("Servings: \(Fmt.num(recipe.servings, digits: 2))")
                            Spacer()
                            let kcal = store.recipeNutrition(recipe: recipe)[.kcal] ?? 0
                            Text("\(Fmt.num(kcal)) kcal total")
                        }
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    }
                }
            }
            .onDelete { offsets in
                for idx in offsets {
                    store.deleteRecipe(store.recipes[idx].id)
                }
            }
        }
        .navigationTitle("Recipes")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    guard let firstFood = store.foods.first else { return }
                    editingRecipe = Recipe(
                        id: UUID(),
                        name: "",
                        servings: 1,
                        ingredientItems: [
                            RecipeIngredient(id: UUID(), foodId: firstFood.id, quantity: 1, unit: .servings)
                        ]
                    )
                } label: {
                    Image(systemName: "plus")
                }
                .disabled(store.foods.isEmpty)
            }
        }
        .sheet(item: $editingRecipe) { recipe in
            RecipeEditorView(
                recipe: recipe,
                onSave: { store.upsertRecipe($0) },
                onDelete: store.recipeById[recipe.id] == nil ? nil : { store.deleteRecipe(recipe.id) }
            )
        }
    }
}
