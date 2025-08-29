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

// Get model configurations from environment variables with fallback defaults
const architectureModel = process.env.ARCHITECTURE_MODEL || 'anthropic.claude-3-sonnet-20240229-v1:0';
const developmentModel = process.env.DEVELOPMENT_MODEL || 'anthropic.claude-3-haiku-20240307-v1:0';
const testingModel = process.env.TESTING_MODEL || 'amazon.nova-lite-v1:0';
const documentationModel = process.env.DOCUMENTATION_MODEL || 'amazon.titan-text-express-v1';

console.log('Model Configuration:');
console.log(`  Architecture: ${architectureModel}`);
console.log(`  Development:  ${developmentModel}`);
console.log(`  Testing:      ${testingModel}`);
console.log(`  Documentation: ${documentationModel}`);
console.log();

const architectAgent = new BedrockAgent(architectureModel,
    'You are a software architect. Create detailed technical specifications and architecture for software projects.'
);

const developerAgent = new BedrockAgent(developmentModel,
    'You are a Python developer. Write clean, functional code based on specifications.'
);

const testerAgent = new BedrockAgent(testingModel,
    'You are a QA engineer. Create comprehensive tests for code to ensure it works correctly.'
);

const documenterAgent = new BedrockAgent(documentationModel);

function printTimingSummary(timings, totalTime) {
    console.log('\n📊 TIMING SUMMARY');
    console.log('-'.repeat(50));
    
    timings.forEach(([stageName, duration, success]) => {
        const status = success ? '✅' : '❌';
        console.log(`${stageName.padEnd(35)}: ${duration.toFixed(2).padStart(8)} sec ${status}`);
    });
    
    console.log('-'.repeat(50));
    console.log(`Total Pipeline Time: ${totalTime.toFixed(2)} seconds`);
    console.log('='.repeat(50));
}

