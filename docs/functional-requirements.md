# Fleet Maintenance Process – Functional Requirements

> **Version**: 1.0  
> **Date**: 2026-05-29  
> **Status**: Draft  

---

## 1. Overview

### 1.1 Purpose

Define the end-to-end fleet maintenance workflow where a **Coordinator** creates maintenance requests, assigns them to **Service Providers**, reviews inspection estimates, and makes approval decisions that ultimately trigger payment-readiness events via Kafka.

### 1.2 Scope

- Maintenance request lifecycle (create → assign → inspect → decide → pay)
- BFF (Backend-for-Frontend) API serving an Angular UI
- Domain event publishing to Kafka
- OpenAPI contract-first API definitions
- Containerized deployment with CI/CD quality gates

### 1.3 Out of Scope

- Actual payment processing (only the readiness event is published)
- Vehicle telematics / IoT integration
- User authentication provider implementation (assumes external IdP)
- Inventory / parts management

---

## 2. Actors & Roles

| Actor | Role | Permissions |
|---|---|---|
| **Coordinator** | Fleet operations staff | Create requests, assign providers, approve/reject/request-info |
| **Service Provider** | External or internal maintenance vendor | View assigned requests, submit inspection report & cost estimate |
| **System** | Kafka + backend automation | Publish domain events, enforce state transitions, send notifications |

---

## 3. Domain Model

### 3.1 Bounded Contexts

| Context | Responsibility |
|---|---|
| **Request Management** | Create, assign, and track maintenance requests |
| **Inspection** | Capture inspection findings and cost estimates |
| **Decision** | Coordinator approval workflow |
| **Payment Event** | Publish payment-readiness event on approval |

### 3.2 Aggregates & Entities

#### MaintenanceRequest (Aggregate Root)

| Field | Type | Constraints |
|---|---|---|
| `requestId` | UUID | PK, auto-generated |
| `vehicleId` | String | Required, references fleet vehicle |
| `description` | String | Required, max 2000 chars |
| `priority` | Enum | LOW, MEDIUM, HIGH, CRITICAL |
| `status` | Enum | See state machine (§4) |
| `assignedProviderId` | UUID | Nullable until assigned |
| `createdBy` | String | Coordinator user ID |
| `createdAt` | Instant | Auto-set |
| `updatedAt` | Instant | Auto-set on mutation |

#### InspectionReport (Entity, child of MaintenanceRequest)

| Field | Type | Constraints |
|---|---|---|
| `reportId` | UUID | PK |
| `requestId` | UUID | FK → MaintenanceRequest |
| `findings` | String | Required, max 5000 chars |
| `estimatedCost` | BigDecimal | Required, >= 0 |
| `estimatedDurationDays` | Integer | Required, >= 1 |
| `attachments` | List\<String\> | Optional, URLs to uploaded docs |
| `submittedAt` | Instant | Auto-set |
| `submittedBy` | String | Service provider user ID |

#### Decision (Entity, child of MaintenanceRequest)

| Field | Type | Constraints |
|---|---|---|
| `decisionId` | UUID | PK |
| `requestId` | UUID | FK → MaintenanceRequest |
| `outcome` | Enum | APPROVED, REJECTED, INFO_REQUESTED |
| `remarks` | String | Required when REJECTED or INFO_REQUESTED, max 2000 chars |
| `decidedBy` | String | Coordinator user ID |
| `decidedAt` | Instant | Auto-set |

### 3.3 Value Objects

- **Money** – `{ amount: BigDecimal, currency: String }` (default USD)
- **VehicleRef** – `{ vehicleId: String, licensePlate: String, make: String, model: String }`

---

## 4. Workflow & State Machine

```
                    ┌────────────────────────┐
                    │        CREATED          │
                    └───────────┬─────────────┘
                                │ assign(providerId)
                                ▼
                    ┌────────────────────────┐
                    │       ASSIGNED          │
                    └───────────┬─────────────┘
                                │ submitInspection(report)
                                ▼
                    ┌────────────────────────┐
                    │  INSPECTION_SUBMITTED   │
                    └───┬───────┬─────────┬───┘
                        │       │         │
              approve() │       │         │ requestInfo()
                        ▼       │         ▼
              ┌──────────┐      │   ┌───────────────┐
              │ APPROVED  │      │   │ INFO_REQUESTED │
              └─────┬─────┘      │   └───────┬────────┘
                    │            │           │ submitInspection(report)
                    │     reject()│           │ (returns to INSPECTION_SUBMITTED)
                    │            ▼           │
                    │    ┌───────────┐       │
                    │    │ REJECTED   │       │
                    │    └───────────┘       │
                    ▼
           ┌────────────────┐
           │ PAYMENT_READY   │
           └────────────────┘
           (Kafka event published)
```

