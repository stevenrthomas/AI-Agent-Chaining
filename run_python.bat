@echo off
echo =========================================
echo AWS Bedrock 4-Agent Pipeline - Python
echo =========================================
echo.

REM Check if Python is installed
python --version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Python is not installed or not in PATH
    echo Please install Python and ensure it's available in PATH
    pause
    exit /b 1
)

echo Python found: 
python --version

echo.
echo Installing Python dependencies...
pip install -r requirements.txt
if errorlevel 1 (
    echo ERROR: Failed to install Python dependencies
    pause
    exit /b 1
)

echo.
echo Running Python implementation...
echo.
python game_pipeline.py

echo.
echo Python implementation completed.
pause