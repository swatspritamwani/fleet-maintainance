# Security Guardrails

> Referenced spec: `docs/functional-requirements.md` §7 NFR-3, §9.2
> These guardrails protect against credential exposure and enforce role-based access at the API level.

---

## GR-02 · `no_plain_text_passwords`

| | |
|---|---|
| **Severity** | 🔴 error — halts generation and delivery |
| **Enforcement** | `gitleaks` / `trufflehog` secret scanning + CI review + `review-checklist` hook |

### Rule

No passwords, API keys, AWS credentials, database connection strings, JWT signing secrets, or any sensitive value of any kind may appear as a literal string in source code, configuration files committed to version control, Dockerfiles, GitHub Actions workflow YAML, or Terraform HCL files (NFR-3).

### Where Secrets Must Live Instead

| Secret Type | Approved Storage |
|-------------|-----------------|
| AWS credentials (DynamoDB) | Kubernetes Secret → injected as `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` env vars |
| Kafka bootstrap servers | Kubernetes ConfigMap (non-sensitive); credentials (if SASL) via Kubernetes Secret |
| JWT signing key / IdP client secret | Kubernetes Secret or AWS Secrets Manager |
| SonarQube token | GitHub Actions encrypted secret (`secrets.SONAR_TOKEN`) |
| NVD API key (OWASP) | GitHub Actions encrypted secret (`secrets.NVD_API_KEY`) |
| Database passwords | Kubernetes Secret (no DynamoDB passwords, but applies if any RDBMS is ever added) |

### Prohibited Patterns

```java
// ❌ BANNED: hardcoded AWS credentials
AmazonDynamoDBClientBuilder.standard()
    .withCredentials(new AWSStaticCredentialsProvider(
        new BasicAWSCredentials("AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY")
    ));

// ❌ BANNED: hardcoded Kafka password in application.yml
spring:
  kafka:
    properties:
      sasl.jaas.config: "... password=\"my-secret-password\""

// ❌ BANNED: secret in Dockerfile
ENV AWS_SECRET_ACCESS_KEY=wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY
```

### Required Pattern

```yaml
# application.yml — reference env vars, never literal values
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}

# Kubernetes Deployment — inject from Secret
env:
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
```

### Suppression Policy for `.gitignore`

The following files must be in `.gitignore` and never committed:

```
.env
.env.*
application-local.yml
application-secrets.yml
**/secrets/**
terraform.tfvars          # if it contains real values
*.tfstate
*.tfstate.backup
```

### How It Is Checked

1. **`gitleaks` scan** — runs on every PR in CI:
```yaml
- name: Secret scan
  uses: gitleaks/gitleaks-action@v2
  env:
    GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

2. **`review-checklist` after_codegen hook** — searches all generated files for patterns matching known secret formats:
```bash
# Patterns checked
grep -rn "AKIA[0-9A-Z]{16}" src/          # AWS Access Key
grep -rn "password\s*=\s*['\"][^$]" src/  # Literal password (not env var reference)
grep -rn "secret\s*=\s*['\"][^$]" src/    # Literal secret
```

3. **Trivy secret scanning** — `trivy fs . --scanners secret` runs as part of `static-analysis` CI job.

4. **Terraform `sensitive` attribute** — all secret Terraform variables must be marked:
```hcl
variable "aws_secret_access_key" {
  type      = string
  sensitive = true    # prevents value appearing in plan output
}
```
