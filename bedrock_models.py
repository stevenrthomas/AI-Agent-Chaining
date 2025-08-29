import boto3
import os
from dotenv import load_dotenv
from botocore.exceptions import NoCredentialsError, ClientError

load_dotenv()

def list_bedrock_models():
    try:
        region = os.getenv('AWS_DEFAULT_REGION', 'us-east-1')
        bedrock = boto3.client('bedrock', region_name=region)
        print(f"Using region: {region}")
        response = bedrock.list_foundation_models()
        
        print(f"Found {len(response['modelSummaries'])} models:\n")
        for model in response['modelSummaries']:
            print(f"Model ID: {model['modelId']}")
            print(f"Model Name: {model['modelName']}")
            print(f"Provider: {model['providerName']}")
            print("-" * 40)
            
    except NoCredentialsError:
        print("Error: No AWS credentials found.")
        print("Please configure AWS credentials using one of these methods:")
        print("1. AWS CLI: aws configure")
        print("2. Environment variables: AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY")
        print("3. IAM roles (if running on EC2)")
        
    except ClientError as e:
        print(f"AWS Error: {e}")
        
    except Exception as e:
        print(f"Unexpected error: {e}")

if __name__ == "__main__":
    list_bedrock_models()