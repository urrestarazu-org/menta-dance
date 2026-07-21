# Pipeline CI/CD

[← Volver al índice](./README.md) | [← Matriz de Riesgos](./15-RISK-MATRIX.md)

---

## 1. Visión General

Pipeline de Integración Continua y Despliegue Continuo para el monorepo Menta Dance.

### 1.1 Objetivos

- Automatizar build, test y deploy
- Detectar errores temprano (fail fast)
- Garantizar calidad antes de merge
- Deploys reproducibles y auditables

### 1.2 Herramientas

| Herramienta | Propósito |
|-------------|-----------|
| **GitHub Actions** | CI/CD principal |
| **Gradle** | Build y tests backend |
| **Node/npm** | Build assets BFF (Tailwind) |
| **Docker** | Containerización |
| **Checkstyle** | Estilo de código |
| **JaCoCo** | Cobertura de código |
| **SonarCloud** | Análisis estático y seguridad |
| **Trivy** | Escaneo de vulnerabilidades |
| **Playwright** | Tests E2E (BFF) |
| **Gatling** | Tests de carga |

> Ver [ADR-0024](./adr/0024-technology-baseline.md) para versiones exactas.

---

## 2. Arquitectura del Pipeline

```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│   Commit    │───▶│    Build    │───▶│    Test     │───▶│   Analyze   │
│             │    │  (Gradle)   │    │  (JUnit)    │    │  (JaCoCo+   │
│             │    │             │    │             │    │   Trivy)    │
└─────────────┘    └─────────────┘    └─────────────┘    └─────────────┘
                                                                │
                         ┌──────────────────────────────────────┘
                         ▼
              ┌─────────────────────┐
              │   Quality Gate?     │
              │   Coverage > 80%?   │
              │   Vulnerabilities?  │
              └─────────────────────┘
                    │         │
                 PASS       FAIL
                    │         │
                    ▼         ▼
         ┌──────────────┐  ┌──────────────┐
         │ Build Docker │  │ Block Merge  │
         │    Images    │  │   + Notify   │
         └──────────────┘  └──────────────┘
                    │
                    ▼ (solo main)
         ┌──────────────┐
         │   Deploy to  │
         │   Staging    │
         └──────────────┘
```

---

## 3. Workflows de GitHub Actions

### 3.1 CI - Pull Requests

**Archivo:** `.github/workflows/ci.yml`

```yaml
name: CI Pipeline

on:
  pull_request:
    branches: [main, develop]
  push:
    branches: [main, develop]

jobs:
  build:
    runs-on: ubuntu-latest

    services:
      mysql:
        image: mysql:8.0
        env:
          MYSQL_ROOT_PASSWORD: test
          MYSQL_DATABASE: menta_test
        ports:
          - 3306:3306
        options: >-
          --health-cmd="mysqladmin ping"
          --health-interval=10s
          --health-timeout=5s
          --health-retries=3

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Setup Java 21
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '21'
          cache: 'gradle'

      - name: Grant execute permission
        run: chmod +x gradlew

      - name: Setup Node.js (BFF assets)
        uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'npm'
          cache-dependency-path: bff/package-lock.json

      - name: Build BFF assets (Tailwind)
        run: |
          cd bff
          npm ci
          npm run build:css

      - name: Run Checkstyle
        run: ./gradlew checkstyleMain checkstyleTest

      - name: Build project
        run: ./gradlew build -x test

      - name: Run tests
        run: ./gradlew test

      - name: Generate coverage report
        run: ./gradlew jacocoTestReport

      - name: Check coverage threshold
        run: ./gradlew jacocoTestCoverageVerification

      - name: SonarCloud analysis
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
        run: ./gradlew sonar

      - name: Upload test results
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: test-results
          path: '**/build/reports/tests/'

  security-scan:
    runs-on: ubuntu-latest
    needs: build

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Run Trivy vulnerability scanner
        uses: aquasecurity/trivy-action@master
        with:
          scan-type: 'fs'
          scan-ref: '.'
          severity: 'HIGH,CRITICAL'
          exit-code: '1'
          format: 'table'

  architecture-test:
    runs-on: ubuntu-latest
    needs: build

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Setup Java 21
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '21'
          cache: 'gradle'

      - name: Run ArchUnit tests
        run: ./gradlew test --tests "*ArchitectureTest"

  e2e-test:
    runs-on: ubuntu-latest
    needs: build

    services:
      mysql:
        image: mysql:8.0
        env:
          MYSQL_ROOT_PASSWORD: test
          MYSQL_DATABASE: menta_test
        ports:
          - 3306:3306

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Setup Java 21
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '21'
          cache: 'gradle'

      - name: Setup Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '20'

      - name: Install Playwright
        run: npx playwright install --with-deps chromium

      - name: Start API and BFF
        run: |
          ./gradlew :api:app:bootRun &
          ./gradlew :bff:bootRun &
          sleep 30

      - name: Run E2E tests
        run: npx playwright test
        working-directory: e2e

      - name: Upload Playwright report
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: playwright-report
          path: e2e/playwright-report/

  load-test:
    runs-on: ubuntu-latest
    needs: [build, e2e-test]
    if: github.ref == 'refs/heads/main'

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Setup Java 21
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '21'
          cache: 'gradle'

      - name: Run Gatling tests
        run: ./gradlew gatlingRun
        env:
          TARGET_URL: ${{ secrets.STAGING_URL }}

      - name: Upload Gatling report
        uses: actions/upload-artifact@v4
        with:
          name: gatling-report
          path: build/reports/gatling/
```

