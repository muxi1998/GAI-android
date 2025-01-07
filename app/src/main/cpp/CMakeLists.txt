# Minimum required version of CMake
cmake_minimum_required(VERSION 3.10.2)

# Project name
project(LlmApp)

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Specify the path to your prebuilt libraries
set(PREBUILT_LIB_DIR ${CMAKE_SOURCE_DIR}/../libs/arm64-v8a)

# Add libllava_runner as an imported library
add_library(llava_runner SHARED IMPORTED)
set_target_properties(llava_runner PROPERTIES
    IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/../libs/${ANDROID_ABI}/libllava_runner.so
)

# Find and link Android log library
find_library(log-lib log)

# ====== LLM ======
#add_library(llm_jni SHARED
#    llm_jni.cpp
#)
#
## Set the output directory for the built library
#set_target_properties(llm_jni PROPERTIES
#    LIBRARY_OUTPUT_DIRECTORY ${CMAKE_SOURCE_DIR}/../libs/arm64-v8a
#)
#
## Add this section to ensure symbols are properly exported
#set_target_properties(llm_jni PROPERTIES
#    LINK_FLAGS "-Wl,--no-as-needed"
#)
#
## Add this to force the linker to include all symbols
#target_link_options(llm_jni PRIVATE -Wl,--whole-archive ${PREBUILT_LIB_DIR}/libmtk_llm.so -Wl,--no-whole-archive)
#
#target_link_libraries(llm_jni
#    android
#    log
#)

# Find required Android libraries
find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)

# ====== VLM (LLaVA) ======
add_library(vlm_jni SHARED
    include/llava/llava_runner_simplified.cpp
    vlm_jni.cpp
)

set_target_properties(vlm_jni PROPERTIES
    LIBRARY_OUTPUT_DIRECTORY "${CMAKE_SOURCE_DIR}/../libs/${ANDROID_ABI}"
)

target_include_directories(vlm_jni PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/include/llava
)

target_link_libraries(vlm_jni
    android
    ${log-lib}
    ${jnigraphics-lib}
)

# Ensure llava_runner is copied to the output directory
add_custom_command(TARGET vlm_jni POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E copy 
        ${CMAKE_CURRENT_SOURCE_DIR}/../libs/${ANDROID_ABI}/libllava_runner.so
        ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/
)
