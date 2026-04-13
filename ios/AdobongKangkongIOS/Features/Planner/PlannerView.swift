import SwiftUI

struct PlannerView: View {
    @EnvironmentObject private var store: AppStore
    @State private var selectedDate = Date()
    @State private var activeSlot: MealSlot?

    var body: some View {
        List {
            Section("Date") {
                DatePicker("Plan date", selection: $selectedDate, displayedComponents: .date)
                    .datePickerStyle(.compact)
            }

            Section("Meal Slots") {
                ForEach(MealSlot.allCases) { slot in
                    HStack {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(slot.title)
                            Text(planSummary(for: slot))
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }

                        Spacer()

                        Button("Set") {
                            activeSlot = slot
                        }
                        .buttonStyle(.bordered)
                    }
                }
            }
        }
        .navigationTitle("Planner")
        .confirmationDialog(
            "Select Recipe",
            isPresented: Binding(
                get: { activeSlot != nil },
                set: { if !$0 { activeSlot = nil } }
            ),
            titleVisibility: .visible
        ) {
            if let slot = activeSlot {
                ForEach(store.recipes) { recipe in
                    Button(recipe.name) {
                        store.planMeal(date: selectedDate, slot: slot, recipeId: recipe.id, servings: recipe.servings)
                    }
                }

                Button("Clear Slot", role: .destructive) {
                    store.removePlannedMeal(date: selectedDate, slot: slot)
                }
            }

            Button("Cancel", role: .cancel) {}
        }
    }

    private func planSummary(for slot: MealSlot) -> String {
        let plan = store.plannedMeals.first {
            Calendar.iso8601Local.isDate($0.date, inSameDayAs: selectedDate) && $0.slot == slot
        }

        guard let plan else { return "No meal planned" }
        guard let recipe = store.recipeById[plan.recipeId] else { return "Missing recipe" }
        return "\(recipe.name) • \(Fmt.num(plan.servings, digits: 1)) servings"
    }
}
