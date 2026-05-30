# After Codegen Hooks

> These hooks run **after code generation completes**. All must pass before the delivery is accepted. Failures block merge and are logged to `ai-delivery-log.md`.
>
> Referenced spec: `docs/functional-requirements.md`

---

## Hook 1 · `run-static-analysis`

**Purpose**: Execute Checkstyle, SpotBugs, and SonarQube on all generated backend Java code. Fail on any critical or blocker issue.

### Execution Steps

1. Run Checkstyle:
   ```bash
   mvn checkstyle:check
   ```
   - Config file: `checkstyle.xml` (Google Java Style or project-defined ruleset)
   - Must produce **zero violations**.

2. Run SpotBugs:
   ```bash
   mvn spotbugs:check
   ```
   - Must produce **zero bugs** at `HIGH` or `CRITICAL` priority.
   - Suppress false positives only via `spotbugs-exclude.xml` with documented justification.

3. Run SonarQube analysis:
   ```bash
   mvn sonar:sonar -Dsonar.projectKey=fleet-maintenance
   ```
   - SonarQube Quality Gate must pass:
     - No `BLOCKER` or `CRITICAL` issues
     - No new security hotspots unreviewed
     - Coverage not below 80% threshold (enforced by GR-09)

### Pass Criteria

Checkstyle: 0 violations. SpotBugs: 0 HIGH/CRITICAL bugs. SonarQube Quality Gate: PASSED.

### Failure Action

Halt delivery. Append to `ai-delivery-log.md`:

```
[ERROR] run-static-analysis FAILED
Checkstyle violations: <count> — see target/checkstyle-result.xml
SpotBugs issues: <count> — see target/spotbugsXml.xml
SonarQube gate: FAILED — <reason>
```

---

## Hook 2 · `run-tests`

**Purpose**: Execute backend and frontend test suites; enforce 80% coverage threshold and integration test presence for all use cases (NFR-8).

### Execution Steps

1. Run backend tests:
   ```bash
   mvn test
   ```
   - All unit tests must pass (0 failures, 0 errors).
   - JaCoCo line coverage report generated at `target/site/jacoco/`.

2. Run frontend tests:
   ```bash
   ng test --watch=false --code-coverage --browsers=ChromeHeadless
   ```
   - All Karma/Jest tests must pass.

3. Assert backend line coverage ≥ 80% (GR-09):
   - Parse `target/site/jacoco/jacoco.xml` and check `<counter type="LINE">` missed vs covered.

4. Assert integration test presence for each use case (GR-09):

   | Use Case | Required Integration Test Class |
   |----------|--------------------------------|
   | UC-1 | `CreateMaintenanceRequestIT` |
   | UC-2 | `AssignProviderIT` |
   | UC-3 | `SubmitInspectionIT` |
   | UC-4 | `MakeDecisionIT` |
   | UC-5 | `PublishPaymentReadinessIT` |

5. Assert each integration test covers its acceptance criteria:
   - AC-1.1, AC-1.2, AC-1.3 covered by `CreateMaintenanceRequestIT`
   - AC-2.1, AC-2.2, AC-2.3 covered by `AssignProviderIT`
   - AC-3.1 through AC-3.4 covered by `SubmitInspectionIT`
   - AC-4.1, AC-4.2, AC-4.3 covered by `MakeDecisionIT`
   - AC-5.1, AC-5.2, AC-5.3 covered by `PublishPaymentReadinessIT`

### Pass Criteria

All tests pass. Backend coverage ≥ 80%. All 5 integration test classes exist and cover their ACs.

### Failure Action

Halt delivery. Append to `ai-delivery-log.md`:

```
[ERROR] run-tests FAILED
Backend test failures: <count>
Frontend test failures: <count>
Coverage: <actual>% (required: 80%)
Missing integration test classes: <list>
```

---

## Hook 3 · `review-checklist`

**Purpose**: Automated verification of coding conventions, architectural rules, and delivery standards.

### Checklist Items

Run each check and assert PASS. Any FAIL halts delivery.

| # | Check | How Verified |
|---|-------|-------------|
| 1 | All 10 Appendix A endpoints have corresponding controller tests | Scan `*ControllerTest.java` for `@Test` methods covering each path+method combination |
| 2 | No hardcoded secrets or passwords (GR-02) | `gitleaks detect` or `trufflehog` scan on generated source; zero findings |
| 3 | All REST paths use plural nouns + kebab-case, base `/api/v1/` (GR-03, §9.3) | Parse `@RequestMapping` and `@PostMapping` annotations; regex `^/api/v1/[a-z][a-z0-9-]*s(/.*)?$` |
| 4 | All 7 Kafka topic names match `maintenance.<domain>.<event>` (§6.1, §9.4) | Grep all `KafkaTemplate.send(topic, ...)` calls; assert topic string matches pattern |
| 5 | No repository or DynamoDB SDK imports in controller classes (GR-04) | ArchUnit assertion (also run as part of `mvn test`) |
| 6 | All `@ControllerAdvice` error responses use RFC 7807 Problem Detail (GR-08) | Assert `ProblemDetail` return type on all `@ExceptionHandler` methods |
| 7 | All Angular components have `standalone: true` (GR-05) | Parse `*.component.ts` files; assert `standalone: true` in `@Component` decorator |
| 8 | No `@NgModule` declarations in frontend source | Grep `*.module.ts` — must return no files outside generated client code |

