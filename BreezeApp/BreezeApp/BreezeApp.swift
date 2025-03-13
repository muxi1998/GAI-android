//
//  BreezeApp.swift
//  BreezeApp
//
//  Created for BreezeApp
//

import SwiftUI

// Make the model files available to import
import Foundation
import AVFoundation

// Declare this as the entry point for the app
@main
struct BreezeApp: App {
    // Create service instances
    @StateObject private var llmService = LLMService()
    @StateObject private var vlmService = VLMService()
    @StateObject private var asrService = ASRService()
    @StateObject private var ttsService = TTSService()
    
    // Create utility instances
    @StateObject private var logManager = LogManager()
    @StateObject private var resourceManager = ResourceManager()
    
    init() {
        // Set up global configurations
        setupGlobalAppearance()
        
        // Log app launch
        print("BreezeApp launched")
    }
    
    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(llmService)
                .environmentObject(vlmService)
                .environmentObject(asrService)
                .environmentObject(ttsService)
                .environmentObject(logManager)
                .environmentObject(resourceManager)
                .onAppear {
                    setupResources()
                }
        }
    }
    
    private func setupGlobalAppearance() {
        // Configure global appearance settings
        UINavigationBar.appearance().tintColor = .systemBlue
    }
    
    private func setupResources() {
        do {
            try resourceManager.createDirectoriesIfNeeded()
            logManager.info("Resource directories created successfully")
        } catch {
            logManager.error("Failed to create resource directories: \(error.localizedDescription)")
        }
    }
} 