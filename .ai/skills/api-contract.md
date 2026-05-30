# API Contract Skills

> Tech stack reference: `docs/functional-requirements.md` §9.1, §9.2, §9.3, Appendix A & B
> The OpenAPI spec is the **single source of truth** for all endpoints and DTOs. Never hand-write controller signatures that diverge from it (GR-07).

---

## OpenAPI 3.1

| | |
|---|---|
| **Version** | 3.1 (YAML format) |
| **File location** | `api/openapi.yaml` |
| **Purpose** | Contract-first API definition covering all 10 endpoints in Appendix A and all 4 DTOs in Appendix B. |

### File Structure Template

```yaml
openapi: "3.1.0"
info:
  title: Fleet Maintenance BFF API
  version: "1.0.0"
servers:
  - url: /api/v1

paths:
  /maintenance-requests:
    post:
      operationId: createMaintenanceRequest
      tags: [MaintenanceRequests]
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/CreateRequestDto'
      responses:
        '201':
          description: Request created
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/RequestDto'
        '400':
          $ref: '#/components/responses/BadRequest'
    get:
      operationId: listMaintenanceRequests
      # ... (filter params, pagination)

  /maintenance-requests/{id}:
    get:
      operationId: getMaintenanceRequest
      # ...

  /maintenance-requests/{id}/assignments:
    post:
      operationId: assignProvider
      # ...

  /maintenance-requests/{id}/inspections:
    post:
      operationId: submitInspection
      # ...
    get:
      operationId: listInspections
      # ...

  /maintenance-requests/{id}/decisions:
    post:
      operationId: submitDecision
      # ...
    get:
      operationId: listDecisions
      # ...

  /events:
    get:
      operationId: listEvents
      # ...

  /service-providers:
    get:
      operationId: listServiceProviders
      # ...

components:
  schemas:
    CreateRequestDto:
      type: object
      required: [vehicleId, description, priority]
      properties:
        vehicleId: { type: string }
        description: { type: string, maxLength: 2000 }
        priority: { $ref: '#/components/schemas/Priority' }

    AssignProviderDto:
      type: object
      required: [providerId]
      properties:
        providerId: { type: string, format: uuid }

    InspectionReportDto:
      type: object
      required: [findings, estimatedCost, estimatedDurationDays]
      properties:
        findings: { type: string, maxLength: 5000 }
        estimatedCost: { type: number, minimum: 0 }
        estimatedDurationDays: { type: integer, minimum: 1 }
        attachments:
          type: array
          items: { type: string, format: uri }

    DecisionDto:
      type: object
      required: [outcome]
      properties:
        outcome: { $ref: '#/components/schemas/DecisionOutcome' }
        remarks: { type: string, maxLength: 2000 }

    Priority:
      type: string
      enum: [LOW, MEDIUM, HIGH, CRITICAL]

    DecisionOutcome:
      type: string
      enum: [APPROVED, REJECTED, INFO_REQUESTED]

    ProblemDetail:
      type: object
      required: [type, title, status]
      properties:
        type: { type: string, format: uri }
        title: { type: string }
        status: { type: integer }
        detail: { type: string }
        instance: { type: string, format: uri }

  responses:
    BadRequest:
      description: Validation error
      content:
        application/problem+json:
          schema:
            $ref: '#/components/schemas/ProblemDetail'
```

### API Naming Conventions (§9.3)

- Base path: `/api/v1/`
- Resources: plural nouns, kebab-case (e.g., `maintenance-requests`, `service-providers`)
- Actions as sub-resources: `POST .../assignments`, `POST .../inspections`, `POST .../decisions`
- No verbs in paths (GR-03)

### When to Use

Define or update `api/openapi.yaml` whenever any endpoint or DTO changes. All server and client code is regenerated from this file.

### When NOT to Use

- Do **not** use OpenAPI 2.0 (Swagger 2.0) format.
- Do **not** modify generated server interfaces or Angular client code directly — change the spec and regenerate.

