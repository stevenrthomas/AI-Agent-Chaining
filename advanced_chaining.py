import os
from dotenv import load_dotenv
from pydantic_ai import Agent, RunContext
from pydantic_ai.models.bedrock import BedrockModel
from pydantic import BaseModel

load_dotenv()

class ContentPipeline(BaseModel):
    topic: str
    draft: str = ""
    reviewed: str = ""
    final: str = ""

# Multi-model pipeline
analyzer = Agent(BedrockModel('anthropic.claude-3-haiku-20240307-v1:0'))
writer = Agent(BedrockModel('amazon.nova-lite-v1:0'))
editor = Agent(BedrockModel('anthropic.claude-3-haiku-20240307-v1:0'))

async def content_pipeline(topic: str):
    """3-stage model chain: analyze -> write -> edit"""
    
    pipeline = ContentPipeline(topic=topic)
    
    # Stage 1: Analyze topic
    analysis = await analyzer.run(f"Analyze this topic and suggest key points: {topic}")
    print(f"📊 Analysis: {analysis.data}")
    
    # Stage 2: Write content
    draft = await writer.run(f"Write content about: {topic}. Key points: {analysis.data}")
    pipeline.draft = draft.data
    print(f"✍️ Draft: {pipeline.draft}")
    
    # Stage 3: Edit content
    final = await editor.run(f"Edit and polish this content: {pipeline.draft}")
    pipeline.final = final.data
    print(f"✅ Final: {pipeline.final}")
    
    return pipeline

if __name__ == "__main__":
    import asyncio
    result = asyncio.run(content_pipeline("Benefits of cloud computing for small businesses"))