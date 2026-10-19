# GitHub Actions Workflows

Esta carpeta contiene los workflows de CI/CD optimizados para GitFlow.

## Estrategia

Tenemos dos workflows de PR, que se ejecutan según el branch target, y uno de release, que se ejecuta con un tag:

### 1. `pr-develop.yml` - Fast Feedback

**Triggers**: PRs hacia `develop` y pushes directos/merges a `develop`.

**Objetivo**: Feedback rápido (<5 min) para iterar durante desarrollo.

**Jobs**:
- ✅ Build básico
- ✅ Tests unitarios (sin Android)
- ✅ Architecture tests (ArchUnit)
- ✅ Checkstyle
- ✅ NGINX config validation (sintaxis)
- ✅ Docker Compose validation
- ✅ ShellCheck
- ✅ Hadolint

**Excluye** (reservado para main):
- ❌ JaCoCo coverage verification
- ❌ NGINX integration tests (levantar stack completo)
- ❌ Upload de artifacts
- ❌ SonarCloud, Playwright, Gatling y Trivy (Trivy corre solo en `pr-main.yml`)

---

### 2. `pr-main.yml` - Full Pipeline

**Triggers**: PRs hacia `main` y pushes a `main`.

GitHub Actions solo filtra `pull_request` por rama **destino**. Por eso este
workflow se ejecuta para toda PR cuyo destino sea `main`; la protección de la
rama debe permitir únicamente `release/*` y `hotfix/*` como ramas fuente.

**Objetivo**: Validación exhaustiva antes de release.

**Jobs**: Todo de `pr-develop.yml` **más**:
- ✅ JaCoCo coverage report
- ✅ JaCoCo coverage verification (100/80 strategy)
- ✅ NGINX integration tests (stack completo con Docker Compose)
- ✅ Upload de test results y coverage reports
- ✅ Trivy (`trivy-image`): escanea las imágenes `menta-api:ci` y `menta-bff:ci`
  con `scripts/trivy-scan.sh` y falla ante CRITICAL/HIGH con fix disponible.
  Corre como imagen oficial con versión exacta y digest, sube el reporte como
  artefacto y registra las excepciones en `.trivyignore.yaml`. Ver
  `docs/16-CICD-PIPELINE.md`.
- ⏳ SonarCloud, Playwright y Gatling: son gates de release previstos
  exclusivamente para este workflow, pero siguen diferidos hasta que existan
  su configuración segura: SonarCloud necesita organización/proyecto/token y
  Playwright y Gatling no están integrados al build. No se agregan pasos rotos.

---

### 3. `release.yml` - Release a producción

**Triggers**: push de un tag `v*.*.*` (el workflow valida `vX.Y.Z[-sufijo]`).

**Objetivo**: construir una vez, escanear, publicar por digest y desplegar.

**Jobs** (en este orden):
- ✅ `build`: valida el tag, exige que un tag estable esté en `main`, corre `build`
  de los módulos JVM con `-Pversion` y sube los dos JAR.
- ✅ `publish`: construye las imágenes con esos JAR, las escanea con
  `scripts/trivy-scan.sh` y, solo si pasan, las publica en GHCR y toma sus digests.
- ✅ `release`: crea la GitHub Release con notas generadas, los digests y los JAR.
- ✅ `deploy`: solo tags estables. Environment `production`, SSH con
  `StrictHostKeyChecking=yes`: los digests van como único argumento y la configuración
  del stack (`infra/docker/prod` y `conf.d`) como `tar.gz` por la entrada estándar. El
  servidor los recibe en el comando forzado `infra/docker/prod/deploy.sh` (#593).

Un tag con sufijo (`v0.1.0-rc.1`) publica y crea una pre-release, pero no despliega.
`concurrency: production` sin cancelación evita dos releases simultáneos. Detalle,
secretos y contrato con `deploy.sh` en `docs/16-CICD-PIPELINE.md`.

---

## Flujo GitFlow

```
feature/* → develop (pr-develop.yml ejecuta)
              ↓
          release/* → main (pr-main.yml ejecuta)
          hotfix/*  → main (pr-main.yml ejecuta)
                       ↓
                    tag vX.Y.Z (release.yml ejecuta)
```

## Migración de branch protection

El workflow eliminado `ci.yml` publicaba contextos bajo **`CI / <job>`** para
PRs y pushes a `develop` y `main`. Actualizá los required checks de GitHub
antes de borrar esos contextos:

- Para `develop`, requerí los contextos nuevos de **`PR to Develop (Fast Feedback)`**
  (por ejemplo, `PR to Develop (Fast Feedback) / quick-build-and-test`), además
  de los jobs rápidos que la política marque como obligatorios.
- Para `main`, requerí los contextos nuevos de **`PR to Main (Full Pipeline)`**
  (por ejemplo, `PR to Main (Full Pipeline) / full-build-and-test`) y todos los
  jobs de release configurados en ese workflow.

No mantengas como requeridos los contextos viejos `CI / build-and-test`,
`CI / nginx-validation`, `CI / docker-compose-validation`, `CI / shellcheck`,
`CI / hadolint` o `CI / nginx-integration`: ya no se publicarán tras eliminar
`ci.yml`.

## Beneficios

1. **Desarrollo ágil**: PRs a `develop` tienen feedback inmediato sin esperar tests pesados
2. **Calidad garantizada**: PRs a `main` validan TODO antes de merge
3. **Costos optimizados**: Tests de integración solo cuando realmente importan
4. **Claridad**: Cada workflow tiene un propósito único y visible en la UI de GitHub

## Personalización

Si necesitás ajustar umbrales de ejecución (ej: integración en `develop` para features específicas), usá condicionales:

```yaml
# En pr-develop.yml, solo para ciertos paths
nginx-integration:
  if: contains(github.event.pull_request.labels.*.name, 'needs-integration')
```
