# API de Facturación y Pagos

[← Volver al índice](./README.md) | [← Physical API](./05-PHYSICAL-API.md)

Este documento describe los endpoints del módulo Billing. La autoridad funcional
para pagos de clases presenciales está en
[28-PHYSICAL-CLASS-PAYMENTS.md](./28-PHYSICAL-CLASS-PAYMENTS.md).

---

## Diseño de Endpoints

```
# Planes de suscripción (cursos virtuales)
GET    /api/v1/billing/plans                  # Listar planes activos
GET    /api/v1/billing/plans/{planId}         # Detalle de plan

# Suscripciones
POST   /api/v1/billing/subscriptions          # Crear suscripción
GET    /api/v1/billing/subscriptions          # Mis suscripciones
GET    /api/v1/billing/subscriptions/{id}     # Detalle de suscripción
DELETE /api/v1/billing/subscriptions/{id}     # Cancelar suscripción

# Pagos
GET    /api/v1/billing/payments               # Mis pagos
GET    /api/v1/billing/payments/{paymentId}   # Detalle de pago
POST   /api/v1/billing/payments/{paymentId}/proof  # Subir comprobante

# Clases presenciales (ownership financiero de Billing)
PUT    /api/v1/billing/physical-course-pricing/{courseId}
POST   /api/v1/billing/physical-course-quotes
GET    /api/v1/billing/physical-course-quotes/{quoteId}
POST   /api/v1/billing/physical-purchases
GET    /api/v1/billing/physical-purchases/{purchaseId}

# Webhooks (no requieren autenticación de usuario)
POST   /api/v1/billing/payments/mercadopago/webhook

# Admin (requiere ROLE_ADMIN)
GET    /api/v1/billing/admin/payments?status=PENDING_VERIFICATION
GET    /api/v1/billing/admin/payments/{paymentId}
POST   /api/v1/billing/admin/payments/{paymentId}/approve
POST   /api/v1/billing/admin/payments/{paymentId}/reject
```

---

## Planes de Suscripción

### Listar planes activos

```http
GET /api/v1/billing/plans
```

**Autenticación:** Pública (sin token)

**Response:**

```json
{
  "plans": [
    {
      "id": "abc123",
      "name": "Plan Mensual",
      "description": "Acceso completo por 30 días",
      "price": 15000.00,
      "currency": "ARS",
      "durationDays": 30,
      "featured": true,
      "paymentMethods": ["MERCADO_PAGO", "BANK_TRANSFER"],
      "courses": [
        {"id": "xyz789", "name": "Tango Básico"},
        {"id": "def456", "name": "Salsa Intermedio"}
      ]
    }
  ]
}
```

---

## Suscripciones

### Crear suscripción

```http
POST /api/v1/billing/subscriptions
Authorization: Bearer {accessToken}
```

**Request:**

```json
{
  "planId": "abc123",
  "paymentMethod": "MERCADO_PAGO"
}
```

**Response (Mercado Pago):**

```json
{
  "subscriptionId": "sub_123",
  "status": "PENDING",
  "checkoutUrl": "https://www.mercadopago.com.ar/checkout/v1/redirect?pref_id=xxx"
}
```

**Response (Transferencia bancaria):**

```json
{
  "subscriptionId": "sub_123",
  "paymentId": "pay_456",
  "status": "PENDING",
  "bankDetails": {
    "bankName": "Banco Nación",
    "accountType": "Cuenta Corriente",
    "cbu": "0110000000000000000000",
    "alias": "MENTA.VIRTUAL.ACADEMY",
    "accountHolder": "Menta Academy SRL",
    "amount": 15000.00,
    "currency": "ARS",
    "reference": "SUB-123-USR-456"
  },
  "expiresAt": "2026-07-23T00:00:00Z"
}
```

---

## Pagos de Clases Presenciales

La autoridad completa es
[Pagos de Clases Presenciales](./28-PHYSICAL-CLASS-PAYMENTS.md). Billing posee
pricing, quotes, Purchases y Payments; Physical posee sesiones y capacidad.
`api:app` orquesta contratos neutrales de `shared`, sin dependencias core-to-core.