async function gameDevelopmentPipeline() {
    const projectRequest = "Create a simple Tic-Tac-Toe (X&Os) game in Python";
    const pipelineStart = Date.now();
    const timings = [];
    
    try {
        // Stage 1: Architecture
        console.log('[1/4] Creating architecture with Claude Sonnet...');
        const stage1Start = Date.now();
        const architecture = await architectAgent.run(`Create a detailed architecture and rulebook for: ${projectRequest}`);
        const stage1End = Date.now();
        const stage1Duration = (stage1End - stage1Start) / 1000;
        
        if (architecture.startsWith('Error:')) {
            timings.push(['Architecture (Claude Sonnet)', stage1Duration, false]);
            console.error(`\n❌ PIPELINE FAILED at Stage 1 (Architecture)`);
            console.error(`Error details: ${architecture}`);
            console.error(`Time spent: ${stage1Duration.toFixed(2)} seconds`);
            console.error('\nPossible causes:');
            console.error('1. Network connectivity issue');
            console.error('2. Invalid AWS credentials');
            console.error(`3. Model not available in region ${region}`);
            console.error('4. Insufficient permissions for Claude Sonnet model');
            printTimingSummary(timings, stage1Duration);
            return false;
        }
        
        timings.push(['Architecture (Claude Sonnet)', stage1Duration, true]);
        console.log(`\n=== ARCHITECTURE ===\n${architecture}\n`);
        console.log(`✅ Stage 1 completed successfully in ${stage1Duration.toFixed(2)} seconds\n`);
        
        // Stage 2: Development
        console.log('[2/4] Writing code with Claude Haiku...');
        const stage2Start = Date.now();
        const code = await developerAgent.run(`Based on this architecture, write complete Python code:\n${architecture}`);
        const stage2End = Date.now();
        const stage2Duration = (stage2End - stage2Start) / 1000;
        
        if (code.startsWith('Error:')) {
            timings.push(['Development (Claude Haiku)', stage2Duration, false]);
            console.error(`\n❌ PIPELINE FAILED at Stage 2 (Development)`);
            console.error(`Error details: ${code}`);
            console.error(`Time spent: ${stage2Duration.toFixed(2)} seconds`);
            printTimingSummary(timings, (stage2End - pipelineStart) / 1000);
            return false;
        }
        
        timings.push(['Development (Claude Haiku)', stage2Duration, true]);
        console.log(`\n=== CODE ===\n${code}\n`);
        console.log(`✅ Stage 2 completed successfully in ${stage2Duration.toFixed(2)} seconds\n`);
        
        // Stage 3: Testing
        console.log('[3/4] Creating tests with Nova Lite...');
        const stage3Start = Date.now();
        const tests = await testerAgent.run(`Create comprehensive unit tests for this code:\n${code}`);
        const stage3End = Date.now();
        const stage3Duration = (stage3End - stage3Start) / 1000;
        
        if (tests.startsWith('Error:')) {
            timings.push(['Testing (Nova Lite)', stage3Duration, false]);
            console.error(`\n❌ PIPELINE FAILED at Stage 3 (Testing)`);
            console.error(`Error details: ${tests}`);
            console.error(`Time spent: ${stage3Duration.toFixed(2)} seconds`);
            printTimingSummary(timings, (stage3End - pipelineStart) / 1000);
            return false;
        }
        
        timings.push(['Testing (Nova Lite)', stage3Duration, true]);
        console.log(`\n=== TESTS ===\n${tests}\n`);
        console.log(`✅ Stage 3 completed successfully in ${stage3Duration.toFixed(2)} seconds\n`);
        
        // Stage 4: Documentation
        console.log('[4/4] Creating documentation with Titan Express...');
        const stage4Start = Date.now();
        const docPrompt = `Act as a technical writer. Create comprehensive documentation for this Tic-Tac-Toe game. Include setup instructions, usage guide, architecture overview, testing approach, and API reference.\n\nArchitecture:\n${architecture}\n\nCode Implementation:\n${code}\n\nTest Suite:\n${tests}\n\nCreate documentation that explains the architecture decisions, how to use the application, and how it was tested.`;
        const documentation = await documenterAgent.run(docPrompt);
        const stage4End = Date.now();
        const stage4Duration = (stage4End - stage4Start) / 1000;
        
        if (documentation.startsWith('Error:')) {
            timings.push(['Documentation (Titan Express)', stage4Duration, false]);
            console.error(`\n❌ PIPELINE FAILED at Stage 4 (Documentation)`);
            console.error(`Error details: ${documentation}`);
            console.error(`Time spent: ${stage4Duration.toFixed(2)} seconds`);
            printTimingSummary(timings, (stage4End - pipelineStart) / 1000);
            return false;
        }
        
        timings.push(['Documentation (Titan Express)', stage4Duration, true]);
        console.log(`\n=== DOCUMENTATION ===\n${documentation}\n`);
        console.log(`✅ Stage 4 completed successfully in ${stage4Duration.toFixed(2)} seconds\n`);
        
        // Calculate total time
        const pipelineEnd = Date.now();
        const totalDuration = (pipelineEnd - pipelineStart) / 1000;
        
        console.log('\n' + '='.repeat(50));
        console.log('✅ PIPELINE COMPLETE - 4 AGENTS COLLABORATED');
        console.log('='.repeat(50));
        console.log('[DONE] Architecture designed by Claude Sonnet');
        console.log('[DONE] Code written by Claude Haiku');
        console.log('[DONE] Tests created by Nova Lite');
        console.log('[DONE] Documentation written by Titan Express');
        console.log('='.repeat(50));
        
        // Print timing summary
        printTimingSummary(timings, totalDuration);
        
        return {
            architecture,
            code,
            tests,
            documentation,
            timings,
            totalTime: totalDuration
        };
        
    } catch (error) {
        const pipelineEnd = Date.now();
        const totalDuration = (pipelineEnd - pipelineStart) / 1000;
        
        console.error(`\n❌ PIPELINE FAILED: ${error.message}`);
        console.error(`Time spent before failure: ${totalDuration.toFixed(2)} seconds`);
        
        if (timings.length > 0) {
            printTimingSummary(timings, totalDuration);
        }
        
        throw error;
    }
}

async function main() {
    console.log('AWS Bedrock 4-Agent Pipeline (Node.js)');
    console.log('======================================');
    console.log('Starting 4-Agent Game Development Pipeline...\n');
    
    try {
        const result = await gameDevelopmentPipeline();
        console.log('\n🚀 Node.js Implementation Complete!');
        return result;
    } catch (error) {
        console.error(`\n❌ Pipeline failed with error: ${error.message}`);
        process.exit(1);
    }
}

if (require.main === module) {
    main();
}

module.exports = { gameDevelopmentPipeline, BedrockAgent };