@echo off
echo =====================================
echo AWS Bedrock 4-Agent Pipeline - Go
echo =====================================
echo.

REM Check if Go is installed in PATH first
go version >nul 2>&1
if not errorlevel 1 (
    echo Go found in PATH:
    go version
    set GO_CMD=go
) else (
    REM Check if Go is installed in standard location
    "C:\Program Files\Go\bin\go.exe" version >nul 2>&1
    if not errorlevel 1 (
        echo Go found in standard location:
        "C:\Program Files\Go\bin\go.exe" version
        set GO_CMD="C:\Program Files\Go\bin\go.exe"
    ) else (
        echo ERROR: Go is not installed or not found
        echo Please install Go from https://golang.org/dl/
        pause
        exit /b 1
    )
)

echo.
echo Installing Go dependencies...
%GO_CMD% mod tidy
if errorlevel 1 (
    echo ERROR: Failed to install Go dependencies
    pause
    exit /b 1
)

echo.
echo Running Go implementation...
echo.
%GO_CMD% run game_pipeline.go

echo.
echo Go implementation completed.
pause