### Configurar pricing

```http
PUT /api/v1/billing/physical-course-pricing/{courseId}
```

```json
{
  "monthlyPrice": 5000.00,
  "individualSurchargePercent": 10.00,
  "reason": "Actualización de temporada"
}
```

Cada escritura agrega una revisión append-only con old/new, profesor, motivo,
versión y timestamp.

### Crear quote

```http
POST /api/v1/billing/physical-course-quotes
```

```json
{
  "courseId": 10,
  "purchaseType": "MONTHLY"
}
```

Billing fija `coverageStartDate`. Un quote `MONTHLY` prohíbe
`selectedSessionId`; un quote `INDIVIDUAL` lo exige. Cada quote representa una
sola modalidad y conserva un snapshot inmutable hasta `expiresAt`.

```json
{
  "quoteId": "quote_abc",
  "purchaseType": "MONTHLY",
  "coverageStartDate": "2026-09-01",
  "coverageEndExclusive": "2026-10-01",
  "scheduledSessionCount": 5,
  "sessionIds": [101, 102, 103, 104, 105],
  "monthlyPrice": 5000.00,
  "effectiveRounded": 1000.00,
  "individualSurchargePercent": 10.00,
  "individualPrice": 1100.00,
  "quotedTotal": 5000.00,
  "expiresAt": "2026-09-01T00:10:00-03:00"
}
```

### Crear Purchase

```http
POST /api/v1/billing/physical-purchases
```

```json
{
  "quoteId": "quote_abc",
  "paymentMethod": "MERCADO_PAGO",
  "idempotencyKey": "user-123-quote-abc"
}
```

La Purchase debe iniciarse antes de vencer el quote. Los estados son:

- Payment: `PENDING`, `COMPLETED`, `FAILED`, `REQUIRES_MANUAL_REVIEW`.
- Purchase: `QUOTED`, `PAYMENT_PENDING`, `CAPACITY_ASSIGNED`, `EXCEPTION`,
  `CANCELLED`.
- CapacityHold: `ACTIVE`, `CONVERTED`, `EXPIRED`, `RELEASED`.

Payment solo pasa a `COMPLETED` después de asignar todos los cupos. Movimiento
externo sin capacidad implica Payment `REQUIRES_MANUAL_REVIEW` y Purchase
`EXCEPTION`, sin política automática de reembolso.

## Webhooks y callback

```http
POST /api/v1/billing/payments/mercadopago/webhook
```

La API valida firma y persiste/deduplica el evento en el inbox antes del ACK. Un
worker procesa con reintentos idempotentes. El callback del BFF correlaciona
`purchaseId` con `state` firmado y consulta:

```http
GET /api/v1/billing/physical-purchases/{purchaseId}
```

El callback no confirma Payment ni acepta estado o importe del navegador.

## Códigos de error

| Código | Descripción |
|--------|-------------|
| `QUOTE_EXPIRED` | Quote vencido antes de iniciar Purchase |
| `NO_SCHEDULED_SESSIONS` | Período sin sesiones programadas |
| `INVALID_INDIVIDUAL_SURCHARGE` | Recargo no positivo |
| `COURSE_CAPACITY_UNAVAILABLE` | Capacidad insuficiente |
| `MANUAL_REVIEW_REQUIRED` | Movimiento externo con excepción pendiente |
| `IDEMPOTENCY_CONFLICT` | Reutilización incompatible de clave |

## User Stories Relacionadas

- [US-BILLING-001](./user-stories/US-BILLING-001.md) a
  [US-BILLING-005](./user-stories/US-BILLING-005.md): planes y suscripciones.
- [US-BILLING-006](./user-stories/US-BILLING-006.md): quotes presenciales.
- [US-BILLING-007](./user-stories/US-BILLING-007.md): compra mensual/individual.
- [US-BILLING-008](./user-stories/US-BILLING-008.md): transferencia y excepción.
- [US-BILLING-009](./user-stories/US-BILLING-009.md): pricing y auditoría.
