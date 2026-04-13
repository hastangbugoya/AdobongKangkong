import SwiftUI

struct DashboardView: View {
    @EnvironmentObject private var store: AppStore
    @State private var selectedDate = Date()

    var body: some View {
        let summary = store.dailyNutrition(for: selectedDate)

        List {
            Section("Date") {
                DatePicker("Selected day", selection: $selectedDate, displayedComponents: .date)
                    .datePickerStyle(.compact)
            }

            Section("Daily Nutrition") {
                ForEach(NutrientKey.allCases) { nutrient in
                    HStack {
                        Text(nutrient.label)
                        Spacer()
                        Text("\(Fmt.num(summary.totals[nutrient] ?? 0)) \(nutrient.unit)")
                            .foregroundStyle(.secondary)
                    }
                }
            }

            Section("Overview") {
                Text("Deterministic nutrition math with canonical nutrient basis and explicit serving bridges.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
        .navigationTitle("Dashboard")
    }
}
