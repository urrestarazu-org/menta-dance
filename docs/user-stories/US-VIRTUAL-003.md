# US-VIRTUAL-003: Ver lección gratuita

**ID:** US-VIRTUAL-003
**Título:** Acceso a lecciones gratuitas de preview
**Módulo / API:** Virtual API
**Prioridad (MoSCoW):** Must Have
**Estado:** Draft
**Épica:** EP-02 Catálogo Virtual

---

## 1. Historia de Usuario

> **Como** visitante sin suscripción
> **Quiero** ver la primera lección de cada curso de forma gratuita
> **Para** evaluar la calidad del contenido antes de suscribirme.

---

## 2. Criterios de Aceptación (BDD)

**Escenario 1: Acceso a lección gratuita (visitante)**

* **Dado que (Given):** Un visitante sin autenticación accede a una lección gratuita.
* **Cuando (When):** Envía `GET /api/v1/virtual/lessons/{lessonId}` donde `isFree = true`.
* **Entonces (Then):** El sistema debe devolver el detalle completo de la lección.
* **Y (And):** No debe incluir una URL de video.
* **Y (And):** Debe incluir descripción, duración, y materiales adicionales.
* **Y (And):** Debe devolver un código HTTP `200 OK`.

* **Y (And):** Al solicitar `GET /api/v1/virtual/lessons/{lessonId}/stream`,
  debe devolver una URL firmada y temporal de Bunny.net.

* **Y (And):** Cada material adicional debe referenciar
  `GET /api/v1/virtual/materials/{materialId}`, que valida el acceso a la
  lección y devuelve una URL de descarga firmada y temporal.

**Escenario 2: Acceso a lección gratuita (usuario sin suscripción)**

* **Dado que (Given):** Un usuario autenticado sin suscripción activa.
* **Cuando (When):** Accede a una lección marcada como gratuita.
* **Entonces (Then):** El sistema debe permitir el acceso completo.
* **Y (And):** Debe mostrar invitación a suscribirse para más contenido.

**Escenario 3: Primera lección siempre gratuita**

* **Dado que (Given):** Un curso tiene la primera lección de cada módulo gratuita.
* **Cuando (When):** Un visitante accede a la primera lección del primer módulo.
* **Entonces (Then):** El sistema debe permitir acceso completo al video.
* **Y (And):** Debe indicar "Lección de preview gratuita".

**Escenario 4: Intento de acceso a lección premium (sin suscripción)**

* **Dado que (Given):** Un visitante o usuario sin suscripción.
* **Cuando (When):** Intenta acceder a una lección con `isFree = false`.
* **Entonces (Then):** El sistema debe devolver un código HTTP `403 Forbidden`.
* **Y (And):** Debe incluir preview (thumbnail, duración, descripción).
* **Y (And):** No debe incluir URL del video.
* **Y (And):** Debe incluir mensaje de suscripción requerida.

**Escenario 5: Lección no encontrada**

* **Dado que (Given):** El usuario solicita una lección con lessonId inválido.
* **Cuando (When):** El lessonId no existe.
* **Entonces (Then):** El sistema debe devolver un código HTTP `404 Not Found`.
* **Y (And):** El mensaje debe indicar "Lección no encontrada".

---

## 3. Requisitos No Funcionales y Restricciones

* **Seguridad / Autorización:**
  * Lecciones gratuitas son públicas.
  * Sólo `/stream` devuelve URLs de video de Bunny.net, siempre firmadas y temporales.
  * No cachear URLs de video.
* **Rendimiento / Rate Limiting:**
  * Máximo 30 requests por minuto por IP para video streams.
  * Respuesta de metadata debe ser < 150ms.
* **CDN (Bunny.net):**
  * URLs firmadas con expiración de 4 horas.
  * Token-based authentication para videos.

---

## 4. Notas Técnicas (Arquitectura)

* **Endpoints Involucrados:**
  * `GET /api/v1/virtual/lessons/{lessonId}` - Detalle de lección
  * `GET /api/v1/virtual/lessons/{lessonId}/stream` - URL firmada de streaming
  * `GET /api/v1/virtual/materials/{materialId}` - URL firmada de descarga,
    autorizada por el acceso a la lección asociada
* **Response Body (lección gratuita - acceso permitido):**

  ```json
  {
    "lesson": {
      "lessonId": "3da66c0c-7fbf-45f1-a830-b19f5bfd0001",
      "title": "Historia del Tango",
      "description": "En esta lección aprenderás sobre los orígenes...",
      "duration": "10:30",
      "isFree": true,
      "order": 1,
      "module": {
        "moduleId": "9c1c9477-249d-44d4-a4f1-cd9942c15001",
        "title": "Introducción al Tango"
      },
      "course": {
        "courseId": "b6bb98d6-179e-49d0-9dda-6c03a16998f0",
        "title": "Tango Básico"
      },
      "thumbnailUrl": "https://cdn.bunny.net/thumbs/abc123.jpg",
      "materials": [
        {
          "name": "Guía de pasos básicos",
          "url": "/api/v1/virtual/materials/pdf123",
          "type": "PDF"
        }
      ]
    },
    "navigation": {
      "previousLesson": null,
      "nextLesson": {
        "lessonId": "3da66c0c-7fbf-45f1-a830-b19f5bfd0002",
        "title": "Postura básica",
        "isFree": false
      }
    },
    "subscription": {
      "message": "¡Suscríbete para acceder a todas las lecciones!",
      "plansUrl": "/api/v1/billing/plans"
    }
  }
  ```

* **Response Body (lección premium - sin acceso):**

  ```json
  {
    "lesson": {
      "lessonId": "3da66c0c-7fbf-45f1-a830-b19f5bfd0002",
      "title": "Postura básica",
      "description": "Descripción breve...",
      "duration": "15:00",
      "isFree": false,
      "thumbnailUrl": "https://cdn.bunny.net/thumbs/def456.jpg"
    },
    "access": {
      "allowed": false,
      "reason": "SUBSCRIPTION_REQUIRED",
      "message": "Esta lección requiere una suscripción activa",
      "plansUrl": "/api/v1/billing/plans"
    }
  }
  ```

* **Tablas de BD (Schemas):**
  * `virtual_lessons` - Lecciones
  * `virtual_videos` - Referencias a Bunny.net
  * `virtual_lesson_materials` - Materiales descargables
* **Integraciones:**
  * Bunny.net CDN para streaming de video.
  * Generación de signed URLs.

---

## 5. Definition of Done (Criterios de Finalización)

* [ ] La lógica implementa todos los Criterios de Aceptación (5 escenarios).
* [ ] Se han escrito **Pruebas Unitarias** para lógica de acceso gratuito/premium.
* [ ] Se han escrito **Pruebas de Integración** para el endpoint REST.
* [ ] El endpoint está documentado en el contrato **OpenAPI/Swagger**.
* [ ] El código pasa la validación de Checkstyle y ArchUnit.
* [ ] No introduce nuevos "Issues" severos en SonarCloud.
* [ ] Las URLs de video son firmadas y temporales.
* [ ] Se verifica que lecciones premium no exponen URLs de video sin suscripción.
