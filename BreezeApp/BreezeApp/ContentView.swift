//
//  ContentView.swift
//  BreezeApp
//
//  Created for BreezeApp
//

import SwiftUI

struct ContentView: View {
    @EnvironmentObject var llmService: LLMService
    @EnvironmentObject var vlmService: VLMService
    @EnvironmentObject var asrService: ASRService
    @EnvironmentObject var ttsService: TTSService
    @EnvironmentObject var logManager: LogManager
    @EnvironmentObject var resourceManager: ResourceManager
    
    @State private var userInput = ""
    @State private var messages: [Message] = []
    @State private var showingLogs = false
    @State private var showingSettings = false
    @State private var isImagePickerPresented = false
    @State private var selectedImage: UIImage?
    
    var body: some View {
        NavigationView {
            VStack {
                ScrollView {
                    VStack(alignment: .leading, spacing: 12) {
                        ForEach(messages) { message in
                            MessageView(message: message)
                        }
                    }
                    .padding()
                }
                
                HStack {
                    Button(action: {
                        if asrService.isRecording {
                            asrService.stopRecording { transcribedText in
                                userInput = transcribedText
                            }
                        } else {
                            asrService.startRecording()
                        }
                    }) {
                        Image(systemName: asrService.isRecording ? "stop.circle.fill" : "mic.circle")
                            .font(.system(size: 24))
                            .foregroundColor(asrService.isRecording ? .red : .blue)
                    }
                    .disabled(!asrService.isAuthorized)
                    
                    TextField("Type a message...", text: $userInput)
                        .textFieldStyle(RoundedBorderTextFieldStyle())
                        .disabled(asrService.isRecording)
                    
                    Button(action: sendMessage) {
                        Image(systemName: "arrow.up.circle.fill")
                            .font(.system(size: 24))
                            .foregroundColor(.blue)
                    }
                    .disabled(userInput.isEmpty || llmService.isLoading)
                }
                .padding()
            }
            .navigationTitle("BreezeApp")
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(action: {
                        showingSettings.toggle()
                    }) {
                        Image(systemName: "gear")
                    }
                }
                
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: {
                        showingLogs.toggle()
                    }) {
                        Image(systemName: "list.bullet.rectangle")
                    }
                }
            }
            .sheet(isPresented: $showingLogs) {
                LogView()
            }
            .sheet(isPresented: $showingSettings) {
                SettingsView()
            }
            .onAppear {
                logManager.info("ContentView appeared")
                // Check if speech recognition is authorized
                if !asrService.isAuthorized {
                    logManager.warning("Speech recognition not authorized")
                }
            }
        }
    }
    
    private func sendMessage() {
        guard !userInput.isEmpty else { return }
        
        let userMessage = Message(content: userInput, isUser: true)
        messages.append(userMessage)
        
        let query = userInput
        userInput = ""
        
        logManager.info("Sending message: \(query)")
        
        llmService.generateText(prompt: query) { result in
            switch result {
            case .success(let response):
                let aiMessage = Message(content: response, isUser: false)
                messages.append(aiMessage)
                
                if !ttsService.isSpeaking {
                    ttsService.speak(text: response)
                }
                
                logManager.info("Received response from LLM")
            case .failure(let error):
                let errorMessage = Message(content: "Error: \(error.localizedDescription)", isUser: false)
                messages.append(errorMessage)
                logManager.error("LLM error: \(error.localizedDescription)")
            }
        }
    }
}

struct MessageView: View {
    let message: Message
    
    var body: some View {
        HStack {
            if message.isUser {
                Spacer()
            }
            
            VStack(alignment: message.isUser ? .trailing : .leading) {
                if let image = message.image {
                    image
                        .resizable()
                        .scaledToFit()
                        .frame(maxWidth: 200, maxHeight: 200)
                        .cornerRadius(8)
                }
                
                Text(message.content)
                    .padding()
                    .background(message.isUser ? Color.blue : Color.gray.opacity(0.3))
                    .foregroundColor(message.isUser ? .white : .primary)
                    .cornerRadius(12)
            }
            
            if !message.isUser {
                Spacer()
            }
        }
        .contextMenu {
            Button(action: {
                UIPasteboard.general.string = message.content
            }) {
                Label("Copy", systemImage: "doc.on.doc")
            }
        }
    }
}

struct SettingsView: View {
    @EnvironmentObject var resourceManager: ResourceManager
    @Environment(\.dismiss) var dismiss
    
    var body: some View {
        NavigationView {
            List {
                Section(header: Text("Models")) {
                    NavigationLink(destination: ModelSelectionView(modelType: .llm)) {
                        HStack {
                            Text("LLM Model")
                            Spacer()
                            Text(resourceManager.isLLMModelValid ? resourceManager.llmModelName : "Not Selected")
                                .foregroundColor(.secondary)
                        }
                    }
                    
                    NavigationLink(destination: ModelSelectionView(modelType: .vlm)) {
                        HStack {
                            Text("VLM Model")
                            Spacer()
                            Text(resourceManager.isVLMModelValid ? resourceManager.vlmModelName : "Not Selected")
                                .foregroundColor(.secondary)
                        }
                    }
                }
                
                Section(header: Text("Tokenizer")) {
                    NavigationLink(destination: TokenizerSelectionView()) {
                        HStack {
                            Text("Tokenizer")
                            Spacer()
                            Text(resourceManager.isTokenizerValid ? resourceManager.tokenizerName : "Not Selected")
                                .foregroundColor(.secondary)
                        }
                    }
                }
            }
            .navigationTitle("Settings")
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Close") {
                        dismiss()
                    }
                }
            }
        }
    }
}

struct ModelSelectionView: View {
    enum ModelType {
        case llm
        case vlm
    }
    
    @EnvironmentObject var resourceManager: ResourceManager
    @Environment(\.dismiss) var dismiss
    let modelType: ModelType
    
    var body: some View {
        List {
            ForEach(resourceManager.listModels(), id: \.self) { modelURL in
                Button(action: {
                    switch modelType {
                    case .llm:
                        resourceManager.llmModelPath = modelURL.path
                    case .vlm:
                        resourceManager.vlmModelPath = modelURL.path
                    }
                    dismiss()
                }) {
                    Text(modelURL.lastPathComponent)
                }
            }
        }
        .navigationTitle(modelType == .llm ? "Select LLM Model" : "Select VLM Model")
    }
}

struct TokenizerSelectionView: View {
    @EnvironmentObject var resourceManager: ResourceManager
    @Environment(\.dismiss) var dismiss
    
    var body: some View {
        List {
            ForEach(resourceManager.listTokenizers(), id: \.self) { tokenizerURL in
                Button(action: {
                    resourceManager.tokenizerPath = tokenizerURL.path
                    dismiss()
                }) {
                    Text(tokenizerURL.lastPathComponent)
                }
            }
        }
        .navigationTitle("Select Tokenizer")
    }
}

#Preview {
    ContentView()
        .environmentObject(LLMService())
        .environmentObject(VLMService())
        .environmentObject(ASRService())
        .environmentObject(TTSService())
        .environmentObject(LogManager())
        .environmentObject(ResourceManager())
}
