# US-BILLING-001: Ver planes disponibles

**ID:** US-BILLING-001
**Título:** Consulta de planes de suscripción disponibles
**Módulo / API:** Billing API
**Prioridad (MoSCoW):** Must Have
**Estado:** Draft
**Épica:** EP-03 Suscripciones

---

## 1. Historia de Usuario

> **Como** usuario autenticado o visitante
> **Quiero** ver los planes de suscripción disponibles con sus precios y beneficios
> **Para** elegir el plan que mejor se adapte a mis necesidades y presupuesto.

---

## 2. Criterios de Aceptación (BDD)

**Escenario 1: Listar planes activos**

* **Dado que (Given):** Un usuario accede a la página de planes.
* **Cuando (When):** Envía una petición `GET /api/v1/billing/plans`.
* **Entonces (Then):** El sistema debe devolver todos los planes con `status = ACTIVE`.
* **Y (And):** Cada plan debe incluir: nombre, descripción, precio, duración, y lista de cursos incluidos.
* **Y (And):** Los planes deben estar ordenados por precio ascendente.
* **Y (And):** Debe devolver un código HTTP `200 OK`.

**Escenario 2: Plan destacado/recomendado**

* **Dado que (Given):** Existen múltiples planes activos.
* **Cuando (When):** El usuario consulta los planes.
* **Entonces (Then):** El plan marcado como `featured = true` debe indicarse como recomendado.
* **Y (And):** Debe mostrarse visualmente destacado en la respuesta.

**Escenario 3: Sin planes disponibles**

* **Dado que (Given):** No hay planes con `status = ACTIVE` en el sistema.
* **Cuando (When):** El usuario consulta los planes.
* **Entonces (Then):** El sistema debe devolver una lista vacía.
* **Y (And):** Debe devolver un código HTTP `200 OK`.
* **Y (And):** El frontend mostrará mensaje "No hay planes disponibles en este momento".

**Escenario 4: Ver detalle de un plan específico**

* **Dado que (Given):** El usuario quiere más información sobre un plan.
* **Cuando (When):** Envía una petición `GET /api/v1/billing/plans/{planId}`.
* **Entonces (Then):** El sistema debe devolver el detalle completo del plan.
* **Y (And):** Debe incluir: términos y condiciones, política de cancelación, cursos incluidos con detalle.
* **Y (And):** Debe devolver un código HTTP `200 OK`.

**Escenario 5: Plan no encontrado**

* **Dado que (Given):** El usuario solicita un plan específico.
* **Cuando (When):** El `planId` no existe o está inactivo.
* **Entonces (Then):** El sistema debe devolver un código HTTP `404 Not Found`.
* **Y (And):** El mensaje debe indicar "Plan no encontrado o no disponible".

---

## 3. Requisitos No Funcionales y Restricciones

* **Seguridad / Autorización:**
  * Endpoint público (no requiere autenticación para consultar).
  * Los planes con `status != ACTIVE` no deben mostrarse a usuarios.
* **Rendimiento / Rate Limiting:**
  * Máximo 60 requests por minuto por IP (prevención de scraping).
  * Respuesta debe ser < 200ms (cacheable).
* **Caché:**
  * Los planes pueden cachearse por 5 minutos (cambian poco frecuentemente).

---

## 4. Notas Técnicas (Arquitectura)

* **Endpoints Involucrados:**
  * `GET /api/v1/billing/plans` - Listar planes activos
  * `GET /api/v1/billing/plans/{planId}` - Detalle de plan
* **Response Body (lista):**

  ```json
  {
    "plans": [
      {
        "id": 1,
        "name": "Plan Mensual",
        "description": "Acceso completo por 30 días",
        "price": 15000.00,
        "currency": "ARS",
        "durationDays": 30,
        "featured": false,
        "paymentMethods": ["MERCADO_PAGO", "BANK_TRANSFER"],
        "courses": [
          {"id": "abc123", "name": "Tango Básico"}
        ]
      }
    ]
  }
  ```

* **Tablas de BD (Schemas):**
  * `menta_billing.plans` - Planes de suscripción
  * `menta_billing.plan_courses` - Relación plan-cursos

---

## 5. Definition of Done (Criterios de Finalización)

* [ ] La lógica implementa todos los Criterios de Aceptación (5 escenarios).
* [ ] Se han escrito **Pruebas Unitarias** para el servicio de planes.
* [ ] Se han escrito **Pruebas de Integración** para los endpoints REST.
* [ ] El endpoint está documentado en el contrato **OpenAPI/Swagger**.
* [ ] El código pasa la validación de Checkstyle y ArchUnit.
* [ ] No introduce nuevos "Issues" severos en SonarCloud.
* [ ] El caché de planes funciona correctamente.
