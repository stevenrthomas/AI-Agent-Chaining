#include <aws/core/Aws.h>
#include <aws/core/auth/AWSCredentials.h>
#include <aws/core/client/ClientConfiguration.h>
#include <aws/bedrock-runtime/BedrockRuntimeClient.h>
#include <aws/bedrock-runtime/model/ConverseRequest.h>
#include <aws/bedrock-runtime/model/ConverseResult.h>
#include <aws/bedrock-runtime/model/Message.h>
#include <aws/bedrock-runtime/model/ContentBlock.h>
#include <iostream>
#include <string>
#include <vector>
#include <fstream>
#include <sstream>
#include <map>
#include <memory>
#include <cstdlib>
#include <chrono>
#include <iomanip>
#ifdef _WIN32
#define WIN32_LEAN_AND_MEAN
#define NOMINMAX
#include <windows.h>
#include <io.h>
#include <fcntl.h>
#ifdef GetMessage
#undef GetMessage
#endif
#endif

// Function to set Windows console to UTF-8 mode
void setupConsoleEncoding() {
#ifdef _WIN32
    // Set console code page to UTF-8
    SetConsoleOutputCP(CP_UTF8);
    SetConsoleCP(CP_UTF8);
    
    // Enable buffering to prevent byte-by-byte writes
    setvbuf(stdout, nullptr, _IOFBF, 1000);
#endif
}

// Function to clean up text by removing problematic characters
std::string cleanText(const std::string& input) {
    std::string output;
    for (char c : input) {
        // Only keep printable ASCII characters and newlines/tabs
        if ((c >= 32 && c <= 126) || c == '\n' || c == '\t' || c == '\r') {
            output += c;
        } else if (c == '\u2019' || c == '\u2018') {
            // Replace smart quotes with regular quotes
            output += '\'';
        } else if (c == '\u201C' || c == '\u201D') {
            // Replace smart double quotes with regular quotes
            output += '"';
        } else if (c == '\u2014') {
            // Replace em dash with regular dash
            output += '-';
        }
    }
    return output;
}

// Function to load environment variables from .env file
std::map<std::string, std::string> loadEnvFile(const std::string& filename = ".env") {
    std::map<std::string, std::string> envVars;
    
    // Try multiple locations for .env file
    std::vector<std::string> paths = {
        filename,                                    // Current directory
        "../../" + filename,                         // Two directories up (from build/Release)
        "../../../" + filename,                      // Three directories up
        "C:/Archive/Projects/AWS Bedrock 1.0/.env"  // Absolute path
    };
    
    std::ifstream file;
    std::string usedPath;
    
    for (const auto& path : paths) {
        file.open(path);
        if (file.is_open()) {
            usedPath = path;
            std::cout << "Found .env file at: " << path << std::endl;
            break;
        }
    }
    
    if (!file.is_open()) {
        std::cerr << "Error: Could not find .env file in any of these locations:\n";
        for (const auto& path : paths) {
            std::cerr << "  - " << path << std::endl;
        }
        std::cerr << "\nPlease ensure .env file exists with AWS credentials:\n";
        std::cerr << "  AWS_ACCESS_KEY_ID=your_key\n";
        std::cerr << "  AWS_SECRET_ACCESS_KEY=your_secret\n";
        std::cerr << "  AWS_DEFAULT_REGION=us-east-1\n";
        return envVars;
    }
    
    std::string line;
    while (std::getline(file, line)) {
        // Skip empty lines and comments
        if (line.empty() || line[0] == '#') continue;
        
        size_t pos = line.find('=');
        if (pos != std::string::npos) {
            std::string key = line.substr(0, pos);
            std::string value = line.substr(pos + 1);
            
            // Remove quotes if present
            if (!value.empty() && value.front() == '"' && value.back() == '"') {
                value = value.substr(1, value.length() - 2);
            }
            
            envVars[key] = value;
            // Also set as environment variable for AWS SDK
            #ifdef _WIN32
                _putenv_s(key.c_str(), value.c_str());
            #else
                setenv(key.c_str(), value.c_str(), 1);
            #endif
        }
    }
    
    file.close();
    return envVars;
}

class BedrockAgent {
private:
    std::unique_ptr<Aws::BedrockRuntime::BedrockRuntimeClient> client;
    std::string modelId;
    std::string systemPrompt;

public:
    BedrockAgent(const std::string& model, const std::string& prompt = "", 
                 const Aws::Client::ClientConfiguration& config = Aws::Client::ClientConfiguration()) 
        : modelId(model), systemPrompt(prompt) {
        client = std::make_unique<Aws::BedrockRuntime::BedrockRuntimeClient>(config);
    }

