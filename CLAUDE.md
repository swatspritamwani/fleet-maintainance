# CLAUDE.md — Fleet Maintenance Process

> Project memory for Claude Code. This file is auto-loaded every session. It wires the
> agent-agnostic scaffolding under `.ai/` into Claude Code's native behavior. **Read the
> referenced `.ai/` files before generating code** — they are the authoritative source.

---

## 1. What This Project Is

End-to-end fleet maintenance workflow: a **Coordinator** creates maintenance requests,
assigns them to **Service Providers**, reviews inspection estimates, and approves/rejects/
requests-info. Approval publishes a payment-readiness event to Kafka.

Authoritative spec: `docs/functional-requirements.md` (v1.0).
Functional design: `docs/functional-design.md`. Technical design: `docs/technical-design.md`.
API contract (source of truth): `api/openapi.yaml` (OpenAPI 3.1).

---

## 2. Source-of-Truth Documents (read these first)

| Purpose | File |
|---|---|
| Requirements (UC-1..UC-5, §1-§10, Appendix A/B) | `docs/functional-requirements.md` |
| Functional design (screens, DTOs, RBAC) | `docs/functional-design.md` |
| Technical design (modules, DynamoDB schema, infra) | `docs/technical-design.md` |
| API contract (10 endpoints, DTOs) | `api/openapi.yaml` |
| Guardrails (GR-01..GR-12) | `.ai/guardrails/*.md` |
| Skills / approved tech | `.ai/skills/*.md` |
| Pre-generation gates | `.ai/hooks/before_codegen.md` |
| Post-generation gates | `.ai/hooks/after_codegen.md` |
| Agent role definitions | `.ai/agents/*.yaml` |
| Delivery audit log | `ai-delivery-log.md` |

---

## 3. Mandatory Workflow

Code generation is gated. **Do not skip gates.**

1. **Before codegen** — run `/before-codegen` (validates spec, OpenAPI contract, domain model,
   tech stack, state machine per `.ai/hooks/before_codegen.md`). Halt on any failure.
2. **Generate** — follow `.ai/agents/codegen-agent.yaml` order, one bounded context at a time:
   domain -> application -> infrastructure -> BFF controllers -> Angular -> tests -> infra.
3. **After codegen** — run `/after-codegen` (static analysis, tests >=80%, review checklist,
   Kafka event validation, contract tests per `.ai/hooks/after_codegen.md`). Halt on failure.
4. **Log** — append outcomes to `ai-delivery-log.md` (one session entry per module).

Use subagents for phase work: `requirement-agent`, `functional-agent`, `technical-agent`,
`codegen-agent` (see `.claude/agents/`).

---

## 4. Architecture (DDD + BFF, contract-first)

Maven multi-module (see `docs/technical-design.md` and `.ai/skills/backend.md`):

```
fleet-maintenance/                     (parent POM)
├── fleet-maintenance-domain/          pure Java — NO framework imports
├── fleet-maintenance-infrastructure/  DynamoDB + Kafka adapters
├── fleet-maintenance-bff/             Spring Boot BFF (controllers, application services)
└── frontend/                          Angular 17+ standalone app
```

Layering (strict, GR-04): `controller -> application -> domain -> infrastructure`.
Controllers call application services only; domain has zero `org.springframework.*`,
`software.amazon.awssdk.*`, `org.apache.kafka.*` imports; infrastructure implements domain ports.

---

## 5. Approved Tech Stack (GR-01; full detail in `.ai/skills/`)

- **Backend**: Java 21, Spring Boot 3.x, Maven 3.9+, AWS SDK v2 (DynamoDB Enhanced Client),
  Spring Kafka, MapStruct. No Quarkus/Micronaut/Vert.x, no raw servlets, no Spring Data JPA.
- **Frontend**: Angular 17+ (standalone components + signals), TypeScript 5.x strict,
  RxJS, Angular Material **or** PrimeNG (pick one, never both).
