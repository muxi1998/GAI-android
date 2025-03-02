# BreezeApp iOS

BreezeApp is an iOS application that demonstrates the integration of various AI capabilities using ExecuTorch for on-device inference:

- **LLM (Large Language Model)**: Text generation using LLaMA models
- **VLM (Vision Language Model)**: Image understanding using LLaVA models
- **ASR (Automatic Speech Recognition)**: Converting speech to text using Apple's Speech framework
- **TTS (Text-to-Speech)**: Converting text to speech using AVSpeechSynthesizer

## Features

- **Chat Interface**: Interact with AI models through a familiar chat interface
- **Image Analysis**: Upload images for the VLM to analyze
- **Voice Input**: Use speech recognition to dictate messages
- **Voice Output**: Have the AI's responses read aloud
- **Model Management**: Download and manage AI models from the settings
- **Debugging Tools**: View logs and debug information

## Architecture

The app is built with a clean architecture that separates UI, business logic, and model integration:

### UI Layer (SwiftUI)
- **ChatView**: Main interface for interacting with AI models
- **MessageView/MessageListView**: Components for displaying chat messages
- **SettingsView**: Interface for configuring the app
- **ImagePicker**: Component for selecting images

### Service Layer
- **LLMService**: Manages text generation using LLaMA models
- **VLMService**: Manages image analysis using LLaVA models
- **ASRService**: Manages speech recognition using Apple's Speech framework
- **TTSService**: Manages text-to-speech using AVSpeechSynthesizer

### Integration Layer
- **ExecuTorchWrapper**: Swift wrapper for ExecuTorch
- **LLMRunnerObjC/VLMRunnerObjC**: Objective-C++ bridges to ExecuTorch C++ code
- **ResourceManager**: Manages model files and resources

## ExecuTorch Integration

This app uses ExecuTorch as a Git submodule to ensure proper syncing with the upstream repository. ExecuTorch provides the on-device inference capabilities for running LLaMA and LLaVA models efficiently on iOS devices.

To update ExecuTorch to the latest version:

```bash
cd Dependencies/executorch
git pull origin main
cd ../..
git add Dependencies/executorch
git commit -m "Update ExecuTorch to latest version"
```


## Getting Started

### Prerequisites

- Xcode 14.0+
- iOS 15.0+
- CMake 3.18+ (for building ExecuTorch)
- Python 3.8+ (for ExecuTorch scripts)

### Setup Instructions

1. Clone the repository:
   ```bash:BreezeApp-iOS/README.md
   git clone https://github.com/yourusername/GAIandroid.git
   cd GAIandroid
   ```

2. Initialize the submodules:
   ```bash
   git submodule update --init --recursive
   ```BreezeApp-iOS/README.md

3. Build ExecuTorch:
   ```bash
   cd BreezeApp-iOS
   chmod +x build_executorch.sh
   ./build_executorch.sh
   ```

4. Open the Xcode project:
   ```bash
   open BreezeApp.xcodeproj
   ```

5. Build and run the app in Xcode

### Model Files

The app requires model files to function. You can download them from the Settings screen in the app, or manually place them in the app's documents directory:

- LLaMA model: `llama3_8b.pte`
- Tokenizer: `tokenizer.bin`
- LLaVA model: `llava_1_5.pte`

To find your app's documents directory:
1. Run the app in debug mode
2. Check the console logs for "Model path:" entries
3. Copy the files to the displayed paths


## Implementation Details

### ExecuTorch Integration

The app integrates with ExecuTorch through an Objective-C++ bridge:

1. **Swift Services** (LLMService, VLMService) call into...
2. **Objective-C++ Wrappers** (LLMRunnerObjC, VLMRunnerObjC) which call into...
3. **ExecuTorch C++ Runtime** for model inference

This layered approach allows for clean Swift code while still leveraging the C++ ExecuTorch runtime.

### Speech Services

The app uses Apple's native frameworks for speech:

- **Speech Recognition**: Uses the Speech framework (SFSpeechRecognizer)
- **Text-to-Speech**: Uses AVFoundation (AVSpeechSynthesizer)

## Troubleshooting

### Common Issues

1. **Models Not Loading**
   - Check that model files exist in the correct location
   - Verify file permissions
   - Check logs for specific error messages

2. **Build Errors**
   - Ensure ExecuTorch is properly built using the provided script
   - Check that the bridging header is correctly configured
   - Verify that all dependencies are installed

3. **Runtime Crashes**
   - Check for memory issues when loading large models
   - Ensure device compatibility (some models require newer devices)
   - Review logs for error messages

### Debugging

The app includes a built-in logging system accessible from the Settings tab. Use this to view detailed logs about model loading, inference, and other operations.

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

- [ExecuTorch](https://github.com/pytorch/executorch) - On-device inference runtime
- [LLaMA](https://ai.meta.com/llama/) - Large Language Model from Meta AI
- [LLaVA](https://llava-vl.github.io/) - Large Language and Vision Assistant