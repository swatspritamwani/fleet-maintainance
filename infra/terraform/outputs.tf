output "dynamodb_table_arn" {
  description = "ARN of the fleet-maintenance DynamoDB table"
  value       = aws_dynamodb_table.fleet_maintenance.arn
}

output "dynamodb_outbox_table_arn" {
  description = "ARN of the fleet-maintenance-outbox DynamoDB table"
  value       = aws_dynamodb_table.fleet_maintenance_outbox.arn
}

output "kafka_topic_names" {
  description = "All provisioned Kafka topic names"
  value       = [for t in kafka_topic.fleet_topics : t.name]
}

output "kafka_dlq_topic_name" {
  description = "Dead-letter queue Kafka topic name"
  value       = kafka_topic.dead_letter.name
}
