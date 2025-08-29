import os
from dotenv import load_dotenv
from pydantic_ai import Agent
from pydantic_ai.models.bedrock import BedrockConverseModel

load_dotenv()

# Get region from .env
region = os.getenv('AWS_DEFAULT_REGION', 'us-east-1')
print(f"Using region: {region}")

# Initialize 4 specialized Bedrock models
claude_sonnet = BedrockConverseModel('anthropic.claude-3-sonnet-20240229-v1:0')
claude_haiku = BedrockConverseModel('anthropic.claude-3-haiku-20240307-v1:0')
nova_lite = BedrockConverseModel('amazon.nova-lite-v1:0')
titan_express = BedrockConverseModel('amazon.titan-text-express-v1')

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

async def game_development_pipeline():
    """4-stage pipeline: Architect -> Developer -> Tester -> Documenter"""
    
    project_request = "Create a simple Tic-Tac-Toe (X&Os) game in Python"
    
    # Stage 1: Architecture & Design
    print("[1/4] Creating architecture with Claude Sonnet...")
    arch_result = await architect_agent.run(f"Create a detailed architecture and rulebook for: {project_request}")
    architecture = arch_result.output
    print(f"\n=== ARCHITECTURE ===\n{architecture}\n")
    
    # Stage 2: Code Development
    print("[2/4] Writing code with Claude Haiku...")
    dev_prompt = f"Based on this architecture, write complete Python code:\n{architecture}"
    code_result = await developer_agent.run(dev_prompt)
    code = code_result.output
    print(f"\n=== CODE ===\n{code}\n")
    
    # Stage 3: Testing
    print("[3/4] Creating tests with Nova Lite...")
    test_prompt = f"Create comprehensive unit tests for this code:\n{code}"
    test_result = await tester_agent.run(test_prompt)
    tests = test_result.output
    print(f"\n=== TESTS ===\n{tests}\n")
    
    # Stage 4: Documentation
    print("[4/4] Creating documentation with Titan Express...")
    doc_prompt = f"Act as a technical writer. Create comprehensive documentation for this Tic-Tac-Toe game. Include setup instructions, usage guide, architecture overview, testing approach, and API reference.\n\nArchitecture:\n{architecture}\n\nCode Implementation:\n{code}\n\nTest Suite:\n{tests}\n\nCreate documentation that explains the architecture decisions, how to use the application, and how it was tested."
    doc_result = await documenter_agent.run(doc_prompt)
    documentation = doc_result.output
    print(f"\n=== DOCUMENTATION ===\n{documentation}\n")
    
    return {
        'architecture': architecture,
        'code': code,
        'tests': tests,
        'documentation': documentation
    }

if __name__ == "__main__":
    import asyncio
    
    print("Starting 4-Agent Game Development Pipeline...\n")
    result = asyncio.run(game_development_pipeline())
    
    print("\n" + "="*50)
    print("PIPELINE COMPLETE - 4 AGENTS COLLABORATED")
    print("="*50)
    print("[DONE] Architecture designed by Claude Sonnet")
    print("[DONE] Code written by Claude Haiku") 
    print("[DONE] Tests created by Nova Lite")
    print("[DONE] Documentation written by Titan Express")
    print("="*50)