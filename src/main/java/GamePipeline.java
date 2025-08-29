import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
                    ObjectNode requestJson;
                    
                    if (modelId.contains("anthropic.claude")) {
                        // Claude request format
                        requestJson = objectMapper.createObjectNode();
                        requestJson.put("anthropic_version", "bedrock-2023-05-31");
                        requestJson.put("max_tokens", 4000);
                        
                        ArrayNode messages = objectMapper.createArrayNode();
                        ObjectNode message = objectMapper.createObjectNode();
                        message.put("role", "user");
                        message.put("content", userInput);
                        messages.add(message);
                        requestJson.set("messages", messages);
                        
                        if (!systemPrompt.isEmpty()) {
                            requestJson.put("system", systemPrompt);
                        }
                    } else if (modelId.contains("amazon.titan")) {
                        // Titan request format
                        requestJson = objectMapper.createObjectNode();
                        requestJson.put("inputText", userInput);
                        
                        ObjectNode config = objectMapper.createObjectNode();
                        config.put("maxTokenCount", 4000);
                        config.put("temperature", 0.7);
                        requestJson.set("textGenerationConfig", config);
                    } else if (modelId.contains("amazon.nova")) {
                        // Nova Lite request format with required parameters
                        requestJson = objectMapper.createObjectNode();
                        
                        ArrayNode messages = objectMapper.createArrayNode();
                        ObjectNode message = objectMapper.createObjectNode();
                        message.put("role", "user");
                        
                        ArrayNode content = objectMapper.createArrayNode();
                        ObjectNode textContent = objectMapper.createObjectNode();
                        textContent.put("text", userInput);
                        content.add(textContent);
                        message.set("content", content);
                        messages.add(message);
                        requestJson.set("messages", messages);
                        
                        // Nova Lite specific configuration
                        ObjectNode inferenceConfig = objectMapper.createObjectNode();
                        inferenceConfig.put("maxTokens", 4000);
                        inferenceConfig.put("temperature", 0.7);
                        requestJson.set("inferenceConfig", inferenceConfig);
                        
                        if (!systemPrompt.isEmpty()) {
                            ArrayNode system = objectMapper.createArrayNode();
                            ObjectNode systemContent = objectMapper.createObjectNode();
                            systemContent.put("text", systemPrompt);
                            system.add(systemContent);
                            requestJson.set("system", system);
                        }
                    } else {
                        throw new RuntimeException("Unsupported model: " + modelId);
                    }
                    
                    String requestBody = objectMapper.writeValueAsString(requestJson);
                    
                    InvokeModelRequest request = InvokeModelRequest.builder()
                        .modelId(modelId)
                        .contentType("application/json")
                        .body(SdkBytes.fromUtf8String(requestBody))
                        .build();
                    
                    InvokeModelResponse response = client.invokeModel(request);
                    String responseBody = response.body().asUtf8String();
                    
                    JsonNode responseJson = objectMapper.readTree(responseBody);
                    
                    // Parse response based on model type
                    if (modelId.contains("anthropic.claude")) {
                        JsonNode content = responseJson.get("content");
                        if (content != null && content.isArray() && content.size() > 0) {
                            return content.get(0).get("text").asText();
                        }
                    } else if (modelId.contains("amazon.titan")) {
                        // Titan Express returns response in results[0].outputText structure
                        JsonNode results = responseJson.get("results");
                        if (results != null && results.isArray() && results.size() > 0) {
                            JsonNode firstResult = results.get(0);
                            if (firstResult != null && firstResult.has("outputText")) {
                                return firstResult.get("outputText").asText();
                            }
                        }
                        // Debug: Print the actual response for Titan if parsing fails
                        System.err.println("Titan response debug: " + responseJson.toString());
                    } else if (modelId.contains("amazon.nova")) {
                        // Nova Lite returns response in output.message.content structure
                        JsonNode output = responseJson.get("output");
                        if (output != null) {
                            JsonNode message = output.get("message");
                            if (message != null) {
                                JsonNode content = message.get("content");
                                if (content != null && content.isArray() && content.size() > 0) {
                                    JsonNode firstContent = content.get(0);
                                    if (firstContent != null && firstContent.has("text")) {
                                        return firstContent.get("text").asText();
                                    }
                                }
                            }
                        }
                        // Debug: Print the actual response for Nova if parsing fails
                        System.err.println("Nova response debug: " + responseJson.toString());
                    }
                    
                    throw new RuntimeException("No content in response");
                    
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
                System.out.println("✅ .env file loaded successfully");
            } catch (Exception e) {
                System.out.println("Warning: Could not load .env file: " + e.getMessage());
            }
            
            System.out.println("AWS Bedrock 4-Agent Pipeline (Java)");
            System.out.println("====================================");
            System.out.println("Starting 4-Agent Game Development Pipeline...\n");
            
            // Set environment variables from .env file if loaded
            if (dotenv != null) {
                // Set AWS credentials in system properties if they exist in .env
                String accessKey = dotenv.get("AWS_ACCESS_KEY_ID");
                String secretKey = dotenv.get("AWS_SECRET_ACCESS_KEY");
                String region = dotenv.get("AWS_DEFAULT_REGION");
                
                if (accessKey != null && secretKey != null) {
                    System.setProperty("aws.accessKeyId", accessKey);
                    System.setProperty("aws.secretAccessKey", secretKey);
                    System.out.println("AWS credentials loaded from .env file");
                }
                if (region != null) {
                    System.setProperty("aws.region", region);
                }
            }
            
            // Get region from environment
            String region = System.getProperty("aws.region");
            if (region == null) {
                region = System.getenv("AWS_DEFAULT_REGION");
            }
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