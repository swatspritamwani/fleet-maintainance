# Quality Guardrails

> Referenced spec: `docs/functional-requirements.md` §7 NFR-1, NFR-2, NFR-8
> These guardrails enforce test coverage thresholds, event immutability, and idempotent state transitions.

---

## GR-09 · `test_coverage_minimum`

| | |
|---|---|
| **Severity** | 🔴 error — halts generation and delivery |
| **Enforcement** | JaCoCo + SonarQube Quality Gate + `run-tests` after_codegen hook |

### Rule

- Backend **line coverage must be ≥ 80%** for all non-generated source code (NFR-8).
- Every use case (UC-1 through UC-5) must have **at least one integration test** that covers its acceptance criteria.

### Coverage Scope

Coverage is measured on:
- `domain/` package — all domain model, event, and value object classes
- `application/` package — all use case service classes
- `infrastructure/` package — DynamoDB repositories and Kafka producers

Coverage is **excluded** from:
- `target/generated-sources/` — OpenAPI-generated interfaces and DTOs
- `**/config/**` — Spring configuration classes
- Entry point classes (`*Application.java`)

### JaCoCo Configuration (`pom.xml`)

```xml
<plugin>
  <groupId>org.jacoco</groupId>
  <artifactId>jacoco-maven-plugin</artifactId>
  <configuration>
    <excludes>
      <exclude>**/generated/**</exclude>
      <exclude>**/*Application.class</exclude>
      <exclude>**/config/**</exclude>
      <exclude>**/*Config.class</exclude>
    </excludes>
  </configuration>
  <executions>
    <execution>
      <id>jacoco-prepare</id>
      <goals><goal>prepare-agent</goal></goals>
    </execution>
    <execution>
      <id>jacoco-report</id>
      <phase>verify</phase>
      <goals><goal>report</goal></goals>
    </execution>
    <execution>
      <id>jacoco-check</id>
      <phase>verify</phase>
      <goals><goal>check</goal></goals>
      <configuration>
        <rules>
          <rule>
            <element>BUNDLE</element>
            <limits>
              <limit>
                <counter>LINE</counter>
                <value>COVEREDRATIO</value>
                <minimum>0.80</minimum>  <!-- 80% minimum (GR-09) -->
              </limit>
            </limits>
          </rule>
        </rules>
      </configuration>
    </execution>
  </executions>
</plugin>
```

### Required Integration Test Coverage Matrix

| Use Case | Integration Test Class | Acceptance Criteria Covered |
|----------|----------------------|----------------------------|
| UC-1 | `CreateMaintenanceRequestIT` | AC-1.1, AC-1.2, AC-1.3 |
| UC-2 | `AssignProviderIT` | AC-2.1, AC-2.2, AC-2.3 |
| UC-3 | `SubmitInspectionIT` | AC-3.1, AC-3.2, AC-3.3, AC-3.4 |
| UC-4 | `MakeDecisionIT` | AC-4.1, AC-4.2, AC-4.3 |
| UC-5 | `PublishPaymentReadinessIT` | AC-5.1, AC-5.2, AC-5.3 |

### Integration Test Template

```java
// Each IT class must use @SpringBootTest with DynamoDB Local + embedded Kafka
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CreateMaintenanceRequestIT {

    @Autowired private TestRestTemplate rest;
    @Autowired private KafkaTestConsumer kafkaConsumer;

    @Test
    // AC-1.1: Request is persisted with all required fields
    void createRequest_persistsAllRequiredFields() { ... }

    @Test
    // AC-1.2: maintenance.request.created Kafka event is published
    void createRequest_publishesKafkaEvent() {
        // arrange + act
        var response = rest.postForEntity("/api/v1/maintenance-requests", payload, RequestDto.class);
        // assert Kafka event received
        var event = kafkaConsumer.poll("maintenance.request.created", Duration.ofSeconds(5));
        assertThat(event).isNotNull();
        assertThat(event.getPayload().get("requestId")).isEqualTo(response.getBody().requestId());
    }

    @Test
    // AC-1.3: Validation errors return 400 with field-level messages
    void createRequest_missingVehicleId_returns400() {
        var response = rest.postForEntity("/api/v1/maintenance-requests",
            Map.of("description", "test", "priority", "LOW"), ProblemDetail.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getDetail()).contains("vehicleId");
    }
}
```

