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
    
    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(llmService)
                .environmentObject(vlmService)
                .environmentObject(asrService)
                .environmentObject(ttsService)
        }
    }
} 