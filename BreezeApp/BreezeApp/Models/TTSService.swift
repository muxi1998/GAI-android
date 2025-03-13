//
//  TTSService.swift
//  BreezeApp
//
//  Created for BreezeApp
//

import Foundation
import SwiftUI
import AVFoundation

class TTSService: NSObject, ObservableObject, AVSpeechSynthesizerDelegate {
    @Published var isSpeaking = false
    private let synthesizer = AVSpeechSynthesizer()
    
    override init() {
        super.init()
        // Initialize TTS service
        print("TTS Service initialized")
        synthesizer.delegate = self
        setupAudioSession()
    }
    
    private func setupAudioSession() {
        do {
            let audioSession = AVAudioSession.sharedInstance()
            // Use mixWithOthers to allow other audio to play while TTS is active
            try audioSession.setCategory(.playback, mode: .spokenAudio, options: [.duckOthers, .mixWithOthers])
            
            // Don't activate by default, only when needed
            // This helps prevent conflicts with other audio services
        } catch {
            print("Failed to set up audio session: \(error.localizedDescription)")
        }
    }
    
    func speak(text: String) {
        // Basic implementation using AVSpeechSynthesizer
        if !text.isEmpty {
            // Ensure audio session is active
            do {
                try AVAudioSession.sharedInstance().setActive(true, options: .notifyOthersOnDeactivation)
            } catch {
                print("Could not activate audio session: \(error)")
            }
            
            isSpeaking = true
            
            let utterance = AVSpeechUtterance(string: text)
            utterance.rate = 0.5
            utterance.pitchMultiplier = 1.0
            utterance.volume = 1.0
            
            // Use a high-quality voice if available
            if let voice = AVSpeechSynthesisVoice(language: "en-US") {
                utterance.voice = voice
            }
            
            synthesizer.speak(utterance)
        }
    }
    
    func stop() {
        synthesizer.stopSpeaking(at: .immediate)
        isSpeaking = false
        
        // Deactivate audio session when stopped manually
        deactivateAudioSession()
    }
    
    private func deactivateAudioSession() {
        do {
            try AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        } catch {
            print("Could not deactivate audio session: \(error)")
        }
    }
    
    // MARK: - AVSpeechSynthesizerDelegate
    
    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        DispatchQueue.main.async {
            self.isSpeaking = false
            self.deactivateAudioSession()
        }
    }
    
    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) {
        DispatchQueue.main.async {
            self.isSpeaking = false
            self.deactivateAudioSession()
        }
    }
} 