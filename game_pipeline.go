package main

import (
	"context"
	"fmt"
	"log"
	"os"
	"strings"
	"time"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/service/bedrockruntime"
	"github.com/aws/aws-sdk-go-v2/service/bedrockruntime/types"
	"github.com/joho/godotenv"
)

type BedrockAgent struct {
	client       *bedrockruntime.Client
	modelID      string
	systemPrompt string
}

type StageResult struct {
	Name     string
	Duration time.Duration
	Success  bool
}

func NewBedrockAgent(client *bedrockruntime.Client, modelID, systemPrompt string) *BedrockAgent {
	return &BedrockAgent{
		client:       client,
		modelID:      modelID,
		systemPrompt: systemPrompt,
	}
}

func (agent *BedrockAgent) Run(ctx context.Context, userInput string) (string, error) {
	messages := []types.Message{
		{
			Role: types.ConversationRoleUser,
			Content: []types.ContentBlock{
				&types.ContentBlockMemberText{
					Value: userInput,
				},
			},
		},
	}

	input := &bedrockruntime.ConverseInput{
		ModelId:  aws.String(agent.modelID),
		Messages: messages,
	}

	if agent.systemPrompt != "" {
		input.System = []types.SystemContentBlock{
			&types.SystemContentBlockMemberText{
				Value: agent.systemPrompt,
			},
		}
	}

	result, err := agent.client.Converse(ctx, input)
	if err != nil {
		return "", fmt.Errorf("bedrock API error: %w", err)
	}

	if result.Output == nil || result.Output.Message == nil || len(result.Output.Message.Content) == 0 {
		return "", fmt.Errorf("empty response from model")
	}

	if textBlock, ok := result.Output.Message.Content[0].(*types.ContentBlockMemberText); ok {
		return textBlock.Value, nil
	}

	return "", fmt.Errorf("unexpected response format")
}

func printTimingSummary(timings []StageResult, totalTime time.Duration) {
	fmt.Println("\n📊 TIMING SUMMARY")
	fmt.Println(strings.Repeat("-", 50))

	for _, timing := range timings {
		status := "✅"
		if !timing.Success {
			status = "❌"
		}
		fmt.Printf("%-35s: %8.2f sec %s\n", timing.Name, timing.Duration.Seconds(), status)
	}

	fmt.Println(strings.Repeat("-", 50))
	fmt.Printf("Total Pipeline Time: %.2f seconds\n", totalTime.Seconds())
	fmt.Println(strings.Repeat("=", 50))
}

