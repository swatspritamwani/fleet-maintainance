# Architecture Guardrails

> Referenced spec: `docs/functional-requirements.md` §9.1, §9.2
> These guardrails enforce the DDD layering, framework usage, and separation of concerns rules that apply to all backend code.

---

## GR-01 · `enforce_java_springboot`

| | |
|---|---|
| **Severity** | 🔴 error — halts generation and delivery |
| **Enforcement** | `pom.xml` inspection + CI dependency audit |

### Rule

All backend code must use the **Spring Boot 3.x framework**. No raw servlets, no alternative frameworks (Quarkus, Micronaut, Helidon, Vert.x, etc.).

### Rationale

Standardises the backend stack so that Spring Actuator health probes, Spring Kafka integration, and the `openapi-generator-maven-plugin` Spring server stub generation all work together without custom glue code. Simplifies onboarding and tooling (§9.1).

### What Is Allowed

- `spring-boot-starter-web` for HTTP
- `spring-boot-starter-validation` for bean validation
- `spring-boot-starter-actuator` for health/readiness (NFR-5)
- `spring-kafka` for Kafka integration
- Any Spring Boot 3.x compatible library listed in `.ai/skills/backend.md`

### What Is Prohibited

```
# Prohibited dependencies in pom.xml
io.quarkus:*
io.micronaut:*
io.helidon:*
io.vertx:*
javax.servlet:javax.servlet-api  (raw servlet — use Spring MVC instead)
```

### How It Is Checked

1. **`validate-tech-stack` before_codegen hook**: parses `pom.xml` and fails if any prohibited `groupId` is present.
2. **CI lint job**: runs `mvn dependency:analyze` and flags unlisted dependencies.
3. **ArchUnit test** (runs as part of `mvn test`):

```java
@ArchTest
static final ArchRule noAlternativeFrameworks = noClasses()
    .that().resideInAPackage("com.fleet.maintenance..")
    .should().dependOnClassesThat()
    .resideInAnyPackage("io.quarkus..", "io.micronaut..", "io.vertx..");
```

---

## GR-04 · `enforce_ddd_layering`

| | |
|---|---|
| **Severity** | 🔴 error — halts generation and delivery |
| **Enforcement** | ArchUnit tests + `validate-domain-model` before_codegen hook |

### Rule

The four-layer DDD architecture must be strictly respected (§9.2):

```
controller  →  application  →  domain  →  infrastructure
```

- **Controllers** may only call **application services**. No repository, DynamoDB SDK, or Kafka imports in controllers.
- **Application services** may call **domain objects** and **infrastructure ports** (interfaces).
- **Domain objects** (`MaintenanceRequest`, `InspectionReport`, `Decision`, `Money`, `VehicleRef`) must have **zero imports** from `org.springframework.*`, `software.amazon.awssdk.*`, `org.apache.kafka.*`, or any persistence/messaging framework.
- **Infrastructure adapters** implement the ports defined by the domain/application layer.

### Package Structure

```
com.fleet.maintenance.bff/
├── controller/          ← Spring @RestController classes only
├── application/         ← Use case orchestration, @Service
│   └── service/
├── domain/              ← Pure Java — NO framework imports
│   ├── model/           ← MaintenanceRequest, InspectionReport, Decision
│   ├── event/           ← Domain events (sealed interface + records)
│   ├── port/            ← Repository + event publisher interfaces
│   └── valueobject/     ← Money, VehicleRef
└── infrastructure/      ← DynamoDB adapters, Kafka producers
    ├── repository/
    └── kafka/
```

### Prohibited Patterns

```java
// ❌ BANNED: Repository injection in controller
@RestController
public class MaintenanceRequestController {
    @Autowired private MaintenanceRequestRepository repo; // VIOLATION GR-04
}

// ❌ BANNED: AWS SDK import in domain class
import software.amazon.awssdk.services.dynamodb.DynamoDbClient; // VIOLATION GR-04

// ❌ BANNED: Kafka import in application service
import org.springframework.kafka.core.KafkaTemplate; // VIOLATION GR-04 — use port interface
```