- **Contract**: OpenAPI 3.1, `openapi-generator-maven-plugin` (Spring interfaces),
  `typescript-angular` generator (client). Generated code is never hand-edited.
- **Messaging**: Apache Kafka 3.x, JSON common envelope (§6.2), transactional outbox (NFR-1).
- **Infra**: Podman, Kubernetes 1.28+, Terraform 1.6+.
- **CI/Quality**: GitHub Actions, SonarQube, OWASP Dependency-Check, Checkstyle, SpotBugs, Trivy.

Any dependency not listed in `.ai/skills/*.md` is a `validate-tech-stack` violation — halt.

---

## 6. Guardrails (enforce on every edit; detail in `.ai/guardrails/`)

| ID | Rule | Severity |
|---|---|---|
| GR-01 | Backend uses Spring Boot 3.x only — no alternative frameworks | error |
| GR-02 | No secrets/passwords/keys in source; use env vars / K8s Secrets | error |
| GR-03 | REST paths: `/api/v1/`, plural nouns, kebab-case, no verbs | error |
| GR-04 | DDD layering; domain has no framework imports | error |
| GR-05 | Angular components standalone (no NgModule); use signals | warning |
| GR-06 | Kafka events use common envelope (§6.2); topic `maintenance.<domain>.<event>` | error |
| GR-07 | Contract-first: controllers implement generated OpenAPI interfaces | error |
| GR-08 | Errors use RFC 7807 ProblemDetail (`application/problem+json`) | error |
| GR-09 | Backend line coverage >= 80%; each UC has an integration test | error |
| GR-10 | Controllers handle HTTP only; no business logic | error |
| GR-11 | Domain events immutable (Java records, no setters) | warning |
| GR-12 | State transitions idempotent (re-apply = no-op, no duplicate event) | error |

---

## 7. Domain Quick Reference

- **Aggregate**: `MaintenanceRequest` (status enum + transition methods, §4.1).
- **Entities**: `InspectionReport`, `Decision`. **Value objects**: `Money`, `VehicleRef` (immutable).
- **States**: CREATED -> ASSIGNED -> INSPECTION_SUBMITTED -> (APPROVED -> PAYMENT_READY | REJECTED | INFO_REQUESTED).
  Terminal: REJECTED, PAYMENT_READY (further transitions throw `IllegalStateTransitionException`).
- **7 Kafka topics** (§6.1): `maintenance.request.created`, `.request.assigned`,
  `.inspection.submitted`, `.decision.approved`, `.decision.rejected`, `.decision.info-requested`,
  `.payment.ready`. Key = `requestId`.
- **10 endpoints**: see `api/openapi.yaml` / Appendix A.
- **7 screens** (§8.1): `/requests`, `/requests/new`, `/requests/:id`, `/requests/:id/assign`,
  `/requests/:id/inspect`, Decision Panel, `/events`. RBAC via route guards (NFR-3).
- **Persistence**: DynamoDB single-table (`fleet-maintenance`) + outbox table (`.ai/skills/messaging.md`).

---

## 8. Code Conventions

- Do not add comments or documentation unless asked.
- Do not edit generated code (`target/generated-sources/**`, `frontend/src/app/api/generated/**`).
- Domain events and value objects are Java records.
- Kafka publishing always goes through the transactional outbox — never direct from app services.
- All inputs validated server-side; errors as RFC 7807.

---

## 9. Useful Commands

- Generate sources: `mvn -B generate-sources`
- Build + test + coverage gate: `mvn -B verify`
- Static analysis: `mvn -B checkstyle:check spotbugs:check`
- OpenAPI lint: `npx @stoplight/spectral-cli lint api/openapi.yaml`
- Frontend test: `ng test --watch=false --browsers=ChromeHeadless`
- Frontend client gen: `openapi-generator-cli generate -i api/openapi.yaml -g typescript-angular -o frontend/src/app/api/generated`
