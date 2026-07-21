# US-BILLING-002: Suscripción con Mercado Pago

**ID:** US-BILLING-002
**Título:** Suscripción mediante pago con Mercado Pago
**Módulo / API:** Billing API
**Prioridad (MoSCoW):** Must Have
**Estado:** Draft
**Épica:** EP-03 Suscripciones

---

## 1. Historia de Usuario

> **Como** usuario autenticado
> **Quiero** suscribirme a un plan pagando con Mercado Pago
> **Para** acceder inmediatamente al contenido premium sin esperar verificación manual.

---

## 2. Criterios de Aceptación (BDD)

**Escenario 1: Iniciar checkout con Mercado Pago**

* **Dado que (Given):** Un usuario autenticado seleccionó un plan.
* **Cuando (When):** Envía `POST /api/v1/billing/subscriptions` con `planId` y `paymentMethod: MERCADO_PAGO`.
* **Entonces (Then):** El sistema debe crear una suscripción en estado `PENDING`.
* **Y (And):** Debe generar una preferencia de pago en Mercado Pago.
* **Y (And):** Debe devolver la URL de checkout de MP para redirección.
* **Y (And):** Debe devolver un código HTTP `201 Created`.

**Escenario 2: Pago completado exitosamente (webhook)**

* **Dado que (Given):** El usuario completó el pago en Mercado Pago.
* **Cuando (When):** MP envía webhook de pago aprobado a `POST /api/v1/billing/payments/mercadopago/webhook`.
* **Entonces (Then):** El sistema debe actualizar la suscripción a `ACTIVE`.
* **Y (And):** Debe registrar el pago con `status = COMPLETED`.
* **Y (And):** Debe calcular `endDate` basado en `durationDays` del plan.
* **Y (And):** Debe enviar email de confirmación al usuario.

**Escenario 3: Pago rechazado**

* **Dado que (Given):** El usuario intentó pagar pero fue rechazado.
* **Cuando (When):** MP envía webhook de pago rechazado.
* **Entonces (Then):** El sistema debe mantener la suscripción en `PENDING`.
* **Y (And):** Debe registrar el pago con `status = FAILED` y razón del rechazo.
* **Y (And):** Debe enviar email informando que el pago no pudo procesarse.

**Escenario 4: Pago pendiente (procesando)**

* **Dado que (Given):** El usuario eligió un método de pago que requiere procesamiento.
* **Cuando (When):** MP envía webhook de pago pendiente.
* **Entonces (Then):** El sistema debe mantener la suscripción en `PENDING`.
* **Y (And):** Debe registrar el pago con `status = PENDING_PAYMENT`.
* **Y (And):** Debe notificar al usuario que el pago está siendo procesado.

**Escenario 5: Usuario ya tiene suscripción activa**

* **Dado que (Given):** Un usuario con suscripción `ACTIVE` vigente.
* **Cuando (When):** Intenta crear otra suscripción al mismo plan.
* **Entonces (Then):** El sistema debe devolver un código HTTP `409 Conflict`.
* **Y (And):** El mensaje debe indicar "Ya tiene una suscripción activa para este plan".

**Escenario 6: Redirección post-pago (retorno de MP)**

* **Dado que (Given):** El usuario completó el flujo de pago en MP.
* **Cuando (When):** MP redirige al usuario a `/redirection/subscription/mercadopago`.
* **Entonces (Then):** El sistema debe verificar el estado del pago con MP.
* **Y (And):** Debe mostrar página de éxito o error según corresponda.
* **Y (And):** Debe prevenir creación de suscripciones duplicadas (por `payment_id`).

---

## 3. Requisitos No Funcionales y Restricciones

* **Seguridad / Autorización:**
  * `POST /subscriptions`: Requiere access token válido.
  * `POST /api/v1/billing/payments/mercadopago/webhook`: Validar firma HMAC de MP.
  * No exponer credenciales de MP en respuestas.
* **Rendimiento / Rate Limiting:**
  * Máximo 5 intentos de suscripción por usuario por hora.
  * Webhooks de MP no tienen rate limiting (prioridad alta).
* **Idempotencia:**
  * Webhooks deben ser idempotentes (pueden llegar duplicados).
  * Verificar `payment_id` antes de procesar para evitar duplicados.
* **Timeout:**
  * Llamadas a API de MP con timeout de 10 segundos.

---

## 4. Notas Técnicas (Arquitectura)

* **Endpoints Involucrados:**
  * `POST /api/v1/billing/subscriptions` - Crear suscripción
  * `POST /api/v1/billing/payments/mercadopago/webhook` - Recibir notificaciones de MP
  * `GET /redirection/subscription/mercadopago` - Retorno post-pago
* **Request Body (crear suscripción):**

  ```json
  {
    "planId": 1,
    "paymentMethod": "MERCADO_PAGO"
  }
  ```

* **Response Body (crear suscripción):**

  ```json
  {
    "subscriptionId": 123,
    "status": "PENDING",
    "checkoutUrl": "https://www.mercadopago.com.ar/checkout/v1/redirect?pref_id=xxx"
  }
  ```

* **Tablas de BD (Schemas):**
  * `menta_billing.subscriptions` - Suscripciones de usuarios
  * `menta_billing.payments` - Registro de pagos
  * `menta_billing.mercadopago_redirections` - Registro de redirecciones MP
* **Integraciones:**
  * Mercado Pago Checkout Pro API
  * Servicio de email para confirmaciones

---

## 5. Definition of Done (Criterios de Finalización)

* [ ] La lógica implementa todos los Criterios de Aceptación (6 escenarios).
* [ ] Se han escrito **Pruebas Unitarias** para la lógica de suscripción y webhooks.
* [ ] Se han escrito **Pruebas de Integración** para los endpoints REST.
* [ ] Se verifica la validación de firma HMAC de webhooks.
* [ ] El endpoint está documentado en el contrato **OpenAPI/Swagger**.
* [ ] El código pasa la validación de Checkstyle y ArchUnit.
* [ ] No introduce nuevos "Issues" severos en SonarCloud.
* [ ] Se prueba el flujo completo en ambiente sandbox de MP.
