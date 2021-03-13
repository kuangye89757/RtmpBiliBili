cmake_minimum_required(VERSION 3.10.2)

project("rtmpbilibili")

# 添加子目录 「会找到该目录下的CMakeLists.txt进行编译」
add_subdirectory(librtmp)

add_library(
        native-lib
        SHARED
        native-lib.cpp)


find_library(
        log-lib
        log)

target_link_libraries(
        native-lib
        ${log-lib}
        rtmp
)