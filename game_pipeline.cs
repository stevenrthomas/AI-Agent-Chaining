using Amazon.BedrockRuntime;
using Amazon.BedrockRuntime.Model;
using System.Text.Json;
using dotenv.net;

namespace AiAgentChaining
{
    public class BedrockAgent
    {
        private readonly AmazonBedrockRuntimeClient _client;
        private readonly string _modelId;
        private readonly string _systemPrompt;

        public BedrockAgent(AmazonBedrockRuntimeClient client, string modelId, string systemPrompt = "")
        {
            _client = client;
            _modelId = modelId;
            _systemPrompt = systemPrompt;
        }

        public async Task<string> RunAsync(string userInput)
        {
            try
            {
                var messages = new List<Message>
                {
                    new Message
                    {
                        Role = ConversationRole.User,
                        Content = new List<ContentBlock>
                        {
                            new ContentBlock { Text = userInput }
                        }
                    }
                };

                var request = new ConverseRequest
                {
                    ModelId = _modelId,
                    Messages = messages
                };

                if (!string.IsNullOrEmpty(_systemPrompt))
                {
                    request.System = new List<SystemContentBlock>
                    {
                        new SystemContentBlock { Text = _systemPrompt }
                    };
                }

                var response = await _client.ConverseAsync(request);

                if (response.Output?.Message?.Content?.Count > 0)
                {
                    var content = response.Output.Message.Content[0];
                    if (!string.IsNullOrEmpty(content.Text))
                    {
                        return content.Text;
                    }
                }

                throw new InvalidOperationException("Empty response from model");
            }
            catch (Exception ex)
            {
                throw new InvalidOperationException($"Bedrock API error: {ex.Message}", ex);
            }
        }
    }

    public class StageResult
    {
        public string Name { get; set; } = string.Empty;
        public TimeSpan Duration { get; set; }
        public bool Success { get; set; }

        public StageResult(string name, TimeSpan duration, bool success)
        {
            Name = name;
            Duration = duration;
            Success = success;
        }
    }

    public class GamePipeline
    {
        private static void PrintTimingSummary(List<StageResult> timings, TimeSpan totalTime)
        {
            Console.WriteLine("\n📊 TIMING SUMMARY");
            Console.WriteLine(new string('-', 50));

            foreach (var timing in timings)
            {
                string status = timing.Success ? "✅" : "❌";
                Console.WriteLine($"{timing.Name,-35}: {timing.Duration.TotalSeconds,8:F2} sec {status}");
            }

            Console.WriteLine(new string('-', 50));
            Console.WriteLine($"Total Pipeline Time: {totalTime.TotalSeconds:F2} seconds");
            Console.WriteLine(new string('=', 50));
        }

