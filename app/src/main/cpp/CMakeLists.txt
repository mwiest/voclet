cmake_minimum_required(VERSION 3.22.1)
project(voclet_ocr)

# NCNN_ROOT is the unpacked release, handed over by build.gradle.kts; the
# release carries one CMake package per ABI.
set(ncnn_DIR "${NCNN_ROOT}/${ANDROID_ABI}/lib/cmake/ncnn" CACHE PATH "" FORCE)
find_package(ncnn REQUIRED)

add_library(voclet_ocr SHARED voclet_ocr.cpp)
target_link_libraries(voclet_ocr ncnn log)
