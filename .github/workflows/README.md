# GitHub Actions Workflows

Esta carpeta contiene los workflows de CI/CD optimizados para GitFlow.

## Estrategia

Tenemos dos workflows principales que se ejecutan según el branch target:

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
- ❌ SonarCloud, Playwright, Gatling y Trivy

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
- ⏳ SonarCloud, Playwright, Gatling y Trivy: son gates de release previstos
  exclusivamente para este workflow, pero siguen diferidos hasta que existan
  su configuración segura: SonarCloud necesita organización/proyecto/token,
  Playwright y Gatling no están integrados al build, y Trivy no dispone aún de
  configuración o acción con SHA pinneado. No se agregan pasos rotos.

---

## Flujo GitFlow

```
feature/* → develop (pr-develop.yml ejecuta)
              ↓
          release/* → main (pr-main.yml ejecuta)
          hotfix/*  → main (pr-main.yml ejecuta)
                       ↓
                    tag vX.Y.Z
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
