# Meta-Prompt: Generate AI Scaffolding Files

> **Instructions**: Copy this entire prompt and provide it to any AI coding agent along with the contents of `docs/functional-requirements.md`. The agent will generate all files listed below.

---

## Context

You are an AI engineering assistant. You have been given a functional requirements document for a **Fleet Maintenance Process** (see attached `docs/functional-requirements.md`).

Your task is to generate the following project scaffolding files. Each file must be **self-contained**, reference the functional requirements by section number where relevant, and follow the exact output paths and formats specified below.

## Output File Structure

```
.ai/
├── hooks/
│   └── hooks.md
├── skills/
│   └── skills.md
├── guardrails/
│   └── guardrails.md
└── agents/
    ├── requirement-agent.yaml
    ├── functional-agent.yaml
    ├── technical-agent.yaml
    └── codegen-agent.yaml
ai-delivery-log.md
```

---

## File 1: `.ai/hooks/hooks.md`

Generate a lifecycle hooks definition file with two sections:

### `before_codegen` hooks (run before any code generation)

For each hook, specify: **name**, **description**, **validation steps**, **failure action**.

Include at minimum:
1. **validate-functional-spec** – Confirm the functional-requirements.md exists and all use cases (UC-1 through UC-5) are referenced in the implementation plan.
2. **validate-openapi-contract** – Confirm an OpenAPI YAML file exists at `api/openapi.yaml` and covers all endpoints listed in Appendix A of the functional requirements.
3. **validate-domain-model** – Check that all aggregates, entities, and value objects from §3 are mapped to implementation classes.
4. **validate-tech-stack** – Cross-reference implementation dependencies against `skills.md` to ensure no unapproved tech is introduced.
5. **validate-state-machine** – Verify all state transitions from §4.1 are represented in the domain logic.

### `after_codegen` hooks (run after code generation)

Include at minimum:
1. **run-static-analysis** – Execute Checkstyle, SpotBugs, and SonarQube scan. Fail if any critical/blocker issues found.
2. **run-tests** – Execute `mvn test` and `ng test`. Fail if coverage < 80% or any test fails.
3. **review-checklist** – Automated check:
   - [ ] All endpoints have corresponding tests
   - [ ] No hardcoded secrets or passwords
   - [ ] REST naming conventions followed (kebab-case, plural nouns)
   - [ ] Kafka topics match naming convention from §6.1
   - [ ] DDD layering respected (no repository imports in controllers)
   - [ ] Error responses use RFC 7807 format
4. **validate-kafka-events** – Confirm all 7 Kafka topics from §6.1 have corresponding producer code and event schema matches §6.2.
5. **openapi-contract-test** – Run contract tests to verify implementation matches OpenAPI spec.

---

## File 2: `.ai/skills/skills.md`

Generate a technology skills matrix with these sections. For each technology, specify: **name**, **version**, **purpose**, **when to use**, **when NOT to use**.

### Backend
- Java 21 (LTS, virtual threads, pattern matching)
- Spring Boot 3.x (web, kafka, actuator, validation)
- Maven (build, dependency management)
- Amazon DynamoDB (NoSQL, single-table design); DynamoDB Local for dev/test
- AWS SDK for Java v2 (DynamoDB Enhanced Client)
- Spring Kafka (producer/consumer)
- MapStruct (DTO mapping)

### Frontend
- Angular 17+ (standalone components, signals)
- TypeScript 5.x (strict mode)
- RxJS (reactive streams for HTTP and Kafka event polling)
- Angular Material or PrimeNG (UI component library)
- Angular Router (route guards for role-based access)

### API Contract
- OpenAPI 3.1 (contract-first, YAML format)
- openapi-generator-maven-plugin (generate Spring server interfaces)
- openapi-generator for Angular (generate TypeScript client)

### Messaging
- Apache Kafka (event streaming)
- Schema: JSON with common envelope (see §6.2 of functional requirements)
- Transactional outbox pattern for reliability

### Infrastructure
- Docker / Podman (container images)
- Kubernetes (orchestration, deployments, services, configmaps)
- Terraform (IaC for K8s resources, Kafka topics, DB provisioning)

### CI/CD & Quality
- GitHub Actions (pipelines)
- SonarQube (code quality, coverage gate)
- OWASP Dependency-Check (vulnerability scanning)
- Checkstyle + SpotBugs (static analysis)
- Trivy (container image scanning)

