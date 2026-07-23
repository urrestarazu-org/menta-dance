# Estado del repositorio (leer primero)

- El repositorio es **solo documentación hoy**. Antes de escribir código, revisar `docs/02-ARCHITECTURE.md`, `docs/27-CLEAN-ARCHITECTURE-GUIDE.md`, `docs/24-LOCAL-DEV-SETUP.md` y los ADRs relevantes; el scaffold que crees debe satisfacer esos contratos.
- Stack objetivo: Java 21 + Spring Boot 3 monolito modular (`api`) compuesto por `api:app`, cliente Android Kotlin + Jetpack Compose, Node 20.11.1 para assets web, Docker Compose para helpers de infraestructura.

# Boundaries y ownership de módulos

- La API es un único JAR construido desde los módulos `auth`, `billing`, `virtual`, `physical`, más `api:shared` para contratos cross-module mínimos. `api:app` conecta todo; puede hospedar orquestación pero **sin lógica de dominio**.
- La interfaz web es un JAR BFF separado que habla con la API por HTTP; los clientes del navegador nunca hablan directamente con la API ni almacenan tokens.
- Cada módulo backend debe seguir `domain -> application -> infrastructure` (sin dependencias inversas). `domain` permanece libre de frameworks, `application` expone puertos/casos de uso, `infrastructure` posee adaptadores, controllers, persistencia y config.
- La colaboración entre módulos siempre va a través de interfaces Java (típicamente definidas en `api:shared`). Prohibido: compartir entidades JPA, repositorios, SQL joins, llamadas HTTP, messaging, o cualquier infraestructura de otro módulo.
- Android replica la misma dirección (`presentation -> domain <- data`), con el wiring de Hilt aislado en `di`.

# Datos y migraciones

- Schema único de MySQL `menta`; las tablas tienen prefijo por módulo (`auth_`, `billing_`, etc.). Las foreign keys y queries nunca deben cruzar prefijos de módulo.
- Flyway en `api:app` es el único componente autorizado a modificar el schema. Hibernate permanece en `ddl-auto:validate`; las migraciones fallidas se arreglan con nuevas migraciones, no rollbacks.
- Redis es requerido con `maxmemory-policy noeviction` para locks/blacklists; Caffeine cachea solo datos reconstruibles.

# Entorno local

- Herramientas requeridas: JDK 21, Node 20.11.1, Docker Compose, Gradle wrapper (generar en Fase 0), cliente HTTP Bruno (colección `bruno/` versionada). **No** depender de Gradle instalado globalmente.
- `docker compose up -d` debe iniciar MySQL 8 (DB `menta`, usuario app no-root), Redis, y el stack de observabilidad (OTel/Loki). Las credenciales vienen de un `.env` no versionado.
- Una vez scaffoldeado, los comandos base esperados son:
  ```bash
  docker compose up -d
  ./gradlew check      # ejecuta style + tests + ArchUnit + quality gates
  ./gradlew :api:app:bootRun
  ./gradlew :bff:bootRun
  ```

# Quality gates y testing

- Cada módulo debe incluir tests de ArchUnit que refuercen: sin Spring/JPA en `domain`/`application`, sin acceso a infraestructura o repositorios foráneos, controllers delegando solo a través de puertos.
- CI (GitHub Actions) debe ejecutar: build backend, Checkstyle, tests unitarios + ArchUnit, JaCoCo, SonarCloud, Playwright, Gatling, y Trivy antes del merge. Conectar estos en `./gradlew check`/workflows mientras el código aterriza.
- Observabilidad (Logback JSON + OpenTelemetry) es parte de la definición de done—asegurar que los nuevos servicios emitan `correlationId` y respeten las reglas de retención de 90 días descritas en docs.

# Workflow y delivery

- El branching sigue Git Flow: `feature/*` → `develop`; los releases originan de `release/*`, se mergean a `master` y luego de vuelta a `develop`; hotfixes inician desde `master`.
- Usar Conventional Commits tanto para mensajes de commit como títulos de PR. Los releases se tagean `vMAJOR.MINOR.PATCH` y publican JAR + imágenes GHCR usando digests inmutables.
- Los PRs se mergean via squash una vez que CI está verde; nunca agregar atribuciones de AI en trailers de commit. Los workflows de deployment deben pinear cada GitHub Action a un SHA completo (sin `latest`).

# Guía de selección de modelos

Antes de iniciar una tarea, sugerir el modelo apropiado según la complejidad:

## Modelos Anthropic Claude

| Modelo | Mejor para | Evitar para |
|--------|------------|-------------|
| **Fable 5** | Investigación, tareas de varios días, razonamiento más capaz | Ediciones rápidas, tareas sensibles al costo |
| **Opus 4.8** | Proyectos complejos, flujos agénticos, programación | Q&A simple, documentación |
| **Sonnet 5** | Tareas diarias, escritura, balance costo/rendimiento | Análisis arquitectónico profundo |
| **Haiku 4.5** | Respuestas rápidas, alto volumen, bajo costo | Razonamiento complejo, refactors multi-archivo |

### Mapeo Tarea-Modelo

| Tipo de Tarea | Modelo Recomendado |
|---------------|-------------------|
| Scaffold de nuevo módulo | Opus 4.8 |
| Refactoring multi-archivo | Opus 4.8 / Fable 5 |
| Diseño de arquitectura | Fable 5 |
| Edición de archivo único | Sonnet 5 / Haiku 4.5 |
| Actualizaciones de documentación | Sonnet 5 |
| Verificaciones rápidas de estado | Haiku 4.5 |
| Code review | Opus 4.8 |
| Debug de issue complejo | Opus 4.8 / Fable 5 |
| Investigación | Fable 5 |

## Modelos Google Gemini

| Modelo | Mejor para |
|--------|------------|
| **Gemini Flash** | Ediciones simples, actualizaciones de archivo único, verificaciones rápidas |
| **Gemini Pro** | Diseño arquitectónico, refactoring multi-módulo, análisis profundo |
