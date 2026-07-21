# US-BILLING-005: Verificación de pagos manuales (Admin)

**ID:** US-BILLING-005
**Título:** Verificación administrativa de pagos por transferencia
**Módulo / API:** Billing API
**Prioridad (MoSCoW):** Must Have
**Estado:** Draft
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
* **Cuando (When):** Envía `GET /api/v1/billing/admin/payments?status=PENDING_VERIFICATION`.
* **Entonces (Then):** El sistema debe devolver todos los pagos pendientes de verificación.
* **Y (And):** Debe incluir: usuario, monto, fecha, plan, link al comprobante.
* **Y (And):** Debe ordenarlos por fecha de creación (más antiguos primero).
* **Y (And):** Debe devolver un código HTTP `200 OK`.

**Escenario 2: Ver detalle de pago con comprobante**

* **Dado que (Given):** Un administrador selecciona un pago pendiente.
* **Cuando (When):** Envía `GET /api/v1/billing/admin/payments/{paymentId}`.
* **Entonces (Then):** El sistema debe devolver el detalle completo del pago.
* **Y (And):** Debe incluir URL segura para ver el comprobante.
* **Y (And):** Debe incluir datos del usuario y del plan.

**Escenario 3: Aprobar pago**

* **Dado que (Given):** Un administrador verificó que el comprobante es válido.
* **Cuando (When):** Envía `POST /api/v1/billing/admin/payments/{paymentId}/approve`.
* **Entonces (Then):** El sistema debe cambiar el pago a `status = COMPLETED`.
* **Y (And):** Debe activar la suscripción asociada (`status = ACTIVE`).
* **Y (And):** Debe calcular `endDate` de la suscripción.
* **Y (And):** Debe enviar email de confirmación al usuario.
* **Y (And):** Debe registrar la acción con el admin que la realizó.
* **Y (And):** Debe devolver un código HTTP `200 OK`.

**Escenario 4: Rechazar pago**

* **Dado que (Given):** Un administrador detectó que el comprobante es inválido.
* **Cuando (When):** Envía `POST /api/v1/billing/admin/payments/{paymentId}/reject` con motivo.
* **Entonces (Then):** El sistema debe cambiar el pago a `status = REJECTED`.
* **Y (And):** Debe cancelar la suscripción pendiente asociada.
* **Y (And):** Debe enviar email al usuario con el motivo del rechazo.
* **Y (And):** Debe registrar la acción con el admin y el motivo.
* **Y (And):** Debe devolver un código HTTP `200 OK`.

**Escenario 5: Pago ya procesado**

* **Dado que (Given):** Un pago ya fue aprobado o rechazado anteriormente.
* **Cuando (When):** Un administrador intenta procesarlo nuevamente.
* **Entonces (Then):** El sistema debe devolver un código HTTP `409 Conflict`.
* **Y (And):** El mensaje debe indicar "El pago ya fue procesado".

**Escenario 6: Rechazo sin motivo**

* **Dado que (Given):** Un administrador intenta rechazar un pago.
* **Cuando (When):** No incluye el motivo del rechazo en el request.
* **Entonces (Then):** El sistema debe devolver un código HTTP `400 Bad Request`.
* **Y (And):** El mensaje debe indicar "El motivo de rechazo es obligatorio".

---

## 3. Requisitos No Funcionales y Restricciones

* **Seguridad / Autorización:**
  * Todos los endpoints requieren rol ADMIN.
  * Auditar todas las acciones de aprobación/rechazo.
  * URLs de comprobantes deben ser temporales (signed URLs, 15 minutos).
* **Rendimiento / Rate Limiting:**
  * Sin límite específico para admins.
  * Paginación obligatoria para listados (max 50 por página).
* **Auditoría:**
  * Registrar: admin_id, acción, timestamp, payment_id, motivo (si aplica).

---

## 4. Notas Técnicas (Arquitectura)

* **Endpoints Involucrados:**
  * `GET /api/v1/billing/admin/payments` - Listar pagos (filtrable por status)
  * `GET /api/v1/billing/admin/payments/{paymentId}` - Detalle de pago
  * `POST /api/v1/billing/admin/payments/{paymentId}/approve` - Aprobar pago
  * `POST /api/v1/billing/admin/payments/{paymentId}/reject` - Rechazar pago
* **Request Body (rechazar):**

  ```json
  {
    "reason": "El monto transferido no coincide con el precio del plan"
  }
  ```

* **Response Body (listar):**

  ```json
  {
    "payments": [
      {
        "id": 456,
        "userId": 789,
        "userEmail": "usuario@example.com",
        "userName": "Juan Pérez",
        "planName": "Plan Mensual",
        "amount": 15000.00,
        "currency": "ARS",
        "status": "PENDING_VERIFICATION",
        "hasProof": true,
        "createdAt": "2024-01-15T10:30:00Z"
      }
    ],
    "pagination": {
      "page": 1,
      "size": 20,
      "total": 45
    }
  }
  ```

* **Tablas de BD (Schemas):**
  * `menta_billing.payments` - Pagos
  * `menta_billing.payment_audits` - Auditoría de acciones
  * `menta_billing.subscriptions` - Para activar/cancelar
* **Notificaciones:**
  * Email al usuario tras aprobación/rechazo.
  * Template distinto para cada caso.

---

## 5. Definition of Done (Criterios de Finalización)

* [ ] La lógica implementa todos los Criterios de Aceptación (6 escenarios).
* [ ] Se han escrito **Pruebas Unitarias** para lógica de aprobación/rechazo.
* [ ] Se han escrito **Pruebas de Integración** para los endpoints REST.
* [ ] El endpoint está documentado en el contrato **OpenAPI/Swagger**.
* [ ] El código pasa la validación de Checkstyle y ArchUnit.
* [ ] No introduce nuevos "Issues" severos en SonarCloud.
* [ ] Los emails de confirmación/rechazo funcionan correctamente.
* [ ] La auditoría registra todas las acciones de administradores.
* [ ] Las URLs de comprobantes son temporales y seguras.
