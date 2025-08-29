@echo off
echo =======================================================
echo AWS Bedrock 4-Agent Pipeline - All Implementations
echo =======================================================
echo.
echo This script will run all available implementations:
echo 1. Python
echo 2. Node.js  
echo 3. C++
echo 4. Go
echo 5. Java
echo 6. C#/.NET
echo 7. Rust
echo.
echo Each implementation will be run sequentially with timing.
echo Press any key to continue, or Ctrl+C to cancel...
pause >nul
echo.

REM Record start time
set start_time=%time%
echo Started at: %start_time%
echo.

echo =======================================================
echo [1/5] RUNNING PYTHON IMPLEMENTATION
echo =======================================================
call run_python.bat
echo.

echo =======================================================
echo [2/5] RUNNING NODE.JS IMPLEMENTATION  
echo =======================================================
call run_nodejs.bat
echo.

echo =======================================================
echo [3/5] RUNNING C++ IMPLEMENTATION
echo =======================================================
call run_cpp.bat
echo.

echo =======================================================
echo [4/5] RUNNING GO IMPLEMENTATION
echo =======================================================
call run_go.bat
echo.

echo =======================================================
echo [5/6] RUNNING JAVA IMPLEMENTATION
echo =======================================================
call run_java.bat
echo.

echo =======================================================
echo [6/7] RUNNING C#/.NET IMPLEMENTATION
echo =======================================================
call run_csharp.bat
echo.

echo =======================================================
echo [7/7] RUNNING RUST IMPLEMENTATION
echo =======================================================
call run_rust.bat
echo.

REM Record end time  
set end_time=%time%
echo =======================================================
echo ALL IMPLEMENTATIONS COMPLETED
echo =======================================================
echo Started at:  %start_time%
echo Completed at: %end_time%
echo.
echo Individual timing results are shown in each implementation output.
echo Check TIMING_RESULTS.md for detailed performance comparison.
echo.
pause