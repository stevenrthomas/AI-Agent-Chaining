#!/usr/bin/env python3
"""
Performance Benchmark Comparison Script
Runs all three implementations and compares their execution times
"""

import subprocess
import time
import sys
import os
from datetime import datetime

def run_command_with_timing(command, description):
    """Run a command and return timing information"""
    print(f"\n{'='*60}")
    print(f"RUNNING: {description}")
    print(f"COMMAND: {command}")
    print(f"{'='*60}")
    
    start_time = time.time()
    try:
        # Run the command and capture output
        result = subprocess.run(
            command,
            shell=True,
            capture_output=True,
            text=True,
            timeout=300  # 5 minute timeout
        )
        
        end_time = time.time()
        execution_time = end_time - start_time
        
        if result.returncode == 0:
            print("✅ SUCCESS")
            # Extract timing summary from output if available
            output_lines = result.stdout.split('\n')
            timing_found = False
            
            for i, line in enumerate(output_lines):
                if "TIMING SUMMARY" in line or "📊" in line:
                    timing_found = True
                    # Print timing summary
                    for j in range(i, min(i+10, len(output_lines))):
                        if output_lines[j].strip():
                            print(output_lines[j])
                    break
            
            if not timing_found:
                # Look for individual stage completions
                for line in output_lines:
                    if "completed successfully in" in line or "Total Pipeline Time" in line:
                        print(line.strip())
                        
        else:
            print("❌ FAILED")
            print("STDERR:", result.stderr[:500])  # First 500 chars
            
        return {
            'success': result.returncode == 0,
            'execution_time': execution_time,
            'output': result.stdout,
            'error': result.stderr
        }
        
    except subprocess.TimeoutExpired:
        print("❌ TIMEOUT (5 minutes)")
        return {
            'success': False,
            'execution_time': 300.0,
            'output': '',
            'error': 'Timeout after 5 minutes'
        }
    except Exception as e:
        end_time = time.time()
        print(f"❌ ERROR: {str(e)}")
        return {
            'success': False,
            'execution_time': end_time - start_time,
            'output': '',
            'error': str(e)
        }

def main():
    print("AWS Bedrock 4-Agent Pipeline - Performance Comparison")
    print("=" * 60)
    print(f"Started at: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"Working directory: {os.getcwd()}")
    
    # Test commands for all three implementations
    tests = [
        {
            'name': 'C++ Implementation',
            'command': './build/Release/game_pipeline.exe',
            'icon': '[C++]'
        },
        {
            'name': 'Python Implementation',
            'command': 'python game_development_pipeline.py',
            'icon': '[PY] '
        },
        {
            'name': 'Node.js Implementation',
            'command': 'node game_development_pipeline.js',
            'icon': '[JS] '
        }
    ]
    
    results = []
    
    # Run each implementation
    for test in tests:
        print(f"\n\n{test['icon']} Testing {test['name']}...")
        result = run_command_with_timing(test['command'], test['name'])
        result['name'] = test['name']
        result['icon'] = test['icon']
        results.append(result)
        
        # Small delay between tests
        time.sleep(2)
    
    # Print comparison summary
    print(f"\n\n{'='*80}")
    print("*** PERFORMANCE COMPARISON SUMMARY ***")
    print(f"{'='*80}")
    
    print(f"{'Language':<20} {'Status':<10} {'Total Time':<15} {'Performance'}")
    print("-" * 80)
    
    successful_results = [r for r in results if r['success']]
    if successful_results:
        fastest_time = min(r['execution_time'] for r in successful_results)
        
        for result in results:
            status = "[SUCCESS]" if result['success'] else "[FAILED] "
            if result['success']:
                time_str = f"{result['execution_time']:.2f}s"
                if result['execution_time'] == fastest_time:
                    perf_str = "*** FASTEST ***"
                else:
                    ratio = result['execution_time'] / fastest_time
                    perf_str = f"{ratio:.1f}x slower"
            else:
                time_str = f"{result['execution_time']:.2f}s"
                perf_str = "FAILED"
            
            print(f"{result['icon']} {result['name']:<17} {status:<10} {time_str:<15} {perf_str}")
    
    print("-" * 80)
    print(f"Completed at: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"Total benchmark time: {sum(r['execution_time'] for r in results):.2f} seconds")
    
    # Performance insights
    if len(successful_results) > 1:
        print(f"\n*** PERFORMANCE INSIGHTS ***")
        fastest = min(successful_results, key=lambda x: x['execution_time'])
        slowest = max(successful_results, key=lambda x: x['execution_time'])
        
        print(f"* Fastest: {fastest['name']} ({fastest['execution_time']:.2f}s)")
        print(f"* Slowest: {slowest['name']} ({slowest['execution_time']:.2f}s)")
        
        if len(successful_results) == 3:
            print(f"* Speed difference: {slowest['execution_time']/fastest['execution_time']:.1f}x")
    
    print(f"\n*** NOTE: Performance may vary based on:")
    print("* Network latency to AWS Bedrock")
    print("* Model response times")
    print("* System resources and overhead")
    print("* Language runtime efficiency")

if __name__ == "__main__":
    main()