        private static async Task GameDevelopmentPipelineAsync(AmazonBedrockRuntimeClient client)
        {
            string projectRequest = "Create a simple Tic-Tac-Toe (X&Os) game in Python";
            var pipelineStart = DateTime.UtcNow;
            var timings = new List<StageResult>();

            // Initialize agents
            var architectAgent = new BedrockAgent(client,
                "anthropic.claude-3-sonnet-20240229-v1:0",
                "You are a software architect. Create detailed technical specifications and architecture for software projects.");
            var developerAgent = new BedrockAgent(client,
                "anthropic.claude-3-haiku-20240307-v1:0",
                "You are a Python developer. Write clean, functional code based on specifications.");
            var testerAgent = new BedrockAgent(client,
                "amazon.nova-lite-v1:0",
                "You are a QA engineer. Create comprehensive tests for code to ensure it works correctly.");
            var documenterAgent = new BedrockAgent(client,
                "amazon.titan-text-express-v1");

            try
            {
                // Stage 1: Architecture
                Console.WriteLine("[1/4] Creating architecture with Claude Sonnet...");
                var stage1Start = DateTime.UtcNow;
                string architecture = await architectAgent.RunAsync(
                    $"Create a detailed architecture and rulebook for: {projectRequest}");
                var stage1Duration = DateTime.UtcNow - stage1Start;

                timings.Add(new StageResult("Architecture (Claude Sonnet)", stage1Duration, true));
                Console.WriteLine($"\n=== ARCHITECTURE ===\n{architecture}\n");
                Console.WriteLine($"✅ Stage 1 completed successfully in {stage1Duration.TotalSeconds:F2} seconds\n");

                // Stage 2: Development
                Console.WriteLine("[2/4] Writing code with Claude Haiku...");
                var stage2Start = DateTime.UtcNow;
                string code = await developerAgent.RunAsync(
                    $"Based on this architecture, write complete Python code:\n{architecture}");
                var stage2Duration = DateTime.UtcNow - stage2Start;

                timings.Add(new StageResult("Development (Claude Haiku)", stage2Duration, true));
                Console.WriteLine($"\n=== CODE ===\n{code}\n");
                Console.WriteLine($"✅ Stage 2 completed successfully in {stage2Duration.TotalSeconds:F2} seconds\n");

                // Stage 3: Testing
                Console.WriteLine("[3/4] Creating tests with Nova Lite...");
                var stage3Start = DateTime.UtcNow;
                string tests = await testerAgent.RunAsync(
                    $"Create comprehensive unit tests for this code:\n{code}");
                var stage3Duration = DateTime.UtcNow - stage3Start;

                timings.Add(new StageResult("Testing (Nova Lite)", stage3Duration, true));
                Console.WriteLine($"\n=== TESTS ===\n{tests}\n");
                Console.WriteLine($"✅ Stage 3 completed successfully in {stage3Duration.TotalSeconds:F2} seconds\n");

                // Stage 4: Documentation
                Console.WriteLine("[4/4] Creating documentation with Titan Express...");
                var stage4Start = DateTime.UtcNow;
                string docPrompt = $"Act as a technical writer. Create comprehensive documentation for this Tic-Tac-Toe game. " +
                    $"Include setup instructions, usage guide, architecture overview, testing approach, and API reference.\n\n" +
                    $"Architecture:\n{architecture}\n\nCode Implementation:\n{code}\n\nTest Suite:\n{tests}\n\n" +
                    $"Create documentation that explains the architecture decisions, how to use the application, and how it was tested.";
                string documentation = await documenterAgent.RunAsync(docPrompt);
                var stage4Duration = DateTime.UtcNow - stage4Start;

                timings.Add(new StageResult("Documentation (Titan Express)", stage4Duration, true));
                Console.WriteLine($"\n=== DOCUMENTATION ===\n{documentation}\n");
                Console.WriteLine($"✅ Stage 4 completed successfully in {stage4Duration.TotalSeconds:F2} seconds\n");

                // Calculate total time
                var totalDuration = DateTime.UtcNow - pipelineStart;

                Console.WriteLine("\n" + new string('=', 50));
                Console.WriteLine("✅ PIPELINE COMPLETE - 4 AGENTS COLLABORATED");
                Console.WriteLine(new string('=', 50));
                Console.WriteLine("[DONE] Architecture designed by Claude Sonnet");
                Console.WriteLine("[DONE] Code written by Claude Haiku");
                Console.WriteLine("[DONE] Tests created by Nova Lite");
                Console.WriteLine("[DONE] Documentation written by Titan Express");
                Console.WriteLine(new string('=', 50));

                // Print timing summary
                PrintTimingSummary(timings, totalDuration);
            }
            catch (Exception ex)
            {
                var totalDuration = DateTime.UtcNow - pipelineStart;

                // Determine which stage failed
                string stageName = timings.Count switch
                {
                    0 => "Architecture (Claude Sonnet)",
                    1 => "Development (Claude Haiku)",
                    2 => "Testing (Nova Lite)",
                    3 => "Documentation (Titan Express)",
                    _ => "Unknown Stage"
                };

                var failedStageDuration = totalDuration;
                if (timings.Count > 0)
                {
                    var completedTime = timings.Sum(t => t.Duration.TotalMilliseconds);
                    failedStageDuration = TimeSpan.FromMilliseconds(totalDuration.TotalMilliseconds - completedTime);
                }

                timings.Add(new StageResult(stageName, failedStageDuration, false));

                Console.WriteLine($"\n❌ PIPELINE FAILED at Stage {timings.Count} ({stageName})");
                Console.WriteLine($"Error details: {ex.Message}");
                Console.WriteLine($"Time spent: {failedStageDuration.TotalSeconds:F2} seconds");
                Console.WriteLine("\nPossible causes:");
                Console.WriteLine("1. Network connectivity issue");
                Console.WriteLine("2. Invalid AWS credentials");
                Console.WriteLine($"3. Model not available in region {Environment.GetEnvironmentVariable("AWS_DEFAULT_REGION") ?? "us-east-1"}");
                Console.WriteLine("4. Insufficient permissions for the model");

                PrintTimingSummary(timings, totalDuration);
                throw;
            }
        }

        public static async Task Main(string[] args)
        {
            try
            {
                // Load .env file
                try
                {
                    DotEnv.Load();
                }
                catch (Exception ex)
                {
                    Console.WriteLine($"Warning: Could not load .env file: {ex.Message}");
                }

                Console.WriteLine("AWS Bedrock 4-Agent Pipeline (C#/.NET)");
                Console.WriteLine("=======================================");
                Console.WriteLine("Starting 4-Agent Game Development Pipeline...\n");

                // Get region from environment
                string region = Environment.GetEnvironmentVariable("AWS_DEFAULT_REGION") ?? "us-east-1";
                Console.WriteLine($"Using region: {region}\n");

                // Create Bedrock client
                var config = new AmazonBedrockRuntimeConfig
                {
                    RegionEndpoint = Amazon.RegionEndpoint.GetBySystemName(region)
                };
                using var client = new AmazonBedrockRuntimeClient(config);

                // Run the pipeline
                await GameDevelopmentPipelineAsync(client);

                Console.WriteLine("\n🚀 C#/.NET Implementation Complete!");
            }
            catch (Exception ex)
            {
                Console.WriteLine($"\n❌ Pipeline failed with error: {ex.Message}");
                Console.WriteLine(ex.StackTrace);
                Environment.Exit(1);
            }
        }
    }
}