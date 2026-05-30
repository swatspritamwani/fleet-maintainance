# Messaging Skills

> Tech stack reference: `docs/functional-requirements.md` §6, §9.1, §9.4
> All Kafka events must use the common envelope (GR-06). All publishing must go through the transactional outbox pattern (NFR-1).

---

## Apache Kafka

| | |
|---|---|
| **Version** | 3.x (latest stable) |
| **Purpose** | Event streaming for all 7 domain event topics defined in §6.1. |

### Topic Registry (§6.1)

| Topic | Producer Service | Message Key | Trigger |
|-------|-----------------|-------------|---------|
| `maintenance.request.created` | Request Service | `requestId` | UC-1 |
| `maintenance.request.assigned` | Request Service | `requestId` | UC-2 |
| `maintenance.inspection.submitted` | Inspection Service | `requestId` | UC-3 |
| `maintenance.decision.approved` | Decision Service | `requestId` | UC-4 (approve) |
| `maintenance.decision.rejected` | Decision Service | `requestId` | UC-4 (reject) |
| `maintenance.decision.info-requested` | Decision Service | `requestId` | UC-4 (request info) |
| `maintenance.payment.ready` | Payment Event Service | `requestId` | UC-5 |

### Naming Conventions (§9.4)

- Pattern: `maintenance.<domain>.<event>` — dot-separated, all lowercase.
- Message key: `requestId` (String UUID) for partition co-location — all events for the same request land on the same partition, preserving order.
- Consumer group ID: `<service-name>-group` (e.g., `fleet-maintenance-bff-group`).

### Producer Configuration (in `application.yml`)

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: all                  # wait for all in-sync replicas
      enable-idempotence: true   # exactly-once producer semantics (AC-5.2)
      retries: 5
      retry-backoff-ms: 500
    properties:
      max.in.flight.requests.per.connection: 1  # required for idempotence + ordering
```

### When to Use

Publishing domain events on state transitions (UC-1 through UC-5). Consumer group configuration for downstream integrations.

### When NOT to Use

- Do **not** use Kafka for synchronous request/response patterns — use the REST API for that.
- Do **not** publish UI notifications directly to Kafka topics — the Angular frontend polls `/api/v1/events` (§8.1).
- Do **not** bypass the outbox pattern to publish directly from application services — all publishing goes through the outbox (see Transactional Outbox Pattern below).

---

## Event Schema – Common Envelope (§6.2)

| | |
|---|---|
| **Version** | v1 |
| **Purpose** | Standardised wrapper for all Kafka messages ensuring consistent structure, traceability, and replay capability. |

### Envelope Structure

```json
{
  "eventId":       "<UUID — auto-generated per publish>",
  "eventType":     "maintenance.<domain>.<event>",
  "timestamp":     "2026-05-29T12:00:00.000Z",
  "correlationId": "<UUID — propagated from incoming HTTP request header X-Correlation-ID>",
  "payload": {
    "<event-specific fields>"
  }
}
```

### Java Domain Event Base Class

```java
// In domain/event/ package — no framework imports (GR-04)
public sealed interface DomainEvent
    permits RequestCreatedEvent, RequestAssignedEvent, InspectionSubmittedEvent,
            DecisionApprovedEvent, DecisionRejectedEvent, DecisionInfoRequestedEvent,
            PaymentReadinessEvent {

    UUID eventId();
    String eventType();
    Instant timestamp();
    UUID correlationId();
}

