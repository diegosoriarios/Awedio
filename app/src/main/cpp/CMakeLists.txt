cmake_minimum_required(VERSION 3.22.1)

project("awedio_whisper" C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(WHISPER_LIB_DIR ${CMAKE_SOURCE_DIR}/../../../../external/whisper.cpp)

# Get whisper.cpp version
file(READ "${WHISPER_LIB_DIR}/CMakeLists.txt" MAIN_CMAKE_CONTENT)
string(REGEX MATCH "project\\(\"whisper\\.cpp\" VERSION ([0-9]+\\.[0-9]+\\.[0-9]+)\\)" VERSION_MATCH "${MAIN_CMAKE_CONTENT}")
if(CMAKE_MATCH_1)
    set(WHISPER_VERSION ${CMAKE_MATCH_1})
else()
    set(WHISPER_VERSION "unknown")
endif()

message(STATUS "Whisper version: ${WHISPER_VERSION}")

set(
    SOURCE_FILES
    ${WHISPER_LIB_DIR}/src/whisper.cpp
    ${CMAKE_SOURCE_DIR}/whisper-jni.cpp
)

find_library(LOG_LIB log)

include(FetchContent)

add_library(
    whisper-jni
    SHARED
    ${SOURCE_FILES}
)

target_compile_definitions(whisper-jni PUBLIC GGML_USE_CPU)
target_compile_definitions(whisper-jni PRIVATE WHISPER_VERSION="${WHISPER_VERSION}")

# Per-ABI performance flags, following the official whisper.android example
# (whisper_v8fp16_va for arm64-v8a, whisper_vfpv4 for armeabi-v7a).
if (${ANDROID_ABI} STREQUAL "arm64-v8a")
    target_compile_options(whisper-jni PRIVATE -march=armv8.2-a+fp16)
    set(GGML_COMPILE_OPTIONS -march=armv8.2-a+fp16)
elseif (${ANDROID_ABI} STREQUAL "armeabi-v7a")
    target_compile_options(whisper-jni PRIVATE -mfpu=neon-vfpv4)
    set(GGML_COMPILE_OPTIONS -mfpu=neon-vfpv4)
endif ()

if (NOT ${CMAKE_BUILD_TYPE} STREQUAL "Debug")
    target_compile_options(whisper-jni PRIVATE -O3)
    target_compile_options(whisper-jni PRIVATE -fvisibility=hidden -fvisibility-inlines-hidden)
    target_compile_options(whisper-jni PRIVATE -ffunction-sections -fdata-sections)

    target_link_options(whisper-jni PRIVATE -Wl,--gc-sections)
    target_link_options(whisper-jni PRIVATE -Wl,--exclude-libs,ALL)
    target_link_options(whisper-jni PRIVATE -flto)
endif ()

FetchContent_Declare(ggml SOURCE_DIR ${WHISPER_LIB_DIR}/ggml)
FetchContent_MakeAvailable(ggml)
target_compile_options(ggml PRIVATE ${GGML_COMPILE_OPTIONS})

target_link_libraries(whisper-jni ${LOG_LIB} android ggml)

include_directories(${WHISPER_LIB_DIR})
include_directories(${WHISPER_LIB_DIR}/src)
include_directories(${WHISPER_LIB_DIR}/include)
include_directories(${WHISPER_LIB_DIR}/ggml/include)
include_directories(${WHISPER_LIB_DIR}/ggml/src)
include_directories(${WHISPER_LIB_DIR}/ggml/src/ggml-cpu)
