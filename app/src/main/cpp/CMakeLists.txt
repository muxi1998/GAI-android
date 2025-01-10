cmake_minimum_required(VERSION 3.19)
project(vlm_android)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Define paths
set(PREBUILT_LIBS_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../libs/${ANDROID_ABI})
set(THIRD_PARTY_DIR ${CMAKE_CURRENT_SOURCE_DIR}/third_party)
set(EXECUTORCH_ROOT ${THIRD_PARTY_DIR}/executorch)

# Core definitions
add_definitions(-DLLAVA_NO_TORCH_DUMMY_IMAGE=1)

# Find required Android libraries
find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)

set(_common_include_directories
        ${EXECUTORCH_ROOT}/..
        ${EXECUTORCH_ROOT}/extension/llm
        ${EXECUTORCH_ROOT}/extension/llm/runner
        ${EXECUTORCH_ROOT}/examples/models/llava/runner
)

# Create imported libraries with proper whole-archive linking
add_library(executorch SHARED IMPORTED)
set_target_properties(executorch PROPERTIES
        IMPORTED_LOCATION ${PREBUILT_LIBS_DIR}/libexecutorch_jni.so
)

# Add llava runner
add_library(llava_runner STATIC
        ${EXECUTORCH_ROOT}/examples/models/llava/runner/llava_runner.cpp
        ${EXECUTORCH_ROOT}/extension/llm/sampler/sampler.cpp
        ${EXECUTORCH_ROOT}/extension/llm/tokenizer/bpe_tokenizer.cpp
)

target_include_directories(llava_runner PUBLIC ${_common_include_directories})


target_link_libraries(llava_runner PUBLIC
        executorch
)

# Add JNI library
add_library(vlm_jni SHARED
        vlm_jni.cpp
        vlm_test.cpp
)

target_include_directories(vlm_jni PRIVATE ${_common_include_directories})

target_link_libraries(vlm_jni
        PRIVATE
        android
        ${log-lib}
        ${jnigraphics-lib}
        llava_runner
        -latomic
        -lm
)

target_compile_options(vlm_jni PRIVATE
        -Wno-deprecated-declarations
        -fPIC
)

#set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fsanitize=address -fno-omit-frame-pointer")
#set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -fsanitize=address")