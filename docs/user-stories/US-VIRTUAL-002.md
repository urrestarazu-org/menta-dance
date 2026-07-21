# US-VIRTUAL-002: Ver detalle de curso

**ID:** US-VIRTUAL-002
**Título:** Consulta del detalle completo de un curso
**Módulo / API:** Virtual API
**Prioridad (MoSCoW):** Must Have
**Estado:** Draft
**Épica:** EP-02 Catálogo Virtual

---

## 1. Historia de Usuario

> **Como** visitante o usuario autenticado
> **Quiero** ver el detalle completo de un curso específico
> **Para** conocer su contenido, estructura y decidir si quiero acceder a él.

---

## 2. Criterios de Aceptación (BDD)

**Escenario 1: Ver detalle de curso (visitante)**

* **Dado que (Given):** Un visitante navega a un curso específico.
* **Cuando (When):** Envía `GET /api/v1/virtual/courses/{hashId}`.
* **Entonces (Then):** El sistema debe devolver el detalle completo del curso.
* **Y (And):** Debe incluir: título, descripción completa, módulos con títulos, lecciones (solo títulos).
* **Y (And):** Debe indicar cuáles lecciones son gratuitas.
* **Y (And):** No debe incluir URLs de video para lecciones premium.
* **Y (And):** Debe devolver un código HTTP `200 OK`.

**Escenario 2: Ver detalle de curso (usuario con suscripción)**

* **Dado que (Given):** Un usuario con suscripción activa accede al curso.
* **Cuando (When):** Envía `GET /api/v1/virtual/courses/{hashId}` autenticado.
* **Entonces (Then):** El sistema debe devolver el detalle con acceso completo.
* **Y (And):** Debe incluir indicador de progreso del usuario en cada lección.
* **Y (And):** Debe indicar última lección vista.

**Escenario 3: Curso no encontrado**

* **Dado que (Given):** El usuario solicita un curso con hashId inválido.
* **Cuando (When):** El hashId no existe o no es decodificable.
* **Entonces (Then):** El sistema debe devolver un código HTTP `404 Not Found`.
* **Y (And):** El mensaje debe indicar "Curso no encontrado".

**Escenario 4: Curso no publicado (visitante)**

* **Dado que (Given):** Un visitante intenta acceder a un curso con `status != PUBLISHED`.
* **Cuando (When):** Envía `GET /api/v1/virtual/courses/{hashId}`.
* **Entonces (Then):** El sistema debe devolver un código HTTP `404 Not Found`.
* **Y (And):** No debe revelar que el curso existe pero no está publicado.

**Escenario 5: Curso no publicado (admin)**

* **Dado que (Given):** Un administrador accede a un curso en borrador.
* **Cuando (When):** Envía `GET /api/v1/virtual/courses/{hashId}` autenticado como admin.
* **Entonces (Then):** El sistema debe devolver el detalle del curso.
* **Y (And):** Debe indicar el estado actual del curso.

---

## 3. Requisitos No Funcionales y Restricciones

* **Seguridad / Autorización:**
  * Endpoint público para cursos publicados.
  * Admins pueden ver cursos en cualquier estado.
  * No exponer URLs de video a usuarios sin acceso.
* **Rendimiento / Rate Limiting:**
  * Máximo 60 requests por minuto por IP.
  * Respuesta debe ser < 200ms.
* **Caché:**
  * Cacheable para visitantes (5 minutos).
  * No cacheable para usuarios autenticados (progreso personalizado).
* **SEO:**
  * Incluir metadatos para rich snippets (schema.org Course).

---

## 4. Notas Técnicas (Arquitectura)

* **Endpoints Involucrados:**
  * `GET /api/v1/virtual/courses/{hashId}` - Detalle de curso
* **Response Body (visitante):**

  ```json
  {
    "course": {
      "hashId": "xvb5D1e0",
      "title": "Tango Básico",
      "description": "Descripción completa del curso...",
      "thumbnailUrl": "https://cdn.bunny.net/courses/tango-basico.jpg",
      "category": "tango",
      "level": "beginner",
      "isPremium": true,
      "instructor": {
        "name": "María García",
        "bio": "20 años de experiencia..."
      },
      "modules": [
        {
          "hashId": "mod123",
          "title": "Introducción al Tango",
          "order": 1,
          "lessons": [
            {
              "hashId": "les456",
              "title": "Historia del Tango",
              "duration": "10:30",
              "isFree": true,
              "order": 1
            },
            {
              "hashId": "les789",
              "title": "Postura básica",
              "duration": "15:00",
              "isFree": false,
              "order": 2
            }
          ]
        }
      ],
      "stats": {
        "moduleCount": 5,
        "lessonCount": 20,
        "totalDuration": "2h 30m"
      }
    }
  }
  ```

* **Response Body adicional (usuario autenticado):**

  ```json
  {
    "userProgress": {
      "completedLessons": 5,
      "totalLessons": 20,
      "percentComplete": 25,
      "lastWatched": {
        "lessonHashId": "les789",
        "lessonTitle": "Postura básica",
        "watchedAt": "2024-01-15T10:30:00Z"
      }
    }
  }
  ```

* **Tablas de BD (Schemas):**
  * `menta_virtual.courses` - Cursos
  * `menta_virtual.modules` - Módulos
  * `menta_virtual.lessons` - Lecciones
  * `menta_virtual.user_progress` - Progreso del usuario

---

## 5. Definition of Done (Criterios de Finalización)

* [ ] La lógica implementa todos los Criterios de Aceptación (5 escenarios).
* [ ] Se han escrito **Pruebas Unitarias** para lógica de acceso y progreso.
* [ ] Se han escrito **Pruebas de Integración** para el endpoint REST.
* [ ] El endpoint está documentado en el contrato **OpenAPI/Swagger**.
* [ ] El código pasa la validación de Checkstyle y ArchUnit.
* [ ] No introduce nuevos "Issues" severos en SonarCloud.
* [ ] Se verifica que no se exponen URLs de video a usuarios sin acceso.
* [ ] Los metadatos SEO están incluidos.
