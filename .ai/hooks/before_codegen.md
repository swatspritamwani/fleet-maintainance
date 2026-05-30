# Before Codegen Hooks

> These hooks run **before any code generation begins**. All must pass. Any failure halts the pipeline immediately.
>
> Referenced spec: `docs/functional-requirements.md`

---

## Hook 1 · `validate-functional-spec`

**Purpose**: Confirm `docs/functional-requirements.md` exists and all five use cases are referenced in the implementation plan before any code is generated.

### Validation Steps

1. Assert file `docs/functional-requirements.md` exists and is non-empty.
2. Assert the implementation plan references all five use cases by ID:
   - **UC-1** – Create Maintenance Request (§5)
   - **UC-2** – Assign to Service Provider (§5)
   - **UC-3** – Submit Inspection & Estimate (§5)
   - **UC-4** – Approve / Reject / Request Info (§5)
   - **UC-5** – Publish Payment-Readiness Event (§5)
3. Assert each use case has all four attributes defined: **Precondition**, **Main Flow**, **Postcondition**, and at least one **Acceptance Criterion** (AC-x.x).
4. Assert no acceptance criterion is orphaned (every AC references a valid UC).

### Pass Criteria

All 5 use cases present and complete with acceptance criteria.

### Failure Action

Halt. Log to `ai-delivery-log.md`:

```
[ERROR] validate-functional-spec FAILED
Missing or incomplete use cases: <list UC IDs>
Missing acceptance criteria: <list AC IDs>
```

---

## Hook 2 · `validate-openapi-contract`

**Purpose**: Confirm `api/openapi.yaml` exists and covers all 10 endpoints listed in Appendix A before generating server stubs or Angular client.

### Validation Steps

1. Assert file `api/openapi.yaml` exists.
2. Assert the file is valid OpenAPI 3.1 YAML (run `openapi-generator validate` or equivalent).
3. Assert all 10 paths from Appendix A are declared:

   | Method | Path |
   |--------|------|
   | POST   | `/api/v1/maintenance-requests` |
   | GET    | `/api/v1/maintenance-requests` |
   | GET    | `/api/v1/maintenance-requests/{id}` |
   | POST   | `/api/v1/maintenance-requests/{id}/assignments` |
   | POST   | `/api/v1/maintenance-requests/{id}/inspections` |
   | GET    | `/api/v1/maintenance-requests/{id}/inspections` |
   | POST   | `/api/v1/maintenance-requests/{id}/decisions` |
   | GET    | `/api/v1/maintenance-requests/{id}/decisions` |
   | GET    | `/api/v1/events` |
   | GET    | `/api/v1/service-providers` |

4. Assert each endpoint defines at least one `2xx` success response schema.
5. Assert each mutating endpoint (`POST`/`PUT`/`PATCH`) defines a `400` response using the RFC 7807 Problem Detail schema (§9.3).
6. Assert all four DTOs from Appendix B are declared as reusable `components/schemas`:
   `CreateRequestDto`, `AssignProviderDto`, `InspectionReportDto`, `DecisionDto`.

### Pass Criteria

File valid, all 10 paths present, all DTOs declared, all error responses RFC 7807.

### Failure Action

Halt. Log to `ai-delivery-log.md`:

```
[ERROR] validate-openapi-contract FAILED
Missing paths: <list>
Missing DTO schemas: <list>
Validation errors: <details>
```

---

## Hook 3 · `validate-domain-model`

**Purpose**: Confirm all aggregates, entities, and value objects from §3 are mapped to implementation classes before generating domain layer code.

### Validation Steps

1. Assert `MaintenanceRequest` aggregate root class exists in `domain/` package with all fields from §3.2:
   - `requestId` (UUID), `vehicleId` (String), `description` (String), `priority` (Enum: LOW/MEDIUM/HIGH/CRITICAL)
   - `status` (Enum: CREATED/ASSIGNED/INSPECTION_SUBMITTED/APPROVED/REJECTED/INFO_REQUESTED/PAYMENT_READY)
   - `assignedProviderId` (UUID, nullable), `createdBy` (String), `createdAt` (Instant), `updatedAt` (Instant)

2. Assert `InspectionReport` entity class exists in `domain/` package with all fields from §3.2:
   - `reportId` (UUID), `requestId` (UUID), `findings` (String), `estimatedCost` (BigDecimal)
   - `estimatedDurationDays` (Integer), `attachments` (List\<String\>), `submittedAt` (Instant), `submittedBy` (String)

