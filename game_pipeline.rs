use anyhow::{Context, Result};
use aws_config::BehaviorVersion;
use aws_smithy_types::Blob;
use aws_sdk_bedrockruntime::Client;
use serde_json::{json, Value};
use std::env;
use std::time::{Duration, Instant};
use tokio;

#[derive(Debug, Clone)]
struct StageResult {
    name: String,
    duration: Duration,
    success: bool,
}

impl StageResult {
    fn new(name: String, duration: Duration, success: bool) -> Self {
        Self { name, duration, success }
    }
}

struct BedrockAgent {
    client: Client,
    model_id: String,
    system_prompt: String,
}

impl BedrockAgent {
    fn new(client: Client, model_id: String, system_prompt: String) -> Self {
        Self {
            client,
            model_id,
            system_prompt,
        }
    }

    async fn run(&self, user_input: &str) -> Result<String> {
        let request_json = if self.model_id.contains("anthropic.claude") {
            // Claude request format
            let mut request = json!({
                "anthropic_version": "bedrock-2023-05-31",
                "max_tokens": 4000,
                "messages": [{
                    "role": "user",
                    "content": user_input
                }]
            });

            if !self.system_prompt.is_empty() {
                request["system"] = json!(self.system_prompt);
            }

            request
        } else if self.model_id.contains("amazon.titan") {
            // Titan request format
            json!({
                "inputText": user_input,
                "textGenerationConfig": {
                    "maxTokenCount": 4000,
                    "temperature": 0.7
                }
            })
        } else if self.model_id.contains("amazon.nova") {
            // Nova Lite request format
            let mut request = json!({
                "messages": [{
                    "role": "user",
                    "content": [{
                        "text": user_input
                    }]
                }],
                "inferenceConfig": {
                    "maxTokens": 4000,
                    "temperature": 0.7
                }
            });

            if !self.system_prompt.is_empty() {
                request["system"] = json!([{
                    "text": self.system_prompt
                }]);
            }

            request
        } else {
            return Err(anyhow::anyhow!("Unsupported model: {}", self.model_id));
        };

        let request_body = request_json.to_string();

        let response = self
            .client
            .invoke_model()
            .model_id(&self.model_id)
            .content_type("application/json")
            .body(Blob::new(request_body.as_bytes()))
            .send()
            .await
            .context("Failed to invoke Bedrock model")?;

        let response_body = response
            .body()
            .as_ref();

        let response_str = std::str::from_utf8(response_body)
            .context("Invalid UTF-8 in response")?;

        let response_json: Value = serde_json::from_str(response_str)
            .context("Failed to parse JSON response")?;

        // Extract content based on model type
        if self.model_id.contains("anthropic.claude") {
            response_json["content"][0]["text"]
                .as_str()
                .unwrap_or("")
                .to_string()
        } else if self.model_id.contains("amazon.titan") {
            // Titan Express returns response in results[0].outputText structure
            response_json["results"][0]["outputText"]
                .as_str()
                .unwrap_or("")
                .to_string()
        } else if self.model_id.contains("amazon.nova") {
            // Nova Lite returns response in output.message.content structure
            response_json["output"]["message"]["content"][0]["text"]
                .as_str()
                .unwrap_or("")
                .to_string()
        } else {
            String::new()
        }
        .pipe(|content| {
            if content.is_empty() {
                Err(anyhow::anyhow!("No content in response"))
            } else {
                Ok(content)
            }
        })
    }
}

// Helper trait for pipe operations
trait Pipe {
    fn pipe<F, R>(self, f: F) -> R
    where
        F: FnOnce(Self) -> R,
        Self: Sized,
    {
        f(self)
    }
}

impl<T> Pipe for T {}

fn print_timing_summary(timings: &[StageResult], total_time: Duration) {
    println!("\nTIMING SUMMARY");
    println!("{}", "-".repeat(50));
    
    for timing in timings {
        let status = if timing.success { "SUCCESS" } else { "FAILED" };
        println!("{:<35}: {:>8.2} sec {}", 
            timing.name, 
            timing.duration.as_secs_f64(), 
            status
        );
    }
    
    println!("{}", "-".repeat(50));
    println!("Total Pipeline Time: {:.2} seconds", total_time.as_secs_f64());
    println!("{}", "=".repeat(50));
}

fn get_model_config(key: &str, default: &str) -> String {
    env::var(key).unwrap_or_else(|_| default.to_string())
}

