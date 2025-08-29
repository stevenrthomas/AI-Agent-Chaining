import os
from dotenv import load_dotenv
from pydantic_ai import Agent
from pydantic_ai.models.bedrock import BedrockConverseModel

load_dotenv()

# Get region from .env
region = os.getenv('AWS_DEFAULT_REGION', 'us-east-1')
print(f"Using region: {region}")

# Initialize Bedrock models (using models from our working list)
claude_model = BedrockConverseModel('anthropic.claude-3-haiku-20240307-v1:0')
titan_model = BedrockConverseModel('amazon.titan-text-express-v1')

# Agent 1: Content Generator
content_agent = Agent(
    model=claude_model,
    system_prompt="You are a content creator. Generate creative content based on user input."
)

# Agent 2: Content Reviewer/Editor (Titan doesn't support system prompts)
review_agent = Agent(
    model=titan_model
)

async def chain_models(user_input: str):
    """Chain two models: generate content, then review it"""
    
    # Step 1: Generate content with Claude
    print("[1/2] Generating content with Claude...")
    content_result = await content_agent.run(user_input)
    generated_content = content_result.output
    print(f"Generated: {generated_content}\n")
    
    # Step 2: Review content with Titan
    print("[2/2] Reviewing content with Titan...")
    review_prompt = f"Act as an editor. Review and improve this content to make it more concise and professional: {generated_content}"
    review_result = await review_agent.run(review_prompt)
    final_content = review_result.output
    print(f"Final: {final_content}")
    
    return final_content

if __name__ == "__main__":
    import asyncio
    
    user_input = "Write a short marketing message for a new AI-powered productivity app"
    print(f"Input: {user_input}\n")
    result = asyncio.run(chain_models(user_input))