### 4.1 Transition Rules

| From | Action | To | Guard |
|---|---|---|---|
| CREATED | `assign(providerId)` | ASSIGNED | Provider must exist and be active |
| ASSIGNED | `submitInspection(report)` | INSPECTION_SUBMITTED | Report must have findings + estimate |
| INSPECTION_SUBMITTED | `approve()` | APPROVED | Only Coordinator role |
| INSPECTION_SUBMITTED | `reject(remarks)` | REJECTED | Remarks required |
| INSPECTION_SUBMITTED | `requestInfo(remarks)` | INFO_REQUESTED | Remarks required |
| INFO_REQUESTED | `submitInspection(report)` | INSPECTION_SUBMITTED | Updated report required |
| APPROVED | *(system)* | PAYMENT_READY | Automatic on approval |

### 4.2 Terminal States

- **REJECTED** – no further transitions allowed
- **PAYMENT_READY** – no further transitions allowed

---

## 5. Use Cases

### UC-1: Create Maintenance Request

| Attribute | Detail |
|---|---|
| **Actor** | Coordinator |
| **Precondition** | Coordinator is authenticated |
| **Trigger** | Coordinator fills out request form in Angular UI |
| **Main Flow** | 1. Coordinator enters vehicleId, description, priority. 2. System validates input. 3. System persists request with status CREATED. 4. System returns requestId. 5. System publishes `maintenance.request.created` event. |
| **Postcondition** | Request exists in CREATED status |
| **Acceptance Criteria** | AC-1.1: Request is persisted with all required fields. AC-1.2: `maintenance.request.created` Kafka event is published. AC-1.3: Validation errors return 400 with field-level messages. |

### UC-2: Assign to Service Provider

| Attribute | Detail |
|---|---|
| **Actor** | Coordinator |
| **Precondition** | Request is in CREATED status |
| **Trigger** | Coordinator selects a provider from dropdown and clicks Assign |
| **Main Flow** | 1. Coordinator selects provider. 2. System validates provider is active. 3. System updates status to ASSIGNED, sets assignedProviderId. 4. System publishes `maintenance.request.assigned` event. |
| **Postcondition** | Request is in ASSIGNED status with provider linked |
| **Acceptance Criteria** | AC-2.1: Only CREATED requests can be assigned. AC-2.2: Inactive provider returns 422. AC-2.3: `maintenance.request.assigned` event contains requestId + providerId. |

### UC-3: Submit Inspection & Estimate

| Attribute | Detail |
|---|---|
| **Actor** | Service Provider |
| **Precondition** | Request is in ASSIGNED or INFO_REQUESTED status |
| **Trigger** | Provider fills inspection form and submits |
| **Main Flow** | 1. Provider enters findings, estimatedCost, estimatedDurationDays, optional attachments. 2. System validates report completeness. 3. System persists InspectionReport. 4. System updates request status to INSPECTION_SUBMITTED. 5. System publishes `maintenance.inspection.submitted` event. |
| **Postcondition** | Request is in INSPECTION_SUBMITTED status with linked report |
| **Acceptance Criteria** | AC-3.1: estimatedCost must be >= 0. AC-3.2: estimatedDurationDays must be >= 1. AC-3.3: Findings text is required. AC-3.4: Re-submission from INFO_REQUESTED creates a new report version. |

### UC-4: Approve / Reject / Request Info

| Attribute | Detail |
|---|---|
| **Actor** | Coordinator |
| **Precondition** | Request is in INSPECTION_SUBMITTED status |
| **Trigger** | Coordinator reviews estimate and clicks Approve, Reject, or Request Info |
| **Main Flow – Approve** | 1. Coordinator clicks Approve. 2. System creates Decision(APPROVED). 3. System updates status to APPROVED. 4. System publishes `maintenance.decision.approved` event. 5. System transitions to PAYMENT_READY (see UC-5). |
| **Main Flow – Reject** | 1. Coordinator enters remarks, clicks Reject. 2. System validates remarks non-empty. 3. System creates Decision(REJECTED). 4. System updates status to REJECTED. 5. System publishes `maintenance.decision.rejected` event. |
| **Main Flow – Request Info** | 1. Coordinator enters remarks, clicks Request Info. 2. System validates remarks non-empty. 3. System creates Decision(INFO_REQUESTED). 4. System updates status to INFO_REQUESTED. 5. System publishes `maintenance.decision.info-requested` event. |
| **Postcondition** | Request is in APPROVED, REJECTED, or INFO_REQUESTED status |
| **Acceptance Criteria** | AC-4.1: Only INSPECTION_SUBMITTED requests can be decided. AC-4.2: Reject/Request-Info without remarks returns 400. AC-4.3: Each decision is an immutable audit record. |

