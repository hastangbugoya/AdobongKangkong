import Foundation

extension Calendar {
    static let iso8601Local: Calendar = {
        var calendar = Calendar(identifier: .iso8601)
        calendar.timeZone = .current
        return calendar
    }()
}