---

## openapi-generator-maven-plugin (Backend Server Stubs)

| | |
|---|---|
| **Version** | Latest compatible with OpenAPI 3.1 |
| **Purpose** | Generates Spring Boot server interface from `api/openapi.yaml`. All controllers implement these interfaces (GR-07). |

### Maven Configuration

```xml
<plugin>
  <groupId>org.openapitools</groupId>
  <artifactId>openapi-generator-maven-plugin</artifactId>
  <executions>
    <execution>
      <id>generate-server</id>
      <goals><goal>generate</goal></goals>
      <configuration>
        <inputSpec>${project.basedir}/../../api/openapi.yaml</inputSpec>
        <generatorName>spring</generatorName>
        <apiPackage>com.fleet.maintenance.bff.api</apiPackage>
        <modelPackage>com.fleet.maintenance.bff.dto</modelPackage>
        <configOptions>
          <interfaceOnly>true</interfaceOnly>
          <useSpringBoot3>true</useSpringBoot3>
          <useTags>true</useTags>
          <dateLibrary>java8</dateLibrary>
          <openApiNullable>false</openApiNullable>
        </configOptions>
      </configuration>
    </execution>
  </executions>
</plugin>
```

### Generated Interface Pattern

```java
// Auto-generated — do NOT edit manually
@Tag(name = "MaintenanceRequests")
public interface MaintenanceRequestsApi {
    @PostMapping("/maintenance-requests")
    ResponseEntity<RequestDto> createMaintenanceRequest(
        @Valid @RequestBody CreateRequestDto createRequestDto
    );
    // ...
}

// Hand-written controller implements generated interface
@RestController
public class MaintenanceRequestController implements MaintenanceRequestsApi {
    @Override
    public ResponseEntity<RequestDto> createMaintenanceRequest(CreateRequestDto dto) {
        // delegate to application service only (GR-10)
    }
}
```

### When to Use

Backend build phase (`mvn generate-sources`). Run after any change to `api/openapi.yaml`.

### When NOT to Use

Do not generate domain model POJOs — domain objects are hand-written pure Java (GR-04). Only generate API interfaces and DTO classes.

---

## openapi-generator (Angular TypeScript Client)

| | |
|---|---|
| **Version** | Latest compatible with Angular 17 |
| **Purpose** | Generates typed Angular HTTP service and DTO interfaces from `api/openapi.yaml`. All Angular HTTP calls use the generated services. |

### Generation Command

```bash
openapi-generator-cli generate \
  -i api/openapi.yaml \
  -g typescript-angular \
  -o frontend/src/app/api/generated \
  --additional-properties=ngVersion=17,supportsES6=true,withInterfaces=true
```

### Generated Code Usage

```typescript
// Generated service — do NOT edit manually
@Injectable({ providedIn: 'root' })
export class MaintenanceRequestsService {
  constructor(private http: HttpClient) {}

  createMaintenanceRequest(body: CreateRequestDto): Observable<RequestDto> {
    return this.http.post<RequestDto>('/api/v1/maintenance-requests', body);
  }
}

// Component uses generated service
@Component({ standalone: true, ... })
export class CreateRequestComponent {
  constructor(private api: MaintenanceRequestsService) {}

  submit(form: CreateRequestDto) {
    this.api.createMaintenanceRequest(form).pipe(
      catchError(err => { /* RFC 7807 error handling */ return EMPTY; })
    ).subscribe(result => this.router.navigate(['/requests', result.requestId]));
  }
}
```

### When to Use

Frontend build phase. Re-run whenever `api/openapi.yaml` changes.

### When NOT to Use

- Do **not** hand-write `HttpClient` calls for any endpoint defined in `api/openapi.yaml`.
- Do **not** modify files inside `frontend/src/app/api/generated/` — they are regenerated on every build.
- Customise via generator configuration templates, not by editing generated files.
