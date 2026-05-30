---
description: Drive the full contract-first codegen workflow for one bounded context, with gates.
argument-hint: [module e.g. domain | application | infrastructure | bff | frontend | tests | infra]
---

Generate code for module: **$ARGUMENTS** (if empty, start with `domain`).

Delegate to the `codegen-agent` subagent and follow `.ai/agents/codegen-agent.yaml`:

1. Run `/before-codegen`. **Halt if any gate fails.**
2. Generate only the requested module, obeying all guardrails (GR-01..GR-12) and using only
   technologies from `.ai/skills/*.md`. Reference the relevant FR sections and ACs.
3. Run `/after-codegen` for the affected scope. **Halt if any gate fails.**
4. Append a session entry to `ai-delivery-log.md` (output, validation, errors, corrections).

Order across sessions: `domain` -> `application` -> `infrastructure` -> `bff` -> `frontend` -> `tests` -> `infra`.
Never hand-edit generated OpenAPI/Angular client code. Never publish to Kafka outside the outbox.
