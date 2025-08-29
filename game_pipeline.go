package main

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"os"
	"strings"
	"time"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/service/bedrockruntime"
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

type ClaudeRequest struct {
	AnthropicVersion string                 `json:"anthropic_version"`
	MaxTokens        int                    `json:"max_tokens"`
	System           string                 `json:"system,omitempty"`
	Messages         []ClaudeMessage        `json:"messages"`
}

type ClaudeMessage struct {
	Role    string `json:"role"`
	Content string `json:"content"`
}

type ClaudeResponse struct {
	Content []ClaudeContent `json:"content"`
}

type ClaudeContent struct {
	Text string `json:"text"`
	Type string `json:"type"`
}

type TitanRequest struct {
	InputText              string                    `json:"inputText"`
	TextGenerationConfig   TitanGenerationConfig    `json:"textGenerationConfig"`
}

type TitanGenerationConfig struct {
	MaxTokenCount int     `json:"maxTokenCount"`
	Temperature   float64 `json:"temperature"`
}

type TitanResponse struct {
	OutputText string `json:"outputText"`
}

type NovaRequest struct {
	Messages []NovaMessage `json:"messages"`
	System   []NovaSystem  `json:"system,omitempty"`
}

type NovaSystem struct {
	Text string `json:"text"`
}

type NovaMessage struct {
	Role    string        `json:"role"`
	Content []NovaContent `json:"content"`
}

type NovaContent struct {
	Text string `json:"text"`
}

type NovaResponse struct {
	Content []NovaResponseContent `json:"content"`
}

type NovaResponseContent struct {
	Text string `json:"text"`
}

func (agent *BedrockAgent) Run(ctx context.Context, userInput string) (string, error) {
	var requestBody []byte
	var err error

	if strings.Contains(agent.modelID, "anthropic.claude") {
		req := ClaudeRequest{
			AnthropicVersion: "bedrock-2023-05-31",
			MaxTokens:        4000,
			Messages: []ClaudeMessage{
				{
					Role:    "user",
					Content: userInput,
				},
			},
		}
		if agent.systemPrompt != "" {
			req.System = agent.systemPrompt
		}
		requestBody, err = json.Marshal(req)
	} else if strings.Contains(agent.modelID, "amazon.titan") {
		req := TitanRequest{
			InputText: userInput,
			TextGenerationConfig: TitanGenerationConfig{
				MaxTokenCount: 4000,
				Temperature:   0.7,
			},
		}
		requestBody, err = json.Marshal(req)
	} else if strings.Contains(agent.modelID, "amazon.nova") {
		// Nova Lite uses messages format without max_tokens
		req := NovaRequest{
			Messages: []NovaMessage{
				{
					Role: "user",
					Content: []NovaContent{
						{Text: userInput},
					},
				},
			},
		}
		if agent.systemPrompt != "" {
			req.System = []NovaSystem{{Text: agent.systemPrompt}}
		}
		requestBody, err = json.Marshal(req)
	}

	if err != nil {
		return "", fmt.Errorf("error marshaling request: %w", err)
	}

	input := &bedrockruntime.InvokeModelInput{
		ModelId:     aws.String(agent.modelID),
		ContentType: aws.String("application/json"),
		Body:        requestBody,
	}

	result, err := agent.client.InvokeModel(ctx, input)
	if err != nil {
		return "", fmt.Errorf("error calling Bedrock: %w", err)
	}

	// Parse response based on model type
	if strings.Contains(agent.modelID, "anthropic.claude") {
		var response ClaudeResponse
		err = json.Unmarshal(result.Body, &response)
		if err != nil {
			return "", fmt.Errorf("error unmarshaling Claude response: %w", err)
		}
		if len(response.Content) > 0 {
			return response.Content[0].Text, nil
		}
	} else if strings.Contains(agent.modelID, "amazon.titan") {
		var response TitanResponse
		err = json.Unmarshal(result.Body, &response)
		if err != nil {
			return "", fmt.Errorf("error unmarshaling Titan response: %w", err)
		}
		return response.OutputText, nil
	} else if strings.Contains(agent.modelID, "amazon.nova") {
		// Nova Lite returns content similar to Claude
		var response NovaResponse
		err = json.Unmarshal(result.Body, &response)
		if err != nil {
			return "", fmt.Errorf("error unmarshaling Nova response: %w", err)
		}
		if len(response.Content) > 0 {
			return response.Content[0].Text, nil
		}
	}

	return "", fmt.Errorf("no content in response")
}

