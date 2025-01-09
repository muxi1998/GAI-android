cmake_minimum_required(VERSION 3.19)
project(vlm_android)

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Define paths
set(PREBUILT_LIBS_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../libs/${ANDROID_ABI})
set(THIRD_PARTY_DIR ${CMAKE_CURRENT_SOURCE_DIR}/third_party)
set(EXECUTORCH_ROOT ${THIRD_PARTY_DIR}/executorch)

# Add definitions
add_definitions(
    -DET_USE_THREADPOOL
    -DEXECUTORCH_BUILD_KERNELS_CUSTOM
    -DEXECUTORCH_BUILD_EXTENSION_THREADPOOL
    -DEXECUTORCH_ENABLE_THREADPOOL
)

# Find required packages
find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)

# Add library
add_library(vlm_jni SHARED
    vlm_jni.cpp
    vlm_test.cpp
)

# Include directories
target_include_directories(vlm_jni PRIVATE
    ${THIRD_PARTY_DIR}
    ${THIRD_PARTY_DIR}/executorch
    ${THIRD_PARTY_DIR}/executorch/include
)

# Define optimized kernels and related libraries
set(OPTIMIZED_LIBS
    ${PREBUILT_LIBS_DIR}/liboptimized_kernels.a  # Add optimized kernels
    ${PREBUILT_LIBS_DIR}/libcpublas.a            # Add BLAS support
    ${PREBUILT_LIBS_DIR}/libeigen_blas.a
)

# Define core libraries that don't need whole-archive
set(CORE_LIBS
    ${PREBUILT_LIBS_DIR}/libexecutorch_core.a
    ${PREBUILT_LIBS_DIR}/libportable_kernels.a
    ${PREBUILT_LIBS_DIR}/libquantized_kernels.a
    ${PREBUILT_LIBS_DIR}/libextension_tensor.a
)

# Define threadpool libraries
set(THREADPOOL_LIBS
    ${PREBUILT_LIBS_DIR}/libextension_threadpool.a
    ${PREBUILT_LIBS_DIR}/libpthreadpool.a
    ${PREBUILT_LIBS_DIR}/libcpuinfo.a
)

# Define ops libraries that need whole-archive
set(OPS_LIBS
    ${PREBUILT_LIBS_DIR}/libportable_ops_lib.a
    ${PREBUILT_LIBS_DIR}/libquantized_ops_lib.a
    ${PREBUILT_LIBS_DIR}/libcustom_ops.a
    ${PREBUILT_LIBS_DIR}/liboptimized_ops_lib.a
)

# Define extension libraries
set(EXTENSION_LIBS
    ${PREBUILT_LIBS_DIR}/libextension_module.a
    ${PREBUILT_LIBS_DIR}/libextension_data_loader.a
    ${PREBUILT_LIBS_DIR}/libextension_llm_runner.a
    ${PREBUILT_LIBS_DIR}/libllava_runner.a
)

# Define backend libraries
set(BACKEND_LIBS
    ${PREBUILT_LIBS_DIR}/libXNNPACK.a
    ${PREBUILT_LIBS_DIR}/libxnnpack_backend.a
    ${PREBUILT_LIBS_DIR}/libmicrokernels-prod.a
)

# Link libraries in the correct order
target_link_libraries(vlm_jni
    android
    ${log-lib}
    ${jnigraphics-lib}

    # Threadpool libraries first
    -Wl,--whole-archive
    ${THREADPOOL_LIBS}
    -Wl,--no-whole-archive

    # Optimized kernels (must come before ops)
    ${OPTIMIZED_LIBS}

    # Ops libraries with whole-archive
    -Wl,--whole-archive
    ${OPS_LIBS}
    -Wl,--no-whole-archive

    # Core libraries
    ${CORE_LIBS}

    # Extension libraries
    ${EXTENSION_LIBS}

    # Backend libraries
    ${BACKEND_LIBS}

    -latomic
    -lm
)

# Suppress warnings
target_compile_options(vlm_jni PRIVATE
    -Wno-deprecated-declarations
    -Wno-format
)