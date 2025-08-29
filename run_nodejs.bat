@echo off
echo =========================================
echo AWS Bedrock 4-Agent Pipeline - Node.js
echo =========================================
echo.

REM Check if Node.js is installed
node --version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Node.js is not installed or not in PATH
    echo Please install Node.js and ensure it's available in PATH
    pause
    exit /b 1
)

echo Node.js found:
node --version

echo NPM version:
npm --version

echo.
echo Installing Node.js dependencies...
npm install
if errorlevel 1 (
    echo ERROR: Failed to install Node.js dependencies
    pause
    exit /b 1
)

echo.
echo Running Node.js implementation...
echo.
node game_pipeline.js

echo.
echo Node.js implementation completed.
pause