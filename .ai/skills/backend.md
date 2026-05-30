# Backend Skills

> Tech stack reference: `docs/functional-requirements.md` §9.1
> All backend code must use these technologies only (GR-01). Any unlisted dependency must be approved before use (`validate-tech-stack` hook).

---

## Java 21 (LTS)

| | |
|---|---|
| **Version** | 21 LTS |
| **Purpose** | Primary server-side language for all backend modules. |

### Key Features to Use

- **Virtual threads** (`Thread.ofVirtual()`) — use for Kafka consumer threads and async I/O to maximise throughput without blocking platform threads.
- **Pattern matching for `instanceof`** — use in domain switch expressions for state transition dispatch.
- **Sealed classes** — use for domain event type hierarchies (e.g., `sealed interface DomainEvent permits RequestCreatedEvent, RequestAssignedEvent, ...`).
- **Records** — use for immutable value objects (`Money`, `VehicleRef`) and DTOs where MapStruct is not needed.
- **Text blocks** — use for multi-line JSON templates in tests.

### When to Use

All backend service implementation: domain layer, application services, infrastructure adapters, BFF controllers.

### When NOT to Use

- Frontend code (Angular/TypeScript).
- Scripting or infrastructure tasks (use shell scripts or Terraform HCL).
- Do **not** use deprecated `Thread.sleep()` for rate limiting — use `ScheduledExecutorService` or Spring's `@Scheduled`.

---

## Spring Boot 3.x

| | |
|---|---|
| **Version** | 3.x (latest patch; align with BOM) |
| **Purpose** | Application framework for web, Kafka, health probes, and validation. |

### Starters to Include in `pom.xml`

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.kafka</groupId>
  <artifactId>spring-kafka</artifactId>
</dependency>
```

### When to Use

- HTTP request handling (`@RestController`, `@RequestMapping`).
- Dependency injection (`@Service`, `@Component`, `@Configuration`).
- Bean validation on incoming DTOs (`@Valid`, `@NotBlank`, `@Min`).
- Health and readiness endpoints for Kubernetes probes (NFR-5): `/actuator/health/liveness`, `/actuator/health/readiness`.
- Kafka producer/consumer configuration via `application.yml`.

### When NOT to Use

- **Domain layer classes must have zero Spring annotations** — no `@Service`, `@Component`, `@Autowired` on `MaintenanceRequest`, `InspectionReport`, `Decision`, `Money`, or `VehicleRef` (GR-04).
- Do **not** use Spring Data JPA or Spring Data DynamoDB — use the DynamoDB Enhanced Client directly in the infrastructure layer.
- Do **not** use Spring Security for authentication — the spec assumes an external IdP (§1.3). Use Spring Security only if adding JWT validation middleware for role extraction.

---

## Maven

| | |
|---|---|
| **Version** | 3.9+ |
| **Purpose** | Multi-module build, dependency management, code generation via plugins. |

### Project Structure

```
fleet-maintenance/                  ← parent POM (packaging: pom)
├── fleet-maintenance-domain/       ← pure Java domain module
├── fleet-maintenance-infrastructure/ ← DynamoDB + Kafka adapters
├── fleet-maintenance-bff/          ← Spring Boot BFF service
└── fleet-maintenance-frontend/     ← Angular frontend (optional Maven wrapper)
```

### Key Plugins

```xml
<!-- OpenAPI server stub generation -->
<plugin>
  <groupId>org.openapitools</groupId>
  <artifactId>openapi-generator-maven-plugin</artifactId>
  <configuration>
    <inputSpec>${project.basedir}/../api/openapi.yaml</inputSpec>
    <generatorName>spring</generatorName>
    <configOptions>
      <interfaceOnly>true</interfaceOnly>
      <useSpringBoot3>true</useSpringBoot3>
    </configOptions>
  </configuration>
</plugin>

<!-- Code coverage -->
<plugin>
  <groupId>org.jacoco</groupId>
  <artifactId>jacoco-maven-plugin</artifactId>
</plugin>

<!-- Static analysis -->
<plugin>
  <groupId>com.github.spotbugs</groupId>
  <artifactId>spotbugs-maven-plugin</artifactId>
