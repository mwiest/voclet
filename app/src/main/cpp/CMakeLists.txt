cmake_minimum_required(VERSION 3.22.1)
project(voclet_ocr)

# Options match the upstream Android release this replaced.
set(NCNN_VULKAN OFF CACHE BOOL "" FORCE)
set(NCNN_SHARED_LIB OFF CACHE BOOL "" FORCE)
set(NCNN_OPENMP ON CACHE BOOL "" FORCE)
set(NCNN_THREADS ON CACHE BOOL "" FORCE)
set(NCNN_RUNTIME_CPU ON CACHE BOOL "" FORCE)
set(NCNN_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(NCNN_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(NCNN_BUILD_BENCHMARK OFF CACHE BOOL "" FORCE)
set(NCNN_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(NCNN_PYTHON OFF CACHE BOOL "" FORCE)
set(NCNN_INSTALL_SDK OFF CACHE BOOL "" FORCE)
# Otherwise ncnn stamps the build date into the library, which breaks reproducible builds.
set(NCNN_VERSION 20260526)

# ncnn at -O0 makes OCR unusably slow, so debug builds compile it optimised too.
# Each directory takes these flags as they stand at its end, so ours stay debug.
set(_debug_c_flags "${CMAKE_C_FLAGS_DEBUG}")
set(_debug_cxx_flags "${CMAKE_CXX_FLAGS_DEBUG}")
set(CMAKE_C_FLAGS_DEBUG "${CMAKE_C_FLAGS_RELEASE}")
set(CMAKE_CXX_FLAGS_DEBUG "${CMAKE_CXX_FLAGS_RELEASE}")
add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/ncnn ncnn)
set(CMAKE_C_FLAGS_DEBUG "${_debug_c_flags}")
set(CMAKE_CXX_FLAGS_DEBUG "${_debug_cxx_flags}")

add_library(voclet_ocr SHARED voclet_ocr.cpp)
target_link_libraries(voclet_ocr ncnn log)
