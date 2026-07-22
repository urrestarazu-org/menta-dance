# US-BILLING-002: Confirmación de pago por webhook

## Historia
Como sistema, quiero procesar notificaciones de Mercado Pago para actualizar
pagos, suscripciones y cupos de manera idempotente.

## Aceptación

- Mercado Pago notifica `POST /api/v1/billing/payments/mercadopago/webhook`.
- Billing valida primero HMAC-SHA256 de `x-signature` para el manifiesto con
  `data.id`, `x-request-id` y timestamp mediante comparación timing-safe; luego
  valida una tolerancia máxima de cinco minutos.
- Inmediatamente persiste y deduplica la notificación validada como `RECEIVED`
  en `billing_webhook_inbox`; un duplicado responde `200 OK` sólo tras comprobar
  esa persistencia durable. Redis es suplementario.
- Un worker consulta el pago usando `data.id` en Mercado Pago y exige coincidencia
  exacta de referencia, merchant/cuenta, importe y moneda para cada estado del
  proveedor. `approved`, `rejected`, `cancelled`, `expired` y `pending`/`in_process`
  se procesan sólo después de esas validaciones y se
  mapean respectivamente a `COMPLETED`, `REJECTED`, `CANCELLED`, `EXPIRED` y
  `PENDING/AWAITING_PROVIDER`. Los fallos de consulta se reintentan de manera
  acotada; una discrepancia o agotamiento queda
  `PENDING/RECONCILIATION_REQUIRED` sin asignar acceso ni cupos.
- Al confirmar un pago, aplica los efectos mediante puertos/eventos internos y
  asigna cupos idempotentemente; no hay HTTP ni RabbitMQ entre módulos.
- Un pago vencido queda `EXPIRED` y no habilita acceso ni capacidad.
- Si el resultado del proveedor es incierto, se registra
  `PENDING/RECONCILIATION_REQUIRED`; no se crea un pago externo de nuevo automáticamente.
  ADMIN resuelve la tarea con evidencia y auditoría.
- Webhooks tardíos o duplicados no retroceden estados ya aplicados; una corrección
  excepcional requiere el endpoint administrativo auditado.
- Si no existe pago local para un inbox agotado, se crea una tarea administrativa
  que referencia el evento del proveedor, sin inventar un Payment.

## Hecho cuando

Pruebas cubren firma inválida, timestamp vencido, replay, duplicados, cupo
idempotente, expiración y conciliación manual.