</plugin>
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-checkstyle-plugin</artifactId>
</plugin>
```

### When to Use

All build (`mvn package`), test (`mvn test`), and quality gate (`mvn verify`) tasks.

### When NOT to Use

Frontend build — use Angular CLI (`ng build`). Infrastructure provisioning — use Terraform.

---

## Amazon DynamoDB + DynamoDB Local

| | |
|---|---|
| **Version** | AWS SDK for Java v2 (latest); DynamoDB Local for dev/test |
| **Purpose** | Primary NoSQL data store using single-table design. |

### Single-Table Design (§3.2)

| Entity | PK | SK | GSI-1 PK | GSI-1 SK |
|--------|----|----|----------|---------|
| `MaintenanceRequest` | `REQ#<requestId>` | `REQ#<requestId>` | `STATUS#<status>` | `CREATED#<createdAt>` |
| `InspectionReport` | `REQ#<requestId>` | `INSP#<reportId>` | — | — |
| `Decision` | `REQ#<requestId>` | `DEC#<decisionId>` | — | — |
| `OutboxEvent` | `OUTBOX#<eventId>` | `OUTBOX#<eventId>` | `PUBLISHED#false` | `CREATED#<createdAt>` |

### When to Use

- All persistence of `MaintenanceRequest` aggregate, `InspectionReport`, `Decision`, and outbox events.
- GSI queries for filtering requests by status (Request List screen, §8.1).
- Transactional writes (`TransactWriteItems`) to atomically persist domain state + outbox entry.

### When NOT to Use

- **No DynamoDB annotations (`@DynamoDbBean`, `@DynamoDbPartitionKey`) on domain classes** — only on infrastructure-layer mapper/entity classes (GR-04).
- Do not use DynamoDB for session state or caching — use in-memory or Redis if needed.
- For local development and tests: use DynamoDB Local (started via Docker/Podman).

---

## AWS SDK for Java v2 – DynamoDB Enhanced Client

| | |
|---|---|
| **Version** | AWS SDK v2 (latest) |
| **Purpose** | Type-safe DynamoDB access via `DynamoDbEnhancedClient`. |

### Usage Pattern

```java
// Infrastructure layer ONLY — never in domain or application layer
@DynamoDbBean
public class MaintenanceRequestRecord {
    @DynamoDbPartitionKey
    private String pk;
    @DynamoDbSortKey
    private String sk;
    // ... mapped fields
}

DynamoDbTable<MaintenanceRequestRecord> table =
    enhancedClient.table("fleet-maintenance", TableSchema.fromBean(MaintenanceRequestRecord.class));
```

### When to Use

Infrastructure adapter layer only (`infrastructure/repository/`): save, load, query, transactional write for domain aggregates.

### When NOT to Use

Any class in `domain/` or `application/` packages. No `DynamoDbEnhancedClient` injection outside `infrastructure/` (GR-04).

---

## Spring Kafka

| | |
|---|---|
| **Version** | Aligned with Spring Boot 3.x BOM |
| **Purpose** | Kafka producer and consumer integration; outbox polling publisher. |

### Producer Configuration (`application.yml`)

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: all
      enable-idempotence: true
      retries: 3
    consumer:
      group-id: fleet-maintenance-bff-group
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
```

### When to Use

Infrastructure adapter layer: Kafka producers (`KafkaTemplate`), outbox polling publisher (`@Scheduled`), consumer group definitions.

### When NOT to Use

Domain or application layers must not import `KafkaTemplate` or any `org.apache.kafka.*` type directly — define a `DomainEventPublisher` port interface in the domain module; implement it in infrastructure.

---

## MapStruct

| | |
|---|---|
| **Version** | 1.5+ |
| **Purpose** | Compile-time, type-safe mapping between DTOs and domain objects. |

### Typical Mapper Interface

```java
@Mapper(componentModel = "spring")
public interface MaintenanceRequestMapper {
    MaintenanceRequest toDomain(CreateRequestDto dto);
    RequestDto toDto(MaintenanceRequest domain);
    InspectionReport toDomain(InspectionReportDto dto);
}
```

### When to Use

Application service layer: map incoming request DTOs (Appendix B) to domain commands, and domain objects to response DTOs.

### When NOT to Use

- Do **not** use MapStruct for DynamoDB item mapping — use Enhanced Client annotations on infrastructure record classes.
- Do **not** use MapStruct for Kafka event envelope construction — build the envelope explicitly to ensure `eventId`, `timestamp`, and `correlationId` are always set (GR-06).