### UC-5: Publish Payment-Readiness Event

| Attribute | Detail |
|---|---|
| **Actor** | System (automatic) |
| **Precondition** | Request just transitioned to APPROVED |
| **Trigger** | Approval decision persisted |
| **Main Flow** | 1. System constructs PaymentReadinessEvent with requestId, vehicleId, approvedCost, providerId. 2. System publishes to `maintenance.payment.ready` Kafka topic. 3. System updates status to PAYMENT_READY. |
| **Postcondition** | Event is on Kafka; request is in terminal PAYMENT_READY state |
| **Acceptance Criteria** | AC-5.1: Event contains all required payment fields. AC-5.2: Event is published exactly once (idempotent producer). AC-5.3: If Kafka publish fails, request stays APPROVED and retries via outbox pattern. |

---

## 6. Events & Integration

### 6.1 Kafka Topics

| Topic | Producer | Key | Triggered By |
|---|---|---|---|
| `maintenance.request.created` | Request Service | requestId | UC-1 |
| `maintenance.request.assigned` | Request Service | requestId | UC-2 |
| `maintenance.inspection.submitted` | Inspection Service | requestId | UC-3 |
| `maintenance.decision.approved` | Decision Service | requestId | UC-4 (approve) |
| `maintenance.decision.rejected` | Decision Service | requestId | UC-4 (reject) |
| `maintenance.decision.info-requested` | Decision Service | requestId | UC-4 (request info) |
| `maintenance.payment.ready` | Payment Event Service | requestId | UC-5 |

### 6.2 Event Schema (Common Envelope)

```json
{
  "eventId": "UUID",
  "eventType": "maintenance.request.created",
  "timestamp": "2026-05-29T12:00:00Z",
  "correlationId": "UUID",
  "payload": {
    "requestId": "UUID",
    "...": "event-specific fields"
  }
}
```

### 6.3 PaymentReadinessEvent Payload

```json
{
  "requestId": "UUID",
  "vehicleId": "string",
  "providerId": "UUID",
  "approvedCost": {
    "amount": 1500.00,
    "currency": "USD"
  },
  "estimatedDurationDays": 5,
  "approvedBy": "string",
  "approvedAt": "2026-05-29T12:00:00Z"
}
```

---

## 7. Non-Functional Requirements

| ID | Requirement | Detail |
|---|---|---|
| NFR-1 | **Idempotency** | All state transitions and event publishes must be idempotent. Use transactional outbox pattern for Kafka. |
| NFR-2 | **Audit Trail** | Every state change must record who, when, and what changed. Decisions are immutable. |
| NFR-3 | **Security** | Role-based access: Coordinator vs Service Provider. No plain-text passwords. Secrets via environment variables or vault. |
| NFR-4 | **Validation** | All inputs validated server-side. Field-level error messages returned as RFC 7807 Problem Detail. |
| NFR-5 | **Observability** | Structured logging (JSON). Correlation IDs propagated through Kafka headers. Health and readiness endpoints. |
| NFR-6 | **Performance** | API response < 500ms p95. Kafka publish < 200ms p95. |
| NFR-7 | **Resilience** | Retry with exponential backoff on Kafka publish failure. Circuit breaker on external calls. |
| NFR-8 | **Testing** | Unit tests (>80% coverage), integration tests for Kafka, contract tests for OpenAPI. |

---

## 8. UI Requirements (Angular)

### 8.1 Screens

| Screen | Route | Actor | Features |
|---|---|---|---|
| **Request List** | `/requests` | Coordinator | Table with filters (status, priority, date). Pagination. Click to view detail. |
| **Create Request** | `/requests/new` | Coordinator | Form: vehicleId (dropdown), description (textarea), priority (select). Submit button. |
| **Request Detail** | `/requests/:id` | Coordinator / Provider | Shows request info, assignment, inspection reports, decision history. Action buttons based on status + role. |
| **Assign Provider** | `/requests/:id/assign` | Coordinator | Provider dropdown (filtered to active). Assign button. |
| **Submit Inspection** | `/requests/:id/inspect` | Service Provider | Form: findings (textarea), estimatedCost (number), estimatedDurationDays (number), attachments (file upload). Submit button. |
| **Decision Panel** | (within Request Detail) | Coordinator | Three buttons: Approve, Reject, Request Info. Reject/Request-Info open remarks modal. |
| **Kafka Events Viewer** | `/events` | Coordinator | Real-time or polling-based list of recent domain events with topic, timestamp, payload preview. |

### 8.2 UI Behavior

- Form validation mirrors server-side rules (required fields, min/max values)
- Optimistic UI updates with error rollback
- Toast notifications on success/failure
- Role-based route guards (Coordinator vs Provider)
- Responsive layout (desktop-first, tablet-friendly)