---

## File 3: `.ai/guardrails/guardrails.md`

Generate a coding guardrails file. Each guardrail must have: **id**, **name**, **rule**, **rationale**, **severity** (error/warning), **enforcement** (how it's checked).

### Required Guardrails

| ID | Name | Rule | Severity |
|----|------|------|----------|
| GR-01 | `enforce_java_springboot` | All backend code must use Spring Boot framework. No raw servlets, no alternative frameworks. | error |
| GR-02 | `no_plain_text_passwords` | No passwords, API keys, or secrets in source code. Use environment variables, K8s secrets, or vault. | error |
| GR-03 | `follow_rest_naming` | API paths must use plural nouns, kebab-case. No verbs in URLs except for RPC-style actions as sub-resources. | error |
| GR-04 | `enforce_ddd_layering` | Controllers may only call application services. No repository or domain service injection in controllers. No persistence (DynamoDB SDK / @DynamoDbBean) annotations in domain objects. | error |
| GR-05 | `angular_standalone_components` | All Angular components must be standalone (no NgModules). Use signals where applicable. | warning |
| GR-06 | `kafka_event_envelope` | All Kafka events must follow the common envelope schema defined in §6.2 of functional requirements. | error |
| GR-07 | `openapi_contract_first` | Server endpoints must be generated from OpenAPI spec. No hand-written controller method signatures that diverge from spec. | error |
| GR-08 | `rfc7807_errors` | All error responses must use RFC 7807 Problem Detail format. No custom error shapes. | error |
| GR-09 | `test_coverage_minimum` | Unit test coverage must be >= 80% for backend. All use cases must have at least one integration test. | error |
| GR-10 | `no_business_logic_in_controller` | Controllers handle HTTP concerns only. All business rules live in domain or application service layer. | error |
| GR-11 | `immutable_events` | Domain events are immutable value objects. No setters on event classes. | warning |
| GR-12 | `idempotent_state_transitions` | All state transitions must be idempotent. Re-applying the same transition to the same state must be a no-op. | error |

---

## File 4: `.ai/agents/requirement-agent.yaml`

Generate a YAML agent definition with:

```yaml
name: requirement-agent
description: >
  Validates and refines functional requirements. Ensures completeness,
  consistency, and traceability of the fleet maintenance specification.

role: Requirements Analyst

inputs:
  - docs/functional-requirements.md

responsibilities:
  - Validate all use cases have preconditions, postconditions, and acceptance criteria
  - Ensure state machine transitions are complete and have no orphan states
  - Verify all domain entities are referenced in at least one use case
  - Check event schema completeness against Kafka topic list
  - Flag ambiguities or contradictions in requirements
  - Ensure NFRs are measurable and testable

outputs:
  - requirements-validation-report.md (list of issues, suggestions, and confirmations)

guardrails_ref: .ai/guardrails/guardrails.md
skills_ref: .ai/skills/skills.md
hooks_ref: .ai/hooks/hooks.md
```

---

## File 5: `.ai/agents/functional-agent.yaml`

Generate a YAML agent definition with:

```yaml
name: functional-agent
description: >
  Translates functional requirements into detailed functional design:
  screen flows, API contracts, state machine implementation strategy.

role: Functional Designer

inputs:
  - docs/functional-requirements.md
  - .ai/skills/skills.md

responsibilities:
  - Design screen-by-screen UI flow with wireframe descriptions
  - Map each use case to specific API endpoints (referencing Appendix A)
  - Define request/response DTOs with validation rules (referencing Appendix B)
  - Design state machine implementation strategy (enum-based vs. state pattern)
  - Define Kafka event flow diagram per use case
  - Specify role-based access control matrix

outputs:
  - docs/functional-design.md
  - api/openapi.yaml (draft)

guardrails_ref: .ai/guardrails/guardrails.md
skills_ref: .ai/skills/skills.md
hooks_ref: .ai/hooks/hooks.md
```

---

## File 6: `.ai/agents/technical-agent.yaml`

Generate a YAML agent definition with:

```yaml
name: technical-agent
description: >
  Makes architecture and infrastructure decisions. Produces technical
  design documents, project structure, and deployment configurations.

role: Technical Architect

inputs:
  - docs/functional-requirements.md
  - docs/functional-design.md (from functional-agent)
  - .ai/skills/skills.md
  - .ai/guardrails/guardrails.md

responsibilities:
  - Define Maven multi-module project structure (BFF, domain services)
  - Design DDD package layout (controller, application, domain, infrastructure)
  - Choose and configure outbox pattern implementation (DynamoDB table or DynamoDB Streams)
  - Design DynamoDB single-table schema (partition/sort keys, GSIs, access patterns)
  - Define Docker/Podman image build strategy
  - Create Kubernetes manifest templates
  - Create Terraform resource definitions
  - Design GitHub Actions CI/CD pipeline stages
  - Define environment configuration strategy (profiles, configmaps, secrets)

outputs:
  - docs/technical-design.md
  - project-structure.md (directory tree with explanations)
  - infra/ directory scaffolding (Dockerfile, k8s/, terraform/)
  - .github/workflows/ci.yml (draft)

guardrails_ref: .ai/guardrails/guardrails.md
skills_ref: .ai/skills/skills.md
hooks_ref: .ai/hooks/hooks.md
```

---

## File 7: `.ai/agents/codegen-agent.yaml`

Generate a YAML agent definition with:

```yaml
name: codegen-agent
description: >
  Generates implementation code from functional and technical designs.
  Enforces all guardrails and runs hooks before and after generation.

role: Code Generator

inputs:
  - docs/functional-requirements.md
  - docs/functional-design.md
  - docs/technical-design.md
  - api/openapi.yaml
  - .ai/guardrails/guardrails.md
  - .ai/skills/skills.md
  - .ai/hooks/hooks.md

workflow:
  1_before_codegen:
    - Execute all before_codegen hooks from .ai/hooks/hooks.md
    - Halt if any hook fails

  2_generate:
    - Generate OpenAPI server stubs (Spring Boot interfaces)
    - Generate Angular TypeScript client from OpenAPI
    - Implement domain layer (aggregates, entities, value objects, domain events)
    - Implement application services (use case orchestration)
    - Implement infrastructure adapters (DynamoDB repositories via Enhanced Client, Kafka producers)
    - Implement BFF controllers (delegate to application services)
    - Implement Angular components, services, and route guards
    - Generate unit tests for domain and application layers
    - Generate integration tests for API endpoints and Kafka events
    - Generate DynamoDB table provisioning definitions (Terraform / CloudFormation)

  3_after_codegen:
    - Execute all after_codegen hooks from .ai/hooks/hooks.md
    - Report results in ai-delivery-log.md

responsibilities:
  - Produce compilable, runnable code that passes all quality gates
  - Follow every guardrail in .ai/guardrails/guardrails.md
  - Use only technologies listed in .ai/skills/skills.md
  - Ensure 1:1 traceability from use cases to test cases
  - Log all generation decisions, errors, and corrections in ai-delivery-log.md

outputs:
  - src/ (backend Java code)
  - frontend/ (Angular code)
  - tests/ (unit + integration)
  - infra/dynamodb/ (DynamoDB table definitions)
  - ai-delivery-log.md (updated)

guardrails_ref: .ai/guardrails/guardrails.md
skills_ref: .ai/skills/skills.md
hooks_ref: .ai/hooks/hooks.md
```

---

## File 8: `ai-delivery-log.md`

Generate an initial delivery log template:

```markdown
# AI Delivery Log – Fleet Maintenance Process

## Session Log

### Session 1 – [DATE]

**Prompt**: [paste the prompt used]
**Agent**: [which agent was invoked]
**Output**: [summary of what was generated]
**Validation**:
- [ ] Hooks passed
- [ ] Guardrails checked
- [ ] Tests passed
**Errors / Hallucinations Found**: [list any issues]
**Corrections Applied**: [list fixes]

---

(Repeat for each session)
```

---

## Execution Instructions

1. Read `docs/functional-requirements.md` in full.
2. Generate each file **exactly** at the specified path.
3. Every guardrail, hook, and agent definition must **reference specific sections** of the functional requirements (e.g., "§4.1 Transition Rules", "Appendix A").
4. Use YAML format for agent files, Markdown for everything else.
5. Do not generate any application source code — only the scaffolding/configuration files listed above.
6. After generating all files, output a summary checklist confirming each file was created.
