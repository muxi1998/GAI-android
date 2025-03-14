//
//  LLMService.swift
//  BreezeApp
//
//  Created for BreezeApp
//

import Foundation
import SwiftUI
import Combine

// Define the Runner and LLaVARunner classes to match the Objective-C interface
// These are stub implementations that will be replaced with the actual LLaMARunner implementation
class Runner: NSObject {
    init(modelPath: String, tokenizerPath: String) {
        super.init()
        print("Runner initialized with model: \(modelPath), tokenizer: \(tokenizerPath)")
    }
    
    func isLoaded() -> Bool {
        return false
    }
    
    func load() throws {
        print("Loading model...")
    }
    
    func generate(_ prompt: String, sequenceLength: Int, withTokenCallback callback: ((String) -> Void)?) throws {
        print("Generating text for prompt: \(prompt)")
        callback?("This is a stub implementation. The actual LLaMARunner module needs to be properly integrated.")
    }
    
    func stop() {
        print("Stopping generation")
    }
}

class LLaVARunner: NSObject {
    init(modelPath: String, tokenizerPath: String) {
        super.init()
        print("LLaVARunner initialized with model: \(modelPath), tokenizer: \(tokenizerPath)")
    }
    
    func isLoaded() -> Bool {
        return false
    }
    
    func load() throws {
        print("Loading multimodal model...")
    }
    
    func generate(_ imageBuffer: UnsafeMutableRawPointer, width: CGFloat, height: CGFloat, prompt: String, sequenceLength: Int, withTokenCallback callback: ((String) -> Void)?) throws {
        print("Generating text for image and prompt: \(prompt)")
        callback?("This is a stub implementation. The actual LLaMARunner module needs to be properly integrated.")
    }
    
    func stop() {
        print("Stopping generation")
    }
}

class LLMService: ObservableObject {
    // Published properties for UI updates
    @Published var isGenerating = false
    @Published var generatedText = ""
    @Published var isModelLoaded = false
    @Published var modelLoadingError: String? = nil
    @Published var memoryUsage: Int = 0
    
    // Model paths
    private var modelPath: String = ""
    private var tokenizerPath: String = ""
    
    // Runners for text and multimodal models
    private var textRunner: Runner?
    private var multimodalRunner: LLaVARunner?
    
    // Queue for running inference
    private let runnerQueue = DispatchQueue(label: "com.breezeapp.llm.queue", qos: .userInitiated)
    
    // Cancellation flag
    private var shouldStopGenerating = false
    
    init() {
        // Initialize with default paths if available
        checkForDefaultModels()
    }
    
    // MARK: - Public Methods
    
    /// Set the model and tokenizer paths
    func setModelPaths(modelPath: String, tokenizerPath: String) {
        self.modelPath = modelPath
        self.tokenizerPath = tokenizerPath
        
        // Reset runners when paths change
        self.textRunner = nil
        self.multimodalRunner = nil
        self.isModelLoaded = false
    }
    
    /// Check if the model is ready for inference
    var isReady: Bool {
        return !modelPath.isEmpty && !tokenizerPath.isEmpty
    }
    
    /// Generate text from a prompt
    func generateText(prompt: String, maxLength: Int = 768, completion: @escaping (Result<String, Error>) -> Void) {
        guard isReady else {
            completion(.failure(LLMError.modelNotSet))
            return
        }
        
        isGenerating = true
        shouldStopGenerating = false
        generatedText = ""
        
        let isLlamaModel = modelPath.lowercased().contains("llama")
        
        runnerQueue.async { [weak self] in
            guard let self = self else { return }
            
            defer {
                DispatchQueue.main.async {
                    self.isGenerating = false
                }
            }
            
            do {
                // Load the appropriate model if needed
                if isLlamaModel {
                    try self.loadTextModel()
                } else {
                    try self.loadMultimodalModel()
                }
                
                // Format the prompt based on model type
                let formattedPrompt = isLlamaModel ? 
                    "<|begin_of_text|><|start_header_id|>user<|end_header_id|>\(prompt)<|eot_id|><|start_header_id|>assistant<|end_header_id|>" :
                    "\(prompt) ASSISTANT"
                
                var tokens: [String] = []
                
                // Generate text using the appropriate runner
                if isLlamaModel {
                    try self.textRunner?.generate(formattedPrompt, sequenceLength: maxLength) { token in
                        if token != formattedPrompt {
                            if token == "<|eot_id|>" {
                                self.shouldStopGenerating = true
                                self.textRunner?.stop()
                            } else {
                                tokens.append(token.trimmingCharacters(in: .newlines))
                                if tokens.count > 2 {
                                    let text = tokens.joined()
                                    tokens = []
                                    DispatchQueue.main.async {
                                        self.generatedText += text
                                    }
                                }
                                
                                if self.shouldStopGenerating {
                                    self.textRunner?.stop()
                                }
                            }
                        }
                    }
                }
                
                DispatchQueue.main.async {
                    completion(.success(self.generatedText))
                }
            } catch {
                DispatchQueue.main.async {
                    self.modelLoadingError = error.localizedDescription
                    completion(.failure(error))
                }
            }
        }
    }
    