### Required Pattern

```java
// ✅ CORRECT: Domain port interface (no framework imports)
public interface DomainEventPublisher {
    void publish(DomainEvent event, UUID correlationId);
}

// ✅ CORRECT: Infrastructure implements the port
@Component
public class KafkaDomainEventPublisher implements DomainEventPublisher {
    private final KafkaTemplate<String, Object> kafkaTemplate;
    // ...
}

// ✅ CORRECT: Application service uses the port
@Service
public class CreateMaintenanceRequestService {
    private final MaintenanceRequestRepository repository;    // port interface
    private final DomainEventPublisher eventPublisher;        // port interface
    // No Kafka or DynamoDB SDK imports here
}
```

### How It Is Checked

```java
// ArchUnit test — runs in mvn test
@ArchTest
static final ArchRule controllerOnlyCallsApplication = classes()
    .that().resideInAPackage("..controller..")
    .should().onlyDependOnClassesThat()
    .resideInAnyPackage("..controller..", "..application..", "java..", "org.springframework.web..");

@ArchTest
static final ArchRule domainHasNoFrameworkImports = noClasses()
    .that().resideInAPackage("..domain..")
    .should().dependOnClassesThat()
    .resideInAnyPackage(
        "org.springframework..",
        "software.amazon.awssdk..",
        "org.apache.kafka.."
    );
```

---

## GR-10 · `no_business_logic_in_controller`

| | |
|---|---|
| **Severity** | 🔴 error — halts generation and delivery |
| **Enforcement** | ArchUnit tests + SpotBugs + manual review checklist |

### Rule

Controllers handle **HTTP concerns only**: deserialise request → call one application service → serialise response. All business rules, state transition logic, guard conditions, and decision-making live in the domain or application service layer (§4.1, §9.2).

### What Controllers Are Allowed to Do

```java
@RestController
@RequiredArgsConstructor
public class MaintenanceRequestController implements MaintenanceRequestsApi {

    private final CreateMaintenanceRequestService createService;

    @Override
    public ResponseEntity<RequestDto> createMaintenanceRequest(
            @Valid @RequestBody CreateRequestDto dto) {

        // ✅ Allowed: extract correlation ID from header
        // ✅ Allowed: delegate to application service
        // ✅ Allowed: map result to response DTO
        // ✅ Allowed: return HTTP status

        RequestDto result = createService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }
}
```

### What Controllers Are Prohibited from Doing

```java
// ❌ BANNED: Business rule in controller
if (request.getPriority() == Priority.CRITICAL && request.getEstimatedCost().compareTo(THRESHOLD) > 0) {
    // ... approval logic — VIOLATION GR-10
}

// ❌ BANNED: State check in controller
if (existingRequest.getStatus() != Status.CREATED) {
    throw new IllegalStateException("..."); // VIOLATION GR-10 — belongs in domain
}

// ❌ BANNED: Multiple service calls with conditional logic
RequestDto req = requestService.get(id);
if (req.getStatus().equals("ASSIGNED")) {           // VIOLATION GR-10
    inspectionService.doSomething(id);
}
```

### How It Is Checked

1. **ArchUnit rule** — controller classes must not contain `if` statements that operate on domain state:
```java
@ArchTest
static final ArchRule noBusinessLogicInControllers = noMethods()
    .that().areDeclaredInClassesThat().resideInAPackage("..controller..")
    .and().areAnnotatedWith(Override.class)
    .should().callMethodWhere(target().resideInAPackage("..domain.."));
```
2. **`review-checklist` after_codegen hook** — manually verifies controller methods delegate to exactly one service call.
3. **SpotBugs** — flags complex branching (cyclomatic complexity > 3) in controller classes.
