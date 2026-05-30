---
description: Run the 5 before_codegen validation gates. Halt on any failure.
---

Execute every gate in `.ai/hooks/before_codegen.md`. **Halt and report on the first failure.**
Do not generate any code until all gates pass.

1. **validate-functional-spec** — `docs/functional-requirements.md` exists and non-empty; UC-1..UC-5
   each have Precondition, Main Flow, Postcondition, and at least one acceptance criterion; no orphan ACs.

2. **validate-openapi-contract** — `api/openapi.yaml` exists, is valid **OpenAPI 3.1**, declares all 10
   Appendix A paths, all 4 DTOs (`CreateRequestDto`, `AssignProviderDto`, `InspectionReportDto`,
   `DecisionDto`), 2xx schemas, and RFC 7807 `400` on mutating endpoints. Lint:
   `npx @stoplight/spectral-cli lint api/openapi.yaml`

3. **validate-domain-model** — all §3 aggregates/entities/value objects map to classes with correct
   fields; no `org.springframework.*` / `software.amazon.awssdk.*` / `org.apache.kafka.*` imports in `domain/` (GR-04).

4. **validate-tech-stack** — every `pom.xml` and `frontend/package.json` dependency maps to an approved
   technology in `.ai/skills/*.md`; Java 21; Spring Boot 3.x; TypeScript `strict: true`.

5. **validate-state-machine** — all 7 transitions from §4.1 implemented with guards; terminal states
   REJECTED/PAYMENT_READY throw `IllegalStateTransitionException`; each transition has a unit test.

On failure, append an `[ERROR] <hook> FAILED` entry to `ai-delivery-log.md` with specifics.
If artifacts (pom.xml/package.json/src) do not exist yet, report which gates are blocked and stop.
