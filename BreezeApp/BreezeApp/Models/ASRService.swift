//
//  ASRService.swift
//  BreezeApp
//
//  Created for BreezeApp
//

import Foundation
import SwiftUI
import AVFoundation
import Speech

class ASRService: NSObject, ObservableObject, SFSpeechRecognizerDelegate {
    @Published var isRecording = false
    @Published var transcribedText = ""
    @Published var isAuthorized = false
    
    private let speechRecognizer = SFSpeechRecognizer(locale: Locale(identifier: "en-US"))
    private var recognitionRequest: SFSpeechAudioBufferRecognitionRequest?
    private var recognitionTask: SFSpeechRecognitionTask?
    private let audioEngine = AVAudioEngine()
    
    override init() {
        super.init()
        // Initialize ASR service
        print("ASR Service initialized")
        speechRecognizer?.delegate = self
        checkPermissions()
    }
    
    private func checkPermissions() {
        SFSpeechRecognizer.requestAuthorization { [weak self] authStatus in
            DispatchQueue.main.async {
                switch authStatus {
                case .authorized:
                    self?.isAuthorized = true
                    print("Speech recognition authorized")
                case .denied:
                    self?.isAuthorized = false
                    print("Speech recognition authorization denied")
                case .restricted:
                    self?.isAuthorized = false
                    print("Speech recognition restricted on this device")
                case .notDetermined:
                    self?.isAuthorized = false
                    print("Speech recognition not yet authorized")
                @unknown default:
                    self?.isAuthorized = false
                    print("Unknown authorization status")
                }
            }
        }
    }
    
    func startRecording() {
        // Check if we're already recording or not authorized
        guard !isRecording, isAuthorized else {
            if !isAuthorized {
                print("Speech recognition not authorized")
            }
            return
        }
        
        // Reset previous recognition task if any
        if recognitionTask != nil {
            recognitionTask?.cancel()
            recognitionTask = nil
        }
        
        // Configure audio session for recording
        do {
            let audioSession = AVAudioSession.sharedInstance()
            try audioSession.setCategory(.record, mode: .measurement, options: .duckOthers)
            try audioSession.setActive(true, options: .notifyOthersOnDeactivation)
        } catch {
            print("Failed to set up audio session: \(error.localizedDescription)")
            return
        }
        
        // Create and configure the speech recognition request
        recognitionRequest = SFSpeechAudioBufferRecognitionRequest()
        
        guard let recognitionRequest = recognitionRequest else {
            print("Unable to create speech recognition request")
            return
        }
        
        recognitionRequest.shouldReportPartialResults = true
        
        // Configure the audio engine and input node
        let inputNode = audioEngine.inputNode
        
        // Ensure we get a valid recording format with proper sample rate and channel count
        guard let recordingFormat = inputNode.inputFormat(forBus: 0) else {
            print("Unable to get recording format")
            return
        }
        
        // Check if the format is valid
        if recordingFormat.sampleRate == 0 || recordingFormat.channelCount == 0 {
            print("Invalid recording format: sampleRate=\(recordingFormat.sampleRate), channelCount=\(recordingFormat.channelCount)")
            return
        }
        
        print("Recording format: \(recordingFormat.sampleRate) Hz, \(recordingFormat.channelCount) channels")
        
        // Start recording and append audio buffer to recognition request
        inputNode.installTap(onBus: 0, bufferSize: 1024, format: recordingFormat) { [weak self] buffer, _ in
            self?.recognitionRequest?.append(buffer)
        }
        
        audioEngine.prepare()
        
        do {
            try audioEngine.start()
            isRecording = true
            print("Started recording...")
        } catch {
            print("Could not start audio engine: \(error.localizedDescription)")
            return
        }
        
        // Start the recognition task
        recognitionTask = speechRecognizer?.recognitionTask(with: recognitionRequest) { [weak self] result, error in
            guard let self = self else { return }
            
            if let result = result {
                DispatchQueue.main.async {
                    self.transcribedText = result.bestTranscription.formattedString
                }
            }
            
            if error != nil || (result?.isFinal ?? false) {
                self.stopRecording { _ in }
            }
        }
    }
    
    func stopRecording(completion: @escaping (String) -> Void) {
        // Stop the audio engine and end the recognition task
        audioEngine.stop()
        
        if audioEngine.inputNode.numberOfInputs > 0 {
            audioEngine.inputNode.removeTap(onBus: 0)
        }
        
        recognitionRequest?.endAudio()
        recognitionTask?.cancel()
        
        recognitionRequest = nil
        recognitionTask = nil
        
        // Deactivate audio session
        do {
            try AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        } catch {
            print("Failed to deactivate audio session: \(error.localizedDescription)")
        }
        
        isRecording = false
        completion(transcribedText)
    }
    
    // MARK: - SFSpeechRecognizerDelegate
    
    func speechRecognizer(_ speechRecognizer: SFSpeechRecognizer, availabilityDidChange available: Bool) {
        if !available {
            isRecording = false
            print("Speech recognition is not available")
        }
    }
} 