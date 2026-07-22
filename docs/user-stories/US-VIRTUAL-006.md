# US-VIRTUAL-006: Gestión de cursos virtuales

**ID:** US-VIRTUAL-006
**Título:** Administración de cursos, módulos y lecciones
**Módulo / API:** Virtual API
**Prioridad (MoSCoW):** Must Have
**Estado:** Draft
**Épica:** EP-05 Administración

---

## 1. Historia de Usuario

> **Como** administrador o profesor propietario de un curso
> **Quiero** crear, editar y publicar cursos con sus módulos y lecciones
> **Para** mantener el catálogo de contenido actualizado y disponible para los usuarios.

---

## 2. Criterios de Aceptación (BDD)

**Escenario 1: Crear curso en borrador**

* **Dado que (Given):** Un administrador o profesor autenticado accede al panel de gestión de cursos.
* **Cuando (When):** Envía `POST /api/v1/admin/virtual/courses` con datos del curso.
* **Entonces (Then):** El sistema debe crear el curso con `status = DRAFT`.
* **Y (And):** Si quien crea es `PROFESOR`, debe quedar registrado como propietario; `ADMIN` puede asignar el profesor propietario.
* **Y (And):** Debe generar un `courseId` UUID globalmente único.
* **Y (And):** Debe devolver un código HTTP `201 Created`.

**Escenario 2: Agregar módulo a curso**

* **Dado que (Given):** Un curso existe en el sistema.
* **Cuando (When):** El administrador o profesor propietario envía `POST /api/v1/admin/virtual/courses/{courseId}/modules`.
* **Entonces (Then):** El sistema debe crear el módulo asociado al curso.
* **Y (And):** Debe asignar el orden según posición indicada o al final.

**Escenario 3: Agregar lección a módulo**

* **Dado que (Given):** Un módulo existe en el sistema.
* **Cuando (When):** El administrador o profesor propietario envía `POST /api/v1/admin/virtual/modules/{moduleId}/lessons`.
* **Entonces (Then):** El sistema debe crear la lección asociada al módulo.
* **Y (And):** Debe permitir asociar video de Bunny.net.
* **Y (And):** Debe permitir marcar como gratuita o premium.

**Escenario 4: Publicar curso**

* **Dado que (Given):** Un curso tiene al menos 1 módulo con 1 lección completa.
* **Cuando (When):** El administrador o profesor propietario envía `PUT /api/v1/admin/virtual/courses/{courseId}/publish`.
* **Entonces (Then):** El sistema debe cambiar `status = PUBLISHED`.
* **Y (And):** El curso debe aparecer en el catálogo público.
* **Y (And):** Debe invalidar caché del catálogo.

**Escenario 5: Despublicar curso**

* **Dado que (Given):** Un curso está publicado.
* **Cuando (When):** El administrador o profesor propietario envía `PUT /api/v1/admin/virtual/courses/{courseId}/unpublish`.
* **Entonces (Then):** El sistema debe cambiar `status = DRAFT`.
* **Y (And):** El curso debe desaparecer del catálogo público.
* **Y (And):** Los usuarios con suscripción activa deben mantener acceso temporal.

**Escenario 6: Publicar curso incompleto**

* **Dado que (Given):** Un curso no tiene módulos o lecciones.
* **Cuando (When):** El administrador o profesor propietario intenta publicarlo.
* **Entonces (Then):** El sistema debe devolver un código HTTP `400 Bad Request`.
* **Y (And):** Debe indicar qué falta para poder publicar.

**Escenario 7: Reordenar módulos o lecciones**

* **Dado que (Given):** Un curso tiene múltiples módulos.
* **Cuando (When):** El administrador o profesor propietario envía `PUT /api/v1/admin/virtual/courses/{courseId}/modules/reorder`.
* **Entonces (Then):** El sistema debe actualizar el orden de los módulos.
* **Y (And):** El nuevo orden debe reflejarse en el catálogo.

---

## 3. Requisitos No Funcionales y Restricciones

