# US-BILLING-005: Verificación de pagos manuales (Admin)

**ID:** US-BILLING-005
**Título:** Verificación administrativa de pagos por transferencia
**Módulo / API:** Billing API
**Prioridad (MoSCoW):** Must Have
**Estado:** Implementado (#33)
**Épica:** EP-05 Administración

---

## 1. Historia de Usuario

> **Como** administrador
> **Quiero** verificar los comprobantes de transferencia bancaria de los usuarios
> **Para** aprobar o rechazar pagos y activar las suscripciones correspondientes.

---

## 2. Criterios de Aceptación (BDD)

**Escenario 1: Listar pagos pendientes de verificación**

* **Dado que (Given):** Un administrador accede al panel de pagos.
* **Cuando (When):** Envía `GET /api/v1/admin/billing/payments?status=PENDING&substatus=AWAITING_MANUAL_VERIFICATION`.
* **Entonces (Then):** El sistema debe devolver todos los pagos pendientes de verificación.
* **Y (And):** Debe incluir: usuario, monto, fecha, plan, `hasProof`.
* **Y (And):** Debe ordenarlos por fecha de creación (más antiguos primero).
* **Y (And):** Debe devolver un código HTTP `200 OK`.
* **Y (And):** Un `pageSize` mayor a 50 debe rechazarse con `400 Bad Request` (nunca recortarse).

**Escenario 2: Ver detalle de pago con comprobante**

* **Dado que (Given):** Un administrador selecciona un pago pendiente.
* **Cuando (When):** Envía `GET /api/v1/admin/billing/payments/{paymentId}`.
* **Entonces (Then):** El sistema debe devolver el detalle completo del pago.
* **Y (And):** Debe incluir una URL firmada, válida por 15 minutos, para ver el comprobante (cuando existe).
* **Y (And):** Debe incluir datos del usuario y del plan.

**Escenario 2b: Servir el comprobante mediante token firmado**

* **Dado que (Given):** Un token firmado vigente, emitido por el detalle del pago.
* **Cuando (When):** Se solicita `GET /api/v1/billing/payments/{paymentId}/proof?token=…`.
* **Entonces (Then):** El sistema devuelve el archivo con `200 OK`.
* **Y (And):** Un token ausente responde `401`; un token alterado o expirado responde `403` — sin distinguir entre sí (propiedad anti-oráculo).
* **Y (And):** Este endpoint no requiere rol ADMIN — el token es la credencial.

**Escenario 3: Aprobar pago**

* **Dado que (Given):** Un administrador verificó que el comprobante es válido.
* **Cuando (When):** Envía `POST /api/v1/admin/billing/payments/{paymentId}/approve`.
* **Entonces (Then):** El sistema debe cambiar el pago a `status = COMPLETED`.
* **Y (And):** Debe activar la suscripción asociada (`status = ACTIVE`).
* **Y (And):** Debe calcular `endDate` de la suscripción.
* **Y (And):** Debe enviar email de confirmación al usuario, fuera de la transacción que confirma el pago.
* **Y (And):** Debe registrar la acción con el admin que la realizó en `billing_audit_log`.
* **Y (And):** Debe devolver un código HTTP `200 OK`.

**Escenario 4: Rechazar pago**

* **Dado que (Given):** Un administrador detectó que el comprobante es inválido.
* **Cuando (When):** Envía `POST /api/v1/admin/billing/payments/{paymentId}/reject` con motivo.
* **Entonces (Then):** El sistema debe cambiar el pago a `status = REJECTED`.
* **Y (And):** Debe cancelar la suscripción pendiente asociada.
* **Y (And):** Debe enviar email al usuario con el motivo del rechazo, fuera de la transacción.
* **Y (And):** Debe registrar la acción con el admin y el motivo en `billing_audit_log`.
* **Y (And):** Debe devolver un código HTTP `200 OK`.

**Escenario 5: Pago ya procesado**

* **Dado que (Given):** Un pago ya fue aprobado o rechazado anteriormente.
* **Cuando (When):** Un administrador intenta procesarlo nuevamente.
* **Entonces (Then):** El sistema debe devolver un código HTTP `409 Conflict`.
* **Y (And):** El detalle indica que el pago no está en un estado que admita la operación.

**Escenario 6: Rechazo sin motivo**

* **Dado que (Given):** Un administrador intenta rechazar un pago.
* **Cuando (When):** No incluye el motivo del rechazo en el request.
* **Entonces (Then):** El sistema debe devolver un código HTTP `400 Bad Request`.

**Escenario 7: Corrección excepcional auditada**

* **Dado que (Given):** Un pago está en `PENDING/RECONCILIATION_REQUIRED` y existe evidencia verificable.
* **Cuando (When):** Un administrador envía `POST /api/v1/admin/billing/payments/{paymentId}/corrections` con `decision`, `reason` y `evidence`.
* **Entonces (Then):** El sistema aplica sólo la corrección autorizada (transición a un estado terminal, D9), marca resuelta la tarea de reconciliación ligada, y registra una auditoría append-only con la evidencia.
* **Y (And):** Una corrección sobre un pago que no está en `RECONCILIATION_REQUIRED` responde `409` sin cambiar nada.
* **Y (And):** Esta corrección **no** envía email al comprador — ver "Decisiones de implementación" abajo (a diferencia de aprobar/rechazar).

---

## 3. Requisitos No Funcionales y Restricciones

* **Seguridad / Autorización:**
  * Los endpoints de listado, detalle, aprobar/rechazar y corrección requieren rol ADMIN.
  * El endpoint de servido de comprobante (`GET .../proof?token=`) no requiere rol ADMIN — el token HMAC firmado es la credencial (`permitAll` explícito en `SecurityConfig`).
  * Auditar todas las acciones de aprobación, rechazo y corrección.
  * URLs de comprobantes son temporales (token HMAC firmado, 15 minutos, timing-safe).
* **Rendimiento / Rate Limiting:**
  * Sin límite específico para admins.
  * Paginación obligatoria para listados (max 50 por página, rechazado con `400` si se excede — nunca recortado).
* **Auditoría:**
  * Registrar en `billing_audit_log`: `admin_id`, `action`, `created_at`, `payment_id`, `reason` (si aplica; una corrección compone `"{motivo} | evidencia: {evidence}"`).

---

## 4. Notas Técnicas (Arquitectura)

* **Endpoints Involucrados:**
  * `GET /api/v1/admin/billing/payments` - Listar pagos pendientes de verificación
  * `GET /api/v1/admin/billing/payments/{paymentId}` - Detalle de pago con URL de comprobante firmada
  * `GET /api/v1/billing/payments/{paymentId}/proof?token=` - Servir el archivo del comprobante (token-autenticado, sin rol)
  * `POST /api/v1/admin/billing/payments/{paymentId}/approve` - Aprobar pago (ya existente desde #31)
  * `POST /api/v1/admin/billing/payments/{paymentId}/reject` - Rechazar pago (ya existente desde #31)
  * `POST /api/v1/admin/billing/payments/{paymentId}/corrections` - Corregir excepción auditada
* **Request Body (rechazar):**

  ```json
  {
    "reason": "El monto transferido no coincide con el precio del plan"
  }
  ```

* **Request Body (corrección):**

  ```json
  {
    "decision": "APPROVED",
    "reason": "Transferencia confirmada por el banco fuera del webhook",
    "evidence": "Comprobante bancario adjunto por email, ticket #4521"
  }
  ```

* **Response Body (listar):**

  ```json
  {
    "items": [
      {
        "paymentId": "b3f1...-uuid",
        "userId": "9a02...-uuid",
        "targetModality": "VIRTUAL",
        "targetReference": "plan-uuid",
        "amount": 15000.00,
        "currency": "ARS",
        "statusType": "AWAITING_MANUAL_VERIFICATION",
        "hasProof": true,
        "createdAt": "2026-09-15T10:30:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 45,
    "totalPages": 3
  }
  ```

* **Tablas de BD (Schemas):**
  * `billing_payments` - Pagos
  * `billing_audit_log` - Auditoría append-only de aprobar/rechazar/corregir (V24)
  * `billing_reconciliation_tasks` - Gana `resolved`/`resolved_at`/`resolved_by` (V24), resueltas por la corrección
  * `billing_subscriptions` - Para activar/cancelar
* **Notificaciones:**
  * Email al comprador tras aprobación/rechazo, en español, dos templates distintos (la de rechazo incluye el motivo textual).
  * Enviado **fuera** de la transacción que confirma el pago (`TransactionSynchronization.afterCommit`); una falla de envío nunca revierte la decisión.
  * Puerto y adaptador **separados** del mailbox operativo que ya notifica la subida de un comprobante (`SpringMailPaymentProofNotificationAdapter`, sin cambios en este alcance).

---

## 5. Decisiones de implementación (#33)

Estas decisiones se tomaron con el usuario durante el diseño de #33 y no se reabren.
Documentadas también en `openspec/changes/billing-manual-payment-verification/proposal.md`
(D1–D9).

* **D3 — Prefijo de ruta, diverge del texto literal del issue.** El issue original pedía
  `/api/v1/billing/admin/payments/...` (ver Escenarios arriba en su redacción histórica). La
  implementación final usa **`/api/v1/admin/billing/payments/...`**, igual al prefijo ya
  shippeado de aprobar/rechazar (#31) y consistente con el resto de la superficie admin
  (`/api/v1/admin/billing/subscriptions`, `/api/v1/admin/physical/devices`). La regla existente
  `/api/v1/admin/**` de `SecurityConfig` cubre los tres endpoints administrativos sin un nuevo
  matcher — sólo el endpoint de servido de comprobante, que no vive bajo `/admin/**`, necesita
  su propia regla explícita.
* **D9 — La corrección es una transición de estado, no una edición de datos.** El administrador
  resuelve un pago `PENDING/RECONCILIATION_REQUIRED` hacia un estado terminal (equivalente a
  aprobado o a rechazado) con `reason` y `evidence` obligatorios — la misma forma que
  `resolveManually`, aplicada al estado de partida distinto de esta corrección. Ajustar datos del
  pago (monto, referencia del proveedor) sin una transición de estado queda explícitamente fuera
  de alcance.
* **Sin email de comprador en la corrección.** A diferencia de aprobar/rechazar, la corrección
  **no** dispara el email al comprador — ni el requerimiento de spec "Buyer-facing decision
  email" ni los Success Criteria de la propuesta lo mencionan para este flujo, sólo auditoría y
  resolución de la tarea de reconciliación. Agregarlo sería un cambio nuevo, con su propio spec.
* **D1 — Mecanismo de comprobante firmado.** Token HMAC local con expiración embebida (15
  minutos), sirviendo el archivo desde el almacenamiento local ya existente — sin migrar a
  S3/MinIO en este cambio.

---

## 6. Definition of Done (Criterios de Finalización)

* [x] La lógica implementa todos los Criterios de Aceptación (Escenarios 1–7).
* [x] Se han escrito **Pruebas Unitarias** para lógica de aprobación/rechazo/corrección y para el
      firmado/verificación del token de comprobante.
* [x] Se han escrito **Pruebas de Integración** para el endpoint de servido de comprobante
      (`PaymentProofIntegrationTest`, Testcontainers, filtro de seguridad completo).
* [x] El endpoint está documentado en el contrato **OpenAPI/Swagger** (`api/openapi/billing-v1.yaml`).
* [x] El código pasa la validación de Checkstyle y ArchUnit.
* [x] Los emails de confirmación/rechazo funcionan correctamente, en templates separados del
      mailbox operativo de subida de comprobante.
* [x] La auditoría registra todas las acciones de administradores (aprobar, rechazar, corregir).
* [x] Las URLs de comprobantes son temporales y seguras (token HMAC firmado, timing-safe, 15
      minutos, anti-oráculo entre "expirado" y "nunca válido").
