# Tareas: activación de cuentas

## Previsión de carga de review

| Campo | Valor |
|---|---|
| Líneas estimadas | 900–1300 |
| Riesgo de superar 400 líneas | Alto |
| Estrategia recomendada | Cadena de 3 PRs |
| Branch de tracking | `feature/auth-account-activation` |
| TDD | Estricto: RED antes de cada cambio productivo |

Antes de `sdd-apply` se deberá confirmar la cadena de branches. La propuesta es:

| PR | Base | Alcance | Verificación focalizada |
|---|---|---|---|
| PR1 `feature/auth-account-activation-domain` | tracking | V3, domain, puertos y RED/GREEN de casos de uso | `./gradlew :api:auth:test` |
| PR2 `feature/auth-account-activation-infra` | PR1 | JPA, crypto, Redis, SMTP y dispatch outbox | `./gradlew :api:auth:test :api:app:test` |
| PR3 `feature/auth-account-activation-web` | PR2 | HTTP, integración, Bruno y docs | `./gradlew check` |

## Fase 0 — Characterization y contratos

- [ ] 0.1 RED: agregar characterization tests del reconciler actual para cada
  evento Auth existente y para un tipo desconocido. ArchUnit: N/A. Archivos:
  `api/app/src/test/.../OutboxBlacklistReconcilerTest.java`,
  `OutboxReconciliationWorkerTest.java`.
- [ ] 0.2 RED: agregar test que demuestra que el registro actual crea `ACTIVE` y
  convertirlo en expectativa `PENDING_ACTIVATION`. ArchUnit:
  `application_should_not_depend_on_infrastructure`. Archivo:
  `api/auth/src/test/.../RegisterUserUseCaseImplTest.java`.
- [ ] 0.3 RED: agregar test de login pendiente indistinguible de password
  incorrecta. ArchUnit: mismo rule id. Archivo:
  `api/auth/src/test/.../LoginUseCaseImplTest.java`.

## Fase 1 — Dominio y migración (PR1)

- [ ] 1.1 RED/GREEN: extender `UserStatus` con `PENDING_ACTIVATION`, crear
  `User.register()` y restringir `activate()` a la transición válida. ArchUnit:
  `domain_should_not_depend_on_infrastructure`. Archivos: `User.java`,
  `UserStatus.java`, `UserTest.java`.
- [ ] 1.2 RED/GREEN: crear aggregate `ActivationToken` con expiración, consumo e
  invalidación. ArchUnit: `domain_should_not_depend_on_infrastructure`.
- [ ] 1.3 crear V3 aditiva con `auth_activation_tokens`, ciphertext AES-GCM,
  nonce/key version e índices. Archivo:
  `api/app/src/main/resources/db/migration/V3__auth_account_activation.sql`.
- [ ] 1.4 RED/GREEN: definir `ActivationTokenRepository`, generator, hasher,
  delivery cipher, rate limit y outbox contracts en application. ArchUnit:
  `application_should_not_use_spring_web`.
- [ ] 1.5 RED/GREEN: modificar `RegisterUserUseCaseImpl` para persistir usuario,
  token y evento atómicamente mediante wrapper transaccional.
- [ ] 1.6 RED/GREEN: implementar `ActivateAccountUseCase` y
  `ResendActivationUseCase`, incluyendo invalidación y respuesta no enumerativa.
- [ ] 1.7 REFACTOR: clock y TTL inyectables; eliminar `now()` del dominio para
  tests deterministas.

## Fase 2 — Infraestructura y entrega durable (PR2)

- [ ] 2.1 RED/GREEN: JPA entity, mapper, repository y adapter de activation
  tokens; probar hash único, invalidación masiva y update condicional.
- [ ] 2.2 RED/GREEN: `SecureRandomActivationTokenGenerator` y SHA-256 hasher;
  verificar 32 bytes, Base64 URL-safe y ausencia de secretos en `toString`.
- [ ] 2.3 RED/GREEN: AES-GCM delivery cipher con validación de clave productiva,
  nonce único y limpieza del ciphertext tras entrega.
- [ ] 2.4 RED/GREEN: Redis rate limiter atómico (Lua o operación equivalente)
  por email fingerprint e IP fingerprint; probar 429, TTL y Redis caído.
- [ ] 2.5 RED: fijar comportamiento de dispatch para blacklist, activación,
  handler ausente y handler ambiguo.
- [ ] 2.6 GREEN/REFACTOR: extraer `OutboxEventHandler` y convertir el worker a
  dispatch exacto sin alterar heartbeat/backoff de blacklist. ArchUnit:
  `app_should_not_contain_domain_logic`.
- [ ] 2.7 RED/GREEN: implementar `ActivationNotificationPort` con Spring Mail y
  handler durable; usar Mailpit en local y fake SMTP en tests.
- [ ] 2.8 agregar Mailpit a `docker-compose.yml`, health check y variables SMTP
  sin credenciales versionadas.

## Fase 3 — HTTP, seguridad y wiring (PR3)

- [ ] 3.1 RED/GREEN: exponer `POST /api/v1/auth/register` y mantener alias
  temporal `/api/v1/users/register` sobre el mismo port-in.
- [ ] 3.2 RED/GREEN: exponer `GET /api/v1/auth/activate/{token}` y
  `POST /api/v1/auth/resend-activation` con RFC 9457.
- [ ] 3.3 RED/GREEN: respuesta uniforme de registro/reenvío y `Retry-After` para
  429/503; probar inexistente, activo y pendiente.
- [ ] 3.4 GREEN: wiring en `AuthConfiguration` y wrappers transaccionales; ningún
  controller puede depender de repository. ArchUnit:
  `controllers_should_not_depend_on_repositories`.
- [ ] 3.5 RED/GREEN: sanitización de URI y test de logs sin token, hash, email,
  password ni ciphertext; actualizar NGINX si registra request URI.
- [ ] 3.6 RED/GREEN integración Testcontainers: registro → outbox → entrega →
  activación → login; rollback atómico y doble activación concurrente.
- [ ] 3.7 agregar Bruno para happy path, token expirado, reutilización, reenvío y
  rate limit; nunca versionar tokens o credenciales generadas.
- [ ] 3.8 actualizar `docs/03-AUTH-API.md`, `docs/22-DATA-MODEL.md`, user stories,
  OpenAPI y setup local de Mailpit.

## Fase 4 — Verificación y entrega

- [ ] 4.1 ejecutar `./gradlew :api:auth:test :api:app:test`.
- [ ] 4.2 ejecutar ArchUnit y JaCoCo; mantener 100% agregado domain/application y
  mínimo 50% infrastructure.
- [ ] 4.3 ejecutar `./gradlew check` y `git diff --check`.
- [ ] 4.4 ejecutar flujo Bruno contra MySQL, Redis y Mailpit locales.
- [ ] 4.5 realizar review receipt-driven por PR mientras el modo permanezca
  habilitado; no fabricar aprobación si se deshabilita.

## Criterio TDD

Cada item RED debe fallar por la razón contractual esperada antes de escribir
producción. Cada GREEN implementa sólo lo mínimo para pasar. REFACTOR se realiza
con el set focalizado verde. Ningún task se marca completo únicamente porque el
código compile.
