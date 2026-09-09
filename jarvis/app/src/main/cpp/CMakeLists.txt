cmake_minimum_required(VERSION 3.22.1)
project(jarvis_llama LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ---------------------------------------------------------------------------
# Pull llama.cpp at configure time.
# Pinned to release tag b10876 (verified: include/llama.h at that tag has all
# 25 API symbols + exact signatures this bridge uses — checked against the
# live header, per project convention #4). Bump deliberately.
# ---------------------------------------------------------------------------
set(LLAMA_GIT_TAG "b10876" CACHE STRING "llama.cpp git ref")

set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS    OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL           OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP          OFF CACHE BOOL "" FORCE)  # NDK r26+ often lacks OpenMP
# 32-bit ARM: sgemm.cpp's fp16 NEON intrinsics (vld1q_f16) need the fp16
# vector extension most armv7 devices lack — disable the llamafile sgemm
# fast-path there (standard matmul fallback; speed optimization only)
if(ANDROID_ABI STREQUAL "armeabi-v7a")
    set(GGML_LLAMAFILE    OFF CACHE BOOL "" FORCE)
endif()

include(FetchContent)
FetchContent_Declare(
        llama
        GIT_REPOSITORY https://github.com/ggml-org/llama.cpp
        GIT_TAG        ${LLAMA_GIT_TAG}
        GIT_SHALLOW    TRUE
)
FetchContent_MakeAvailable(llama)

# Our JNI shim, packaged as libjarvis_llama.so
add_library(jarvis_llama SHARED llama_jni.cpp)

find_library(log-lib log)
target_link_libraries(jarvis_llama
        PRIVATE
        llama
        ${log-lib})
