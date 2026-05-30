# Kafka Guardrails

> Referenced spec: `docs/functional-requirements.md` §6.1, §6.2, §6.3, §9.4
> These guardrails ensure all Kafka events follow the common envelope, correct naming, and idempotent delivery semantics.

---

## GR-06 · `kafka_event_envelope`

| | |
|---|---|
| **Severity** | 🔴 error — halts generation and delivery |
| **Enforcement** | `validate-kafka-events` after_codegen hook + integration tests |

### Rule

Every Kafka message published to any of the 7 topics defined in §6.1 must conform to the **common envelope schema** (§6.2). No raw domain object JSON, no partial envelopes, no custom wrapper shapes.

### The 7 Required Topics (§6.1)

| Topic | Triggered By | Producer |
|-------|-------------|----------|
| `maintenance.request.created` | UC-1 | `MaintenanceRequestEventPublisher` |
| `maintenance.request.assigned` | UC-2 | `MaintenanceRequestEventPublisher` |
| `maintenance.inspection.submitted` | UC-3 | `InspectionEventPublisher` |
| `maintenance.decision.approved` | UC-4 | `DecisionEventPublisher` |
| `maintenance.decision.rejected` | UC-4 | `DecisionEventPublisher` |
| `maintenance.decision.info-requested` | UC-4 | `DecisionEventPublisher` |
| `maintenance.payment.ready` | UC-5 | `PaymentReadinessEventPublisher` |

### Common Envelope Schema (§6.2)

Every message value must be a JSON object with these top-level fields:

```json
{
  "eventId":       "550e8400-e29b-41d4-a716-446655440000",
  "eventType":     "maintenance.request.created",
  "timestamp":     "2026-05-29T12:00:00.000Z",
  "correlationId": "7f3a1b2c-9d4e-4f5a-8c6b-1a2b3c4d5e6f",
  "payload": {
    "requestId":  "a1b2c3d4-...",
    "vehicleId":  "VH-001",
    "priority":   "HIGH"
  }
}
```

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `eventId` | UUID string | ✅ | Auto-generated per publish; unique per message |
| `eventType` | string | ✅ | Must match topic name exactly (e.g., `maintenance.request.created`) |
| `timestamp` | ISO-8601 string | ✅ | UTC instant of event creation |
| `correlationId` | UUID string | ✅ | Propagated from HTTP `X-Correlation-ID` header (NFR-5) |
| `payload` | object | ✅ | Event-specific fields — see per-event schemas below |

### Per-Event Payload Schemas

#### `maintenance.request.created` (UC-1, AC-1.2)
```json
{ "requestId": "UUID", "vehicleId": "string", "description": "string", "priority": "HIGH" }
```

#### `maintenance.request.assigned` (UC-2, AC-2.3)
```json
{ "requestId": "UUID", "assignedProviderId": "UUID" }
```

#### `maintenance.inspection.submitted` (UC-3)
```json
{ "requestId": "UUID", "reportId": "UUID", "estimatedCost": 1200.00, "estimatedDurationDays": 5 }
```

#### `maintenance.decision.approved` (UC-4)
```json
{ "requestId": "UUID", "decisionId": "UUID", "decidedBy": "string", "decidedAt": "ISO-8601" }
```

#### `maintenance.decision.rejected` (UC-4)
```json
{ "requestId": "UUID", "decisionId": "UUID", "remarks": "string", "decidedBy": "string" }
```

#### `maintenance.decision.info-requested` (UC-4)
```json
{ "requestId": "UUID", "decisionId": "UUID", "remarks": "string", "decidedBy": "string" }
```

#### `maintenance.payment.ready` (UC-5, §6.3)
```json
{
  "requestId":             "UUID",
  "vehicleId":             "string",
  "providerId":            "UUID",
  "approvedCost":          { "amount": 1500.00, "currency": "USD" },
  "estimatedDurationDays": 5,
  "approvedBy":            "coordinator-user-id",
  "approvedAt":            "2026-05-29T12:00:00Z"
}
```

### Java Implementation (Infrastructure Layer)

```java
// infrastructure/kafka/KafkaEventEnvelope.java
public record KafkaEventEnvelope(
    @JsonProperty("eventId")       String eventId,
    @JsonProperty("eventType")     String eventType,
    @JsonProperty("timestamp")     String timestamp,
    @JsonProperty("correlationId") String correlationId,
    @JsonProperty("payload")       Object payload
) {
    public static KafkaEventEnvelope wrap(DomainEvent event) {
        return new KafkaEventEnvelope(
            event.eventId().toString(),
            event.eventType(),
            event.timestamp().toString(),
            event.correlationId().toString(),
            event
        );
    }
}

// infrastructure/kafka/MaintenanceRequestEventPublisher.java
@Component
@RequiredArgsConstructor
public class MaintenanceRequestEventPublisher implements DomainEventPublisher {

    private final KafkaTemplate<String, KafkaEventEnvelope> kafkaTemplate;
    private final OutboxRepository outboxRepository;

    @Override
    public void publish(DomainEvent event, UUID correlationId) {
        KafkaEventEnvelope envelope = KafkaEventEnvelope.wrap(event);
        // Write to outbox first (within same DynamoDB transaction as domain state change)
        outboxRepository.save(OutboxEvent.from(envelope));
        // Outbox polling publisher will deliver to Kafka asynchronously
    }
}
```

### Topic Naming Convention (§9.4)

- Pattern: `maintenance.<domain>.<event>` — dot-separated, all lowercase
- `<domain>`: the bounded context (e.g., `request`, `inspection`, `decision`, `payment`)
- `<event>`: the past-tense event name (e.g., `created`, `assigned`, `submitted`, `approved`)

### Prohibited Patterns

```java
// ❌ BANNED: raw payload without envelope
kafkaTemplate.send("maintenance.request.created", requestId, requestObject);

// ❌ BANNED: envelope missing correlationId
new KafkaEventEnvelope(UUID.randomUUID(), "maintenance.request.created",
    Instant.now(), null,  // null correlationId — VIOLATION GR-06
    payload);

// ❌ BANNED: topic name not matching convention
kafkaTemplate.send("fleet.requests.new", ...);  // wrong prefix — VIOLATION GR-06
kafkaTemplate.send("maintenance_request_created", ...);  // underscores — VIOLATION GR-06
```

### How It Is Checked

1. **`validate-kafka-events` after_codegen hook** — inspects all `KafkaTemplate.send()` call sites; asserts envelope wrapper is used.
2. **Integration tests** — publish a message for each of the 7 topics and assert the consumed message deserialises correctly with all 5 envelope fields present and non-null.
3. **Unit tests** — `KafkaEventEnvelope.wrap()` tested with a mock `DomainEvent`; asserts no field is null.
4. **`review-checklist` hook** — grep for direct `kafkaTemplate.send(topic, key, rawObject)` calls bypassing the envelope:
```bash
grep -rn "kafkaTemplate.send" src/
# Each match must reference KafkaEventEnvelope, not a domain object directly
```
