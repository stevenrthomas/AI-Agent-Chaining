@echo off
echo ======================================
echo AWS Bedrock Pipeline Timing Features
echo ======================================
echo.

echo [1/3] Testing C++ Implementation
echo -------------------------------------
echo Running: .\build\Release\game_pipeline.exe
echo (This will show timing for each stage and total time)
echo.

echo [2/3] Testing Python Implementation  
echo -------------------------------------
echo Running: python game_development_pipeline.py
echo (This will show timing for each stage and total time)
echo.

echo [3/3] Testing Node.js Implementation
echo -------------------------------------
echo Running: node game_development_pipeline.js
echo (This will show timing for each stage and total time)
echo.

echo NOTE: Each implementation now includes:
echo * Individual stage timing (Architecture, Development, Testing, Documentation)
echo * Success/failure indicators for each stage
echo * Pipeline stops if any stage fails
echo * Comprehensive timing summary at the end
echo * Total pipeline execution time
echo.

echo To run full benchmark comparison:
echo python benchmark_comparison.py
echo.

echo Ready to test? All three implementations have identical timing features!
pause