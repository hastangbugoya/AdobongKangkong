import SwiftUI

struct ShoppingView: View {
    @EnvironmentObject private var store: AppStore
    @State private var startDate = Date()
    @State private var days = 7

    var body: some View {
        List {
            Section("Window") {
                DatePicker("Start", selection: $startDate, displayedComponents: .date)
                Stepper("Days: \(days)", value: $days, in: 1...30)
            }

            Section("Shopping List") {
                ForEach(store.shoppingList(startDate: startDate, days: days)) { item in
                    HStack {
                        Text(item.foodName)
                        Spacer()
                        Text("\(Fmt.num(item.quantity, digits: 2)) \(item.unit.label)")
                            .foregroundStyle(.secondary)
                    }
                }

                if store.shoppingList(startDate: startDate, days: days).isEmpty {
                    Text("No planned meals in range")
                        .foregroundStyle(.secondary)
                }
            }
        }
        .navigationTitle("Shopping")
    }
}