### Pass Criteria

All 8 checklist items PASS.

### Failure Action

Halt delivery. Append to `ai-delivery-log.md`:

```
[ERROR] review-checklist FAILED
Failed checks:
  - [#<n>] <check description>: <file>:<line> — <detail>
```

---

## Hook 4 · `validate-kafka-events`

**Purpose**: Confirm all 7 Kafka topics from §6.1 have producer code and that every published message conforms to the common envelope schema (§6.2).

### Validation Steps

1. Assert a `KafkaTemplate.send(...)` call exists for each of the 7 topics:

   | Topic | Triggered By | Expected Producer Class |
   |-------|-------------|------------------------|
   | `maintenance.request.created` | UC-1 | `MaintenanceRequestEventPublisher` |
   | `maintenance.request.assigned` | UC-2 | `MaintenanceRequestEventPublisher` |
   | `maintenance.inspection.submitted` | UC-3 | `InspectionEventPublisher` |
   | `maintenance.decision.approved` | UC-4 | `DecisionEventPublisher` |
   | `maintenance.decision.rejected` | UC-4 | `DecisionEventPublisher` |
   | `maintenance.decision.info-requested` | UC-4 | `DecisionEventPublisher` |
   | `maintenance.payment.ready` | UC-5 | `PaymentReadinessEventPublisher` |

2. Assert every event class wraps its payload in the common envelope (§6.2):
   ```json
   {
     "eventId":       "UUID — auto-generated",
     "eventType":     "maintenance.<domain>.<event>",
     "timestamp":     "ISO-8601 Instant",
     "correlationId": "UUID — propagated from request header",
     "payload":       { "...event-specific fields..." }
   }
   ```

3. Assert `maintenance.payment.ready` payload includes all fields from §6.3:
   `requestId`, `vehicleId`, `providerId`, `approvedCost` (Money value object), `estimatedDurationDays`, `approvedBy`, `approvedAt`.

4. Assert the transactional outbox pattern is implemented (NFR-1):
   - An outbox DynamoDB table exists in Terraform definitions.
   - Event is written to the outbox table **within the same transaction** as the domain state change.
   - A polling publisher or DynamoDB Streams CDC process reads and publishes outbox entries to Kafka.
   - Outbox entries are marked as published (not deleted) to support replay (AC-5.3).

5. Assert Kafka producer is configured with `acks=all` and `enable.idempotence=true` for exactly-once semantics (AC-5.2).

### Pass Criteria

All 7 topic producers exist. Envelope schema correct. `PaymentReadinessEvent` payload complete. Outbox pattern implemented. Idempotent producer configured.

### Failure Action

Halt delivery. Append to `ai-delivery-log.md`:

```
[ERROR] validate-kafka-events FAILED
Missing producers for topics: <list>
Envelope schema violations: <list of event class + missing field>
PaymentReadinessEvent missing fields: <list>
Outbox pattern: NOT IMPLEMENTED
```

---

## Hook 5 · `openapi-contract-test`

**Purpose**: Run contract tests to verify the live implementation matches every endpoint and schema declared in `api/openapi.yaml`.

### Execution Steps

1. Start the application in `test` Spring profile (uses DynamoDB Local, embedded Kafka).

2. Execute contract tests against the running application:
   ```bash
   mvn verify -Pcontract-tests
   ```
   Contract tests are written using MockMvc + an OpenAPI response validator (e.g., `openapi4j` or `atlassian-swagger-request-validator`).

3. For each of the 10 Appendix A endpoints, assert:
   - **Request schema**: request body matches declared `requestBody` schema (where applicable).
   - **Response schema**: response body matches declared response schema for 2xx, 400, 422 status codes.
   - **HTTP status codes**: implementation returns exactly the status codes declared in the spec.
   - **Content-Type**: all JSON responses include `Content-Type: application/json`.

4. Assert all 4 DTO schemas from Appendix B are validated on input:

   | DTO | Key Validation Rules |
   |-----|---------------------|
   | `CreateRequestDto` | `vehicleId` required; `description` max 2000 chars; `priority` in enum |
   | `AssignProviderDto` | `providerId` required UUID |
   | `InspectionReportDto` | `findings` required max 5000 chars; `estimatedCost` ≥ 0; `estimatedDurationDays` ≥ 1 |
   | `DecisionDto` | `outcome` in enum; `remarks` required when `outcome` ≠ APPROVED |

5. Assert all 4xx error responses return RFC 7807 Problem Detail with `type`, `title`, `status`, `detail` fields (GR-08).

### Pass Criteria

All 10 endpoints pass contract tests. All DTO validations enforced. All error responses RFC 7807 compliant.

### Failure Action

Halt delivery. Append to `ai-delivery-log.md`:

```
[ERROR] openapi-contract-test FAILED
Contract violations:
  - <METHOD> <path>: <expected vs actual schema/status>
RFC 7807 violations:
  - <endpoint>: <missing field>
```
