# Infrastructure Skills

> Tech stack reference: `docs/functional-requirements.md` §9.1
> All infrastructure is defined as code. No manual cloud console changes. Secrets must never appear in source files (GR-02).

---

## Docker / Podman

| | |
|---|---|
| **Version** | Podman 4+ (preferred per §9.1); Docker compatible |
| **Purpose** | Container image builds for backend services and the Angular frontend. |

### Multi-Stage Dockerfile (Backend)

```dockerfile
# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B package -DskipTests

# Stage 2: Runtime — minimal JRE image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Non-root user (security best practice, GR-02)
RUN addgroup -S fleet && adduser -S fleet -G fleet
USER fleet

COPY --from=builder /build/target/fleet-maintenance-bff.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Angular Frontend Dockerfile

```dockerfile
# Stage 1: Build Angular app
FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build -- --configuration production

# Stage 2: Serve via nginx
FROM nginx:alpine
COPY --from=builder /app/dist/fleet-maintenance-frontend /usr/share/nginx/html
COPY nginx.conf /etc/nginx/nginx.conf
EXPOSE 80
```

### When to Use

- Building and tagging images for all services in CI (`podman build`).
- Local development with `podman-compose` (BFF + DynamoDB Local + Kafka).
- CI/CD image build stage before Trivy scan.

### When NOT to Use

- Do **not** run containers as root (non-root user required in all Dockerfiles).
- Do **not** embed secrets, API keys, or credentials in `ENV` or `COPY` steps (GR-02) — pass via Kubernetes Secrets at runtime.
- Do **not** use `latest` tag in production — always pin to a digest or semver tag.

---

## Kubernetes

| | |
|---|---|
| **Version** | 1.28+ |
| **Purpose** | Orchestration for all service deployments. Deployments, Services, ConfigMaps, Secrets, HPA, and health probes. |

### Deployment Manifest Template (BFF Service)

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: fleet-maintenance-bff
  namespace: fleet-maintenance
spec:
  replicas: 2
  selector:
    matchLabels:
      app: fleet-maintenance-bff
  template:
    metadata:
      labels:
        app: fleet-maintenance-bff
    spec:
      containers:
        - name: bff
          image: fleet-maintenance-bff:1.0.0
          ports:
            - containerPort: 8080
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: prod
            - name: KAFKA_BOOTSTRAP_SERVERS
              valueFrom:
                configMapKeyRef:
                  name: fleet-maintenance-config
                  key: kafka.bootstrap-servers
            - name: AWS_ACCESS_KEY_ID
              valueFrom:
                secretKeyRef:
                  name: fleet-maintenance-secrets
                  key: aws-access-key-id
            - name: AWS_SECRET_ACCESS_KEY
              valueFrom:
                secretKeyRef:
                  name: fleet-maintenance-secrets
                  key: aws-secret-access-key
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 20
            periodSeconds: 5
          resources:
            requests:
              cpu: 500m
              memory: 512Mi
            limits:
              cpu: 1000m
              memory: 1Gi
```

### Service Manifest

```yaml
apiVersion: v1
kind: Service
metadata:
  name: fleet-maintenance-bff
  namespace: fleet-maintenance
spec:
  selector:
    app: fleet-maintenance-bff
  ports:
    - port: 80
      targetPort: 8080
  type: ClusterIP
```

### HorizontalPodAutoscaler

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: fleet-maintenance-bff-hpa
  namespace: fleet-maintenance
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: fleet-maintenance-bff
  minReplicas: 2
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
```

### ConfigMap (Non-sensitive config)

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: fleet-maintenance-config
  namespace: fleet-maintenance
data:
  kafka.bootstrap-servers: "kafka-broker:9092"
  dynamodb.endpoint: ""          # empty = use AWS; set for local dev
  dynamodb.table-name: "fleet-maintenance"
  spring.profiles.active: "prod"
```

### When to Use

Production and staging deployments. Health/readiness probes via Spring Actuator (NFR-5). HPA for scaling under load (NFR-6).

### When NOT to Use

- Local development — use Podman Compose or DynamoDB Local instead of a full K8s setup.
- Do **not** hardcode environment-specific values in Deployment manifests — use ConfigMaps and Secrets.
- Do **not** store secrets in ConfigMaps — use Kubernetes Secrets or an external vault.

---

## Terraform

| | |
|---|---|
| **Version** | 1.6+ |
| **Purpose** | IaC for DynamoDB table provisioning, Kafka topic creation, and Kubernetes namespace/ConfigMap setup. |

### DynamoDB Table Definition

```hcl
# infra/terraform/dynamodb.tf

resource "aws_dynamodb_table" "fleet_maintenance" {
  name         = "fleet-maintenance"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "PK"
  range_key    = "SK"

  attribute {
    name = "PK"
    type = "S"
  }
  attribute {
    name = "SK"
    type = "S"
  }
  attribute {
    name = "STATUS"      # For GSI: filter requests by status
    type = "S"
  }
  attribute {
    name = "CREATED_AT"
    type = "S"
  }

  global_secondary_index {
    name            = "status-createdAt-index"
    hash_key        = "STATUS"
    range_key       = "CREATED_AT"
    projection_type = "ALL"
  }

  tags = {
    Project     = "fleet-maintenance"
    Environment = var.environment
  }
}

# Outbox table
resource "aws_dynamodb_table" "fleet_maintenance_outbox" {
  name         = "fleet-maintenance-outbox"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "PK"
  range_key    = "SK"

  attribute { name = "PK"; type = "S" }
  attribute { name = "SK"; type = "S" }
  attribute { name = "STATUS"; type = "S" }
  attribute { name = "CREATED_AT"; type = "S" }

  global_secondary_index {
    name            = "status-createdAt-index"
    hash_key        = "STATUS"
    range_key       = "CREATED_AT"
    projection_type = "ALL"
  }

  ttl {
    attribute_name = "TTL"
    enabled        = true
  }
}
```

### Kafka Topics Definition

```hcl
# infra/terraform/kafka.tf
# (Using Confluent Terraform provider or MSK topic resource as appropriate)

locals {
  kafka_topics = [
    "maintenance.request.created",
    "maintenance.request.assigned",
    "maintenance.inspection.submitted",
    "maintenance.decision.approved",
    "maintenance.decision.rejected",
    "maintenance.decision.info-requested",
    "maintenance.payment.ready"
  ]
}

resource "kafka_topic" "fleet_topics" {
  for_each           = toset(local.kafka_topics)
  name               = each.value
  replication_factor = 3
  partitions         = 6

  config = {
    "retention.ms"    = "604800000"   # 7 days
    "cleanup.policy"  = "delete"
    "min.insync.replicas" = "2"
  }
}
```

### When to Use

All infrastructure provisioning: DynamoDB tables (including outbox table), Kafka topics (all 7 from §6.1), Kubernetes namespaces and ConfigMaps.

### When NOT to Use

- Application-level runtime configuration — use Kubernetes ConfigMaps managed by Terraform, not inline HCL values.
- Do **not** store secret values in Terraform state — use `aws_secretsmanager_secret` or Vault and reference by ARN.
- Do **not** apply Terraform manually in production — all applies go through the CI/CD pipeline with a plan approval step.
