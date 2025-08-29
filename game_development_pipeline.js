const { BedrockRuntimeClient, ConverseCommand } = require('@aws-sdk/client-bedrock-runtime');
require('dotenv').config();

const region = process.env.AWS_DEFAULT_REGION || 'us-east-1';
console.log(`Using region: ${region}`);

const client = new BedrockRuntimeClient({ region });

class BedrockAgent {
    constructor(modelId, systemPrompt = '') {
        this.modelId = modelId;
        this.systemPrompt = systemPrompt;
    }

    async run(userInput) {
        const messages = [{ role: 'user', content: [{ text: userInput }] }];
        const params = { modelId: this.modelId, messages };
        
        if (this.systemPrompt) {
            params.system = [{ text: this.systemPrompt }];
        }

        try {
            const response = await client.send(new ConverseCommand(params));
            return response.output.message.content[0].text;
        } catch (error) {
            return `Error: ${error.message}`;
        }
    }
}

const architectAgent = new BedrockAgent(
    'anthropic.claude-3-sonnet-20240229-v1:0',
    'You are a software architect. Create detailed technical specifications and architecture for software projects.'
);

const developerAgent = new BedrockAgent(
    'anthropic.claude-3-haiku-20240307-v1:0',
    'You are a Python developer. Write clean, functional code based on specifications.'
);

const testerAgent = new BedrockAgent(
    'amazon.nova-lite-v1:0',
    'You are a QA engineer. Create comprehensive tests for code to ensure it works correctly.'
);

const documenterAgent = new BedrockAgent('amazon.titan-text-express-v1');

async function gameDevelopmentPipeline() {
    const projectRequest = "Create a simple Tic-Tac-Toe (X&Os) game in Python";
    
    console.log("[1/4] Creating architecture with Claude Sonnet...");
    const architecture = await architectAgent.run(`Create a detailed architecture and rulebook for: ${projectRequest}`);
    console.log(`\n=== ARCHITECTURE ===\n${architecture}\n`);
    
    console.log("[2/4] Writing code with Claude Haiku...");
    const code = await developerAgent.run(`Based on this architecture, write complete Python code:\n${architecture}`);
    console.log(`\n=== CODE ===\n${code}\n`);
    
    console.log("[3/4] Creating tests with Nova Lite...");
    const tests = await testerAgent.run(`Create comprehensive unit tests for this code:\n${code}`);
    console.log(`\n=== TESTS ===\n${tests}\n`);
    
    console.log("[4/4] Creating documentation with Titan Express...");
    const docPrompt = `Act as a technical writer. Create comprehensive documentation for this Tic-Tac-Toe game. Include setup instructions, usage guide, architecture overview, testing approach, and API reference.\n\nArchitecture:\n${architecture}\n\nCode Implementation:\n${code}\n\nTest Suite:\n${tests}\n\nCreate documentation that explains the architecture decisions, how to use the application, and how it was tested.`;
    const documentation = await documenterAgent.run(docPrompt);
    console.log(`\n=== DOCUMENTATION ===\n${documentation}\n`);
    
    return { architecture, code, tests, documentation };
}

async function main() {
    console.log("Starting 4-Agent Game Development Pipeline...\n");
    await gameDevelopmentPipeline();
    
    console.log("\n" + "=".repeat(50));
    console.log("PIPELINE COMPLETE - 4 AGENTS COLLABORATED");
    console.log("=".repeat(50));
    console.log("[DONE] Architecture designed by Claude Sonnet");
    console.log("[DONE] Code written by Claude Haiku");
    console.log("[DONE] Tests created by Nova Lite");
    console.log("[DONE] Documentation written by Titan Express");
    console.log("=".repeat(50));
}

main().catch(console.error);