import SwiftUI

struct FoodEditorView: View {
    @Environment(\.dismiss) private var dismiss

    @State private var name: String
    @State private var servingSize: String
    @State private var servingUnit: String
    @State private var gramsPerServing: String
    @State private var mlPerServing: String
    @State private var basis: NutrientBasis
    @State private var kcal: String
    @State private var protein: String
    @State private var carbs: String
    @State private var fat: String
    @State private var barcodesCSV: String
    @State private var isFavorite: Bool

    let originalId: UUID
    let onSave: (Food) -> Void
    let onDelete: (() -> Void)?

    init(food: Food, onSave: @escaping (Food) -> Void, onDelete: (() -> Void)? = nil) {
        _name = State(initialValue: food.name)
        _servingSize = State(initialValue: Fmt.num(food.servingSize, digits: 2))
        _servingUnit = State(initialValue: food.servingUnit)
        _gramsPerServing = State(initialValue: food.gramsPerServingUnit.map { Fmt.num($0, digits: 2) } ?? "")
        _mlPerServing = State(initialValue: food.mlPerServingUnit.map { Fmt.num($0, digits: 2) } ?? "")
        _basis = State(initialValue: food.basis)
        _kcal = State(initialValue: Fmt.num(food.nutrientsPerCanonical100[.kcal] ?? 0, digits: 2))
        _protein = State(initialValue: Fmt.num(food.nutrientsPerCanonical100[.protein] ?? 0, digits: 2))
        _carbs = State(initialValue: Fmt.num(food.nutrientsPerCanonical100[.carbs] ?? 0, digits: 2))
        _fat = State(initialValue: Fmt.num(food.nutrientsPerCanonical100[.fat] ?? 0, digits: 2))
        _barcodesCSV = State(initialValue: food.barcodes.joined(separator: ", "))
        _isFavorite = State(initialValue: food.isFavorite)
        self.originalId = food.id
        self.onSave = onSave
        self.onDelete = onDelete
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Identity") {
                    TextField("Name", text: $name)
                    Toggle("Favorite", isOn: $isFavorite)
                }

                Section("Serving") {
                    TextField("Serving size", text: $servingSize)
                        .keyboardType(.decimalPad)
                    TextField("Serving unit", text: $servingUnit)
                    TextField("Grams per serving unit", text: $gramsPerServing)
                        .keyboardType(.decimalPad)
                    TextField("ML per serving unit", text: $mlPerServing)
                        .keyboardType(.decimalPad)
                }

                Section("Nutrient Basis") {
                    Picker("Basis", selection: $basis) {
                        ForEach(NutrientBasis.allCases) { item in
                            Text(item.label).tag(item)
                        }
                    }
                }

                Section("Barcodes") {
                    TextField("Comma-separated barcodes", text: $barcodesCSV)
                        .textInputAutocapitalization(.never)
                }

                Section("Canonical Nutrients") {
                    TextField("Kcal", text: $kcal).keyboardType(.decimalPad)
                    TextField("Protein (g)", text: $protein).keyboardType(.decimalPad)
                    TextField("Carbs (g)", text: $carbs).keyboardType(.decimalPad)
                    TextField("Fat (g)", text: $fat).keyboardType(.decimalPad)
                }

                if let onDelete {
                    Section {
                        Button("Delete Food", role: .destructive) {
                            onDelete()
                            dismiss()
                        }
                    }
                }
            }
            .navigationTitle("Food Editor")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        onSave(makeFood())
                        dismiss()
                    }
                    .disabled(name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
    }

    private func makeFood() -> Food {
        Food(
            id: originalId,
            name: name.trimmingCharacters(in: .whitespacesAndNewlines),
            servingSize: parse(servingSize, fallback: 1),
            servingUnit: servingUnit.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "serving" : servingUnit,
            gramsPerServingUnit: parseOptional(gramsPerServing),
            mlPerServingUnit: parseOptional(mlPerServing),
            basis: basis,
            nutrientsPerCanonical100: [
                .kcal: parse(kcal),
                .protein: parse(protein),
                .carbs: parse(carbs),
                .fat: parse(fat)
            ],
            barcodes: barcodesCSV
                .split(separator: ",")
                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                .filter { !$0.isEmpty },
            isFavorite: isFavorite
        )
    }

    private func parse(_ value: String, fallback: Double = 0) -> Double {
        Double(value.replacingOccurrences(of: ",", with: "")) ?? fallback
    }

    private func parseOptional(_ value: String) -> Double? {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        return parse(trimmed)
    }
}
