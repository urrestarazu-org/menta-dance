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
* **Cuando (When):** Envía `GET /api/v1/virtual/lessons/{hashId}` donde `isFree = true`.
* **Entonces (Then):** El sistema debe devolver el detalle completo de la lección.
* **Y (And):** Debe incluir URL del video de Bunny.net.
* **Y (And):** Debe incluir descripción, duración, y materiales adicionales.
* **Y (And):** Debe devolver un código HTTP `200 OK`.

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

* **Dado que (Given):** El usuario solicita una lección con hashId inválido.
* **Cuando (When):** El hashId no existe o no es decodificable.
* **Entonces (Then):** El sistema debe devolver un código HTTP `404 Not Found`.
* **Y (And):** El mensaje debe indicar "Lección no encontrada".

---

## 3. Requisitos No Funcionales y Restricciones

* **Seguridad / Autorización:**
  * Lecciones gratuitas son públicas.
  * URLs de video de Bunny.net deben ser signed/temporales.
  * No cachear URLs de video en respuestas.
* **Rendimiento / Rate Limiting:**
  * Máximo 30 requests por minuto por IP para video streams.
  * Respuesta de metadata debe ser < 150ms.
* **CDN (Bunny.net):**
  * URLs firmadas con expiración de 4 horas.
  * Token-based authentication para videos.

---

## 4. Notas Técnicas (Arquitectura)

* **Endpoints Involucrados:**
  * `GET /api/v1/virtual/lessons/{hashId}` - Detalle de lección
* **Response Body (lección gratuita - acceso permitido):**

  ```json
  {
    "lesson": {
      "hashId": "les456",
      "title": "Historia del Tango",
      "description": "En esta lección aprenderás sobre los orígenes...",
      "duration": "10:30",
      "isFree": true,
      "order": 1,
      "module": {
        "hashId": "mod123",
        "title": "Introducción al Tango"
      },
      "course": {
        "hashId": "xvb5D1e0",
        "title": "Tango Básico"
      },
      "video": {
        "url": "https://cdn.bunny.net/video/abc123?token=xyz&expires=1705312800",
        "thumbnailUrl": "https://cdn.bunny.net/thumbs/abc123.jpg",
        "quality": ["1080p", "720p", "480p"]
      },
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
        "hashId": "les789",
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
      "hashId": "les789",
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
  * `menta_virtual.lessons` - Lecciones
  * `menta_virtual.videos` - Referencias a Bunny.net
  * `menta_virtual.lesson_materials` - Materiales descargables
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
