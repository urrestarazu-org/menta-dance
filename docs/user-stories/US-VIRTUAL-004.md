# US-VIRTUAL-004: Reproducir video con suscripción

**ID:** US-VIRTUAL-004
**Título:** Reproducción de videos premium con suscripción activa
**Módulo / API:** Virtual API
**Prioridad (MoSCoW):** Must Have
**Estado:** Draft
**Épica:** EP-02 Catálogo Virtual

---

## 1. Historia de Usuario

> **Como** usuario con suscripción activa
> **Quiero** reproducir cualquier video del catálogo sin restricciones
> **Para** aprender el contenido completo de los cursos que incluye mi plan.

---

## 2. Criterios de Aceptación (BDD)

**Escenario 1: Reproducción autorizada**

* **Dado que (Given):** Un usuario con suscripción `ACTIVE` que incluye el curso.
* **Cuando (When):** Envía `GET /api/v1/virtual/lessons/{hashId}` autenticado.
* **Entonces (Then):** El sistema debe devolver el detalle completo de la lección.
* **Y (And):** Debe incluir URL firmada del video de Bunny.net.
* **Y (And):** Debe incluir múltiples calidades disponibles.
* **Y (And):** Debe devolver un código HTTP `200 OK`.

**Escenario 2: Suscripción expirada**

* **Dado que (Given):** Un usuario cuya suscripción tiene `status = EXPIRED`.
* **Cuando (When):** Intenta acceder a una lección premium.
* **Entonces (Then):** El sistema debe devolver un código HTTP `403 Forbidden`.
* **Y (And):** Debe indicar que la suscripción expiró.
* **Y (And):** Debe incluir link para renovar.

**Escenario 3: Plan no incluye el curso**

* **Dado que (Given):** Un usuario con suscripción activa a un plan específico.
* **Cuando (When):** Intenta acceder a un curso que NO está incluido en su plan.
* **Entonces (Then):** El sistema debe devolver un código HTTP `403 Forbidden`.
* **Y (And):** Debe indicar qué plan incluye ese curso.
* **Y (And):** Debe ofrecer opción de upgrade.

**Escenario 4: Obtener URL de streaming**

* **Dado que (Given):** Un usuario con acceso autorizado al video.
* **Cuando (When):** Envía `GET /api/v1/virtual/lessons/{hashId}/stream`.
* **Entonces (Then):** El sistema debe devolver una URL de streaming válida.
* **Y (And):** La URL debe expirar en 4 horas.
* **Y (And):** Debe soportar adaptive bitrate streaming.

**Escenario 5: Controles de reproducción**

* **Dado que (Given):** Un usuario está reproduciendo un video.
* **Cuando (When):** Pausa, adelanta o retrocede el video.
* **Entonces (Then):** El reproductor debe responder correctamente.
* **Y (And):** El progreso debe registrarse para continuar después.

**Escenario 6: Suscripción pendiente de pago**

* **Dado que (Given):** Un usuario con suscripción `PENDING`.
* **Cuando (When):** Intenta acceder a una lección premium.
* **Entonces (Then):** El sistema debe devolver un código HTTP `403 Forbidden`.
* **Y (And):** Debe indicar que el pago está pendiente de verificación.

---

## 3. Requisitos No Funcionales y Restricciones

* **Seguridad / Autorización:**
  * Requiere access token válido.
  * Verificar que el plan del usuario incluye el curso.
  * URLs de video firmadas y con expiración.
  * No permitir download directo (streaming only).
* **Rendimiento / Rate Limiting:**
  * Máximo 10 solicitudes de URL de stream por minuto por usuario.
  * Respuesta debe ser < 100ms.
* **CDN (Bunny.net):**
  * URLs firmadas con HMAC.
  * Expiración de 4 horas.
  * Adaptive bitrate streaming (HLS).
* **Calidad de Video:**
  * Soportar: 1080p, 720p, 480p, 360p.
  * Selección automática basada en bandwidth.

---

## 4. Notas Técnicas (Arquitectura)

* **Endpoints Involucrados:**
  * `GET /api/v1/virtual/lessons/{hashId}` - Detalle con acceso autorizado
  * `GET /api/v1/virtual/lessons/{hashId}/stream` - Obtener URL de streaming
* **Response Body (streaming):**

  ```json
  {
    "stream": {
      "url": "https://cdn.bunny.net/stream/abc123/playlist.m3u8?token=xyz&expires=1705312800",
      "type": "HLS",
      "qualities": [
        {"label": "1080p", "bitrate": 5000000},
        {"label": "720p", "bitrate": 2500000},
        {"label": "480p", "bitrate": 1000000},
        {"label": "360p", "bitrate": 500000}
      ],
      "expiresAt": "2024-01-15T18:00:00Z"
    },
    "lesson": {
      "hashId": "les789",
      "title": "Postura básica",
      "duration": "15:00"
    },
    "progress": {
      "watchedSeconds": 180,
      "totalSeconds": 900,
      "percentWatched": 20
    }
  }
  ```

* **Headers requeridos:**

  ```
  Authorization: Bearer <access-token>
  ```

* **Tablas de BD (Schemas):**
  * `menta_virtual.lessons` - Lecciones
  * `menta_virtual.videos` - Referencias a Bunny.net
  * `menta_billing.subscriptions` - Verificar acceso
  * `menta_billing.plan_courses` - Verificar que plan incluye curso
  * `menta_virtual.user_progress` - Progreso del usuario
* **Integraciones:**
  * Bunny.net Stream API.
  * Generación de tokens HMAC.

---

## 5. Definition of Done (Criterios de Finalización)

* [ ] La lógica implementa todos los Criterios de Aceptación (6 escenarios).
* [ ] Se han escrito **Pruebas Unitarias** para verificación de acceso.
* [ ] Se han escrito **Pruebas de Integración** para los endpoints REST.
* [ ] El endpoint está documentado en el contrato **OpenAPI/Swagger**.
* [ ] El código pasa la validación de Checkstyle y ArchUnit.
* [ ] No introduce nuevos "Issues" severos en SonarCloud.
* [ ] Las URLs de streaming son firmadas y expiran correctamente.
* [ ] Se verifica que usuarios sin acceso no pueden obtener URLs de video.
* [ ] El adaptive bitrate streaming funciona correctamente.
