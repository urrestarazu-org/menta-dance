# Modelo de Datos

## Convenciones

MySQL tiene un único schema `menta`. Cada módulo es dueño exclusivo de sus tablas:
`auth_*`, `billing_*`, `virtual_*` y `physical_*`. IDs de otro módulo se guardan
como valor y se validan mediante puertos; no se crean FKs ni `JOIN`s cruzados.

## Auth

- `auth_users` incluye `token_version` y datos de recuperación; `auth_roles`,
  `auth_users_roles`.
- Roles iniciales: `ALUMNO`, `PROFESOR`, `RECEPCIONISTA`, `ADMIN`.
- `auth_refresh_tokens`: `token_hash CHAR(64) UNIQUE`, `family_id`, `issued_at`,
  `expires_at`, `revoked_at`, `rotated_at` y metadatos de dispositivo.
- `auth_revoked_access_tokens` conserva `jti` y expiración; `auth_security_state`
  conserva el estado global `NORMAL` o `AUTH_DEGRADED`.
- `auth_outbox`: evento durable de blacklist/revocación, estado de entrega e
  intentos. Revocaciones se confirman primero en MySQL; el worker refleja versión
  y blacklist en Redis. Si no puede reconciliar, persiste `AUTH_DEGRADED` hasta
  reparación.
- `auth_password_reset_tokens`: `token_hash`, `expires_at`, `used_at` y
  `user_id`; nunca almacena el token en claro.

## Billing

- `billing_plans`, `billing_plan_courses`, `billing_subscriptions`,
  `billing_quotes`, `billing_payments`, `billing_payment_proofs`,
  `billing_purchases`, `billing_webhook_inbox`,
  `billing_reconciliation_tasks`, `billing_audit_log`,
  `billing_physical_course_pricing`,
  `billing_physical_course_pricing_revisions` y `billing_outbox`.
- `billing_plan_courses` asigna cursos de catálogo a un plan. Guarda
  `course_id` como referencia por valor, sin FK ni `JOIN` hacia Virtual o
  Physical, y es propiedad de Billing.
- `billing_payment_proofs` conserva los metadatos y la clave privada de
  almacenamiento del comprobante vigente de un pago; no guarda una URL pública.
  Su reemplazo se audita en `billing_audit_log`.
- `billing_webhook_inbox` conserva identificador del proveedor, hash de payload,
  `data_id`, `x_request_id`, timestamp validado, resultado de consulta al
  proveedor, estado `RECEIVED`/procesamiento, `attempt_count`, `next_attempt_at`
  y unicidad durable; se persiste antes de la consulta al proveedor. Al agotar
  reintentos crea una tarea de conciliación ligada al pago o al inbox sin pago.
- `billing_payments.principal_status`: `PENDING`, `COMPLETED`, `REJECTED`,
  `CANCELLED`, `EXPIRED`; `substatus` sólo para pendientes:
  `AWAITING_PROVIDER`, `AWAITING_MANUAL_VERIFICATION`,
  `RECONCILIATION_REQUIRED`.
- `billing_purchases.fulfillment_status`: `PENDING_FULFILLMENT`, `ASSIGNED` o
  `EXCEPTION`; es independiente de la liquidación de `billing_payments`.
- Cada pago guarda clave de idempotencia y referencia del proveedor. Una tarea de
  conciliación guarda motivo, evidencia, responsable y resolución.
- `billing_audit_log` registra transiciones y correcciones administrativas; las
  transiciones de proveedor son monotónicas y una corrección exige motivo y
  evidencia.
- `billing_physical_course_pricing_revisions` es append-only y guarda curso,
  profesor, motivo, versión y valores anterior/nuevo; no tiene FK al módulo
  Physical.
- `billing_outbox`: eventos durables de cambio de estado de pago; mismo patrón
  que `auth_outbox`.
- Las reglas de quotes/capacidad presencial se detallan en el documento 28.

## Virtual

- `virtual_courses`, `virtual_modules`, `virtual_lessons`, `virtual_videos`,
  `virtual_lesson_materials` y `virtual_course_audit`.
- `virtual_course_audit` es propiedad de Virtual y append-only. Guarda
  `course_id`, `actor_user_id` (referencia por valor a Auth), acción, motivo
  opcional, versión, valores anterior/nuevo y `created_at`; no tiene FKs ni
  `JOIN`s hacia otros módulos.
- `virtual_lesson_materials` guarda el nombre, tipo, clave privada de
  almacenamiento y metadata de cada material de una lección; las descargas se
  entregan sólo mediante URLs firmadas emitidas por Virtual.
- `virtual_lesson_progress` es único por `user_id + lesson_id` y guarda posición,
  `completed_at` y actualización.
- `virtual_course_progress` es único por `user_id + course_id`; es un resumen
  derivado del detalle de lecciones (porcentaje, contadores y última lección).
- `virtual_outbox`: eventos durables de progreso completado; mismo patrón que
  `auth_outbox`.
- Los IDs de curso son UUID globalmente únicos entre modalidades; `api:app` usa
  la modalidad de la proyección de catálogo para enrutar el ID al módulo dueño.

## Physical

- `physical_courses`, `physical_sessions`, `physical_capacity_assignments`,
  `physical_attendance` y holds técnicos internos.
- Una sesión contiene fecha/hora/capacidad. Una asignación confirmada ocupa un
  cupo y es única por alumno+sesión.
- `physical_qr_devices` guarda `secret_hash`, `expires_at`, `revoked_at`,
  `rotated_at` y auditoría; nunca un secreto en claro.
- `physical_outbox`: eventos durables de asignación y asistencia; mismo patrón
  que `auth_outbox`.
- Los IDs de `physical_courses` comparten el espacio UUID global del catálogo.

## Migraciones

Flyway en `:api:app` es la única autoridad del schema. Las migraciones son
inmutables y sólo hacia adelante; `ddl-auto:validate` verifica entidades. Cada
release declara sus migraciones `backward-compatible` o `manual-recovery-only`.
Un rollback automático de aplicación exige evidencia de que la versión anterior
funciona contra el schema migrado y los datos escritos por la versión nueva.
Los cambios destructivos siguen expand/contract y se hacen una release después.
