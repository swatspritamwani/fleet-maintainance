# Stage 1: Build — eclipse-temurin:21-jdk-alpine
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

# Copy POMs first for better layer caching
COPY pom.xml .
COPY fleet-maintenance-domain/pom.xml fleet-maintenance-domain/pom.xml
COPY fleet-maintenance-application/pom.xml fleet-maintenance-application/pom.xml
COPY fleet-maintenance-infrastructure/pom.xml fleet-maintenance-infrastructure/pom.xml
COPY fleet-maintenance-bff/pom.xml fleet-maintenance-bff/pom.xml

# Download dependencies (cached unless POM changes)
RUN mvn -B dependency:go-offline -pl fleet-maintenance-bff -am 2>/dev/null || true

# Copy sources and API contract
COPY api ./api
COPY fleet-maintenance-domain/src ./fleet-maintenance-domain/src
COPY fleet-maintenance-application/src ./fleet-maintenance-application/src
COPY fleet-maintenance-infrastructure/src ./fleet-maintenance-infrastructure/src
COPY fleet-maintenance-bff/src ./fleet-maintenance-bff/src

RUN mvn -B package -DskipTests -pl fleet-maintenance-bff -am

# Stage 2: Runtime — minimal JRE image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Non-root user (GR-02)
RUN addgroup -S fleet && adduser -S fleet -G fleet
USER fleet

COPY --from=builder /build/fleet-maintenance-bff/target/fleet-maintenance-bff.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
