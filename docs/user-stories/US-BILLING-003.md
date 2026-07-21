# US-BILLING-003: Suscripción con transferencia bancaria

**ID:** US-BILLING-003
**Título:** Suscripción mediante transferencia bancaria
**Módulo / API:** Billing API
**Prioridad (MoSCoW):** Should Have
**Estado:** Draft
**Épica:** EP-03 Suscripciones

---

## 1. Historia de Usuario

> **Como** usuario autenticado
> **Quiero** suscribirme a un plan pagando por transferencia bancaria
> **Para** tener una alternativa de pago cuando no puedo usar tarjeta de crédito/débito.

---

## 2. Criterios de Aceptación (BDD)

**Escenario 1: Solicitar pago por transferencia**

* **Dado que (Given):** Un usuario autenticado seleccionó un plan.
* **Cuando (When):** Envía `POST /api/v1/billing/subscriptions` con `paymentMethod: BANK_TRANSFER`.
* **Entonces (Then):** El sistema debe crear una suscripción en estado `PENDING`.
* **Y (And):** Debe crear un pago en estado `PENDING_VERIFICATION`.
* **Y (And):** Debe devolver los datos bancarios para realizar la transferencia.
* **Y (And):** Debe devolver un código HTTP `201 Created`.

**Escenario 2: Subir comprobante de pago**

* **Dado que (Given):** Un usuario tiene un pago pendiente de verificación.
* **Cuando (When):** Envía `POST /api/v1/billing/payments/{paymentId}/proof` con la imagen del comprobante.
* **Entonces (Then):** El sistema debe almacenar el comprobante de forma segura.
* **Y (And):** Debe notificar al administrador que hay un pago pendiente de verificación.
* **Y (And):** Debe devolver un código HTTP `200 OK` con mensaje de confirmación.

**Escenario 3: Comprobante inválido (formato)**

* **Dado que (Given):** Un usuario intenta subir un comprobante.
* **Cuando (When):** El archivo no es una imagen válida (PNG, JPG, PDF) o excede 5MB.
* **Entonces (Then):** El sistema debe devolver un código HTTP `400 Bad Request`.
* **Y (And):** El mensaje debe indicar el formato o tamaño permitido.

**Escenario 4: Pago ya tiene comprobante**

* **Dado que (Given):** Un usuario ya subió un comprobante para un pago.
* **Cuando (When):** Intenta subir otro comprobante para el mismo pago.
* **Entonces (Then):** El sistema debe reemplazar el comprobante anterior.
* **Y (And):** Debe notificar nuevamente al administrador.
* **Y (And):** Debe devolver un código HTTP `200 OK`.

**Escenario 5: Consultar estado de pago**

* **Dado que (Given):** Un usuario tiene un pago pendiente de verificación.
* **Cuando (When):** Envía `GET /api/v1/billing/payments/{paymentId}`.
* **Entonces (Then):** El sistema debe devolver el estado actual del pago.
* **Y (And):** Debe incluir: estado, fecha de creación, fecha de último update.
* **Y (And):** Debe devolver un código HTTP `200 OK`.

**Escenario 6: Pago expirado por timeout**

* **Dado que (Given):** Un pago está en `PENDING_VERIFICATION` sin comprobante.
* **Cuando (When):** Pasan más de 72 horas desde la creación.
* **Entonces (Then):** El sistema debe marcar el pago como `EXPIRED`.
* **Y (And):** Debe cancelar la suscripción pendiente asociada.

---

## 3. Requisitos No Funcionales y Restricciones

* **Seguridad / Autorización:**
  * Todos los endpoints requieren access token válido.
  * El usuario solo puede ver/modificar sus propios pagos.
  * Los comprobantes deben almacenarse en ubicación segura (no pública).
* **Rendimiento / Rate Limiting:**
  * Máximo 3 uploads de comprobante por pago.
  * Máximo 10 pagos por transferencia por usuario por día.
* **Almacenamiento:**
  * Comprobantes: máximo 5MB por archivo.
  * Formatos aceptados: PNG, JPG, JPEG, PDF.
  * Retención de comprobantes: 1 año después de verificación.

---

## 4. Notas Técnicas (Arquitectura)

* **Endpoints Involucrados:**
  * `POST /api/v1/billing/subscriptions` - Crear suscripción con transferencia
  * `POST /api/v1/billing/payments/{paymentId}/proof` - Subir comprobante
  * `GET /api/v1/billing/payments/{paymentId}` - Consultar estado
* **Request Body (crear suscripción):**

  ```json
  {
    "planId": 1,
    "paymentMethod": "BANK_TRANSFER"
  }
  ```

* **Response Body (crear suscripción):**

  ```json
  {
    "subscriptionId": 123,
    "paymentId": 456,
    "status": "PENDING",
    "bankDetails": {
      "bankName": "Banco Nación",
      "accountType": "Cuenta Corriente",
      "cbu": "0110000000000000000000",
      "alias": "MENTA.VIRTUAL.ACADEMY",
      "accountHolder": "Menta Academy SRL",
      "cuit": "30-12345678-9",
      "amount": 15000.00,
      "currency": "ARS",
      "reference": "SUB-123-USR-456"
    },
    "expiresAt": "2024-01-20T00:00:00Z"
  }
  ```

* **Request (subir comprobante):**
  * Content-Type: `multipart/form-data`
  * Field: `proof` (archivo de imagen)
* **Tablas de BD (Schemas):**
  * `menta_billing.subscriptions` - Suscripciones
  * `menta_billing.payments` - Pagos con referencia a comprobante
  * `menta_billing.payment_proofs` - Metadatos de comprobantes
* **Storage:**
  * Comprobantes en File System Local Seguro (Volumen Docker) para el MVP.

---

## 5. Definition of Done (Criterios de Finalización)

* [ ] La lógica implementa todos los Criterios de Aceptación (6 escenarios).
* [ ] Se han escrito **Pruebas Unitarias** para upload y validación de comprobantes.
* [ ] Se han escrito **Pruebas de Integración** para los endpoints REST.
* [ ] El endpoint está documentado en el contrato **OpenAPI/Swagger**.
* [ ] El código pasa la validación de Checkstyle y ArchUnit.
* [ ] No introduce nuevos "Issues" severos en SonarCloud.
* [ ] El almacenamiento de comprobantes es seguro (no accesible públicamente).
* [ ] El job de expiración de pagos funciona correctamente.
