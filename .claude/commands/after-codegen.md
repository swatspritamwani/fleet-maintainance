---
description: Run the 5 after_codegen quality gates. Block delivery on any failure.
---

Execute every gate in `.ai/hooks/after_codegen.md`. **Block delivery on any failure** and log to `ai-delivery-log.md`.

1. **run-static-analysis** — `mvn -B checkstyle:check spotbugs:check` (0 violations, 0 HIGH/CRITICAL
   bugs) and SonarQube quality gate (no blocker/critical, coverage >= 80%).

2. **run-tests** — `mvn -B verify` (all pass, JaCoCo line coverage >= 80%, GR-09) and
   `ng test --watch=false --browsers=ChromeHeadless`. Confirm the 5 integration test classes exist and
   cover their ACs: `CreateMaintenanceRequestIT`, `AssignProviderIT`, `SubmitInspectionIT`,
   `MakeDecisionIT`, `PublishPaymentReadinessIT`.

3. **review-checklist** — verify:
   - All 10 Appendix A endpoints have controller tests.
   - No hardcoded secrets (GR-02) — secret scan returns zero findings.
   - REST paths plural/kebab-case under `/api/v1/` (GR-03).
   - 7 Kafka topic names match `maintenance.<domain>.<event>` (§6.1).
   - No repository/DynamoDB SDK imports in controllers (GR-04, ArchUnit).
   - `@ExceptionHandler` methods return `ProblemDetail` (GR-08).
   - All Angular components `standalone: true`; no `*.module.ts` outside generated client (GR-05).

4. **validate-kafka-events** — all 7 topics have a producer; every message uses the common envelope
   (§6.2) with non-null `eventId`/`eventType`/`timestamp`/`correlationId`/`payload`; `payment.ready`
   payload complete (§6.3); transactional outbox implemented; producer `acks=all`, `enable.idempotence=true`.

5. **openapi-contract-test** — `mvn -B verify -Pcontract-tests`: each endpoint's request/response schemas,
   status codes, and content-type match `api/openapi.yaml`; DTO validations enforced; 4xx are RFC 7807.

Append a session entry to `ai-delivery-log.md`: output summary, gate results, errors, corrections.
