variable "environment" {
  description = "Deployment environment (dev, staging, prod)"
  type        = string
  default     = "dev"

  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "environment must be one of: dev, staging, prod"
  }
}

variable "aws_region" {
  description = "AWS region for all resources"
  type        = string
  default     = "us-east-1"
}

variable "kafka_bootstrap_servers" {
  description = "Kafka bootstrap server address (host:port)"
  type        = string
}

variable "dynamodb_billing_mode" {
  description = "DynamoDB billing mode"
  type        = string
  default     = "PAY_PER_REQUEST"
}

variable "kafka_replication_factor" {
  description = "Kafka topic replication factor"
  type        = number
  default     = 3
}

variable "kafka_partitions" {
  description = "Number of Kafka topic partitions"
  type        = number
  default     = 6
}
