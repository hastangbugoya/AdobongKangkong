import SwiftUI
import UniformTypeIdentifiers

struct SettingsView: View {
    @EnvironmentObject private var store: AppStore

    @State private var isExporting = false
    @State private var isImporting = false
    @State private var exportDocument = BackupDocument(jsonData: Data())
    @State private var importError: String?

    var body: some View {
        List {
            Section("Backup") {
                Button("Export Backup") {
                    do {
                        exportDocument = BackupDocument(jsonData: try store.exportBackupData())
                        isExporting = true
                    } catch {
                        importError = "Export failed: \(error.localizedDescription)"
                    }
                }

                Button("Import Backup") {
                    isImporting = true
                }
            }

            Section("Data") {
                Button("Reset To Seed Data", role: .destructive) {
                    store.resetToSeedData()
                }
            }

            if let importError {
                Section("Status") {
                    Text(importError)
                        .foregroundStyle(.red)
                }
            }

            Section("About") {
                Text("AdobongKangkong iOS clone in SwiftUI")
                Text("Offline-first local JSON persistence")
                    .foregroundStyle(.secondary)
                Text("Canonical nutrient basis with explicit bridge fields")
                    .foregroundStyle(.secondary)
                Text("Schema version: \(AppStore.currentSchemaVersion)")
                    .foregroundStyle(.secondary)
            }
        }
        .navigationTitle("Settings")
        .fileExporter(
            isPresented: $isExporting,
            document: exportDocument,
            contentType: .json,
            defaultFilename: "adobongkangkong-ios-backup"
        ) { _ in }
        .fileImporter(
            isPresented: $isImporting,
            allowedContentTypes: [.json]
        ) { result in
            switch result {
            case .success(let url):
                do {
                    let data = try Data(contentsOf: url)
                    try store.importBackupData(data)
                    importError = nil
                } catch {
                    importError = "Import failed: \(error.localizedDescription)"
                }
            case .failure(let error):
                importError = "Import canceled/failed: \(error.localizedDescription)"
            }
        }
    }
}

struct BackupDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.json] }

    var jsonData: Data

    init(jsonData: Data) {
        self.jsonData = jsonData
    }

    init(configuration: ReadConfiguration) throws {
        self.jsonData = configuration.file.regularFileContents ?? Data()
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: jsonData)
    }
}
