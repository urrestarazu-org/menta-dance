# Verify Report: Billing Subscription Status (#32, US-BILLING-004)

**Veredicto: PASS**

Verificado sobre `develop @ fea8a4d` (rango `e71758a..fea8a4d`), 5 PRs mergeados (#202-#206).

## Requisitos y escenarios

Los 10 requisitos y 12 escenarios de `specs/billing-subscription-status/spec.md` tienen
un test real que pasa cubriéndolos — desde unit tests de dominio hasta el integration
test end-to-end de `api:app`.

## Decisiones locked

- **D1** confirmado en `GetCurrentSubscriptionUseCaseImpl.current()`
  (`api/billing/.../application/usecase/GetCurrentSubscriptionUseCaseImpl.java:33-38`):
  sólo llama `findCurrentByUserId` y `findLatestExpiredByUserId`;
  `findLatestCancelledWithRemainingAccess` no aparece en el archivo.
- **D2** confirmado: `NoSubscriptionException` (código `NO_SUBSCRIPTION`) es clase
  distinta de `SubscriptionNotFoundException` (código `SUBSCRIPTION_NOT_FOUND`).

## Puntos especulativos de la issue original, confirmados ausentes

- `autoRenew`: 0 matches en main/test.
- `id`/`subscriptionId`: `String` (UUID) en ambas response DTOs, nunca numérico.
- Shape de error: RFC 9457 ProblemDetail vía `SubscriptionExceptionHandler.noSubscription()`,
  no `{"error","message","suggestion"}`.
- Nombre de campo: `expiringSoon` en código; `expiringsSoon` sólo aparece en la tabla de
  divergencias documentada en `proposal.md`.

## Gap de seguridad (hallado y corregido en PR5)

Confirmado real y corregido en `api/auth/.../SecurityConfig.java:218-222` — matcher
explícito `HttpMethod.GET` para `/me` y `/me/history`. El javadoc de esa clase (líneas
70-78) y de `SubscriptionController` ya no afirman la caída falsa en
`anyRequest().authenticated()`; el fallback real (`anyRequest().access(roleAuthorizationManager)`)
concede acceso a rutas no mapeadas sin importar autenticación.

## Tests ejecutados (forzados, conteo real de XML JUnit)

- `:api:billing:test` → 523 tests, 0 failures
- `:api:auth:test` → 488 tests, 0 failures
- `:api:billing:jacocoTestCoverageVerification` → PASS (domain+application 100%, infrastructure 85%)
- `:api:app:test --tests "*SubscriptionStatusIntegrationTest*"` → 9/9 tests, 0 failures
  (6 escenarios del issue + 401×2 + reflejo inmediato post-sweep)

## Alcance

`git diff --name-status e71758a..fea8a4d` → 27 archivos, todos en `api/billing`,
`api/auth/.../SecurityConfig.java` + test, `api/app/.../SubscriptionStatusIntegrationTest.java`
(nuevo), y artefactos openspec. Cero cambios en `bff/` o `android/`. Cero modificaciones
a `SubscriptionExpiryWorker`, `CancelSubscriptionUseCaseImpl` o
`CreateSubscriptionCheckoutUseCaseImpl`.

**Sin issues CRITICAL, WARNING ni SUGGESTION.**