    std::string run(const std::string& userInput) {
        Aws::BedrockRuntime::Model::ConverseRequest request;
        request.SetModelId(modelId);

        // Create message
        Aws::BedrockRuntime::Model::Message message;
        message.SetRole(Aws::BedrockRuntime::Model::ConversationRole::user);
        
        Aws::BedrockRuntime::Model::ContentBlock contentBlock;
        contentBlock.SetText(userInput);
        message.AddContent(contentBlock);
        
        request.AddMessages(message);

        // Add system prompt if provided
        if (!systemPrompt.empty()) {
            Aws::BedrockRuntime::Model::SystemContentBlock systemBlock;
            systemBlock.SetText(systemPrompt);
            request.AddSystem(systemBlock);
        }

        auto outcome = client->Converse(request);
        
        if (outcome.IsSuccess()) {
            const auto& result = outcome.GetResult();
            const auto& output = result.GetOutput();
            const auto& message = output.GetMessage();
            const auto& content = message.GetContent();
            
            if (!content.empty()) {
                // Clean the text to remove encoding issues
                std::string text = content[0].GetText();
                return cleanText(text);
            }
        }
        
        return "Error: " + outcome.GetError().GetMessage();
    }
};

// Structure to store timing information
struct StageTimings {
    std::string stageName;
    double durationSeconds;
    bool success;
};

class GameDevelopmentPipeline {
private:
    BedrockAgent architectAgent;
    BedrockAgent developerAgent;
    BedrockAgent testerAgent;
    BedrockAgent documenterAgent;
    std::vector<StageTimings> timings;

public:
    GameDevelopmentPipeline(const Aws::Client::ClientConfiguration& config) 
        : architectAgent("anthropic.claude-3-sonnet-20240229-v1:0", 
                        "You are a software architect. Create detailed technical specifications and architecture for software projects.", config),
          developerAgent("anthropic.claude-3-haiku-20240307-v1:0",
                        "You are a Python developer. Write clean, functional code based on specifications.", config),
          testerAgent("amazon.nova-lite-v1:0",
                     "You are a QA engineer. Create comprehensive tests for code to ensure it works correctly.", config),
          documenterAgent("amazon.titan-text-express-v1", "", config) {}

