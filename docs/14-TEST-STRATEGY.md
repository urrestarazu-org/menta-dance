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

## Nota técnica: gate de cobertura JaCoCo (issue #96)

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
efectivamente falla. Ver el patrón completo, con el comentario explicando
el porqué, en `api/auth/build.gradle.kts`
(`registerLayeredCoverageVerification`). `api:billing` (#29) debe aplicar
el mismo patrón cuando se retome ese trabajo.
