# Propuesta: activación de cuentas

## Why

Completar el alta de identidad para que una cuenta pública no pueda autenticarse
hasta demostrar control del correo electrónico, con entrega durable, token de un
solo uso y respuestas resistentes a enumeración.

## What Changes

### Incluido

- Estado `PENDING_ACTIVATION` para registros públicos.
- Token opaco de activación: generación criptográfica, hash persistido, TTL de
  24 horas configurable, invalidación y consumo único.
- `GET /api/v1/auth/activate/{token}`.
- `POST /api/v1/auth/resend-activation` con respuesta uniforme.
- Registro transaccional de usuario + token + evento de entrega.
- SMTP detrás de `ActivationNotificationPort`, procesado desde outbox durable.
- Rate limiting Redis por fingerprint de email e IP, sin PII en keys.
- Rechazo de login para cuentas pendientes sin revelar su estado.
- Tests unitarios, integración, arquitectura, contrato y Bruno.
- Actualización de modelo de datos, contrato HTTP y OpenAPI.

### Excluido

- Recuperación/reset/cambio de contraseña (`US-AUTH-005/006`).
- BFF/Android UI para activación.
- MFA, OAuth social y administración de usuarios.
- Cambio de correo de una cuenta existente.

## Capabilities

### New Capabilities

- `auth-account-activation`: emitir y entregar credenciales de activación,
  activar una cuenta pendiente exactamente una vez y reemitir credenciales sin
  enumerar cuentas.

### Modified Capabilities

- Ninguna capacidad base: `openspec/specs/` todavía no contiene specs
  consolidadas. Los cambios de registro y outbox quedan especificados dentro de
  la nueva capacidad.

## Impact

- El registro público deja de crear usuarios activos y pasa a devolver una
  confirmación genérica de activación pendiente.
- El reconciler deja de aplicar una única acción a todos los eventos y delega en
  handlers por `event_type`.
- Se agregan una migración Flyway, infraestructura SMTP/Mailpit y rate limiting
  Redis; no se cruzan boundaries de módulos.

## Enfoque

1. V3 agrega `auth_activation_tokens` sin modificar migraciones existentes.
2. `RegisterUserUseCase` crea `User.register(...)`, token e outbox en una única
   transacción.
3. Un dispatcher en `:api:app` enruta eventos conocidos y deja como FAILED los
   tipos desconocidos; nunca los marca como completados silenciosamente.
4. El handler de activación llama un puerto de notificación implementado con
   Spring Mail; en local se verifica con Mailpit.
5. Activación y reenvío usan operaciones condicionales para tolerar concurrencia.
6. Redis aplica límites configurables; su indisponibilidad falla cerrada con
   `503`, porque no se puede demostrar que la operación no está siendo abusada.

## Éxito

- Un registro nuevo queda `PENDING_ACTIVATION` y no puede iniciar sesión.
- El correo se entrega mediante un evento durable reintentable.
- El token sólo existe en claro durante generación, entrega y request de
  activación; MySQL conserva únicamente SHA-256.
- Un token válido activa una vez; expirado, usado o invalidado no activa.
- Reenvío invalida tokens anteriores y nunca confirma si el email existe.
- `./gradlew check` permanece verde, incluidos ArchUnit y JaCoCo.
- El flujo Bruno registro → activación → login es reproducible localmente.

## Riesgos y mitigaciones

| Riesgo | Mitigación |
|---|---|
| Regresión en blacklist/outbox | Characterization tests antes del refactor y handlers por tipo exacto |
| Doble activación concurrente | Update condicional dentro de transacción + constraint de token hash |
| Fuga de token en logs | Redacción de URI y test de no exposición en API/NGINX |
| Enumeración o spam | Respuesta uniforme, rate limit email+IP y logs sin PII |
| SMTP caído | Outbox FAILED con backoff; la cuenta permanece pendiente |

## Rollback

- Desactivar registro/activación mediante `auth.activation.enabled=false` y
  responder `503` mientras se corrige el incidente.
- Revertir código sin borrar V3; las tablas aditivas pueden permanecer sin uso.
- Si fuera imprescindible remover datos, crear V4 compensatoria. Nunca editar ni
  revertir V3 aplicada.

## Dependencias

- ADR-0021 Clean Architecture.
- ADR-0025 estrategia de tokens y no exposición de secretos.
- ADR-0027 Flyway forward-only.
- ADR-0030 transactional outbox.
- Spring Mail y un SMTP configurado; Mailpit sólo para desarrollo local.