func gameDevelopmentPipeline(ctx context.Context, client *bedrockruntime.Client) error {
	projectRequest := "Create a simple Tic-Tac-Toe (X&Os) game in Python"
	pipelineStart := time.Now()
	var timings []StageResult

	// Initialize agents
	architectAgent := NewBedrockAgent(client, "anthropic.claude-3-sonnet-20240229-v1:0",
		"You are a software architect. Create detailed technical specifications and architecture for software projects.")
	developerAgent := NewBedrockAgent(client, "anthropic.claude-3-haiku-20240307-v1:0",
		"You are a Python developer. Write clean, functional code based on specifications.")
	testerAgent := NewBedrockAgent(client, "amazon.nova-lite-v1:0",
		"You are a QA engineer. Create comprehensive tests for code to ensure it works correctly.")
	documenterAgent := NewBedrockAgent(client, "amazon.titan-text-express-v1", "")

	// Stage 1: Architecture
	fmt.Println("[1/4] Creating architecture with Claude Sonnet...")
	stage1Start := time.Now()
	architecture, err := architectAgent.Run(ctx, fmt.Sprintf("Create a detailed architecture and rulebook for: %s", projectRequest))
	stage1Duration := time.Since(stage1Start)

	if err != nil {
		timings = append(timings, StageResult{"Architecture (Claude Sonnet)", stage1Duration, false})
		fmt.Printf("\n❌ PIPELINE FAILED at Stage 1 (Architecture)\n")
		fmt.Printf("Error details: %v\n", err)
		fmt.Printf("Time spent: %.2f seconds\n", stage1Duration.Seconds())
		fmt.Println("\nPossible causes:")
		fmt.Println("1. Network connectivity issue")
		fmt.Println("2. Invalid AWS credentials")
		fmt.Printf("3. Model not available in region %s\n", os.Getenv("AWS_DEFAULT_REGION"))
		fmt.Println("4. Insufficient permissions for Claude Sonnet model")
		printTimingSummary(timings, stage1Duration)
		return err
	}

	timings = append(timings, StageResult{"Architecture (Claude Sonnet)", stage1Duration, true})
	fmt.Printf("\n=== ARCHITECTURE ===\n%s\n\n", architecture)
	fmt.Printf("✅ Stage 1 completed successfully in %.2f seconds\n\n", stage1Duration.Seconds())

	// Stage 2: Development
	fmt.Println("[2/4] Writing code with Claude Haiku...")
	stage2Start := time.Now()
	code, err := developerAgent.Run(ctx, fmt.Sprintf("Based on this architecture, write complete Python code:\n%s", architecture))
	stage2Duration := time.Since(stage2Start)

	if err != nil {
		timings = append(timings, StageResult{"Development (Claude Haiku)", stage2Duration, false})
		fmt.Printf("\n❌ PIPELINE FAILED at Stage 2 (Development)\n")
		fmt.Printf("Error details: %v\n", err)
		fmt.Printf("Time spent: %.2f seconds\n", stage2Duration.Seconds())
		printTimingSummary(timings, time.Since(pipelineStart))
		return err
	}

	timings = append(timings, StageResult{"Development (Claude Haiku)", stage2Duration, true})
	fmt.Printf("\n=== CODE ===\n%s\n\n", code)
	fmt.Printf("✅ Stage 2 completed successfully in %.2f seconds\n\n", stage2Duration.Seconds())

	// Stage 3: Testing
	fmt.Println("[3/4] Creating tests with Nova Lite...")
	stage3Start := time.Now()
	tests, err := testerAgent.Run(ctx, fmt.Sprintf("Create comprehensive unit tests for this code:\n%s", code))
	stage3Duration := time.Since(stage3Start)

	if err != nil {
		timings = append(timings, StageResult{"Testing (Nova Lite)", stage3Duration, false})
		fmt.Printf("\n❌ PIPELINE FAILED at Stage 3 (Testing)\n")
		fmt.Printf("Error details: %v\n", err)
		fmt.Printf("Time spent: %.2f seconds\n", stage3Duration.Seconds())
		printTimingSummary(timings, time.Since(pipelineStart))
		return err
	}

	timings = append(timings, StageResult{"Testing (Nova Lite)", stage3Duration, true})
	fmt.Printf("\n=== TESTS ===\n%s\n\n", tests)
	fmt.Printf("✅ Stage 3 completed successfully in %.2f seconds\n\n", stage3Duration.Seconds())

	// Stage 4: Documentation
	fmt.Println("[4/4] Creating documentation with Titan Express...")
	stage4Start := time.Now()
	docPrompt := fmt.Sprintf("Act as a technical writer. Create comprehensive documentation for this Tic-Tac-Toe game. Include setup instructions, usage guide, architecture overview, testing approach, and API reference.\n\nArchitecture:\n%s\n\nCode Implementation:\n%s\n\nTest Suite:\n%s\n\nCreate documentation that explains the architecture decisions, how to use the application, and how it was tested.", architecture, code, tests)
	documentation, err := documenterAgent.Run(ctx, docPrompt)
	stage4Duration := time.Since(stage4Start)

	if err != nil {
		timings = append(timings, StageResult{"Documentation (Titan Express)", stage4Duration, false})
		fmt.Printf("\n❌ PIPELINE FAILED at Stage 4 (Documentation)\n")
		fmt.Printf("Error details: %v\n", err)
		fmt.Printf("Time spent: %.2f seconds\n", stage4Duration.Seconds())
		printTimingSummary(timings, time.Since(pipelineStart))
		return err
	}

	timings = append(timings, StageResult{"Documentation (Titan Express)", stage4Duration, true})
	fmt.Printf("\n=== DOCUMENTATION ===\n%s\n\n", documentation)
	fmt.Printf("✅ Stage 4 completed successfully in %.2f seconds\n\n", stage4Duration.Seconds())

	// Calculate total time
	totalDuration := time.Since(pipelineStart)

	fmt.Println("\n" + strings.Repeat("=", 50))
	fmt.Println("✅ PIPELINE COMPLETE - 4 AGENTS COLLABORATED")
	fmt.Println(strings.Repeat("=", 50))
	fmt.Println("[DONE] Architecture designed by Claude Sonnet")
	fmt.Println("[DONE] Code written by Claude Haiku")
	fmt.Println("[DONE] Tests created by Nova Lite")
	fmt.Println("[DONE] Documentation written by Titan Express")
	fmt.Println(strings.Repeat("=", 50))

	// Print timing summary
	printTimingSummary(timings, totalDuration)

	return nil
}

func main() {
	// Load .env file
	if err := godotenv.Load(); err != nil {
		log.Printf("Warning: Could not load .env file: %v", err)
	}

	fmt.Println("AWS Bedrock 4-Agent Pipeline (Go)")
	fmt.Println("==================================")
	fmt.Println("Starting 4-Agent Game Development Pipeline...\n")

	// Get region from environment
	region := os.Getenv("AWS_DEFAULT_REGION")
	if region == "" {
		region = "us-east-1"
	}
	fmt.Printf("Using region: %s\n\n", region)

	// Load AWS configuration
	ctx := context.Background()
	cfg, err := config.LoadDefaultConfig(ctx, config.WithRegion(region))
	if err != nil {
		log.Fatalf("Failed to load AWS config: %v", err)
	}

	// Create Bedrock client
	client := bedrockruntime.NewFromConfig(cfg)

	// Run the pipeline
	if err := gameDevelopmentPipeline(ctx, client); err != nil {
		fmt.Printf("\n❌ Pipeline failed with error: %v\n", err)
		os.Exit(1)
	}

	fmt.Println("\n🚀 Go Implementation Complete!")
}