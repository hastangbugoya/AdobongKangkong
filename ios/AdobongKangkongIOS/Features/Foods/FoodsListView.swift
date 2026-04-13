import SwiftUI

struct FoodsListView: View {
    @EnvironmentObject private var store: AppStore

    @State private var query = ""
    @State private var editingFood: Food?
    @State private var barcodeInput = ""
    @State private var barcodeResolveResult: Food?

    var body: some View {
        List {
            if !barcodeInput.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                Section("Barcode Lookup") {
                    if let barcodeResolveResult {
                        Text("Matched: \(barcodeResolveResult.name)")
                            .foregroundStyle(.secondary)
                    } else {
                        Text("No barcode match")
                            .foregroundStyle(.secondary)
                    }
                }
            }

            ForEach(filteredFoods) { food in
                Button {
                    editingFood = food
                } label: {
                    HStack {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(food.name)
                            Text(food.basis.label)
                                .font(.caption)
                                .foregroundStyle(.secondary)

                            if let firstBarcode = food.barcodes.first {
                                Text("Barcode: \(firstBarcode)")
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                            }
                        }

                        Spacer()

                        if food.isFavorite {
                            Image(systemName: "star.fill")
                                .foregroundStyle(.yellow)
                        }
                    }
                }
            }
            .onDelete { indexSet in
                let targets = indexSet.map { filteredFoods[$0].id }
                for id in targets {
                    store.deleteFood(id)
                }
            }
        }
        .navigationTitle("Foods")
        .searchable(text: $query, prompt: "Search foods or barcode")
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                NavigationLink {
                    UsdaSearchView()
                } label: {
                    Image(systemName: "network")
                }

                Button {
                    editingFood = Food(
                        id: UUID(),
                        name: "",
                        servingSize: 1,
                        servingUnit: "serving",
                        gramsPerServingUnit: nil,
                        mlPerServingUnit: nil,
                        basis: .per100g,
                        nutrientsPerCanonical100: [.kcal: 0, .protein: 0, .carbs: 0, .fat: 0],
                        barcodes: barcodeInput.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? [] : [barcodeInput],
                        isFavorite: false
                    )
                } label: {
                    Image(systemName: "plus")
                }
            }
        }
        .safeAreaInset(edge: .bottom) {
            HStack {
                TextField("Barcode", text: $barcodeInput)
                    .textFieldStyle(.roundedBorder)
                    .textInputAutocapitalization(.never)
                Button("Find") {
                    barcodeResolveResult = store.food(matchingBarcode: barcodeInput)
                }
                .buttonStyle(.bordered)
            }
            .padding()
            .background(.ultraThinMaterial)
        }
        .sheet(item: $editingFood) { food in
            FoodEditorView(
                food: food,
                onSave: { savedFood in
                    store.upsertFood(savedFood)
                    for code in savedFood.barcodes {
                        store.assignBarcode(code, toFoodId: savedFood.id)
                    }
                },
                onDelete: store.foodById[food.id] == nil ? nil : { store.deleteFood(food.id) }
            )
        }
        .onChange(of: query) { _, newValue in
            if !newValue.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                barcodeInput = newValue
                barcodeResolveResult = store.food(matchingBarcode: newValue)
            }
        }
    }

    private var filteredFoods: [Food] {
        if query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return store.foods
        }

        return store.foods.filter { food in
            if food.name.localizedCaseInsensitiveContains(query) {
                return true
            }
            return food.barcodes.contains { $0.localizedCaseInsensitiveContains(query) }
        }
    }
}
