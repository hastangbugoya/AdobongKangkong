import SwiftUI

struct UsdaSearchView: View {
    @EnvironmentObject private var store: AppStore

    @AppStorage("usda.apiKey") private var apiKey: String = ""
    @State private var query = ""
    @State private var results: [USDAFoodCandidate] = []
    @State private var isLoading = false
    @State private var errorText: String?

    private let service = USDAService()

    var body: some View {
        List {
            Section("USDA API") {
                SecureField("USDA API Key", text: $apiKey)
                    .textInputAutocapitalization(.never)
                Text("Key is stored locally on device via AppStorage.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Section("Search") {
                TextField("Search foods", text: $query)
                    .textInputAutocapitalization(.never)
                Button(isLoading ? "Searching..." : "Search USDA") {
                    Task { await runSearch() }
                }
                .disabled(isLoading)
            }

            if let errorText {
                Section("Error") {
                    Text(errorText)
                        .foregroundStyle(.red)
                }
            }

            Section("Results") {
                ForEach(results) { row in
                    VStack(alignment: .leading, spacing: 8) {
                        Text(row.description)
                            .font(.headline)

                        HStack {
                            Text("kcal: \(Fmt.num(row.nutrients[.kcal] ?? 0))")
                            Text("protein: \(Fmt.num(row.nutrients[.protein] ?? 0))")
                            Text("carbs: \(Fmt.num(row.nutrients[.carbs] ?? 0))")
                            Text("fat: \(Fmt.num(row.nutrients[.fat] ?? 0))")
                        }
                        .font(.caption)
                        .foregroundStyle(.secondary)

                        if let barcode = row.barcodes.first {
                            Text("Barcode: \(barcode)")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }

                        Button("Import As Food") {
                            store.importUSDAFood(row)
                        }
                        .buttonStyle(.borderedProminent)
                    }
                    .padding(.vertical, 6)
                }

                if !isLoading && results.isEmpty {
                    Text("No results yet")
                        .foregroundStyle(.secondary)
                }
            }
        }
        .navigationTitle("USDA Search")
    }

    private func runSearch() async {
        isLoading = true
        errorText = nil
        defer { isLoading = false }

        do {
            results = try await service.searchFoods(query: query, apiKey: apiKey)
        } catch USDAServiceError.missingApiKey {
            errorText = "Missing USDA API key"
        } catch {
            errorText = "Search failed: \(error.localizedDescription)"
        }
    }
}
