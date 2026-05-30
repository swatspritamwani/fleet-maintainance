# CI/CD & Quality Skills

> Tech stack reference: `docs/functional-requirements.md` §9.1
> Every pull request and merge to main must pass all pipeline stages before deployment.

---

## GitHub Actions

| | |
|---|---|
| **Version** | Current |
| **Purpose** | CI/CD pipeline: build, test, static analysis, container image scan, deploy. |

### Pipeline Stages (`.github/workflows/ci.yml`)

```yaml
name: CI/CD Pipeline

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

env:
  JAVA_VERSION: '21'
  NODE_VERSION: '20'

jobs:

  lint:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Lint OpenAPI spec
        run: npx @stoplight/spectral-cli lint api/openapi.yaml
      - name: Lint Angular
        working-directory: frontend
        run: |
          npm ci
          npm run lint

  build-and-test:
    runs-on: ubuntu-latest
    needs: lint
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: ${{ env.JAVA_VERSION }}
          distribution: temurin
      - name: Start DynamoDB Local + Kafka (for integration tests)
        run: docker compose -f infra/compose-test.yml up -d
      - name: Build and test
        run: mvn -B verify
      - name: Upload coverage to SonarQube
        run: mvn sonar:sonar
        env:
          SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}

  frontend-test:
    runs-on: ubuntu-latest
    needs: lint
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: ${{ env.NODE_VERSION }}
      - working-directory: frontend
        run: |
          npm ci
          npm run test -- --watch=false --browsers=ChromeHeadless
          npm run build -- --configuration production

  static-analysis:
    runs-on: ubuntu-latest
    needs: build-and-test
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '21', distribution: temurin }
      - run: mvn -B checkstyle:check spotbugs:check
      - name: OWASP Dependency Check
        run: mvn dependency-check:check
        env:
          NVD_API_KEY: ${{ secrets.NVD_API_KEY }}

  container-build:
    runs-on: ubuntu-latest
    needs: [build-and-test, static-analysis]
    if: github.ref == 'refs/heads/main'
    steps:
      - uses: actions/checkout@v4
      - name: Build BFF image
        run: podman build -t fleet-maintenance-bff:${{ github.sha }} -f infra/Dockerfile .
      - name: Build Frontend image
        run: podman build -t fleet-maintenance-frontend:${{ github.sha }} -f infra/Dockerfile.frontend frontend/

  trivy-scan:
    runs-on: ubuntu-latest
    needs: container-build
    steps:
      - name: Scan BFF image
        uses: aquasecurity/trivy-action@master
        with:
          image-ref: fleet-maintenance-bff:${{ github.sha }}
          exit-code: 1
          severity: HIGH,CRITICAL
      - name: Scan Frontend image
        uses: aquasecurity/trivy-action@master
        with:
          image-ref: fleet-maintenance-frontend:${{ github.sha }}
          exit-code: 1
          severity: HIGH,CRITICAL

  deploy:
    runs-on: ubuntu-latest
    needs: trivy-scan
    if: github.ref == 'refs/heads/main'
    steps:
      - uses: actions/checkout@v4
      - name: Push images to registry
        run: |
          podman push fleet-maintenance-bff:${{ github.sha }} $REGISTRY/fleet-maintenance-bff:${{ github.sha }}
          podman push fleet-maintenance-frontend:${{ github.sha }} $REGISTRY/fleet-maintenance-frontend:${{ github.sha }}
      - name: Apply Terraform
        run: |
          terraform -chdir=infra/terraform init
          terraform -chdir=infra/terraform apply -auto-approve
        env:
          AWS_ACCESS_KEY_ID: ${{ secrets.AWS_ACCESS_KEY_ID }}
          AWS_SECRET_ACCESS_KEY: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
      - name: Deploy to Kubernetes
        run: kubectl apply -f infra/k8s/
        env:
          KUBECONFIG_DATA: ${{ secrets.KUBECONFIG }}
```

### When to Use

All automated pipeline stages triggered on pull requests and merges to main.

### When NOT to Use

- Do **not** store secrets in workflow YAML files — use GitHub Actions encrypted secrets (GR-02).
- Do **not** skip the `trivy-scan` or `static-analysis` stages even for hotfixes.

---

## SonarQube

| | |
|---|---|
| **Version** | Latest LTS |
| **Purpose** | Code quality analysis and coverage gate enforcement (>= 80%, GR-09, NFR-8). |

### Quality Gate Configuration

The SonarQube Quality Gate must enforce:

| Metric | Threshold | Action on Breach |
|--------|-----------|-----------------|
| Line coverage (new code) | ≥ 80% | FAIL |
| Duplicated lines (new code) | ≤ 3% | FAIL |
| Blocker issues | 0 | FAIL |
| Critical issues | 0 | FAIL |
| Security hotspots reviewed | 100% | FAIL |