func printTimingSummary(results []StageResult, totalTime time.Duration) {
	fmt.Println("\n📊 TIMING SUMMARY")
	fmt.Println(strings.Repeat("-", 50))
	
	for _, result := range results {
		status := "✅"
		if !result.Success {
			status = "❌"
		}
		fmt.Printf("%-35s: %8.2f sec %s\n", result.Name, result.Duration.Seconds(), status)
	}
	
	fmt.Println(strings.Repeat("-", 50))
	fmt.Printf("Total Pipeline Time: %.2f seconds\n", totalTime.Seconds())
	fmt.Println(strings.Repeat("=", 50))
}

func main() {
	fmt.Println("AWS Bedrock 4-Agent Pipeline (Go)")
	fmt.Println("==================================")
	
	// Load .env file
	fmt.Println("Loading .env file...")
	err := godotenv.Load()
	if err != nil {
		fmt.Printf("Warning: Error loading .env file: %v\n", err)
	} else {
		fmt.Println("✅ .env file loaded successfully")
	}

	// Get AWS region
	region := os.Getenv("AWS_DEFAULT_REGION")
	if region == "" {
		region = "us-east-1"
	}
	fmt.Printf("Using AWS region: %s\n", region)

	// Check for credentials
	accessKey := os.Getenv("AWS_ACCESS_KEY_ID")
	secretKey := os.Getenv("AWS_SECRET_ACCESS_KEY")
	if accessKey != "" && secretKey != "" {
		fmt.Println("AWS credentials loaded from environment")
	} else {
		fmt.Println("Warning: AWS credentials not found in environment")
	}

	// Load AWS configuration
	cfg, err := config.LoadDefaultConfig(context.TODO(), config.WithRegion(region))
	if err != nil {
		log.Fatalf("unable to load SDK config, %v", err)
	}

	// Create Bedrock Runtime client
	client := bedrockruntime.NewFromConfig(cfg)

	// Initialize agents
	architectAgent := NewBedrockAgent(client, "anthropic.claude-3-sonnet-20240229-v1:0", 
		"You are a software architect. Create detailed technical specifications and architecture for software projects.")
	developerAgent := NewBedrockAgent(client, "anthropic.claude-3-haiku-20240307-v1:0",
		"You are a Python developer. Write clean, functional code based on specifications.")
	testerAgent := NewBedrockAgent(client, "anthropic.claude-3-haiku-20240307-v1:0",
		"You are a QA engineer. Create comprehensive tests for code to ensure it works correctly.")
	documenterAgent := NewBedrockAgent(client, "amazon.titan-text-express-v1", "")

	fmt.Println("\nStarting 4-Agent Game Development Pipeline...")
	
	ctx := context.Background()
	pipelineStart := time.Now()
	var results []StageResult

	projectRequest := "Create a simple Tic-Tac-Toe (X&Os) game in Python"

	// Stage 1: Architecture
	fmt.Println("\n[1/4] Creating architecture with Claude Sonnet...")
	stage1Start := time.Now()
	architecture, err := architectAgent.Run(ctx, fmt.Sprintf("Create a detailed architecture and rulebook for: %s", projectRequest))
	stage1Duration := time.Since(stage1Start)

	if err != nil {
		results = append(results, StageResult{"Architecture (Claude Sonnet)", stage1Duration, false})
		fmt.Printf("\n❌ PIPELINE FAILED at Stage 1 (Architecture)\n")
		fmt.Printf("Error details: %v\n", err)
		fmt.Printf("Time spent: %.2f seconds\n", stage1Duration.Seconds())
		printTimingSummary(results, time.Since(pipelineStart))
		return
	}

	results = append(results, StageResult{"Architecture (Claude Sonnet)", stage1Duration, true})
	fmt.Printf("\n=== ARCHITECTURE ===\n%s\n\n", architecture)
	fmt.Printf("✅ Stage 1 completed successfully in %.2f seconds\n\n", stage1Duration.Seconds())

	// Stage 2: Development
	fmt.Println("[2/4] Writing code with Claude Haiku...")
	stage2Start := time.Now()
	code, err := developerAgent.Run(ctx, fmt.Sprintf("Based on this architecture, write complete Python code:\n%s", architecture))
	stage2Duration := time.Since(stage2Start)

	if err != nil {
		results = append(results, StageResult{"Development (Claude Haiku)", stage2Duration, false})
		fmt.Printf("\n❌ PIPELINE FAILED at Stage 2 (Development)\n")
		fmt.Printf("Error details: %v\n", err)
		fmt.Printf("Time spent: %.2f seconds\n", stage2Duration.Seconds())
		printTimingSummary(results, time.Since(pipelineStart))
		return
	}

	results = append(results, StageResult{"Development (Claude Haiku)", stage2Duration, true})
	fmt.Printf("\n=== CODE ===\n%s\n\n", code)
	fmt.Printf("✅ Stage 2 completed successfully in %.2f seconds\n\n", stage2Duration.Seconds())

	// Stage 3: Testing
	fmt.Println("[3/4] Creating tests with Nova Lite...")
	stage3Start := time.Now()
	tests, err := testerAgent.Run(ctx, fmt.Sprintf("Create comprehensive unit tests for this code:\n%s", code))
	stage3Duration := time.Since(stage3Start)

	if err != nil {
		results = append(results, StageResult{"Testing (Nova Lite)", stage3Duration, false})
		fmt.Printf("\n❌ PIPELINE FAILED at Stage 3 (Testing)\n")
		fmt.Printf("Error details: %v\n", err)
		fmt.Printf("Time spent: %.2f seconds\n", stage3Duration.Seconds())
		printTimingSummary(results, time.Since(pipelineStart))
		return
	}

	results = append(results, StageResult{"Testing (Nova Lite)", stage3Duration, true})
	fmt.Printf("\n=== TESTS ===\n%s\n\n", tests)
	fmt.Printf("✅ Stage 3 completed successfully in %.2f seconds\n\n", stage3Duration.Seconds())

	// Stage 4: Documentation
	fmt.Println("[4/4] Creating documentation with Titan Express...")
	stage4Start := time.Now()
	docPrompt := fmt.Sprintf("Act as a technical writer. Create comprehensive documentation for this Tic-Tac-Toe game. Include setup instructions, usage guide, architecture overview, testing approach, and API reference.\n\nArchitecture:\n%s\n\nCode Implementation:\n%s\n\nTest Suite:\n%s\n\nCreate documentation that explains the architecture decisions, how to use the application, and how it was tested.", architecture, code, tests)
	documentation, err := documenterAgent.Run(ctx, docPrompt)
	stage4Duration := time.Since(stage4Start)

	if err != nil {
		results = append(results, StageResult{"Documentation (Titan Express)", stage4Duration, false})
		fmt.Printf("\n❌ PIPELINE FAILED at Stage 4 (Documentation)\n")
		fmt.Printf("Error details: %v\n", err)
		fmt.Printf("Time spent: %.2f seconds\n", stage4Duration.Seconds())
		printTimingSummary(results, time.Since(pipelineStart))
		return
	}

	results = append(results, StageResult{"Documentation (Titan Express)", stage4Duration, true})
	fmt.Printf("\n=== DOCUMENTATION ===\n%s\n\n", documentation)
	fmt.Printf("✅ Stage 4 completed successfully in %.2f seconds\n\n", stage4Duration.Seconds())

	// Print final results
	totalTime := time.Since(pipelineStart)

	fmt.Println("\n" + strings.Repeat("=", 50))
	fmt.Println("✅ PIPELINE COMPLETE - 4 AGENTS COLLABORATED")
	fmt.Println(strings.Repeat("=", 50))
	fmt.Println("[DONE] Architecture designed by Claude Sonnet")
	fmt.Println("[DONE] Code written by Claude Haiku")
	fmt.Println("[DONE] Tests created by Nova Lite")
	fmt.Println("[DONE] Documentation written by Titan Express")
	fmt.Println(strings.Repeat("=", 50))

	printTimingSummary(results, totalTime)

	fmt.Println("\n🚀 Go Implementation Complete!")
}