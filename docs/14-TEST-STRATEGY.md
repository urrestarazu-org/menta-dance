# Estrategia de Testing

## Pirámide

- **Unitarios:** reglas de dominio, pricing, autorizaciones, estados y cálculo
  de progreso.
- **Integración:** puertos, repositorios, Flyway, Redis, webhooks e idempotencia
  usando MySQL/Redis en contenedores.
- **E2E:** BFF, Android/API y flujos críticos con Playwright.
- **Carga:** Gatling para login, catálogo, streaming y check-in.
- **Manual:** Bruno para pruebas exploratorias y validación de endpoints. La
  colección queda versionada en `bruno/` como complemento a Swagger UI.

## Reglas obligatorias

- JUnit 5, Mockito, JaCoCo, Checkstyle, SonarCloud y ArchUnit forman parte de
  CI. La configuración concreta queda fijada en el scaffold.
- ArchUnit prohíbe FKs/JOINs, repositorios y HTTP entre módulos, además de
  dependencias hacia infraestructura ajena. Los únicos cruces permitidos son
  puertos y eventos internos declarados.
- Casos críticos de Auth, pagos, cupos, roles y conciliación requieren cobertura
  completa de decisiones; el resto debe cumplir los umbrales configurados.
- Cada error API se prueba contra `application/problem+json`.
- Los pagos externos se simulan; se prueba que un timeout cree
  `PENDING/RECONCILIATION_REQUIRED` y que nunca emita un segundo cobro automático.
- Webhooks críticos prueban HMAC, ventana temporal, `data.id`, inbox durable
  previo a consulta, reintento de lookup, coincidencia exacta con Mercado Pago y
  transiciones monotónicas.
- Reutilización de refresh, logout-all y reset/cambio de contraseña prueban
  revocación MySQL-first, reflexión Redis, `AUTH_DEGRADED`, reparación por outbox
  y fallo cerrado ante MySQL/Redis. Cada release candidata a rollback automático
  prueba que la aplicación anterior funciona con el schema migrado y datos escritos
  por la versión nueva.

## Cobertura: gates por capa, no por módulo plano

Cada módulo JVM (`auth`, `billing`, `virtual`, `physical`) gatea **por capa
de Clean Architecture**, no con un único número parejo sobre todo el
módulo:

| Módulo | domain + application (BUNDLE) | infrastructure (BUNDLE) |
|---|---|---|
| `auth` | 1.00 (real 99.7%) | 0.85 (real 91.6%) |
| `billing` | 1.00 (real 100%) | 0.85 (real 95.5%) |
| `virtual` | 0.95 (real 98.7%) | 0.90 (real 97.0%) |
| `physical` | 0.95 (real 97.8%) | 0.90 (real 97.3%) |

`shared`, `app` y `bff` no tienen capas propias que gatear por separado;
llevan un piso plano de LINE sobre todo el módulo, declarado en
`moduleCoverageFloor` en el `build.gradle.kts` raíz (`shared` 0.85 —
real 97.1%, `app` 0.90 — real 97.4%, `bff` 0.85 — real 94.9%).

Ningún umbral queda por debajo de 85% — fue un pedido explícito, y
cerrarlo en `auth.infrastructure` (66.0%→91.6% real) y
`billing.infrastructure` (72.5%→95.5% real) fue trabajo de tests nuevo,
no sólo de recalibrar el número.

**Por qué BUNDLE y no CLASS**: un contador por CLASS pesa un record de dos
líneas igual que un caso de uso con ramas — exactamente lo contrario de lo
que Clean Architecture pide (tipos chicos, decoradores finos, value
objects). BUNDLE pregunta lo que realmente importa: "¿está cubierto el
comportamiento de esta capa, dejando viajar el ruido inevitable de
records/DTOs?". `virtual`/`physical` usaron `element = "CLASS"` con un
80% parejo hasta que eso mismo rompió CI en #112: diez decoradores
`Transactional*UseCase` de una línea, cada uno por debajo de 80% en
soledad, contra una capa ya al 97%. Se migraron al mismo mecanismo de
`auth`/`billing`.

**Estos números son un trinquete, no una meta.** Cada mínimo se fija justo
debajo de la cobertura real del momento — nunca muy por debajo, porque
eso deja de proteger nada (permite borrar código de test en silencio sin
que el gate se entere), y se sube cuando la capa mejora de verdad. Nunca
se baja para poner un build en verde.

**Mecanismo compartido**: `registerLayeredCoverageVerification` vive en
`buildSrc/src/main/kotlin/com/menta/buildlogic/LayeredCoverage.kt` (paquete
`buildlogic` y no `build` — ese nombre choca con `**/build/` en
`.gitignore`, que ignora cualquier directorio llamado `build` en
cualquier profundidad, incluido uno de paquete Java/Kotlin) — una sola
implementación, consumida por los cuatro módulos vía `import
com.menta.buildlogic.registerLayeredCoverageVerification`. Antes existían dos
copias idénticas (`auth`/`billing`); extraerla evita que crezcan más.

**Reporte agregado del monorepo**: `./gradlew jacocoAggregatedReport`
combina el `execution data` de los 7 módulos JVM en un solo reporte
(`build/reports/jacoco/jacocoAggregatedReport/`). Es sólo reporte, sin
umbral propio — los gates siguen viviendo por módulo, donde un fallo
señala la capa concreta que retrocedió. Existe porque cada gate por
módulo sólo ve la ejecución de sus propios tests: la suite de integración
de `:api:app` ejercita `virtual`, `auth` y `billing` de punta a punta (con
MySQL real vía Testcontainers) y ese trabajo era invisible para todos los
gates que en realidad cubre.

### Nota técnica: el bug de JaCoCo detrás de todo esto (issue #96)

Una `JacocoViolationRule` con `element = "BUNDLE"` **deja de reportar
cualquier violación en cuanto se le setea `includes`/`excludes`** —
confirmado empíricamente contra este build (Gradle 9.7.0, JaCoCo 0.8.12):
la misma regla, mismos datos, pasa en silencio incluso con un mínimo
matemáticamente imposible (`1.01`, fuera del rango 0.0–1.0 y que ni
siquiera llega a validarse), y falla correctamente apenas se saca el
filtro. `element = "CLASS"` con `includes` sí funciona. Es un
comportamiento confirmado en este proyecto/versión específica, no una
afirmación de bug upstream — no se hizo una reproducción mínima aislada
fuera del repo.

**Consecuencia real**: el gate de 100% de `domain`/`application` en
`api:auth` estuvo efectivamente apagado desde las primeras PRs sin que
nadie lo notara — `./gradlew check` pasaba en verde con clases enteras
sin ningún test.

**Regla a seguir en cualquier módulo nuevo**: nunca usar `includes`/
`excludes` sobre una regla con `element = "BUNDLE"`. Filtrar por capa
scopeando el **input** de la task (`classDirectories`, vía
`FileTree.matching { include(...) }`) en una task
`JacocoCoverageVerification` dedicada por capa, cada una con una regla
`BUNDLE` simple sin `includes` — es la única forma comprobada que
efectivamente falla. Usar `registerLayeredCoverageVerification` de
`buildSrc` en vez de reimplementar el patrón.