### How It Is Checked

1. **`mvn verify`** — JaCoCo `check` goal fails the build if coverage drops below 80%.
2. **SonarQube Quality Gate** — configured with `coverage < 80%` as a gate condition.
3. **`run-tests` after_codegen hook** — parses `target/site/jacoco/jacoco.xml` and asserts the `LINE` counter covered ratio ≥ 0.80.
4. **`run-tests` hook** — asserts all 5 IT classes exist and pass.

---

## GR-11 · `immutable_events`

| | |
|---|---|
| **Severity** | 🟡 warning — logged to `ai-delivery-log.md`, does not halt delivery |
| **Enforcement** | SpotBugs + Checkstyle + `validate-domain-model` before_codegen hook |

### Rule

All domain event classes must be **immutable value objects**: final fields, all-args constructor or factory method, no setter methods (§3.3 Value Objects, NFR-2). Domain events are audit records — once created, they must never change.

### Rationale

- Immutable events are thread-safe and can be passed across threads, cached, serialised, and published to Kafka without risk of post-creation mutation.
- Events serve as the audit trail for all state changes (NFR-2). Mutable events undermine the integrity of that trail.
- Kafka consumers may replay events — immutability ensures replays produce identical results.

### Required Pattern — Java Records (Preferred)

```java
// ✅ CORRECT: Immutable domain event using Java record
// Records are implicitly final with no setters
public record RequestCreatedEvent(
    UUID eventId,
    String eventType,
    Instant timestamp,
    UUID correlationId,
    UUID requestId,
    String vehicleId,
    Priority priority
) implements DomainEvent {

    // Compact constructor for validation
    public RequestCreatedEvent {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(vehicleId, "vehicleId must not be null");
    }

    public static RequestCreatedEvent of(MaintenanceRequest request, UUID correlationId) {
        return new RequestCreatedEvent(
            UUID.randomUUID(),
            "maintenance.request.created",
            Instant.now(),
            correlationId,
            request.requestId(),
            request.vehicleId(),
            request.priority()
        );
    }
}
```

### Prohibited Patterns

```java
// ❌ BANNED: Mutable event class with setters
public class RequestCreatedEvent {
    private UUID requestId;

    public void setRequestId(UUID requestId) {   // VIOLATION GR-11
        this.requestId = requestId;
    }
}

// ❌ BANNED: Non-final fields on event class
public class RequestCreatedEvent {
    public UUID requestId;   // public mutable field — VIOLATION GR-11
}
```

### How It Is Checked

1. **SpotBugs** — `EI_EXPOSE_REP` and `EI_EXPOSE_REP2` bug detectors flag mutable fields returned or stored in event classes.
2. **Checkstyle rule** — scans classes in `domain/event/` package:
```xml
<!-- Detect setter methods in event classes -->
<module name="VisibilityModifier">
  <property name="packageAllowed" value="false"/>
  <property name="protectedAllowed" value="false"/>
</module>
```
3. **`validate-domain-model` before_codegen hook** — asserts no setter methods exist on classes in `domain/event/` package:
```bash
grep -rn "public void set" src/main/java/com/fleet/maintenance/bff/domain/event/
# Any result = violation
```

---

## GR-12 · `idempotent_state_transitions`

| | |
|---|---|
| **Severity** | 🔴 error — halts generation and delivery |
| **Enforcement** | Unit tests + `validate-state-machine` before_codegen hook + integration tests |

### Rule

All state transitions on `MaintenanceRequest` must be **idempotent**: re-applying the same transition to a request already in the target state must be a **no-op** — no exception thrown, no duplicate domain event emitted, no duplicate outbox entry created (NFR-1).

### Rationale

Kafka consumers retry failed deliveries. The transactional outbox polling publisher may retry after a partial failure. Without idempotency, a retry could:
- Transition `APPROVED → PAYMENT_READY` twice, publishing two `maintenance.payment.ready` events.
- Create two `Decision` records for the same approval.
- Trigger duplicate payment processing downstream.

