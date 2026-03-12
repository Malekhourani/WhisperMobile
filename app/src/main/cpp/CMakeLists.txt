cmake_minimum_required(VERSION 3.22)
project(whisper_jni)

# Force CPU-only build for Android
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_CUDA OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
set(GGML_METAL OFF CACHE BOOL "" FORCE)
set(GGML_OPENCL OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP ON CACHE BOOL "" FORCE)

set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

add_subdirectory(whisper)

add_library(whisper_jni SHARED whisper_jni.cpp)

target_link_libraries(whisper_jni
    whisper
    log
    android
)

target_compile_options(whisper_jni PRIVATE -fno-finite-math-only)
