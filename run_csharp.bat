@echo off
echo ==========================================
echo AWS Bedrock 4-Agent Pipeline - C#/.NET
echo ==========================================
echo.

REM Check if .NET SDK is installed
dotnet --version >nul 2>&1
if errorlevel 1 (
    echo ERROR: .NET SDK is not installed or not in PATH
    echo Please install .NET 8 SDK from https://dotnet.microsoft.com/download
    pause
    exit /b 1
)

echo .NET SDK found:
dotnet --version

echo.
echo Restoring NuGet packages...
dotnet restore
if errorlevel 1 (
    echo ERROR: Failed to restore NuGet packages
    pause
    exit /b 1
)

echo.
echo Building C# project...
dotnet build --configuration Release
if errorlevel 1 (
    echo ERROR: C# build failed
    pause
    exit /b 1
)

echo.
echo Running C# implementation...
echo.
dotnet run --configuration Release --no-build

echo.
echo C# implementation completed.
pause