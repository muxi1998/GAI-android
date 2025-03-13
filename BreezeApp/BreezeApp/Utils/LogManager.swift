//
//  LogManager.swift
//  BreezeApp
//
//  Created for BreezeApp
//

import Foundation
import SwiftUI

enum LogLevel: Int, Codable {
    case debug = 0
    case info = 1
    case warning = 2
    case error = 3
    case fatal = 4
}

struct LogEntry: Identifiable, Codable {
    let id: UUID
    let level: LogLevel
    let timestamp: Date
    let message: String
    let file: String
    let function: String
    let line: Int
    
    init(level: LogLevel, message: String, file: String = #file, function: String = #function, line: Int = #line) {
        self.id = UUID()
        self.level = level
        self.timestamp = Date()
        self.message = message
        self.file = URL(fileURLWithPath: file).lastPathComponent
        self.function = function
        self.line = line
    }
}

class LogManager: ObservableObject {
    @AppStorage("logs") private var data = Data()
    
    @Published var logs: [LogEntry] = [] {
        didSet {
            saveLogs()
        }
    }
    
    init() {
        loadLogs()
    }
    
    private func saveLogs() {
        do {
            let encoder = JSONEncoder()
            data = try encoder.encode(logs)
        } catch {
            print("Failed to save logs: \(error.localizedDescription)")
        }
    }
    
    private func loadLogs() {
        do {
            let decoder = JSONDecoder()
            logs = try decoder.decode([LogEntry].self, from: data)
        } catch {
            logs = []
            print("Failed to load logs: \(error.localizedDescription)")
        }
    }
    
    func log(level: LogLevel, message: String, file: String = #file, function: String = #function, line: Int = #line) {
        let entry = LogEntry(level: level, message: message, file: file, function: function, line: line)
        DispatchQueue.main.async {
            self.logs.append(entry)
            
            // Print to console as well
            print("[\(self.formatLogLevel(level))] \(entry.file):\(entry.line) - \(message)")
        }
    }
    
    func debug(_ message: String, file: String = #file, function: String = #function, line: Int = #line) {
        log(level: .debug, message: message, file: file, function: function, line: line)
    }
    
    func info(_ message: String, file: String = #file, function: String = #function, line: Int = #line) {
        log(level: .info, message: message, file: file, function: function, line: line)
    }
    
    func warning(_ message: String, file: String = #file, function: String = #function, line: Int = #line) {
        log(level: .warning, message: message, file: file, function: function, line: line)
    }
    
    func error(_ message: String, file: String = #file, function: String = #function, line: Int = #line) {
        log(level: .error, message: message, file: file, function: function, line: line)
    }
    
    func fatal(_ message: String, file: String = #file, function: String = #function, line: Int = #line) {
        log(level: .fatal, message: message, file: file, function: function, line: line)
    }
    
    func clearLogs() {
        logs.removeAll()
    }
    
    private func formatLogLevel(_ level: LogLevel) -> String {
        switch level {
        case .debug: return "DEBUG"
        case .info: return "INFO"
        case .warning: return "WARNING"
        case .error: return "ERROR"
        case .fatal: return "FATAL"
        }
    }
} 