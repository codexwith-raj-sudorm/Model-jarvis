cmake_minimum_required(VERSION 3.22.1)
project(jarvis_llama LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ---------------------------------------------------------------------------
# Pull llama.cpp at configure time.
# TIP: pin a release tag for reproducible builds, e.g.
#   set(LLAMA_GIT_TAG "b4754")  instead of tracking master below.
# ---------------------------------------------------------------------------
set(LLAMA_GIT_TAG "master" CACHE STRING "llama.cpp git ref")

set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS    OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL           OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP          OFF CACHE BOOL "" FORCE)  # NDK r26+ often lacks OpenMP

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
