import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cdimascio.dotenv.Dotenv;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class GamePipeline {
    
    private static class BedrockAgent {
        private final BedrockRuntimeClient client;
        private final String modelId;
        private final String systemPrompt;
        private final ObjectMapper objectMapper;
        
        public BedrockAgent(BedrockRuntimeClient client, String modelId, String systemPrompt) {
            this.client = client;
            this.modelId = modelId;
            this.systemPrompt = systemPrompt;
            this.objectMapper = new ObjectMapper();
        }
        
        public CompletableFuture<String> runAsync(String userInput) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    List<Message> messages = new ArrayList<>();
                    messages.add(Message.builder()
                        .role(ConversationRole.USER)
                        .content(ContentBlock.fromText(userInput))
                        .build());
                    
                    ConverseRequest.Builder requestBuilder = ConverseRequest.builder()
                        .modelId(modelId)
                        .messages(messages);
                    
                    if (!systemPrompt.isEmpty()) {
                        requestBuilder.system(SystemContentBlock.fromText(systemPrompt));
                    }
                    
                    ConverseResponse response = client.converse(requestBuilder.build());
                    
                    if (response.output() != null && response.output().message() != null 
                        && !response.output().message().content().isEmpty()) {
                        ContentBlock content = response.output().message().content().get(0);
                        if (content.text() != null) {
                            return content.text();
                        }
                    }
                    
                    throw new RuntimeException("Empty response from model");
                    
                } catch (Exception e) {
                    throw new RuntimeException("Bedrock API error: " + e.getMessage(), e);
                }
            });
        }
    }
    
    private static class StageResult {
        public final String name;
        public final Duration duration;
        public final boolean success;
        
        public StageResult(String name, Duration duration, boolean success) {
            this.name = name;
            this.duration = duration;
            this.success = success;
        }
    }
    
    private static void printTimingSummary(List<StageResult> timings, Duration totalTime) {
        System.out.println("\n📊 TIMING SUMMARY");
        System.out.println("-".repeat(50));
        
        for (StageResult timing : timings) {
            String status = timing.success ? "✅" : "❌";
            System.out.printf("%-35s: %8.2f sec %s%n", 
                timing.name, timing.duration.toMillis() / 1000.0, status);
        }
        
        System.out.println("-".repeat(50));
        System.out.printf("Total Pipeline Time: %.2f seconds%n", totalTime.toMillis() / 1000.0);
        System.out.println("=".repeat(50));
    }
    
    private static void gameDevelopmentPipeline(BedrockRuntimeClient client) throws Exception {
        String projectRequest = "Create a simple Tic-Tac-Toe (X&Os) game in Python";
        Instant pipelineStart = Instant.now();
        List<StageResult> timings = new ArrayList<>();
        
        // Initialize agents
        BedrockAgent architectAgent = new BedrockAgent(client, 
            "anthropic.claude-3-sonnet-20240229-v1:0",
            "You are a software architect. Create detailed technical specifications and architecture for software projects.");
        BedrockAgent developerAgent = new BedrockAgent(client,
            "anthropic.claude-3-haiku-20240307-v1:0", 
            "You are a Python developer. Write clean, functional code based on specifications.");
        BedrockAgent testerAgent = new BedrockAgent(client,
            "amazon.nova-lite-v1:0",
            "You are a QA engineer. Create comprehensive tests for code to ensure it works correctly.");
        BedrockAgent documenterAgent = new BedrockAgent(client,
            "amazon.titan-text-express-v1", "");
        
        try {
            // Stage 1: Architecture
            System.out.println("[1/4] Creating architecture with Claude Sonnet...");
            Instant stage1Start = Instant.now();
            String architecture = architectAgent.runAsync(
                "Create a detailed architecture and rulebook for: " + projectRequest).get();
            Duration stage1Duration = Duration.between(stage1Start, Instant.now());
            
            timings.add(new StageResult("Architecture (Claude Sonnet)", stage1Duration, true));
            System.out.printf("%n=== ARCHITECTURE ===%n%s%n%n", architecture);
            System.out.printf("✅ Stage 1 completed successfully in %.2f seconds%n%n", 
                stage1Duration.toMillis() / 1000.0);
            
            // Stage 2: Development
            System.out.println("[2/4] Writing code with Claude Haiku...");
            Instant stage2Start = Instant.now();
            String code = developerAgent.runAsync(
                "Based on this architecture, write complete Python code:\n" + architecture).get();
            Duration stage2Duration = Duration.between(stage2Start, Instant.now());
            
            timings.add(new StageResult("Development (Claude Haiku)", stage2Duration, true));
            System.out.printf("%n=== CODE ===%n%s%n%n", code);
            System.out.printf("✅ Stage 2 completed successfully in %.2f seconds%n%n", 
                stage2Duration.toMillis() / 1000.0);
            
            // Stage 3: Testing
            System.out.println("[3/4] Creating tests with Nova Lite...");
            Instant stage3Start = Instant.now();
            String tests = testerAgent.runAsync(
                "Create comprehensive unit tests for this code:\n" + code).get();
            Duration stage3Duration = Duration.between(stage3Start, Instant.now());
            
            timings.add(new StageResult("Testing (Nova Lite)", stage3Duration, true));
            System.out.printf("%n=== TESTS ===%n%s%n%n", tests);
            System.out.printf("✅ Stage 3 completed successfully in %.2f seconds%n%n", 
                stage3Duration.toMillis() / 1000.0);
            
            // Stage 4: Documentation
            System.out.println("[4/4] Creating documentation with Titan Express...");
            Instant stage4Start = Instant.now();
            String docPrompt = String.format(
                "Act as a technical writer. Create comprehensive documentation for this Tic-Tac-Toe game. " +
                "Include setup instructions, usage guide, architecture overview, testing approach, and API reference.\n\n" +
                "Architecture:\n%s\n\nCode Implementation:\n%s\n\nTest Suite:\n%s\n\n" +
                "Create documentation that explains the architecture decisions, how to use the application, and how it was tested.",
                architecture, code, tests);
            String documentation = documenterAgent.runAsync(docPrompt).get();
            Duration stage4Duration = Duration.between(stage4Start, Instant.now());
            
            timings.add(new StageResult("Documentation (Titan Express)", stage4Duration, true));
            System.out.printf("%n=== DOCUMENTATION ===%n%s%n%n", documentation);
            System.out.printf("✅ Stage 4 completed successfully in %.2f seconds%n%n", 
                stage4Duration.toMillis() / 1000.0);
            
            // Calculate total time
            Duration totalDuration = Duration.between(pipelineStart, Instant.now());
            
            System.out.println("\n" + "=".repeat(50));
            System.out.println("✅ PIPELINE COMPLETE - 4 AGENTS COLLABORATED");
            System.out.println("=".repeat(50));
            System.out.println("[DONE] Architecture designed by Claude Sonnet");
            System.out.println("[DONE] Code written by Claude Haiku");
            System.out.println("[DONE] Tests created by Nova Lite");
            System.out.println("[DONE] Documentation written by Titan Express");
            System.out.println("=".repeat(50));
            
            // Print timing summary
            printTimingSummary(timings, totalDuration);
            
        } catch (Exception e) {
            Duration totalDuration = Duration.between(pipelineStart, Instant.now());
            
            // Determine which stage failed
            String stageName = switch (timings.size()) {
                case 0 -> "Architecture (Claude Sonnet)";
                case 1 -> "Development (Claude Haiku)";
                case 2 -> "Testing (Nova Lite)";
                case 3 -> "Documentation (Titan Express)";
                default -> "Unknown Stage";
            };
            
            Duration failedStageDuration = Duration.between(pipelineStart, Instant.now());
            if (!timings.isEmpty()) {
                Duration completedTime = timings.stream()
                    .map(t -> t.duration)
                    .reduce(Duration.ZERO, Duration::plus);
                failedStageDuration = totalDuration.minus(completedTime);
            }
            
            timings.add(new StageResult(stageName, failedStageDuration, false));
            
            System.out.printf("%n❌ PIPELINE FAILED at Stage %d (%s)%n", timings.size(), stageName);
            System.out.printf("Error details: %s%n", e.getMessage());
            System.out.printf("Time spent: %.2f seconds%n", failedStageDuration.toMillis() / 1000.0);
            System.out.println("\nPossible causes:");
            System.out.println("1. Network connectivity issue");
            System.out.println("2. Invalid AWS credentials");
            System.out.printf("3. Model not available in region %s%n", 
                System.getenv().getOrDefault("AWS_DEFAULT_REGION", "us-east-1"));
            System.out.println("4. Insufficient permissions for the model");
            
            printTimingSummary(timings, totalDuration);
            throw e;
        }
    }
    
    public static void main(String[] args) {
        try {
            // Load .env file
            Dotenv dotenv = null;
            try {
                dotenv = Dotenv.configure().ignoreIfMissing().load();
            } catch (Exception e) {
                System.out.println("Warning: Could not load .env file: " + e.getMessage());
            }
            
            System.out.println("AWS Bedrock 4-Agent Pipeline (Java)");
            System.out.println("====================================");
            System.out.println("Starting 4-Agent Game Development Pipeline...\n");
            
            // Get region from environment
            String region = System.getenv("AWS_DEFAULT_REGION");
            if (region == null && dotenv != null) {
                region = dotenv.get("AWS_DEFAULT_REGION");
            }
            if (region == null) {
                region = "us-east-1";
            }
            System.out.printf("Using region: %s%n%n", region);
            
            // Create Bedrock client
            BedrockRuntimeClient client = BedrockRuntimeClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
            
            // Run the pipeline
            gameDevelopmentPipeline(client);
            
            System.out.println("\n🚀 Java Implementation Complete!");
            
        } catch (Exception e) {
            System.err.printf("%n❌ Pipeline failed with error: %s%n", e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}