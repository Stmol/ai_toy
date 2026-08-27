cmake_minimum_required(VERSION 3.22.1)

project(aitoy_native)

add_library(
    aitoy
    SHARED
    native-lib.cpp
)

find_library(
    log-lib
    log
)

target_link_libraries(
    aitoy
    ${log-lib}
)
