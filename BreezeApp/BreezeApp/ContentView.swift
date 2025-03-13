//
//  ContentView.swift
//  BreezeApp
//
//  Created by 陳沐希 on 2025/3/2.
//

import SwiftUI

struct ContentView: View {
    @EnvironmentObject var llmService: LLMService
    @EnvironmentObject var vlmService: VLMService
    @EnvironmentObject var asrService: ASRService
    @EnvironmentObject var ttsService: TTSService
    @State private var userInput = ""
    @State private var messages: [Message] = []
    
    var body: some View {
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
    }
    
    private func sendMessage() {
        let userMessage = Message(content: userInput, isUser: true)
        messages.append(userMessage)
        
        let query = userInput
        userInput = ""
        
        llmService.generateText(prompt: query) { response in
            let aiMessage = Message(content: response, isUser: false)
            messages.append(aiMessage)
            
            if !ttsService.isSpeaking {
                ttsService.speak(text: response)
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
    }
}

#Preview {
    ContentView()
        .environmentObject(LLMService())
        .environmentObject(VLMService())
        .environmentObject(ASRService())
        .environmentObject(TTSService())
}
