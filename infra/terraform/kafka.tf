locals {
  kafka_topics = [
    "maintenance.request.created",
    "maintenance.request.assigned",
    "maintenance.inspection.submitted",
    "maintenance.decision.approved",
    "maintenance.decision.rejected",
    "maintenance.decision.info-requested",
    "maintenance.payment.ready"
  ]
}

resource "kafka_topic" "fleet_topics" {
  for_each           = toset(local.kafka_topics)
  name               = each.value
  replication_factor = var.kafka_replication_factor
  partitions         = var.kafka_partitions

  config = {
    "retention.ms"                   = "604800000"
    "cleanup.policy"                 = "delete"
    "min.insync.replicas"            = "2"
    "compression.type"               = "lz4"
    "message.timestamp.type"         = "CreateTime"
  }
}

resource "kafka_topic" "dead_letter" {
  name               = "maintenance.events.dlq"
  replication_factor = var.kafka_replication_factor
  partitions         = 3

  config = {
    "retention.ms"    = "2592000000"
    "cleanup.policy"  = "delete"
    "min.insync.replicas" = "2"
  }
}
