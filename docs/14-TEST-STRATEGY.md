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