    bool execute() {
        std::string projectRequest = "Create a simple Tic-Tac-Toe (X&Os) game in Python";
        auto pipelineStart = std::chrono::high_resolution_clock::now();
        
        // Stage 1: Architecture
        std::cout << "[1/4] Creating architecture with Claude Sonnet...\n";
        auto stage1Start = std::chrono::high_resolution_clock::now();
        std::string architecture = architectAgent.run("Create a detailed architecture and rulebook for: " + projectRequest);
        auto stage1End = std::chrono::high_resolution_clock::now();
        double stage1Duration = std::chrono::duration<double>(stage1End - stage1Start).count();
        
        // Check for error in Stage 1
        if (architecture.find("Error:") == 0) {
            timings.push_back({"Architecture (Claude Sonnet)", stage1Duration, false});
            std::cerr << "\n❌ PIPELINE FAILED at Stage 1 (Architecture)\n";
            std::cerr << "Error details: " << architecture << "\n";
            std::cerr << "Time spent: " << std::fixed << std::setprecision(2) << stage1Duration << " seconds\n";
            std::cerr << "\nPossible causes:\n";
            std::cerr << "1. Network connectivity issue\n";
            std::cerr << "2. Invalid AWS credentials\n";
            std::cerr << "3. Model not available in region " << std::getenv("AWS_DEFAULT_REGION") << "\n";
            std::cerr << "4. Insufficient permissions for Claude Sonnet model\n";
            printTimingSummary();
            return false;
        }
        timings.push_back({"Architecture (Claude Sonnet)", stage1Duration, true});
        std::cout << "\n=== ARCHITECTURE ===\n" << architecture << "\n\n";
        std::cout << "✅ Stage 1 completed successfully in " << std::fixed << std::setprecision(2) << stage1Duration << " seconds\n\n";
        
        // Stage 2: Development
        std::cout << "[2/4] Writing code with Claude Haiku...\n";
        auto stage2Start = std::chrono::high_resolution_clock::now();
        std::string code = developerAgent.run("Based on this architecture, write complete Python code:\n" + architecture);
        auto stage2End = std::chrono::high_resolution_clock::now();
        double stage2Duration = std::chrono::duration<double>(stage2End - stage2Start).count();
        
        // Check for error in Stage 2
        if (code.find("Error:") == 0) {
            timings.push_back({"Development (Claude Haiku)", stage2Duration, false});
            std::cerr << "\n❌ PIPELINE FAILED at Stage 2 (Development)\n";
            std::cerr << "Error details: " << code << "\n";
            std::cerr << "Time spent: " << std::fixed << std::setprecision(2) << stage2Duration << " seconds\n";
            printTimingSummary();
            return false;
        }
        timings.push_back({"Development (Claude Haiku)", stage2Duration, true});
        std::cout << "\n=== CODE ===\n" << code << "\n\n";
        std::cout << "✅ Stage 2 completed successfully in " << std::fixed << std::setprecision(2) << stage2Duration << " seconds\n\n";
        
        // Stage 3: Testing
        std::cout << "[3/4] Creating tests with Nova Lite...\n";
        auto stage3Start = std::chrono::high_resolution_clock::now();
        std::string tests = testerAgent.run("Create comprehensive unit tests for this code:\n" + code);
        auto stage3End = std::chrono::high_resolution_clock::now();
        double stage3Duration = std::chrono::duration<double>(stage3End - stage3Start).count();
        
        // Check for error in Stage 3
        if (tests.find("Error:") == 0) {
            timings.push_back({"Testing (Nova Lite)", stage3Duration, false});
            std::cerr << "\n❌ PIPELINE FAILED at Stage 3 (Testing)\n";
            std::cerr << "Error details: " << tests << "\n";
            std::cerr << "Time spent: " << std::fixed << std::setprecision(2) << stage3Duration << " seconds\n";
            printTimingSummary();
            return false;
        }
        timings.push_back({"Testing (Nova Lite)", stage3Duration, true});
        std::cout << "\n=== TESTS ===\n" << tests << "\n\n";
        std::cout << "✅ Stage 3 completed successfully in " << std::fixed << std::setprecision(2) << stage3Duration << " seconds\n\n";
        
        // Stage 4: Documentation
        std::cout << "[4/4] Creating documentation with Titan Express...\n";
        auto stage4Start = std::chrono::high_resolution_clock::now();
        std::string docPrompt = "Act as a technical writer. Create comprehensive documentation for this Tic-Tac-Toe game. Include setup instructions, usage guide, architecture overview, testing approach, and API reference.\n\nArchitecture:\n" + architecture + "\n\nCode Implementation:\n" + code + "\n\nTest Suite:\n" + tests + "\n\nCreate documentation that explains the architecture decisions, how to use the application, and how it was tested.";
        std::string documentation = documenterAgent.run(docPrompt);
        auto stage4End = std::chrono::high_resolution_clock::now();
        double stage4Duration = std::chrono::duration<double>(stage4End - stage4Start).count();
        
        // Check for error in Stage 4
        if (documentation.find("Error:") == 0) {
            timings.push_back({"Documentation (Titan Express)", stage4Duration, false});
            std::cerr << "\n❌ PIPELINE FAILED at Stage 4 (Documentation)\n";
            std::cerr << "Error details: " << documentation << "\n";
            std::cerr << "Time spent: " << std::fixed << std::setprecision(2) << stage4Duration << " seconds\n";
            printTimingSummary();
            return false;
        }
        timings.push_back({"Documentation (Titan Express)", stage4Duration, true});
        std::cout << "\n=== DOCUMENTATION ===\n" << documentation << "\n\n";
        std::cout << "✅ Stage 4 completed successfully in " << std::fixed << std::setprecision(2) << stage4Duration << " seconds\n\n";
        
        // Calculate total time
        auto pipelineEnd = std::chrono::high_resolution_clock::now();
        double totalDuration = std::chrono::duration<double>(pipelineEnd - pipelineStart).count();
        
        std::cout << "\n" << std::string(50, '=') << "\n";
        std::cout << "✅ PIPELINE COMPLETE - 4 AGENTS COLLABORATED\n";
        std::cout << std::string(50, '=') << "\n";
        std::cout << "[DONE] Architecture designed by Claude Sonnet\n";
        std::cout << "[DONE] Code written by Claude Haiku\n";
        std::cout << "[DONE] Tests created by Nova Lite\n";
        std::cout << "[DONE] Documentation written by Titan Express\n";
        std::cout << std::string(50, '=') << "\n";
        
        // Print timing summary
        printTimingSummary();
        std::cout << "Total Pipeline Time: " << std::fixed << std::setprecision(2) << totalDuration << " seconds\n";
        std::cout << std::string(50, '=') << "\n";
        
        return true;
    }
    