* **Seguridad / Autorización:**
  * `ADMIN` puede gestionar cualquier curso; `PROFESOR` sólo puede gestionar los cursos de los que es propietario.
  * La autorización de ownership se aplica a cada endpoint que recibe `courseId`, `moduleId` o `lessonId`.
  * Auditar todas las operaciones de creación/modificación/publicación, incluido actor, curso, acción y valores anterior/nuevo cuando corresponda.
* **Rendimiento / Rate Limiting:**
  * Sin límite específico para admins.
  * Operaciones de publicación deben invalidar caché.
* **Validación:**
  * Título obligatorio (3-100 caracteres).
  * Descripción obligatoria (10-5000 caracteres).
  * Imagen de thumbnail obligatoria para publicar.
* **Integridad:**
  * No permitir eliminar cursos con suscriptores activos.
  * No permitir eliminar módulos/lecciones de cursos publicados (solo despublicar primero).

---

## 4. Notas Técnicas (Arquitectura)

* **Endpoints Involucrados:**
  * `POST /api/v1/admin/virtual/courses` - Crear curso
  * `PUT /api/v1/admin/virtual/courses/{courseId}` - Editar curso
  * `DELETE /api/v1/admin/virtual/courses/{courseId}` - Eliminar curso (solo draft)
  * `PUT /api/v1/admin/virtual/courses/{courseId}/publish` - Publicar
  * `PUT /api/v1/admin/virtual/courses/{courseId}/unpublish` - Despublicar
  * `POST /api/v1/admin/virtual/courses/{courseId}/modules` - Agregar módulo
  * `PUT /api/v1/admin/virtual/modules/{moduleId}` - Editar módulo
  * `POST /api/v1/admin/virtual/modules/{moduleId}/lessons` - Agregar lección
  * `PUT /api/v1/admin/virtual/lessons/{lessonId}` - Editar lección
  * `PUT /api/v1/admin/virtual/courses/{courseId}/modules/reorder` - Reordenar
* **Request Body (crear curso):**

  ```json
  {
    "title": "Tango Avanzado",
    "description": "Curso completo de técnicas avanzadas...",
    "shortDescription": "Domina el tango argentino",
    "category": "tango",
    "level": "advanced",
    "thumbnailUrl": "https://cdn.bunny.net/images/tango-avanzado.jpg",
    "instructorId": 1
  }
  ```

* **Request Body (crear lección):**

  ```json
  {
    "title": "Sacadas y barridas",
    "description": "Técnicas avanzadas de...",
    "videoId": "bunny-video-id-123",
    "duration": "18:45",
    "isFree": false,
    "order": 3,
    "materials": [
      {"name": "Guía PDF", "materialId": "mat-uuid"}
    ]
  }
  ```

* **Response Body (crear curso):**

  ```json
  {
    "course": {
      "courseId": "b6bb98d6-179e-49d0-9dda-6c03a16998f0",
      "title": "Tango Avanzado",
      "status": "DRAFT",
      "createdAt": "2024-01-15T12:00:00Z",
      "createdBy": "admin@mentavirtual.com"
    }
  }
  ```

* **Tablas de BD (Schemas):**
  * `virtual_courses` - Cursos
  * `virtual_modules` - Módulos
  * `virtual_lessons` - Lecciones
  * `virtual_videos` - Referencias a Bunny.net
  * `virtual_course_audit` - Auditoría append-only de cambios de gestión

---

## 5. Definition of Done (Criterios de Finalización)

* [ ] La lógica implementa todos los Criterios de Aceptación (7 escenarios).
* [ ] Se han escrito **Pruebas Unitarias** para validaciones de publicación.
* [ ] Se han escrito **Pruebas de Integración** para todos los endpoints REST.
* [ ] El endpoint está documentado en el contrato **OpenAPI/Swagger**.
* [ ] El código pasa la validación de Checkstyle y ArchUnit.
* [ ] No introduce nuevos "Issues" severos en SonarCloud.
* [ ] La auditoría registra todas las operaciones de admin.
* [ ] La invalidación de caché funciona correctamente al publicar/despublicar.
* [ ] El panel admin permite gestión completa del contenido.
