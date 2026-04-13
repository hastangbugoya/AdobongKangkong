import SwiftUI

struct DayLogView: View {
    @EnvironmentObject private var store: AppStore

    @State private var selectedDate = Date()
    @State private var selectedFoodId: UUID?
    @State private var quantity = "1"
    @State private var unit: QuantityUnit = .servings

    var body: some View {
        List {
            Section("Date") {
                DatePicker("Log date", selection: $selectedDate, displayedComponents: .date)
                    .datePickerStyle(.compact)
            }

            Section("Add") {
                Picker("Food", selection: Binding(
                    get: { selectedFoodId ?? store.foods.first?.id },
                    set: { selectedFoodId = $0 }
                )) {
                    ForEach(store.foods) { food in
                        Text(food.name).tag(Optional(food.id))
                    }
                }

                TextField("Quantity", text: $quantity)
                    .keyboardType(.decimalPad)

                Picker("Unit", selection: $unit) {
                    ForEach(QuantityUnit.allCases) { item in
                        Text(item.label).tag(item)
                    }
                }

                Button("Add Log Entry") {
                    guard let foodId = selectedFoodId ?? store.foods.first?.id else { return }
                    let parsedQty = Double(quantity.replacingOccurrences(of: ",", with: "")) ?? 0
                    guard parsedQty > 0 else { return }
                    store.addLog(foodId: foodId, date: selectedDate, quantity: parsedQty, unit: unit)
                    quantity = "1"
                }
                .disabled(store.foods.isEmpty)
            }

            Section("Entries") {
                ForEach(entriesForDate) { entry in
                    HStack {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(store.foodById[entry.foodId]?.name ?? "Unknown food")
                            Text("\(Fmt.num(entry.quantity, digits: 2)) \(entry.unit.label)")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                        Button(role: .destructive) {
                            store.deleteLog(entry.id)
                        } label: {
                            Image(systemName: "trash")
                        }
                        .buttonStyle(.borderless)
                    }
                }

                if entriesForDate.isEmpty {
                    Text("No log entries for selected date")
                        .foregroundStyle(.secondary)
                }
            }
        }
        .navigationTitle("Day Log")
        .onAppear {
            selectedFoodId = selectedFoodId ?? store.foods.first?.id
        }
    }

    private var entriesForDate: [LogEntry] {
        store.logs
            .filter { Calendar.iso8601Local.isDate($0.date, inSameDayAs: selectedDate) }
            .sorted { $0.date > $1.date }
    }
}