    void printTimingSummary() {
        std::cout << "\n📊 TIMING SUMMARY\n";
        std::cout << std::string(50, '-') << "\n";
        double totalTime = 0.0;
        
        for (const auto& timing : timings) {
            std::cout << std::left << std::setw(35) << timing.stageName << ": ";
            std::cout << std::right << std::setw(8) << std::fixed << std::setprecision(2) << timing.durationSeconds << " sec ";
            if (timing.success) {
                std::cout << "✅";
            } else {
                std::cout << "❌";
            }
            std::cout << "\n";
            totalTime += timing.durationSeconds;
        }
        
        std::cout << std::string(50, '-') << "\n";
    }
};

int main() {
    // Set up console encoding for Windows
    setupConsoleEncoding();
    
    // Load .env file
    std::cout << "AWS Bedrock 4-Agent Pipeline\n";
    std::cout << "=============================\n";
    std::cout << "Loading .env file...\n";
    auto envVars = loadEnvFile();
    
    // Check if credentials were loaded
    if (envVars.empty()) {
        std::cerr << "\nFailed to load .env file. Checking environment variables...\n";
        const char* accessKey = std::getenv("AWS_ACCESS_KEY_ID");
        const char* secretKey = std::getenv("AWS_SECRET_ACCESS_KEY");
        
        if (!accessKey || !secretKey) {
            std::cerr << "\nError: No AWS credentials found!\n";
            std::cerr << "Please either:\n";
            std::cerr << "1. Create a .env file in the project root with AWS credentials, or\n";
            std::cerr << "2. Set AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY environment variables\n";
            return 1;
        }
    }
    
    // Initialize AWS SDK
    Aws::SDKOptions options;
    Aws::InitAPI(options);
    
    {
        // Configure AWS client
        Aws::Client::ClientConfiguration clientConfig;
        
        // Get region from environment or use default
        const char* region = std::getenv("AWS_DEFAULT_REGION");
        if (region) {
            clientConfig.region = region;
            std::cout << "Using AWS region: " << region << "\n";
        } else {
            clientConfig.region = "us-east-1";
            std::cout << "Using default AWS region: us-east-1\n";
        }
        
        // Set timeouts (in milliseconds)
        clientConfig.requestTimeoutMs = 30000;  // 30 seconds timeout
        clientConfig.connectTimeoutMs = 5000;   // 5 seconds connection timeout
        
        // Check if credentials are set
        const char* accessKey = std::getenv("AWS_ACCESS_KEY_ID");
        const char* secretKey = std::getenv("AWS_SECRET_ACCESS_KEY");
        
        if (accessKey && secretKey) {
            std::cout << "AWS credentials loaded from environment\n";
        } else {
            std::cerr << "Warning: AWS credentials not found in environment\n";
        }
        
        // Test connectivity with a simpler model first
        std::cout << "\nTesting AWS Bedrock connectivity...\n";
        BedrockAgent testAgent("amazon.titan-text-express-v1", "", clientConfig);
        std::string testResult = testAgent.run("Say 'Hello World'");
        
        if (testResult.find("Error:") == 0) {
            std::cerr << "\n❌ Failed to connect to AWS Bedrock\n";
            std::cerr << "Error: " << testResult << "\n";
            std::cerr << "\nPlease check:\n";
            std::cerr << "1. AWS credentials are valid\n";
            std::cerr << "2. You have access to Bedrock models in region " << clientConfig.region << "\n";
            std::cerr << "3. Network connectivity to AWS\n";
            Aws::ShutdownAPI(options);
            return 1;
        }
        
        std::cout << "✅ Successfully connected to AWS Bedrock\n";
        std::cout << "Test response: " << testResult.substr(0, 50) << "...\n";
        
        std::cout << "\nStarting 4-Agent Game Development Pipeline in C++...\n\n";
        GameDevelopmentPipeline pipeline(clientConfig);
        bool success = pipeline.execute();
        
        if (!success) {
            std::cerr << "\n⚠️  Pipeline execution failed. Please check the error messages above.\n";
            Aws::ShutdownAPI(options);
            return 1;
        }
    }
    
    Aws::ShutdownAPI(options);
    return 0;
}