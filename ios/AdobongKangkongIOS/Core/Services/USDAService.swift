import Foundation

struct USDAFoodCandidate: Identifiable, Equatable {
    var id: Int
    var description: String
    var servingSize: Double?
    var servingSizeUnit: String?
    var barcodes: [String]
    var nutrients: [NutrientKey: Double]
}

enum USDAServiceError: Error {
    case missingApiKey
    case invalidResponse
}

final class USDAService {
    private let session: URLSession

    init(session: URLSession = .shared) {
        self.session = session
    }

    func searchFoods(query: String, apiKey: String) async throws -> [USDAFoodCandidate] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        let key = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)

        guard !trimmed.isEmpty else { return [] }
        guard !key.isEmpty else { throw USDAServiceError.missingApiKey }

        var components = URLComponents(string: "https://api.nal.usda.gov/fdc/v1/foods/search")
        components?.queryItems = [
            URLQueryItem(name: "query", value: trimmed),
            URLQueryItem(name: "pageSize", value: "25"),
            URLQueryItem(name: "api_key", value: key)
        ]

        guard let url = components?.url else { throw USDAServiceError.invalidResponse }

        let (data, response) = try await session.data(from: url)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            throw USDAServiceError.invalidResponse
        }

        let decoded = try JSONDecoder().decode(USDASearchResponse.self, from: data)

        return decoded.foods.map { food in
            USDAFoodCandidate(
                id: food.fdcId,
                description: food.description,
                servingSize: food.servingSize,
                servingSizeUnit: food.servingSizeUnit,
                barcodes: [food.gtinUpc].compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty },
                nutrients: Self.extractNutrients(from: food.foodNutrients)
            )
        }
    }

    private static func extractNutrients(from rows: [USDASearchNutrient]) -> [NutrientKey: Double] {
        var result: [NutrientKey: Double] = [:]

        for row in rows {
            let name = row.nutrientName.lowercased()
            if name.contains("energy") || name.contains("calories") {
                result[.kcal] = row.value
            } else if name.contains("protein") {
                result[.protein] = row.value
            } else if name.contains("carbohydrate") || name.contains("carb") {
                result[.carbs] = row.value
            } else if name == "total lipid (fat)" || name.contains("fat") {
                result[.fat] = row.value
            }
        }

        return result
    }
}

private struct USDASearchResponse: Decodable {
    var foods: [USDASearchFood]
}

private struct USDASearchFood: Decodable {
    var fdcId: Int
    var description: String
    var gtinUpc: String?
    var servingSize: Double?
    var servingSizeUnit: String?
    var foodNutrients: [USDASearchNutrient]
}

private struct USDASearchNutrient: Decodable {
    var nutrientName: String
    var value: Double
}
