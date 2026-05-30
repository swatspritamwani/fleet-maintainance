resource "aws_dynamodb_table" "fleet_maintenance" {
  name         = "fleet-maintenance"
  billing_mode = var.dynamodb_billing_mode
  hash_key     = "PK"
  range_key    = "SK"

  attribute {
    name = "PK"
    type = "S"
  }
  attribute {
    name = "SK"
    type = "S"
  }
  attribute {
    name = "STATUS"
    type = "S"
  }
  attribute {
    name = "CREATED_AT"
    type = "S"
  }

  global_secondary_index {
    name            = "status-createdAt-index"
    hash_key        = "STATUS"
    range_key       = "CREATED_AT"
    projection_type = "ALL"
  }

  point_in_time_recovery {
    enabled = true
  }

  server_side_encryption {
    enabled = true
  }
}

resource "aws_dynamodb_table" "fleet_maintenance_outbox" {
  name         = "fleet-maintenance-outbox"
  billing_mode = var.dynamodb_billing_mode
  hash_key     = "PK"
  range_key    = "SK"

  attribute {
    name = "PK"
    type = "S"
  }
  attribute {
    name = "SK"
    type = "S"
  }
  attribute {
    name = "STATUS"
    type = "S"
  }
  attribute {
    name = "CREATED_AT"
    type = "S"
  }

  global_secondary_index {
    name            = "status-createdAt-index"
    hash_key        = "STATUS"
    range_key       = "CREATED_AT"
    projection_type = "ALL"
  }

  ttl {
    attribute_name = "TTL"
    enabled        = true
  }

  server_side_encryption {
    enabled = true
  }
}