### 3.2 Deploy - Staging

**Archivo:** `.github/workflows/deploy-staging.yml`

```yaml
name: Deploy to Staging

on:
  push:
    branches: [main]

jobs:
  deploy:
    runs-on: ubuntu-latest
    environment: staging

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Setup Java 21
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '21'
          cache: 'gradle'

      - name: Build API JAR
        run: ./gradlew :api:app:bootJar -x test

      - name: Build BFF JAR
        run: ./gradlew :bff:bootJar -x test

      - name: Build Docker images
        run: |
          docker build -t menta-api:${{ github.sha }} -f api/Dockerfile .
          docker build -t menta-bff:${{ github.sha }} -f bff/Dockerfile .

      - name: Deploy to staging server
        uses: appleboy/ssh-action@v1.0.0
        with:
          host: ${{ secrets.STAGING_HOST }}
          username: ${{ secrets.STAGING_USER }}
          key: ${{ secrets.STAGING_SSH_KEY }}
          script: |
            cd /opt/menta
            docker-compose pull
            docker-compose up -d --force-recreate
            docker system prune -f

      - name: Verify deployment
        run: |
          sleep 30
          curl -f http://${{ secrets.STAGING_HOST }}:8081/actuator/health || exit 1
          curl -f http://${{ secrets.STAGING_HOST }}:8080/actuator/health || exit 1
```

---

## 4. Secrets Requeridos

| Secret | Descripción |
|--------|-------------|
| `STAGING_HOST` | IP/hostname del servidor staging |
| `STAGING_USER` | Usuario SSH |
| `STAGING_SSH_KEY` | Clave privada SSH |
| `STAGING_URL` | URL base para tests de carga |
| `SONAR_TOKEN` | Token de SonarCloud |

---

## 5. Branch Protection Rules

### Reglas para `main`

- [x] Require pull request reviews (1 approval)
- [x] Require status checks to pass:
  - `build` (incluye Checkstyle, JaCoCo, SonarCloud)
  - `security-scan`
  - `architecture-test`
  - `e2e-test`
- [x] Require branches to be up to date

### Reglas para `develop`

- [x] Require status checks to pass:
  - `build`

---

## 6. Estrategia de Branches

```
main (producción)
   │
   ├── hotfix/critical-bug
   │
develop (integración)
   │
   ├── feature/auth-module
   ├── feature/billing-module
   └── bugfix/login-error
```

### Convenciones de Nombres

| Tipo | Patrón | Ejemplo |
|------|--------|---------|
| Feature | `feature/{descripcion}` | `feature/auth-jwt` |
| Bugfix | `bugfix/{descripcion}` | `bugfix/login-timeout` |
| Hotfix | `hotfix/{descripcion}` | `hotfix/critical-security` |

---

## 7. Comandos Gradle para CI

```bash
# Build completo
./gradlew build

# Solo tests
./gradlew test

# Tests de arquitectura
./gradlew test --tests "*ArchitectureTest"

# Cobertura
./gradlew jacocoTestReport
./gradlew jacocoTestCoverageVerification

# Build JARs
./gradlew :api:app:bootJar
./gradlew :bff:bootJar
```

---

## 8. Checklist Pre-Deploy

### Staging

- [ ] CI pipeline verde
- [ ] Cobertura ≥ 80% (capas domain/application)
- [ ] Sin vulnerabilidades HIGH/CRITICAL
- [ ] ArchUnit tests pasan
- [ ] Changelog actualizado

### Production

- [ ] Staging testeado y aprobado
- [ ] Backup de base de datos realizado
- [ ] Plan de rollback revisado
- [ ] Monitoreo activo durante deploy

---

## Referencias

- [ADR-0018: CI/CD con GitHub Actions](./adr/0018-cicd-github-actions-gitflow.md)
- [14-TEST-STRATEGY.md](./14-TEST-STRATEGY.md)