### Required Behaviour per Transition

| Current State | Transition | Target State | If Already in Target State |
|---------------|-----------|--------------|---------------------------|
| CREATED | `assign(providerId)` | ASSIGNED | No-op (return current state) |
| ASSIGNED | `submitInspection(report)` | INSPECTION_SUBMITTED | No-op (return current state) |
| INSPECTION_SUBMITTED | `approve()` | APPROVED → PAYMENT_READY | No-op |
| INSPECTION_SUBMITTED | `reject(remarks)` | REJECTED | No-op |
| INSPECTION_SUBMITTED | `requestInfo(remarks)` | INFO_REQUESTED | No-op |
| INFO_REQUESTED | `submitInspection(report)` | INSPECTION_SUBMITTED | No-op |

### Implementation Pattern

```java
// domain/model/MaintenanceRequest.java
public class MaintenanceRequest {

    public MaintenanceRequest assign(UUID providerId) {
        // Idempotency check: already assigned to the same provider → no-op
        if (this.status == Status.ASSIGNED && providerId.equals(this.assignedProviderId)) {
            return this;  // no-op, no event emitted
        }
        // Guard: only CREATED requests can be assigned
        if (this.status != Status.CREATED) {
            throw new IllegalStateTransitionException(
                "Cannot assign a request in status " + this.status);
        }
        return this.withStatus(Status.ASSIGNED)
                   .withAssignedProviderId(providerId)
                   .withDomainEvent(new RequestAssignedEvent(...));
    }

    public MaintenanceRequest approve() {
        // Idempotency: already approved → no-op
        if (this.status == Status.APPROVED || this.status == Status.PAYMENT_READY) {
            return this;  // no duplicate event
        }
        if (this.status != Status.INSPECTION_SUBMITTED) {
            throw new IllegalStateTransitionException(
                "Cannot approve a request in status " + this.status);
        }
        return this.withStatus(Status.APPROVED)
                   .withDomainEvent(new DecisionApprovedEvent(...));
    }
}
```

### Required Unit Tests (per transition)

```java
class MaintenanceRequestIdempotencyTest {

    @Test
    void assign_whenAlreadyAssignedToSameProvider_isNoOp() {
        var request = aMaintenanceRequest()
            .withStatus(Status.ASSIGNED)
            .withAssignedProviderId(PROVIDER_ID)
            .build();

        var result = request.assign(PROVIDER_ID);

        assertThat(result.status()).isEqualTo(Status.ASSIGNED);
        assertThat(result.domainEvents()).isEmpty();  // no duplicate event
    }

    @Test
    void approve_whenAlreadyApproved_isNoOp() {
        var request = aMaintenanceRequest()
            .withStatus(Status.APPROVED)
            .build();

        var result = request.approve();

        assertThat(result.status()).isEqualTo(Status.APPROVED);
        assertThat(result.domainEvents()).isEmpty();
    }

    @Test
    void approve_whenPaymentReady_isNoOp() {
        var request = aMaintenanceRequest()
            .withStatus(Status.PAYMENT_READY)
            .build();

        var result = request.approve();

        assertThat(result.status()).isEqualTo(Status.PAYMENT_READY);
        assertThat(result.domainEvents()).isEmpty();
    }
}
```

### How It Is Checked

1. **`validate-state-machine` before_codegen hook** — asserts each transition method in `MaintenanceRequest` has a corresponding idempotency unit test (test method name must contain `_isNoOp` or `_idempotent`).
2. **Integration test** — the outbox table must not contain duplicate entries when the same transition is applied twice:
```java
@Test
void approve_retried_doesNotCreateDuplicateOutboxEntry() {
    // Simulate retry by calling approve twice
    decisionService.approve(requestId, correlationId);
    decisionService.approve(requestId, correlationId);  // second call — should be no-op

    List<OutboxEvent> outboxEntries = outboxRepository.findByRequestId(requestId)
        .stream().filter(e -> e.eventType().equals("maintenance.decision.approved"))
        .toList();

    assertThat(outboxEntries).hasSize(1);  // exactly one, not two
}
```
3. **`run-tests` after_codegen hook** — fails if any idempotency test fails or is missing.
