cmake_minimum_required(VERSION 3.22.1)
project(ainote_llm CXX)

# ---- 关闭 llama.cpp 无关组件（tests/tools/examples/server/common）----
set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP      OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON   OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL        OFF CACHE BOOL "" FORCE)

# ---- ggml：交叉编译，关 native / OpenMP / ccache，静态编入 ----
set(GGML_NATIVE        OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP       OFF CACHE BOOL "" FORCE)
set(GGML_CCACHE       OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

# third_party 与 app 模块平级：app/src/main/cpp -> 上溯四层到工程根
set(LLAMA_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/llama.cpp")
add_subdirectory(${LLAMA_DIR} ${CMAKE_BINARY_DIR}/llama.cpp)

add_library(ainote-llm SHARED llm_jni.cpp)

target_include_directories(ainote-llm PRIVATE
    ${LLAMA_DIR}/include
    ${LLAMA_DIR}/ggml/include
)

find_library(log-lib log)
target_link_libraries(ainote-llm PRIVATE llama ggml-base ${log-lib})
