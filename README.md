# AI Agent Chaining with AWS Bedrock

This project demonstrates AI agent chaining using AWS Bedrock models with both Python and C++ implementations. It showcases how multiple AI models can work together in a pipeline to accomplish complex tasks.

## Features

- **Multi-Model Chaining**: Chain different AWS Bedrock models (Claude, Nova, Titan) for specialized tasks
- **Python Implementation**: Using PydanticAI for easy model orchestration
- **C++ Implementation**: Native AWS SDK integration for high-performance scenarios
- **Game Development Pipeline**: 4-stage pipeline (Architect → Developer → Tester → Documenter)
- **Content Pipeline**: 3-stage content creation and editing workflow

## Project Structure

```
├── advanced_chaining.py          # Advanced multi-model content pipeline
├── bedrock_models.py             # List available Bedrock models
├── model_chaining.py             # Basic 2-model chaining example
├── simple_chaining_demo.py       # Simulation without API calls
├── game_development_pipeline.py  # 4-agent game development workflow
├── game_pipeline.cpp             # C++ implementation of the pipeline
├── CMakeLists.txt                # CMake build configuration
├── requirements.txt              # Python dependencies
└── .env.example                  # Environment variables template
```

## Prerequisites

### AWS Setup
1. AWS account with Bedrock access
2. Request access to required models in AWS Bedrock console:
   - Claude 3 Haiku
   - Claude 3 Sonnet
   - Amazon Nova Lite
   - Amazon Titan Text Express

### Python Setup
```bash
pip install -r requirements.txt
```

### C++ Setup (Optional)
- CMake 3.16+
- C++17 compatible compiler
- vcpkg for AWS SDK dependencies

## Configuration

1. Copy `.env.example` to `.env`
2. Add your AWS credentials:
```
AWS_ACCESS_KEY_ID=your_access_key
AWS_SECRET_ACCESS_KEY=your_secret_key
AWS_DEFAULT_REGION=us-east-1
```

## Usage

### Python Examples

**Basic Model Chaining:**
```bash
python model_chaining.py
```

**Advanced Content Pipeline:**
```bash
python advanced_chaining.py
```

**Game Development Pipeline:**
```bash
python game_development_pipeline.py
```

**Simulation Mode (No API calls):**
```bash
python simple_chaining_demo.py
```

### C++ Implementation

```bash
mkdir build && cd build
cmake ..
cmake --build . --config Release
./Release/game_pipeline.exe
```

## Model Pipeline Examples

### Content Creation Pipeline
1. **Analyzer** (Claude Haiku) - Analyzes topic and suggests key points
2. **Writer** (Nova Lite) - Creates content based on analysis
3. **Editor** (Claude Haiku) - Polishes and refines the content

### Game Development Pipeline
1. **Architect** (Claude Sonnet) - Creates technical specifications
2. **Developer** (Claude Haiku) - Writes code based on specs
3. **Tester** (Nova Lite) - Creates comprehensive tests
4. **Documenter** (Titan Express) - Generates documentation

## Security Notes

- Never commit `.env` files containing credentials
- Use IAM roles when running on AWS infrastructure
- Rotate access keys regularly
- Follow AWS security best practices

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly
5. Submit a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details.