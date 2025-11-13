cmake_minimum_required(VERSION 3.22)
project(edgeviewer)

add_library(edgeviewer SHARED native-lib.cpp)

find_library(log-lib log)

target_link_libraries(edgeviewer ${log-lib})
