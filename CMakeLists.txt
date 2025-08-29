cmake_minimum_required(VERSION 3.16)
project(GamePipeline)

set(CMAKE_CXX_STANDARD 17)

# Find AWS SDK using the vcpkg method
set(AWSSDK_ROOT_DIR "${CMAKE_CURRENT_SOURCE_DIR}/vcpkg/installed/x64-windows")
set(AWSSDK_DIR "${CMAKE_CURRENT_SOURCE_DIR}/vcpkg/installed/x64-windows/share/awssdk")
find_package(AWSSDK CONFIG COMPONENTS core bedrock-runtime REQUIRED)

# Create executable
add_executable(game_pipeline game_pipeline.cpp)

# Include directories
target_include_directories(game_pipeline PRIVATE ${AWSSDK_INCLUDE_DIRS})

# Link AWS SDK
target_link_libraries(game_pipeline PRIVATE ${AWSSDK_LIBRARIES})