async fn game_development_pipeline(client: Client) -> Result<()> {
    let project_request = "Create a simple Tic-Tac-Toe (X&Os) game in Python";
    let pipeline_start = Instant::now();
    let mut timings = Vec::new();

    // Get model configurations from environment variables with fallback defaults
    let architecture_model = get_model_config("ARCHITECTURE_MODEL", "anthropic.claude-3-sonnet-20240229-v1:0");
    let development_model = get_model_config("DEVELOPMENT_MODEL", "anthropic.claude-3-haiku-20240307-v1:0");
    let testing_model = get_model_config("TESTING_MODEL", "amazon.nova-lite-v1:0");
    let documentation_model = get_model_config("DOCUMENTATION_MODEL", "amazon.titan-text-express-v1");

    println!("Model Configuration:");
    println!("  Architecture: {}", architecture_model);
    println!("  Development:  {}", development_model);
    println!("  Testing:      {}", testing_model);
    println!("  Documentation: {}", documentation_model);
    println!();

    // Initialize agents
    let architect_agent = BedrockAgent::new(
        client.clone(),
        architecture_model,
        "You are a software architect. Create detailed technical specifications and architecture for software projects.".to_string(),
    );
    let developer_agent = BedrockAgent::new(
        client.clone(),
        development_model,
        "You are a Python developer. Write clean, functional code based on specifications.".to_string(),
    );
    let tester_agent = BedrockAgent::new(
        client.clone(),
        testing_model,
        "You are a QA engineer. Create comprehensive tests for code to ensure it works correctly.".to_string(),
    );
    let documenter_agent = BedrockAgent::new(
        client.clone(),
        documentation_model,
        String::new(),
    );

    // Stage 1: Architecture
    println!("[1/4] Creating architecture with Claude Sonnet...");
    let stage1_start = Instant::now();
    let architecture = architect_agent
        .run(&format!("Create a detailed architecture and rulebook for: {}", project_request))
        .await
        .context("Stage 1 failed")?;
    let stage1_duration = stage1_start.elapsed();

    timings.push(StageResult::new("Architecture (Claude Sonnet)".to_string(), stage1_duration, true));
    println!("\n=== ARCHITECTURE ===\n{}\n", architecture);
    println!("Stage 1 completed successfully in {:.2} seconds\n", stage1_duration.as_secs_f64());

    // Stage 2: Development
    println!("[2/4] Writing code with Claude Haiku...");
    let stage2_start = Instant::now();
    let code = developer_agent
        .run(&format!("Based on this architecture, write complete Python code:\n{}", architecture))
        .await
        .context("Stage 2 failed")?;
    let stage2_duration = stage2_start.elapsed();

    timings.push(StageResult::new("Development (Claude Haiku)".to_string(), stage2_duration, true));
    println!("\n=== CODE ===\n{}\n", code);
    println!("Stage 2 completed successfully in {:.2} seconds\n", stage2_duration.as_secs_f64());

    // Stage 3: Testing
    println!("[3/4] Creating tests with Nova Lite...");
    let stage3_start = Instant::now();
    let tests = tester_agent
        .run(&format!("Create comprehensive unit tests for this code:\n{}", code))
        .await
        .context("Stage 3 failed")?;
    let stage3_duration = stage3_start.elapsed();

    timings.push(StageResult::new("Testing (Nova Lite)".to_string(), stage3_duration, true));
    println!("\n=== TESTS ===\n{}\n", tests);
    println!("Stage 3 completed successfully in {:.2} seconds\n", stage3_duration.as_secs_f64());

    // Stage 4: Documentation
    println!("[4/4] Creating documentation with Titan Express...");
    let stage4_start = Instant::now();
    let doc_prompt = format!(
        "Act as a technical writer. Create comprehensive documentation for this Tic-Tac-Toe game. \
        Include setup instructions, usage guide, architecture overview, testing approach, and API reference.\n\n\
        Architecture:\n{}\n\nCode Implementation:\n{}\n\nTest Suite:\n{}\n\n\
        Create documentation that explains the architecture decisions, how to use the application, and how it was tested.",
        architecture, code, tests
    );
    let documentation = documenter_agent
        .run(&doc_prompt)
        .await
        .context("Stage 4 failed")?;
    let stage4_duration = stage4_start.elapsed();

    timings.push(StageResult::new("Documentation (Titan Express)".to_string(), stage4_duration, true));
    println!("\n=== DOCUMENTATION ===\n{}\n", documentation);
    println!("Stage 4 completed successfully in {:.2} seconds\n", stage4_duration.as_secs_f64());

    // Calculate total time
    let total_duration = pipeline_start.elapsed();

    println!("\n{}", "=".repeat(50));
    println!("PIPELINE COMPLETE - 4 AGENTS COLLABORATED");
    println!("{}", "=".repeat(50));
    println!("[DONE] Architecture designed by Claude Sonnet");
    println!("[DONE] Code written by Claude Haiku");
    println!("[DONE] Tests created by Nova Lite");
    println!("[DONE] Documentation written by Titan Express");
    println!("{}", "=".repeat(50));

    print_timing_summary(&timings, total_duration);

    Ok(())
}

#[tokio::main]
async fn main() -> Result<()> {
    // Load .env file
    if let Err(e) = dotenvy::dotenv() {
        println!("Warning: Could not load .env file: {}", e);
    } else {
        println!(".env file loaded successfully");
    }

    println!("AWS Bedrock 4-Agent Pipeline (Rust)");
    println!("====================================");
    println!("Starting 4-Agent Game Development Pipeline...");

    // Get region from environment variable
    let region = env::var("AWS_DEFAULT_REGION").unwrap_or_else(|_| "us-east-1".to_string());
    println!("Using region: {}\n", region);

    // Load AWS configuration
    let config = aws_config::defaults(BehaviorVersion::latest())
        .region(aws_config::Region::new(region))
        .load()
        .await;

    // Create Bedrock Runtime client
    let client = Client::new(&config);

    // Run the pipeline
    match game_development_pipeline(client).await {
        Ok(()) => {
            println!("\nRust Implementation Complete!");
            Ok(())
        }
        Err(e) => {
            eprintln!("\nPipeline failed with error: {}", e);
            
            // Print the error chain for better debugging
            let mut current_error = e.source();
            while let Some(err) = current_error {
                eprintln!("Caused by: {}", err);
                current_error = err.source();
            }
            
            std::process::exit(1);
        }
    }
}