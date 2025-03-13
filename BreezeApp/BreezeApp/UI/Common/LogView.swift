//
//  LogView.swift
//  BreezeApp
//
//  Created for BreezeApp
//

import SwiftUI

struct LogView: View {
    @EnvironmentObject var logManager: LogManager
    @Environment(\.dismiss) var dismiss
    
    var body: some View {
        NavigationView {
            List {
                ForEach(logManager.logs) { log in
                    VStack(alignment: .leading) {
                        Text(formatTimestamp(log.timestamp))
                            .font(.caption)
                            .foregroundColor(.secondary)
                        
                        Text(log.message)
                            .font(.body)
                            .foregroundColor(colorForLogLevel(log.level))
                        
                        Text("\(log.file):\(log.line) - \(log.function)")
                            .font(.caption2)
                            .foregroundColor(.secondary)
                    }
                    .padding(.vertical, 4)
                }
                .onDelete { indexSet in
                    var logsToDelete = [LogEntry]()
                    for index in indexSet {
                        logsToDelete.append(logManager.logs[index])
                    }
                    for log in logsToDelete {
                        if let index = logManager.logs.firstIndex(where: { $0.id == log.id }) {
                            logManager.logs.remove(at: index)
                        }
                    }
                }
            }
            .navigationTitle("Logs")
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Close") {
                        dismiss()
                    }
                }
                
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Clear") {
                        logManager.clearLogs()
                    }
                }
            }
        }
    }
    
    private func formatTimestamp(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd HH:mm:ss.SSS"
        return formatter.string(from: date)
    }
    
    private func colorForLogLevel(_ level: LogLevel) -> Color {
        switch level {
        case .debug:
            return .blue
        case .info:
            return .primary
        case .warning:
            return .orange
        case .error:
            return .red
        case .fatal:
            return .purple
        }
    }
}

#Preview {
    LogView()
        .environmentObject(LogManager())
} 