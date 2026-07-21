# US-VIRTUAL-007: Visualización de Cursos con Suscripción Cancelada o Inactiva

**ID:** US-VIRTUAL-007
**Título:** Restricción visual y lógica de contenido para suscripción cancelada
**Módulo / API:** BFF Web / Virtual API / Billing API
**Prioridad (MoSCoW):** Must Have
**Estado:** Draft
**Épica:** EP-02 Catálogo Virtual

---

## 1. Historia de Usuario

> **Como** alumno con una suscripción cancelada, expirada o inexistente
> **Quiero** ver claramente qué módulos y lecciones están bloqueados y cuáles están disponibles gratis en la página del curso
> **Para** comprender las limitaciones de mi acceso y saber cómo reactivar mi suscripción.

---

## 2. Criterios de Aceptación (BDD)

**Escenario 1: Mostrar mensaje de advertencia e invitación a suscribirse**

* **Dado que (Given):** Un alumno con una suscripción cancelada o inactiva accede a la página de detalle de un curso (`GET /courses/{hashId}`).
* **Cuando (When):** El BFF renderiza la página del curso.
* **Entonces (Then):** Debe mostrar un banner de advertencia destacado en la parte superior del curso con el mensaje: *"Solo podrás acceder al contenido de muestra gratis. Vuelve a suscribirte para acceder a todo el contenido."*
* **Y (And):** Debe incluir un enlace o botón destacado que redirija al listado de planes de suscripción (`/plans`).

**Escenario 2: Bloqueo visual de módulos premium con icono de candado**

* **Dado que (Given):** Un alumno con una suscripción cancelada o inactiva visualiza el listado de módulos de un curso.
* **Cuando (When):** El sistema consulta los datos del catálogo.
* **Entonces (Then):** Para cada módulo/lección que no sea gratuito (`locked == true` devuelto por la API), el BFF debe renderizar un icono de candado (`🔒`) al lado de su nombre.
* **Y (And):** Debe deshabilitar el enlace de navegación y click para acceder a la lección.

**Escenario 3: Acceso a módulos de muestra gratuita (Free Preview)**

* **Dado que (Given):** Un alumno con una suscripción cancelada o inactiva visualiza el catálogo del curso.
* **Cuando (When):** Un módulo o lección está marcado como gratuito (`locked == false`).
* **Entonces (Then):** Debe aparecer de forma normal, habilitado y 100% accesible para su navegación y reproducción.

**Escenario 4: Intento de acceso forzado por URL directa a lección bloqueada**

* **Dado que (Given):** Un alumno con una suscripción cancelada o inactiva intenta acceder directamente por URL a una lección protegida (`GET /lessons/{lessonHashId}`).
* **Cuando (When):** La Virtual API procesa la consulta y detecta que la lección está bloqueada para el usuario.
* **Entonces (Then):** Debe devolver un código HTTP `403 Forbidden`.
* **Y (And):** El BFF debe redirigir al alumno a la página de planes (`/plans`) con un mensaje de error: *"Tu suscripción ha expirado o está cancelada. Por favor, suscríbete para acceder al contenido."*

---

## 3. Requisitos No Funcionales y Restricciones

* **Seguridad lógica:**
  * El bloqueo no debe ser únicamente a nivel de frontend (CSS/HTML). La Virtual API debe validar en cada request de reproducción (`GET /api/v1/virtual/lessons/{hashId}/stream`) el access token y el derecho de acceso del usuario antes de emitir una URL firmada de Bunny.net.
* **Rendimiento / Latencia:**
  * Para evitar latencias de renderizado, Virtual puede mantener una caché local no autoritativa en Caffeine. Billing publica el evento de aplicación en proceso `SubscriptionStatusChanged` al activar o cancelar una suscripción; Virtual lo consume para invalidar esa caché. No se usa RabbitMQ en el monolito modular.

---

## 4. Notas Técnicas (Arquitectura)

* **Endpoints Involucrados:**
  * `GET /api/v1/virtual/courses/{hashId}` - Detalle del curso para alumno.
  * `GET /api/v1/virtual/lessons/{hashId}/stream` - Obtener URL firmada cuando el alumno tiene acceso.
* **Comunicación entre módulos:**
  * Virtual consulta el estado actual mediante un puerto Java síncrono expuesto por Billing; no realiza una llamada HTTP interna.
  * Billing publica `SubscriptionStatusChanged` dentro del proceso para invalidar la caché de derechos de acceso de Virtual.
* **Response Body de la Virtual API (`GET /api/v1/virtual/courses/{hashId}`):**

  ```json
  {
    "course": {
      "hashId": "xvb5D1e0",
      "title": "Salsa Estilo NY",
      "description": "Aprende los fundamentos...",
      "accessLevel": "PREVIEW_ONLY",
      "modules": [
        {
          "hashId": "mod111",
          "title": "Introducción y Pasos Básicos",
          "locked": false,
          "lessons": [
            {
              "hashId": "les111",
              "title": "El paso básico on 2",
              "locked": false
            }
          ]
        },
        {
          "hashId": "mod222",
          "title": "Giros y Figuras Avanzadas",
          "locked": true,
          "lessons": [
            {
              "hashId": "les222",
              "title": "Giro cruzado en pareja",
              "locked": true
            }
          ]
        }
      ]
    },
    "subscriptionInfo": {
      "active": false,
      "status": "CANCELLED",
      "message": "Solo podrás acceder al contenido de muestra gratis. Vuelve a suscribirte para acceder a todo el contenido.",
      "plansUrl": "/plans"
    }
  }
  ```

* **Tablas de BD (schema único `menta`):**
  * `billing_subscriptions` (para validar status = CANCELLED / EXPIRED).
  * `virtual_courses`, `virtual_modules`, `virtual_lessons` (con bandera isFree / previewOnly).

---

## 5. Definition of Done (Criterios de Finalización)

* [ ] La interfaz del curso muestra el banner e icono de candado (`🔒`) en módulos protegidos al cancelar la suscripción.
* [ ] La reproducción y URL firmada de videos premium están bloqueadas en backend con retorno `403 Forbidden` para suscripciones canceladas.
* [ ] Se han escrito **Pruebas Unitarias** para la lógica de filtrado (`locked = true/false`) en la Virtual API.
* [ ] Se han escrito **Pruebas de Integración** en el BFF simulando un alumno con suscripción inactiva.
* [ ] El código pasa la validación de Checkstyle y no introduce issues en SonarCloud.