### Maven Configuration (`pom.xml`)

```xml
<properties>
  <sonar.projectKey>fleet-maintenance</sonar.projectKey>
  <sonar.host.url>${SONAR_HOST_URL}</sonar.host.url>
  <sonar.coverage.jacoco.xmlReportPaths>
    target/site/jacoco/jacoco.xml
  </sonar.coverage.jacoco.xmlReportPaths>
  <sonar.exclusions>
    **/generated/**,**/dto/**
  </sonar.exclusions>
</properties>
```

### When to Use

Post-build analysis in every CI pipeline run. Quality Gate must pass before merge is allowed.

### When NOT to Use

Not a replacement for unit tests — SonarQube analyses coverage reports produced by JaCoCo; tests must still actually run.

---

## OWASP Dependency-Check

| | |
|---|---|
| **Version** | Latest |
| **Purpose** | Scan Maven and npm dependencies for known CVEs. Fail build on HIGH or CRITICAL vulnerabilities. |

### Maven Plugin Configuration

```xml
<plugin>
  <groupId>org.owasp</groupId>
  <artifactId>dependency-check-maven</artifactId>
  <configuration>
    <failBuildOnCVSS>7</failBuildOnCVSS>        <!-- Fail on HIGH (CVSS >= 7) -->
    <suppressionFile>owasp-suppressions.xml</suppressionFile>
    <nvdApiKey>${env.NVD_API_KEY}</nvdApiKey>
    <formats>HTML,JSON</formats>
  </configuration>
  <executions>
    <execution>
      <goals><goal>check</goal></goals>
    </execution>
  </executions>
</plugin>
```

### Suppression Policy

Suppressions in `owasp-suppressions.xml` are allowed only with:
- CVE ID
- Documented reason why the vulnerability is not exploitable in this context
- Expiry date (review within 90 days)

### When to Use

CI pipeline on every build for both Maven (`pom.xml`) and npm (`package.json`).

### When NOT to Use

Not a runtime security monitoring tool — apply Trivy for container scanning and a separate runtime WAF for HTTP-level protection.

---

## Checkstyle + SpotBugs

| | |
|---|---|
| **Versions** | Checkstyle 10+, SpotBugs 4+ |
| **Purpose** | Static analysis for Java code style violations (Checkstyle) and bug patterns (SpotBugs). |

### Checkstyle Rules to Enforce

Key rules in `checkstyle.xml`:

```xml
<module name="Checker">
  <module name="TreeWalker">
    <!-- No wildcard imports -->
    <module name="AvoidStarImport"/>
    <!-- Javadoc on public methods -->
    <module name="JavadocMethod">
      <property name="scope" value="public"/>
    </module>
    <!-- Max method length -->
    <module name="MethodLength">
      <property name="max" value="40"/>
    </module>
    <!-- No magic numbers in domain/application layers -->
    <module name="MagicNumber">
      <property name="ignoreNumbers" value="-1, 0, 1, 2"/>
    </module>
  </module>
</module>
```

### SpotBugs Exclude Filter

`spotbugs-exclude.xml` should suppress noise on generated code:

```xml
<FindBugsFilter>
  <Match>
    <Package name="~com\.fleet\.maintenance\.bff\.api\..*"/>
  </Match>
</FindBugsFilter>
```

### When to Use

All backend Java source files. Run via `mvn checkstyle:check` and `mvn spotbugs:check` in CI (`static-analysis` job).

### When NOT to Use

Frontend TypeScript — use ESLint with `@typescript-eslint` plugin for frontend static analysis instead.

---

## Trivy

| | |
|---|---|
| **Version** | Latest |
| **Purpose** | Container image vulnerability scanning before push to registry. |

### Scan Configuration

```yaml
# In GitHub Actions (trivy-scan job)
- uses: aquasecurity/trivy-action@master
  with:
    image-ref: fleet-maintenance-bff:${{ github.sha }}
    format: table
    exit-code: 1          # Fail pipeline on findings
    ignore-unfixed: true  # Only flag vulnerabilities with available fixes
    severity: HIGH,CRITICAL
    output: trivy-report.txt
```

### Trivy vs OWASP Dependency-Check

| Tool | Scans | Layer |
|------|-------|-------|
| OWASP Dependency-Check | Maven + npm source dependencies | Build time |
| Trivy | Container image layers (OS packages + app JARs) | Image build time |

Both run independently — a dependency may pass OWASP but introduce a vulnerable OS package detected only by Trivy.

### When to Use

CI pipeline after every container image build, before pushing to the registry. Fail pipeline on HIGH or CRITICAL CVEs that have available fixes.

### When NOT to Use

Not a substitute for OWASP Dependency-Check at the source level. Do not skip Trivy scans for "minor" releases — run on every image build.
