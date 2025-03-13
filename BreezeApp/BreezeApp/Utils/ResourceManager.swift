//
//  ResourceManager.swift
//  BreezeApp
//
//  Created for BreezeApp
//

import Foundation
import SwiftUI

final class ResourceManager: ObservableObject {
    @AppStorage("llmModelPath") var llmModelPath = ""
    @AppStorage("vlmModelPath") var vlmModelPath = ""
    @AppStorage("tokenizerPath") var tokenizerPath = ""
    
    private let fileManager = FileManager.default
    
    var isLLMModelValid: Bool {
        fileManager.fileExists(atPath: llmModelPath)
    }
    
    var isVLMModelValid: Bool {
        fileManager.fileExists(atPath: vlmModelPath)
    }
    
    var isTokenizerValid: Bool {
        fileManager.fileExists(atPath: tokenizerPath)
    }
    
    var llmModelName: String {
        URL(fileURLWithPath: llmModelPath).deletingPathExtension().lastPathComponent
    }
    
    var vlmModelName: String {
        URL(fileURLWithPath: vlmModelPath).deletingPathExtension().lastPathComponent
    }
    
    var tokenizerName: String {
        URL(fileURLWithPath: tokenizerPath).deletingPathExtension().lastPathComponent
    }
    
    func createDirectoriesIfNeeded() throws {
        guard let documentsDirectory = fileManager.urls(for: .documentDirectory, in: .userDomainMask).first else { return }
        try fileManager.createDirectory(at: documentsDirectory.appendingPathComponent("models"), withIntermediateDirectories: true, attributes: nil)
        try fileManager.createDirectory(at: documentsDirectory.appendingPathComponent("tokenizers"), withIntermediateDirectories: true, attributes: nil)
    }
    
    func getDocumentsDirectory() -> URL? {
        return fileManager.urls(for: .documentDirectory, in: .userDomainMask).first
    }
    
    func getModelsDirectory() -> URL? {
        guard let documentsDirectory = getDocumentsDirectory() else { return nil }
        return documentsDirectory.appendingPathComponent("models")
    }
    
    func getTokenizersDirectory() -> URL? {
        guard let documentsDirectory = getDocumentsDirectory() else { return nil }
        return documentsDirectory.appendingPathComponent("tokenizers")
    }
    
    func listModels() -> [URL] {
        guard let modelsDirectory = getModelsDirectory() else { return [] }
        do {
            let modelFiles = try fileManager.contentsOfDirectory(at: modelsDirectory, includingPropertiesForKeys: nil)
            return modelFiles
        } catch {
            print("Failed to list models: \(error.localizedDescription)")
            return []
        }
    }
    
    func listTokenizers() -> [URL] {
        guard let tokenizersDirectory = getTokenizersDirectory() else { return [] }
        do {
            let tokenizerFiles = try fileManager.contentsOfDirectory(at: tokenizersDirectory, includingPropertiesForKeys: nil)
            return tokenizerFiles
        } catch {
            print("Failed to list tokenizers: \(error.localizedDescription)")
            return []
        }
    }
} 