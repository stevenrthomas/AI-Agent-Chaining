@echo off
echo =============================================
echo AWS Bedrock 4-Agent Pipeline - Quick Test
echo =============================================
echo.
echo Select which implementation to test:
echo 1. Python
echo 2. Node.js
echo 3. C++
echo 4. Go  
echo 5. Java
echo 6. C#/.NET
echo 7. Rust
echo.
set /p choice="Enter your choice (1-7): "

if "%choice%"=="1" (
    echo Running Python implementation...
    call run_python.bat
) else if "%choice%"=="2" (
    echo Running Node.js implementation...
    call run_nodejs.bat
) else if "%choice%"=="3" (
    echo Running C++ implementation...
    call run_cpp.bat
) else if "%choice%"=="4" (
    echo Running Go implementation...
    call run_go.bat
) else if "%choice%"=="5" (
    echo Running Java implementation...
    call run_java.bat
) else if "%choice%"=="6" (
    echo Running C#/.NET implementation...
    call run_csharp.bat
) else if "%choice%"=="7" (
    echo Running Rust implementation...
    call run_rust.bat
) else (
    echo Invalid choice. Please run the script again and select 1-7.
    pause
    exit /b 1
)

echo.
echo Test completed.
pause