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
echo.
set /p choice="Enter your choice (1-5): "

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
) else (
    echo Invalid choice. Please run the script again and select 1-5.
    pause
    exit /b 1
)

echo.
echo Test completed.
pause