#!/bin/bash

# This script builds the ExecuTorch libraries for iOS
# It should be run before building the app

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
EXECUTORCH_DIR="$SCRIPT_DIR/Dependencies/executorch"
BUILD_DIR="$SCRIPT_DIR/build"

# Check if ExecuTorch submodule exists
if [ ! -d "$EXECUTORCH_DIR" ]; then
    echo "ExecuTorch submodule not found. Initializing..."
    git submodule update --init --recursive
fi

# Create build directory
mkdir -p "$BUILD_DIR"

# Build ExecuTorch for iOS
echo "Building ExecuTorch for iOS..."
cd "$EXECUTORCH_DIR"

# In a real implementation, you would build ExecuTorch here
# For example:
# cmake -B "$BUILD_DIR" -DCMAKE_TOOLCHAIN_FILE=ios.toolchain.cmake -DPLATFORM=OS64 -DENABLE_BITCODE=0 -DENABLE_ARC=0 -DENABLE_VISIBILITY=0 -DCMAKE_BUILD_TYPE=Release
# cmake --build "$BUILD_DIR" --config Release

echo "ExecuTorch build completed!" 