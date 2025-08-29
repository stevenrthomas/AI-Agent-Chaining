import os
import time
import asyncio
from dotenv import load_dotenv
from pydantic_ai import Agent
from pydantic_ai.models.bedrock import BedrockConverseModel

load_dotenv()

# Get region from .env
region = os.getenv('AWS_DEFAULT_REGION', 'us-east-1')
print(f"Using region: {region}")

# Initialize 4 specialized Bedrock models with .env configuration support
# Fallback to hardcoded defaults if not specified in .env
architecture_model = os.getenv('ARCHITECTURE_MODEL', 'anthropic.claude-3-sonnet-20240229-v1:0')
development_model = os.getenv('DEVELOPMENT_MODEL', 'anthropic.claude-3-haiku-20240307-v1:0')
testing_model = os.getenv('TESTING_MODEL', 'amazon.nova-lite-v1:0')
documentation_model = os.getenv('DOCUMENTATION_MODEL', 'amazon.titan-text-express-v1')

print(f"Model Configuration:")
print(f"  Architecture: {architecture_model}")
print(f"  Development:  {development_model}")
print(f"  Testing:      {testing_model}")
print(f"  Documentation: {documentation_model}")
print()

claude_sonnet = BedrockConverseModel(architecture_model)
claude_haiku = BedrockConverseModel(development_model)
nova_lite = BedrockConverseModel(testing_model)
titan_express = BedrockConverseModel(documentation_model)

# Agent 1: Architect - Design the game structure
architect_agent = Agent(
    model=claude_sonnet,
    system_prompt="You are a software architect. Create detailed technical specifications and architecture for software projects."
)

# Agent 2: Developer - Write the code
developer_agent = Agent(
    model=claude_haiku,
    system_prompt="You are a Python developer. Write clean, functional code based on specifications."
)

# Agent 3: Tester - Create and run tests
tester_agent = Agent(
    model=nova_lite,
    system_prompt="You are a QA engineer. Create comprehensive tests for code to ensure it works correctly."
)

# Agent 4: Documenter - Create documentation (Titan doesn't support system prompts)
documenter_agent = Agent(
    model=titan_express
)

def print_timing_summary(timings, total_time):
    """Print a formatted timing summary"""
    print("\n*** TIMING SUMMARY ***")
    print("-" * 50)
    
    for stage_name, duration, success in timings:
        status = "[SUCCESS]" if success else "[FAILED] "
        print(f"{stage_name:<35}: {duration:>8.2f} sec {status}")
    
    print("-" * 50)
    print(f"Total Pipeline Time: {total_time:.2f} seconds")
    print("=" * 50)

async def game_development_pipeline():
    """4-stage pipeline: Architect -> Developer -> Tester -> Documenter"""
    
    project_request = "Create a simple Tic-Tac-Toe (X&Os) game in Python"
    pipeline_start = time.time()
    timings = []
    
    try:
        # Stage 1: Architecture & Design
        print("[1/4] Creating architecture with Claude Sonnet...")
        stage1_start = time.time()
        arch_result = await architect_agent.run(f"Create a detailed architecture and rulebook for: {project_request}")
        stage1_end = time.time()
        stage1_duration = stage1_end - stage1_start
        
        architecture = arch_result.output
        timings.append(("Architecture (Claude Sonnet)", stage1_duration, True))
        print(f"\n=== ARCHITECTURE ===\n{architecture}\n")
        print(f"[SUCCESS] Stage 1 completed successfully in {stage1_duration:.2f} seconds\n")
        
        # Stage 2: Code Development
        print("[2/4] Writing code with Claude Haiku...")
        stage2_start = time.time()
        dev_prompt = f"Based on this architecture, write complete Python code:\n{architecture}"
        code_result = await developer_agent.run(dev_prompt)
        stage2_end = time.time()
        stage2_duration = stage2_end - stage2_start
        
        code = code_result.output
        timings.append(("Development (Claude Haiku)", stage2_duration, True))
        print(f"\n=== CODE ===\n{code}\n")
        print(f"[SUCCESS] Stage 2 completed successfully in {stage2_duration:.2f} seconds\n")
        
        # Stage 3: Testing
        print("[3/4] Creating tests with Nova Lite...")
        stage3_start = time.time()
        test_prompt = f"Create comprehensive unit tests for this code:\n{code}"
        test_result = await tester_agent.run(test_prompt)
        stage3_end = time.time()
        stage3_duration = stage3_end - stage3_start
        
        tests = test_result.output
        timings.append(("Testing (Nova Lite)", stage3_duration, True))
        print(f"\n=== TESTS ===\n{tests}\n")
        print(f"[SUCCESS] Stage 3 completed successfully in {stage3_duration:.2f} seconds\n")
        
        # Stage 4: Documentation
        print("[4/4] Creating documentation with Titan Express...")
        stage4_start = time.time()
        doc_prompt = f"Act as a technical writer. Create comprehensive documentation for this Tic-Tac-Toe game. Include setup instructions, usage guide, architecture overview, testing approach, and API reference.\n\nArchitecture:\n{architecture}\n\nCode Implementation:\n{code}\n\nTest Suite:\n{tests}\n\nCreate documentation that explains the architecture decisions, how to use the application, and how it was tested."
        doc_result = await documenter_agent.run(doc_prompt)
        stage4_end = time.time()
        stage4_duration = stage4_end - stage4_start
        
        documentation = doc_result.output
        timings.append(("Documentation (Titan Express)", stage4_duration, True))
        print(f"\n=== DOCUMENTATION ===\n{documentation}\n")
        print(f"[SUCCESS] Stage 4 completed successfully in {stage4_duration:.2f} seconds\n")
        
        # Calculate total time
        pipeline_end = time.time()
        total_duration = pipeline_end - pipeline_start
        
        # Print final summary
        print("\n" + "=" * 50)
        print("[SUCCESS] PIPELINE COMPLETE - 4 AGENTS COLLABORATED")
        print("=" * 50)
        print("[DONE] Architecture designed by Claude Sonnet")
        print("[DONE] Code written by Claude Haiku") 
        print("[DONE] Tests created by Nova Lite")
        print("[DONE] Documentation written by Titan Express")
        print("=" * 50)
        
        # Print timing summary
        print_timing_summary(timings, total_duration)
        
        return {
            'architecture': architecture,
            'code': code,
            'tests': tests,
            'documentation': documentation,
            'timings': timings,
            'total_time': total_duration
        }
        
    except Exception as e:
        pipeline_end = time.time()
        total_duration = pipeline_end - pipeline_start
        
        print(f"\n[FAILED] PIPELINE FAILED: {str(e)}")
        print(f"Time spent before failure: {total_duration:.2f} seconds")
        
        if timings:
            print_timing_summary(timings, total_duration)
        
        raise

if __name__ == "__main__":
    print("AWS Bedrock 4-Agent Pipeline (Python)")
    print("=====================================")
    print("Starting 4-Agent Game Development Pipeline...\n")
    
    try:
        result = asyncio.run(game_development_pipeline())
        print(f"\n[PYTHON] Python Implementation Complete!")
    except Exception as e:
        print(f"\n[FAILED] Pipeline failed with error: {str(e)}")