// Example implementation — immutable record (GR-11)
public record RequestCreatedEvent(
    UUID eventId,
    String eventType,
    Instant timestamp,
    UUID correlationId,
    UUID requestId,
    String vehicleId,
    String priority
) implements DomainEvent {
    public static RequestCreatedEvent of(MaintenanceRequest request, UUID correlationId) {
        return new RequestCreatedEvent(
            UUID.randomUUID(),
            "maintenance.request.created",
            Instant.now(),
            correlationId,
            request.requestId(),
            request.vehicleId(),
            request.priority().name()
        );
    }
}
```

### Envelope Serialisation (Infrastructure Layer)

```java
// infrastructure/kafka/KafkaEventEnvelope.java
public record KafkaEventEnvelope(
    String eventId,
    String eventType,
    String timestamp,
    String correlationId,
    Object payload
) {
    public static KafkaEventEnvelope from(DomainEvent event) {
        return new KafkaEventEnvelope(
            event.eventId().toString(),
            event.eventType(),
            event.timestamp().toString(),
            event.correlationId().toString(),
            event   // serialised as payload
        );
    }
}
```

### PaymentReadinessEvent Payload (§6.3)

```json
{
  "requestId":            "UUID",
  "vehicleId":            "string",
  "providerId":           "UUID",
  "approvedCost": {
    "amount":             1500.00,
    "currency":           "USD"
  },
  "estimatedDurationDays": 5,
  "approvedBy":           "coordinator-user-id",
  "approvedAt":           "2026-05-29T12:00:00Z"
}
```

### When to Use

Every Kafka event published to any of the 7 topics must use this envelope (GR-06). The `correlationId` must be propagated from the originating HTTP request `X-Correlation-ID` header (NFR-5).

### When NOT to Use

- Do **not** publish raw domain object JSON without the envelope.
- Do **not** add event-specific fields outside the `payload` block.
- Do **not** mutate event objects after construction — all domain events are immutable records (GR-11).

---

## Transactional Outbox Pattern

| | |
|---|---|
| **Version** | Custom implementation |
| **Purpose** | Guarantee event delivery to Kafka even when Kafka is temporarily unavailable. Ensures exactly-once publish semantics (NFR-1, AC-5.2, AC-5.3). |

### How It Works

```
Application Service
       │
       ▼
  DynamoDB TransactWriteItems ──────────────────────────────────────┐
       │                                                             │
       ├── Write domain state (e.g., MaintenanceRequest updated)    │
       └── Write OutboxEvent record (status=PENDING)                │
                                                                     │
  Outbox Polling Publisher (@Scheduled, every 500ms)                │
       │                                                             │
       ├── Query DynamoDB: OutboxEvent where status=PENDING         │
       ├── For each event: publish to Kafka via KafkaTemplate       │
       └── On success: update OutboxEvent status=PUBLISHED          │
                                                                     │
  (On Kafka failure: OutboxEvent stays PENDING, retried next poll)  ─┘
```

### DynamoDB Outbox Table Schema

| Field | Type | Notes |
|-------|------|-------|
| `PK` | String | `OUTBOX#<eventId>` |
| `SK` | String | `OUTBOX#<eventId>` |
| `eventId` | String (UUID) | Unique event identifier |
| `eventType` | String | e.g., `maintenance.request.created` |
| `kafkaTopic` | String | Target Kafka topic |
| `messageKey` | String | `requestId` |
| `payload` | String (JSON) | Serialised `KafkaEventEnvelope` |
| `status` | String | `PENDING` or `PUBLISHED` |
| `createdAt` | String (ISO-8601) | For ordering and TTL |
| `publishedAt` | String (ISO-8601) | Set on successful publish |
| `retryCount` | Number | Incremented on each failed attempt |

GSI: `status-createdAt-index` (PK=`status`, SK=`createdAt`) — for efficient `PENDING` queries.

### Outbox Publisher Implementation Sketch

```java
// infrastructure/kafka/OutboxPublisher.java
@Component
public class OutboxPublisher {

    @Scheduled(fixedDelay = 500)
    public void publishPending() {
        List<OutboxEvent> pending = outboxRepository.findPending(MAX_BATCH);
        for (OutboxEvent event : pending) {
            try {
                kafkaTemplate.send(event.kafkaTopic(), event.messageKey(), event.payload()).get();
                outboxRepository.markPublished(event.eventId());
            } catch (Exception e) {
                outboxRepository.incrementRetry(event.eventId());
                log.warn("Outbox publish failed for event {}: {}", event.eventId(), e.getMessage());
            }
        }
    }
}
```

### When to Use

All Kafka event publishing (UC-1 through UC-5). The outbox entry and the domain state change must be written atomically using `DynamoDbEnhancedClient.transactWriteItems()`.

### When NOT to Use

- Do **not** publish directly to Kafka from application services — always go through the outbox.
- Do **not** delete outbox entries after publishing — mark them `PUBLISHED` to support replay and audit (AC-5.3).
- Do **not** use the outbox for large binary payloads — keep event payloads small (reference IDs, not full documents).
