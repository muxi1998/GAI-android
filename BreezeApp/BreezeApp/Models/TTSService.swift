//
//  TTSService.swift
//  BreezeApp
//
//  Created for BreezeApp
//

import Foundation
import SwiftUI
import AVFoundation

class TTSService: ObservableObject {
    @Published var isSpeaking = false
    private let synthesizer = AVSpeechSynthesizer()
    
    init() {
        // Initialize TTS service
        print("TTS Service initialized")
    }
    
    func speak(text: String) {
        // Basic implementation using AVSpeechSynthesizer
        if !text.isEmpty {
            isSpeaking = true
            
            let utterance = AVSpeechUtterance(string: text)
            utterance.rate = 0.5
            utterance.pitchMultiplier = 1.0
            utterance.volume = 1.0
            
            synthesizer.speak(utterance)
            
            // In a real implementation, you'd use the delegate to track when speech is done
            DispatchQueue.main.asyncAfter(deadline: .now() + Double(text.count) * 0.05) {
                self.isSpeaking = false
            }
        }
    }
    
    func stop() {
        synthesizer.stopSpeaking(at: .immediate)
        isSpeaking = false
    }
} 