@echo off
echo ==========================================
echo AWS Bedrock 4-Agent Pipeline - Rust
echo ==========================================
echo.

REM Check if Cargo is available in PATH first
cargo --version >nul 2>&1
if not errorlevel 1 (
    echo Rust/Cargo found in PATH:
    cargo --version
    set CARGO_CMD=cargo
    goto :build
)

REM Check common Rust installation locations
echo Searching for Rust/Cargo installation...

REM Check default rustup installation (Windows)
if exist "%USERPROFILE%\.cargo\bin\cargo.exe" (
    echo Rust found in user profile: %USERPROFILE%\.cargo\bin\
    set "PATH=%USERPROFILE%\.cargo\bin;%PATH%"
    set CARGO_CMD="%USERPROFILE%\.cargo\bin\cargo.exe"
    goto :found
)

REM Check Program Files locations
if exist "C:\Program Files\Rust\bin\cargo.exe" (
    echo Rust found in Program Files: C:\Program Files\Rust\bin\
    set "PATH=C:\Program Files\Rust\bin;%PATH%"
    set CARGO_CMD="C:\Program Files\Rust\bin\cargo.exe"
    goto :found
)

if exist "C:\Program Files (x86)\Rust\bin\cargo.exe" (
    echo Rust found in Program Files (x86): C:\Program Files (x86)\Rust\bin\
    set "PATH=C:\Program Files (x86)\Rust\bin;%PATH%"
    set CARGO_CMD="C:\Program Files (x86)\Rust\bin\cargo.exe"
    goto :found
)

REM Check scoop installation
if exist "%USERPROFILE%\scoop\apps\rust\current\bin\cargo.exe" (
    echo Rust found via Scoop: %USERPROFILE%\scoop\apps\rust\current\bin\
    set "PATH=%USERPROFILE%\scoop\apps\rust\current\bin;%PATH%"
    set CARGO_CMD="%USERPROFILE%\scoop\apps\rust\current\bin\cargo.exe"
    goto :found
)

REM Check chocolatey installation
if exist "C:\ProgramData\chocolatey\lib\rust\tools\bin\cargo.exe" (
    echo Rust found via Chocolatey: C:\ProgramData\chocolatey\lib\rust\tools\bin\
    set "PATH=C:\ProgramData\chocolatey\lib\rust\tools\bin;%PATH%"
    set CARGO_CMD="C:\ProgramData\chocolatey\lib\rust\tools\bin\cargo.exe"
    goto :found
)

REM Check if rustc is available (alternative approach)
rustc --version >nul 2>&1
if not errorlevel 1 (
    echo Rust compiler found, but Cargo not found in PATH
    echo Attempting to locate Cargo from rustc location...
    for /f "tokens=*" %%i in ('where rustc 2^>nul') do (
        set RUSTC_PATH=%%i
        goto :check_cargo_from_rustc
    )
)

:check_cargo_from_rustc
if defined RUSTC_PATH (
    for %%i in ("%RUSTC_PATH%") do set RUST_BIN_DIR=%%~dpi
    if exist "%RUST_BIN_DIR%cargo.exe" (
        echo Cargo found alongside rustc: %RUST_BIN_DIR%
        set "PATH=%RUST_BIN_DIR%;%PATH%"
        set CARGO_CMD="%RUST_BIN_DIR%cargo.exe"
        goto :found
    )
)

echo ERROR: Rust/Cargo not found in any of these locations:
echo   - PATH environment variable
echo   - %USERPROFILE%\.cargo\bin\
echo   - C:\Program Files\Rust\bin\
echo   - C:\Program Files (x86)\Rust\bin\
echo   - %USERPROFILE%\scoop\apps\rust\current\bin\
echo   - C:\ProgramData\chocolatey\lib\rust\tools\bin\
echo.
echo Please install Rust from https://rustup.rs/ or ensure it's in your PATH
echo Standard installation command: curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
pause
exit /b 1

:found
echo Rust/Cargo version:
%CARGO_CMD% --version

:build
echo.
echo Building Rust project with Cargo...
%CARGO_CMD% build --release
if errorlevel 1 (
    echo ERROR: Rust build failed
    pause
    exit /b 1
)

echo.
echo Running Rust implementation...
echo.
%CARGO_CMD% run --release --bin game_pipeline

echo.
echo Rust implementation completed.
pause