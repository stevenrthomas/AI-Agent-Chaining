@echo off
echo ==========================================
echo AWS Bedrock 4-Agent Pipeline - C++
echo ==========================================
echo.

REM Check if CMake is available
cmake --version >nul 2>&1
if errorlevel 1 (
    echo ERROR: CMake is not installed or not in PATH
    echo Please install CMake and ensure it's available in PATH
    pause
    exit /b 1
)

echo CMake found:
cmake --version | findstr /r "cmake version"

echo.
echo Configuring C++ project with CMake...
if not exist "build" mkdir build
cd build

cmake .. -DCMAKE_TOOLCHAIN_FILE="C:/vcpkg/scripts/buildsystems/vcpkg.cmake"
if errorlevel 1 (
    echo ERROR: CMake configuration failed
    echo Please ensure vcpkg is installed and AWS SDK dependencies are available
    cd ..
    pause
    exit /b 1
)

echo.
echo Building C++ project...
cmake --build . --config Release
if errorlevel 1 (
    echo ERROR: C++ build failed
    cd ..
    pause
    exit /b 1
)

echo.
echo Running C++ implementation...
echo.
cd Release
game_pipeline.exe
cd ..

cd ..
echo.
echo C++ implementation completed.
pause