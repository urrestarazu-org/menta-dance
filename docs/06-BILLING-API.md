# API de Facturación y Pagos

Billing es dueño de planes, precios, suscripciones, quotes, compras, pagos,
webhooks y conciliación. Physical conserva sesiones/cupos; Virtual conserva
contenido/progreso.

## Rutas canónicas

```text
GET  /api/v1/billing/plans
GET  /api/v1/billing/plans/{planId}
POST /api/v1/billing/subscriptions
GET  /api/v1/billing/subscriptions/me
GET  /api/v1/billing/subscriptions/me/history
GET  /api/v1/billing/payments
GET  /api/v1/billing/payments/{paymentId}
POST /api/v1/billing/physical/quotes
POST /api/v1/billing/physical/purchases
GET  /api/v1/billing/physical/courses/{courseId}/pricing
PUT  /api/v1/billing/physical/courses/{courseId}/pricing
POST /api/v1/billing/payments/mercadopago/webhook
GET  /api/v1/billing/admin/reconciliation
POST /api/v1/billing/admin/reconciliation/{paymentId}/resolve
GET  /api/v1/billing/admin/payments
GET  /api/v1/billing/admin/payments/{paymentId}
POST /api/v1/billing/admin/payments/{paymentId}/approve
POST /api/v1/billing/admin/payments/{paymentId}/reject
POST /api/v1/billing/admin/payments/{paymentId}/corrections
POST /api/v1/billing/payments/{paymentId}/proof
```

Las rutas `/me` y `/payments` son autoservicio del alumno; las de conciliación
son exclusivas de `ADMIN`. Los errores usan `application/problem+json`.

## Estados e idempotencia

`Payment` usa un estado principal y, cuando está pendiente, un subestado.
Estados principales: `PENDING`, `COMPLETED`, `REJECTED`, `CANCELLED` y `EXPIRED`;
los últimos cuatro son terminales. Subestados de `PENDING`:
`AWAITING_PROVIDER`, `AWAITING_MANUAL_VERIFICATION` y
`RECONCILIATION_REQUIRED`. Transferencia inicia como
`PENDING/AWAITING_MANUAL_VERIFICATION`; un rechazo del proveedor o de ADMIN
termina en `REJECTED`, una cancelación explícita en `CANCELLED` y un deadline sin
confirmación en `EXPIRED`. La respuesta del proveedor se mapea así: `approved` a
`COMPLETED`, `rejected` a `REJECTED`, `cancelled` a `CANCELLED`, `expired` a
`EXPIRED` y `pending`/`in_process` a `PENDING/AWAITING_PROVIDER`.
`RECONCILIATION_REQUIRED` sólo representa mismatch, resultado desconocido o falla
al consultar al proveedor. Un pago confirmado que no logra asignar capacidad queda
`COMPLETED` porque representa liquidación financiera. Su Purchase inicia
`PENDING_FULFILLMENT` y sólo luego pasa a `ASSIGNED` o `EXCEPTION`; no se habilita
asistencia hasta `ASSIGNED`. `EXPIRED` no habilita suscripción ni asigna cupo.

Las transiciones originadas por proveedor son monotónicas: un webhook tardío o
duplicado nunca puede retroceder ni reemplazar un estado ya aplicado. Sólo
`POST /api/v1/billing/admin/payments/{paymentId}/corrections` puede aplicar una
corrección excepcional; requiere `ADMIN`, motivo, evidencia y auditoría
append-only.

La creación de un pago externo **nunca** se reintenta automáticamente. Ante
incertidumbre se persiste la clave de idempotencia, correlación del proveedor,
request seguro redactado y estado `PENDING/RECONCILIATION_REQUIRED`; se consulta al
proveedor y un administrador resuelve el caso. Al recibir un webhook,
Billing verifica HMAC-SHA256 de `x-signature` para el manifiesto que incluye
`data.id`, `x-request-id` y timestamp mediante comparación timing-safe, y rechaza
timestamps con más de cinco minutos. Inmediatamente después persiste y deduplica
la notificación validada como `RECEIVED` en `billing_webhook_inbox`; un duplicado
responde `200 OK` sólo tras comprobar ese inbox durable. Un worker consulta el
pago en Mercado Pago usando `data.id` y exige, para **todo** estado del proveedor,
coincidencia exacta con la compra esperada: referencia externa, cuenta/merchant,
importe y moneda. Luego mapea el estado recibido según la tabla anterior. Los
fallos de consulta quedan retenidos en inbox para reintento; una discrepancia crea
`PENDING/RECONCILIATION_REQUIRED` sin aplicar efectos. Sólo una verificación
exitosa y la transición monotónica se aplican transaccionalmente.
El inbox conserva `attempt_count` y `next_attempt_at`: al agotar los reintentos,
el pago asociado pasa a `PENDING/RECONCILIATION_REQUIRED` y se crea una tarea
administrativa; si no existe pago local asociado, la tarea referencia el inbox y
el evento del proveedor. Redis puede acelerar protección contra replay, pero no
es la fuente de verdad.

## Incidentes de base de datos

No se promete RPO 0 financiero. Después de un incidente de BD, un administrador
reconcilia manualmente pagos contra Mercado Pago usando `providerPaymentId`,
correlation IDs, inbox/outbox y auditoría. Se retienen por 90 días logs
estructurados y redactados, auditoría de pagos e incidentes.

## Pagos presenciales

La fórmula, snapshots, capacidad y casos de borde están definidos en
[28-PHYSICAL-CLASS-PAYMENTS.md](28-PHYSICAL-CLASS-PAYMENTS.md). Billing coordina
con Physical mediante puertos/eventos internos; no hay HTTP ni broker interno.
El quote expone una proyección informativa de disponibilidad, sin reserva. Antes
de crear un `Payment` o iniciar el proveedor, `api:app` solicita a Physical un
`CapacityHold` atómico. Si no hay cupo, responde `409 CAPACITY_UNAVAILABLE` y no
se crea pago. El hold reduce, pero no elimina, la excepción posterior si la
cobertura mensual calculada al confirmar cambia o el hold expira.

## Pricing presencial

`GET /billing/physical/courses/{courseId}/pricing` consulta la configuración
vigente. `PUT /billing/physical/courses/{courseId}/pricing` permite a `PROFESOR`
propietario o `ADMIN` cambiar `monthlyPrice` e `individualSurchargePercent` con
motivo. Billing valida ownership mediante puerto, versiona el pricing y registra
una revisión append-only; los quotes existentes no cambian.

## Transferencias bancarias

Una transferencia crea `PENDING/AWAITING_MANUAL_VERIFICATION` y recibe un comprobante privado.
Sólo `ADMIN` puede listar, ver detalle, aprobar o rechazar estos pagos; las
decisiones son idempotentes y auditadas. Aprobar habilita los efectos de compra;
rechazar requiere motivo y no habilita acceso ni cupo.