3. Assert `Decision` entity class exists in `domain/` package with all fields from §3.2:
   - `decisionId` (UUID), `requestId` (UUID), `outcome` (Enum: APPROVED/REJECTED/INFO_REQUESTED)
   - `remarks` (String), `decidedBy` (String), `decidedAt` (Instant)

4. Assert `Money` value object exists with fields `amount` (BigDecimal) and `currency` (String), no setters (§3.3).
5. Assert `VehicleRef` value object exists with fields `vehicleId`, `licensePlate`, `make`, `model` (String), no setters (§3.3).
6. Assert no domain class imports from `software.amazon.awssdk.*`, `org.springframework.*`, or `org.apache.kafka.*` (GR-04).

### Pass Criteria

All 3 entity/aggregate classes and 2 value objects present with correct fields; no framework imports in domain.

### Failure Action

Halt. Log to `ai-delivery-log.md`:

```
[ERROR] validate-domain-model FAILED
Unmapped domain objects: <list>
Missing fields: <class> → <field list>
Illegal framework imports in domain: <class> → <import>
```

---

## Hook 4 · `validate-tech-stack`

**Purpose**: Cross-reference all declared dependencies against `.ai/skills/skills.md` to ensure no unapproved technology is introduced.

### Validation Steps

1. Parse `pom.xml` — extract all `<dependency>` `groupId:artifactId` entries.
2. Parse `frontend/package.json` — extract all `dependencies` and `devDependencies`.
3. For each dependency, assert it maps to an approved technology in `.ai/skills/`:
   - **Backend approved**: `org.springframework.boot:*`, `software.amazon.awssdk:dynamodb*`, `org.apache.kafka:*`, `org.springframework.kafka:*`, `org.mapstruct:mapstruct`, `org.openapitools:openapi-generator-maven-plugin`
   - **Frontend approved**: `@angular/*`, `rxjs`, `typescript`, `@angular/material` or `primeng`, `openapi-generator` (Angular client)
   - **Quality approved**: `com.github.spotbugs:*`, `org.owasp:dependency-check-maven`, `com.puppycrawl.tools:checkstyle`
4. Assert backend uses Java 21 (`<java.version>21</java.version>` in `pom.xml`).
5. Assert backend uses Spring Boot 3.x (`<parent>` version starts with `3.`).
6. Assert frontend `tsconfig.json` has `"strict": true`.
7. Flag any dependency **not** on the approved list as a violation.

### Pass Criteria

All dependencies map to approved technologies; Java 21; Spring Boot 3.x; TypeScript strict mode enabled.

### Failure Action

Halt. Log to `ai-delivery-log.md`:

```
[ERROR] validate-tech-stack FAILED
Unapproved dependencies: <list of groupId:artifactId or npm package>
```

---

## Hook 5 · `validate-state-machine`

**Purpose**: Verify all 7 state transitions from §4.1 are represented in domain logic before generating application services.

### Validation Steps

1. Assert `MaintenanceRequest` or a dedicated `MaintenanceRequestStateMachine` class implements all 7 transitions from §4.1:

   | Transition | Method | Guard |
   |------------|--------|-------|
   | `CREATED → ASSIGNED` | `assign(providerId)` | Provider must exist and be active |
   | `ASSIGNED → INSPECTION_SUBMITTED` | `submitInspection(report)` | Report must have findings + estimate |
   | `INSPECTION_SUBMITTED → APPROVED` | `approve()` | Caller must have Coordinator role |
   | `INSPECTION_SUBMITTED → REJECTED` | `reject(remarks)` | Remarks must be non-empty |
   | `INSPECTION_SUBMITTED → INFO_REQUESTED` | `requestInfo(remarks)` | Remarks must be non-empty |
   | `INFO_REQUESTED → INSPECTION_SUBMITTED` | `submitInspection(report)` | Updated report required |
   | `APPROVED → PAYMENT_READY` | *(system automatic)* | Triggered on approval persist |

2. Assert terminal states `REJECTED` and `PAYMENT_READY` (§4.2) throw `IllegalStateTransitionException` on any further transition attempt.
3. Assert each guard condition has a corresponding unit test.
4. Assert no transition bypasses the domain object — all state changes go through `MaintenanceRequest` methods (GR-10).

### Pass Criteria

All 7 transitions implemented with guards; terminal states protected; unit tests present for each transition.

### Failure Action

Halt. Log to `ai-delivery-log.md`:

```
[ERROR] validate-state-machine FAILED
Missing transitions: <list>
Missing terminal state guards: <list>
Missing guard unit tests: <list>
```
