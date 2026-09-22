cmake_minimum_required(VERSION 3.22.1)

project("awedio_whisper" C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_library(log-lib log)

add_library(
    whisper-jni
    SHARED
    whisper-jni.cpp
    whisper.cpp
)

target_include_directories(whisper-jni PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_link_libraries(whisper-jni ${log-lib})
