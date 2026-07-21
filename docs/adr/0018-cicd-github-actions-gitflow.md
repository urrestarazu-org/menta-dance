# ADR-0018: CI/CD con GitHub Actions y GitFlow

**Estado:** Aceptado
**Fecha:** 2026-06-03
**Decisores:** Equipo de desarrollo

## Vigencia actual

La automatización con GitHub Actions continúa aceptada. El proyecto vigente es el monorepo Gradle multi-module descrito por [ADR-0019](./0019-monorepo-structure.md) y [ADR-0020](./0020-modular-monolith.md); los nombres históricos `menta-monolith`, `auth-api` o `billing-api` no representan artefactos desplegables separados.


## Contexto y Problema

El proyecto necesita una estrategia de CI/CD que:

1. Automatice la validación de código (build, tests, linting)
2. Garantice la calidad antes de merge a ramas principales
3. Aplique escaneo de seguridad continuo
4. Respete las convenciones de GitFlow
5. Proteja las ramas `master` y `develop` de merges no autorizados

## Factores Clave (Decision Drivers)

* GitFlow como estrategia de branching registrada históricamente en este ADR
* Proyecto multi-módulo con Gradle (menta-monolith, auth-api, billing-api, etc.)
* Necesidad de ejecutar tests unitarios, Checkstyle y JaCoCo en cada PR
* Tests E2E costosos que solo deben ejecutarse antes de releases a producción
* Repositorio en organización GitHub (`urrestarazu-org`) con plan Team

## Opciones Consideradas

### Opción 1: GitHub Actions con workflows básicos

* **Descripción:** Un único workflow que ejecuta build y tests en todos los PRs
* **Pros:**
  * Configuración simple
  * Fácil de mantener
* **Contras:**
  * No diferencia entre tipos de PR (feature vs release)
  * Tests E2E ejecutados innecesariamente
  * No valida convenciones de GitFlow

### Opción 2: GitHub Actions con workflows especializados + Branch Protection

* **Descripción:** Múltiples workflows especializados con validación de GitFlow y branch protection rules
* **Pros:**
  * Tests E2E solo en PRs de release/hotfix a master
  * Validación automática de convenciones GitFlow
  * Branch protection previene merges no autorizados
  * Escaneo de seguridad con Trivy
  * Dependabot para actualizaciones automáticas
* **Contras:**
  * Más archivos de configuración
  * Requiere plan Team para branch protection

### Opción 3: CI/CD externo (Jenkins, GitLab CI, CircleCI)

* **Descripción:** Usar herramienta externa de CI/CD
* **Pros:**
  * Mayor flexibilidad en algunos casos
* **Contras:**
  * Infraestructura adicional
  * Integración menos nativa con GitHub
  * Costo adicional

## Decisión

Elegimos **Opción 2: GitHub Actions con workflows especializados + Branch Protection** porque ofrece la mejor integración con GitHub, respeta GitFlow y optimiza costos al ejecutar tests E2E solo cuando es necesario.

## Justificación (Rationale)

GitHub Actions está integrado nativamente con GitHub, eliminando la necesidad de infraestructura adicional. La separación de workflows permite optimizar el tiempo de CI según el tipo de cambio:

* PRs a `develop`: build + tests unitarios + Checkstyle + SonarCloud
* PRs a `master` desde `release/*` o `hotfix/*`: todo lo anterior + tests E2E

La validación de GitFlow mediante workflow garantiza que solo las ramas correctas puedan hacer merge a cada rama principal.

## Implementación

### Workflows Configurados

| Workflow | Archivo | Trigger | Propósito |
|----------|---------|---------|-----------|
| Java CI | `gradle.yml` | PR/push a master, develop | Build, tests, Checkstyle, JaCoCo |
| SonarCloud | `sonarcloud.yml` | PR/push a master, develop | Análisis de calidad |
| Trivy | `trivy.yml` | PR/push + diario | Escaneo de vulnerabilidades |
| Playwright E2E | `playwright.yml` | PR release/hotfix → master | Tests end-to-end |
| GitFlow Check | `gitflow-check.yml` | PR a master, develop | Validación de convenciones |
| Claude Code | `claude.yml` | Menciones @claude | Asistencia de IA en PRs |
| Claude Review | `claude-code-review.yml` | PRs | Code review automático |

### Branch Protection Rules

| Rama | Reglas |
|------|--------|
| `master` | Requiere PR, status checks (GitFlow + Build), no force push, no delete |
| `develop` | Requiere PR, status checks (GitFlow + Build), no force push, no delete |

### Validación GitFlow

```
master  ← solo acepta: release/*, hotfix/*
develop ← solo acepta: feature/*, bugfix/*, release/*, hotfix/*
```

### Dependabot

Actualizaciones automáticas semanales para:

* Dependencias Gradle (Java/Spring Boot)
* Dependencias npm (Tailwind, Playwright)
* GitHub Actions

## Consecuencias

### Positivas

* Validación automática de código en cada PR
* Tests E2E optimizados (solo en releases)
* GitFlow reforzado por CI
* Escaneo de seguridad continuo
* Actualizaciones de dependencias automatizadas
* Branch protection previene errores humanos

### Negativas / Deuda Técnica

* Múltiples workflows requieren mantenimiento
* Los status checks deben coincidir exactamente con los nombres de jobs

### Implicaciones de Costos

* **GitHub Actions:** 2,000 minutos/mes incluidos en Team (suficiente para el proyecto)
* **GitHub Team:** $4/usuario/mes (necesario para branch protection en repo privado)
* **SonarCloud:** Gratis para proyectos open source, plan pago si es privado

### Riesgos y Reversibilidad

* **Riesgo Principal:** Falsos positivos en GitFlow check bloqueando PRs válidos
* **Plan de Mitigación:** Workflow dispatch manual disponible; admins pueden bypass
* **Reversibilidad:** Alta - se pueden deshabilitar workflows o branch protection en minutos

## Referencias y Decisiones Relacionadas

* `CONTRIBUTING.md` - Estrategia GitFlow documentada
* `.github/workflows/` - Archivos de configuración de workflows
* [GitHub Actions Documentation](https://docs.github.com/en/actions)
* [GitHub Branch Protection](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches)
