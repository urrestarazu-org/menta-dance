# US-BILLING-004: Consulta de estado de suscripción

**ID:** US-BILLING-004
**Título:** Consulta de estado de suscripción del usuario
**Módulo / API:** Billing API
**Prioridad (MoSCoW):** Must Have
**Estado:** Draft
**Épica:** EP-03 Suscripciones

---

## 1. Historia de Usuario

> **Como** usuario autenticado
> **Quiero** consultar el estado de mi suscripción actual
> **Para** saber cuánto tiempo de acceso me queda y gestionar mi cuenta.

---

## 2. Criterios de Aceptación (BDD)

**Escenario 1: Consultar suscripción activa**

* **Dado que (Given):** Un usuario tiene una suscripción con `status = ACTIVE`.
* **Cuando (When):** Envía `GET /api/v1/billing/subscriptions/me`.
* **Entonces (Then):** El sistema debe devolver los detalles de la suscripción activa.
* **Y (And):** Debe incluir: plan actual, fecha de inicio, fecha de vencimiento, días restantes.
* **Y (And):** Debe devolver un código HTTP `200 OK`.

**Escenario 2: Suscripción próxima a vencer**

* **Dado que (Given):** Un usuario tiene suscripción que vence en menos de 7 días.
* **Cuando (When):** Consulta su suscripción.
* **Entonces (Then):** La respuesta debe incluir flag `expiringsSoon: true`.
* **Y (And):** Debe incluir mensaje de recordatorio de renovación.

**Escenario 3: Suscripción expirada**

* **Dado que (Given):** Un usuario tiene una suscripción con `status = EXPIRED`.
* **Cuando (When):** Consulta su suscripción.
* **Entonces (Then):** El sistema debe indicar que la suscripción expiró.
* **Y (And):** Debe incluir la fecha de expiración.
* **Y (And):** Debe sugerir renovación con link a planes.

**Escenario 4: Sin suscripción**

* **Dado que (Given):** Un usuario nunca ha tenido una suscripción.
* **Cuando (When):** Consulta su suscripción.
* **Entonces (Then):** El sistema debe devolver un código HTTP `404 Not Found`.
* **Y (And):** El mensaje debe indicar "No tiene suscripciones".
* **Y (And):** Debe sugerir ver planes disponibles.

**Escenario 5: Historial de suscripciones**

* **Dado que (Given):** Un usuario quiere ver todas sus suscripciones anteriores.
* **Cuando (When):** Envía `GET /api/v1/billing/subscriptions/me/history`.
* **Entonces (Then):** El sistema debe devolver lista de todas las suscripciones.
* **Y (And):** Debe ordenarlas por fecha de creación descendente.
* **Y (And):** Debe incluir estado de cada una.

**Escenario 6: Suscripción pendiente de pago**

* **Dado que (Given):** Un usuario tiene una suscripción con `status = PENDING`.
* **Cuando (When):** Consulta su suscripción.
* **Entonces (Then):** El sistema debe indicar que el pago está pendiente.
* **Y (And):** Debe incluir información del pago pendiente y cómo completarlo.

---

## 3. Requisitos No Funcionales y Restricciones

* **Seguridad / Autorización:**
  * Requiere access token válido.
  * El usuario solo puede ver sus propias suscripciones.
* **Rendimiento / Rate Limiting:**
  * Sin límite específico (operación de lectura básica).
  * Respuesta debe ser < 100ms.
* **Caché:**
  * No cacheable (datos personales que pueden cambiar).

---

## 4. Notas Técnicas (Arquitectura)

* **Endpoints Involucrados:**
  * `GET /api/v1/billing/subscriptions/me` - Suscripción actual
  * `GET /api/v1/billing/subscriptions/me/history` - Historial
* **Response Body (suscripción activa):**

  ```json
  {
    "subscription": {
      "id": 123,
      "status": "ACTIVE",
      "plan": {
        "id": 1,
        "name": "Plan Mensual",
        "price": 15000.00
      },
      "startDate": "2024-01-01T00:00:00Z",
      "endDate": "2024-01-31T23:59:59Z",
      "daysRemaining": 15,
      "expiringSoon": false,
      "autoRenew": false
    }
  }
  ```

* **Response Body (sin suscripción):**

  ```json
  {
    "error": "NO_SUBSCRIPTION",
    "message": "No tiene suscripciones activas",
    "suggestion": {
      "action": "VIEW_PLANS",
      "url": "/api/v1/billing/plans"
    }
  }
  ```

* **Tablas de BD (Schemas):**
  * `menta_billing.subscriptions` - Suscripciones del usuario
  * `menta_billing.plans` - Información del plan

---

## 5. Definition of Done (Criterios de Finalización)

* [ ] La lógica implementa todos los Criterios de Aceptación (6 escenarios).
* [ ] Se han escrito **Pruebas Unitarias** para cálculo de días restantes.
* [ ] Se han escrito **Pruebas de Integración** para los endpoints REST.
* [ ] El endpoint está documentado en el contrato **OpenAPI/Swagger**.
* [ ] El código pasa la validación de Checkstyle y ArchUnit.
* [ ] No introduce nuevos "Issues" severos en SonarCloud.
* [ ] Se verifica correcta detección de suscripciones próximas a vencer.
