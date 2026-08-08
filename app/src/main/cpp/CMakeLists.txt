cmake_minimum_required(VERSION 3.22.1)
project(lovault)

add_library(lovault SHARED lok_bridge.cpp)

# LibreOfficeKit C API headers are vendored by scripts/setup-libreoffice.sh.
target_include_directories(lovault PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/include)

set_target_properties(lovault PROPERTIES CXX_STANDARD 17 CXX_STANDARD_REQUIRED ON)

find_library(log-lib log)
# We dlsym the engine at runtime (no build-time link against liblo-native-code),
# so we only need: logging, Android Bitmap access, and dl.
target_link_libraries(lovault ${log-lib} jnigraphics dl)