    /// Generate text from a prompt with an image
    func generateTextWithImage(prompt: String, imageData: Data, width: CGFloat, height: CGFloat, maxLength: Int = 768, completion: @escaping (Result<String, Error>) -> Void) {
        guard isReady else {
            completion(.failure(LLMError.modelNotSet))
            return
        }
        
        isGenerating = true
        shouldStopGenerating = false
        generatedText = ""
        
        runnerQueue.async { [weak self] in
            guard let self = self else { return }
            
            defer {
                DispatchQueue.main.async {
                    self.isGenerating = false
                }
            }
            
            do {
                // Load the multimodal model
                try self.loadMultimodalModel()
                
                // Process the image data
                let imagePointer = (imageData as NSData).bytes
                let imageBuffer = UnsafeMutableRawPointer(mutating: imagePointer)
                
                let formattedPrompt = "\(prompt) ASSISTANT"
                var tokens: [String] = []
                
                // Generate text with image
                try self.multimodalRunner?.generate(imageBuffer, width: width, height: height, prompt: formattedPrompt, sequenceLength: maxLength) { token in
                    if token != formattedPrompt {
                        if token == "</s>" {
                            self.shouldStopGenerating = true
                            self.multimodalRunner?.stop()
                        } else {
                            tokens.append(token)
                            if tokens.count > 2 {
                                let text = tokens.joined()
                                tokens = []
                                DispatchQueue.main.async {
                                    self.generatedText += text
                                }
                            }
                            
                            if self.shouldStopGenerating {
                                self.multimodalRunner?.stop()
                            }
                        }
                    }
                }
                
                DispatchQueue.main.async {
                    completion(.success(self.generatedText))
                }
            } catch {
                DispatchQueue.main.async {
                    self.modelLoadingError = error.localizedDescription
                    completion(.failure(error))
                }
            }
        }
    }
    
    /// Stop the current generation
    func stopGeneration() {
        shouldStopGenerating = true
    }
    
    // MARK: - Private Methods
    
    private func loadTextModel() throws {
        if textRunner == nil {
            textRunner = Runner(modelPath: modelPath, tokenizerPath: tokenizerPath)
        }
        
        if let runner = textRunner, !runner.isLoaded() {
            let startLoadTime = Date()
            try runner.load()
            let loadTime = Date().timeIntervalSince(startLoadTime)
            
            DispatchQueue.main.async { [weak self] in
                self?.isModelLoaded = true
                self?.modelLoadingError = nil
                print("Text model loaded in \(String(format: "%.2f", loadTime)) s")
            }
        }
    }
    
    private func loadMultimodalModel() throws {
        if multimodalRunner == nil {
            multimodalRunner = LLaVARunner(modelPath: modelPath, tokenizerPath: tokenizerPath)
        }
        
        if let runner = multimodalRunner, !runner.isLoaded() {
            let startLoadTime = Date()
            try runner.load()
            let loadTime = Date().timeIntervalSince(startLoadTime)
            
            DispatchQueue.main.async { [weak self] in
                self?.isModelLoaded = true
                self?.modelLoadingError = nil
                print("Multimodal model loaded in \(String(format: "%.2f", loadTime)) s")
            }
        }
    }
    
    private func checkForDefaultModels() {
        // Check for default models in the app bundle or documents directory
        // This is app-specific and would need to be implemented based on your app's structure
    }
}

// MARK: - Error Types

enum LLMError: Error {
    case modelNotSet
    case modelLoadFailed
    case generationFailed
    case imageProcessingFailed
} 