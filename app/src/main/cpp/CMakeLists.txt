# Minimum required version of CMake
cmake_minimum_required(VERSION 3.10.2)

# Project name
project(LlmApp)

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Define ExecuTorch root directory
set(EXECUTORCH_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/third_party/executorch)

# Set paths for prebuilt libraries
set(PREBUILT_LIBS_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../libs)

# Find required Android libraries
find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)

# Define source files
set(VLM_SOURCES
    vlm_jni.cpp
    vlm_test.cpp
    include/llava/llava_runner.cpp
)

# Define header files
set(VLM_HEADERS
    include/llava/llava_runner.h
    include/llava/llava_image_prefiller.h
    include/llava/llava_text_decoder_runner.h
)

# Import prebuilt llava_runner library
add_library(llava_runner SHARED IMPORTED)
set_target_properties(llava_runner PROPERTIES
    IMPORTED_LOCATION ${PREBUILT_LIBS_DIR}/libllava_runner.so
)

# VLM JNI library
add_library(vlm_jni SHARED ${VLM_SOURCES} ${VLM_HEADERS})

set_target_properties(vlm_jni PROPERTIES
    LIBRARY_OUTPUT_DIRECTORY "${CMAKE_SOURCE_DIR}/../libs/${ANDROID_ABI}"
)

# Include directories
target_include_directories(vlm_jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/include
    ${EXECUTORCH_ROOT}
    ${EXECUTORCH_ROOT}/..
    ${EXECUTORCH_ROOT}/extension/llm
    ${EXECUTORCH_ROOT}/extension/llm/runner
)

target_compile_options(vlm_jni PRIVATE
    -Wno-deprecated-declarations
)

# Link against required libraries
target_link_libraries(vlm_jni
    android
    ${log-lib}
    ${jnigraphics-lib}
    llava_runner
    -latomic
    -lm
)