---

## 9. Tech Constraints & Architecture

### 9.1 Tech Stack

| Layer | Technology |
|---|---|
| **Backend** | Java 21, Spring Boot 3.x, Maven |
| **Frontend** | Angular 17+, TypeScript, RxJS |
| **Messaging** | Apache Kafka |
| **API Contract** | OpenAPI 3.1 (contract-first, generate server stubs + client) |
| **Architecture** | BFF (Backend-for-Frontend) + DDD (Domain-Driven Design) |
| **Database** Dynamo DB |
| **Containerization** | Podman |
| **Orchestration** | Kubernetes |
| **IaC** | Terraform |
| **CI/CD** | GitHub Actions |
| **Quality Gates** | SonarQube, OWASP dependency check, Checkstyle, SpotBugs |

### 9.2 Architecture Decisions

- **Contract-first**: OpenAPI YAML is the source of truth. Server interfaces and Angular clients are generated.
- **BFF Pattern**: A dedicated BFF service aggregates domain services for the Angular UI.
- **DDD Layering**: `controller → application-service → domain → infrastructure`. No repository access from controllers.
- **Outbox Pattern**: Domain events stored in an outbox table, published to Kafka by a polling publisher or CDC.
- **Hexagonal Ports**: Domain logic has no Spring/Kafka/JPA imports. Ports define interfaces; adapters implement them.

### 9.3 API Naming Conventions

- Base path: `/api/v1/`
- Resources: plural nouns, kebab-case (e.g., `/api/v1/maintenance-requests`)
- Actions on resources: use sub-resources (e.g., `POST /api/v1/maintenance-requests/{id}/assignments`)
- Standard HTTP methods: GET (read), POST (create/action), PUT (full update), PATCH (partial update)
- Error responses: RFC 7807 Problem Detail

### 9.4 Kafka Conventions

- Topic naming: `maintenance.<domain>.<event>` (dot-separated, lowercase)
- Key: `requestId` (String UUID) for partition co-location
- Value: JSON with common envelope (§6.2)
- Consumer group: `<service-name>-group`

---

## 10. Glossary

| Term | Definition |
|---|---|
| **Coordinator** | Fleet operations user who manages maintenance lifecycle |
| **Service Provider** | Vendor who performs inspections and maintenance work |
| **BFF** | Backend-for-Frontend: a backend layer tailored to UI needs |
| **DDD** | Domain-Driven Design: architecture organized by business domains |
| **Outbox Pattern** | Store events in DB first, then publish to Kafka for reliability |
| **Contract-First** | Define API schema (OpenAPI) before writing implementation code |

---

## Appendix A: OpenAPI Resource Summary

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| POST | `/api/v1/maintenance-requests` | Create request | CreateRequestDto | 201 + RequestDto |
| GET | `/api/v1/maintenance-requests` | List requests (filtered) | – | 200 + Page\<RequestDto\> |
| GET | `/api/v1/maintenance-requests/{id}` | Get request detail | – | 200 + RequestDetailDto |
| POST | `/api/v1/maintenance-requests/{id}/assignments` | Assign provider | AssignProviderDto | 200 + RequestDto |
| POST | `/api/v1/maintenance-requests/{id}/inspections` | Submit inspection | InspectionReportDto | 201 + InspectionDto |
| GET | `/api/v1/maintenance-requests/{id}/inspections` | List inspections for request | – | 200 + List\<InspectionDto\> |
| POST | `/api/v1/maintenance-requests/{id}/decisions` | Submit decision | DecisionDto | 201 + DecisionDto |
| GET | `/api/v1/maintenance-requests/{id}/decisions` | List decisions for request | – | 200 + List\<DecisionDto\> |
| GET | `/api/v1/events` | List recent Kafka events | – | 200 + Page\<EventDto\> |
| GET | `/api/v1/service-providers` | List active providers | – | 200 + List\<ProviderDto\> |

---

## Appendix B: DTO Summary

### CreateRequestDto
```yaml
vehicleId: string (required)
description: string (required, maxLength: 2000)
priority: enum [LOW, MEDIUM, HIGH, CRITICAL] (required)
```

### AssignProviderDto
```yaml
providerId: string/UUID (required)
```

### InspectionReportDto
```yaml
findings: string (required, maxLength: 5000)
estimatedCost: number (required, minimum: 0)
estimatedDurationDays: integer (required, minimum: 1)
attachments: array of string (optional)
```

### DecisionDto
```yaml
outcome: enum [APPROVED, REJECTED, INFO_REQUESTED] (required)
remarks: string (required when outcome != APPROVED, maxLength: 2000)
```
