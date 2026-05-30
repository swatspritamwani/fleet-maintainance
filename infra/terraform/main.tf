terraform {
  required_version = ">= 1.6.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    kafka = {
      source  = "Mongey/kafka"
      version = "~> 0.7"
    }
  }

  backend "s3" {
    bucket         = "fleet-maintenance-tf-state"
    key            = "fleet-maintenance/terraform.tfstate"
    region         = "us-east-1"
    dynamodb_table = "fleet-maintenance-tf-lock"
    encrypt        = true
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "fleet-maintenance"
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}

provider "kafka" {
  bootstrap_servers = [var.kafka_bootstrap_servers]
}

resource "aws_dynamodb_table" "tf_lock" {
  name         = "fleet-maintenance-tf-lock"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "LockID"

  attribute {
    name = "LockID"
    type = "S"
  }
}
