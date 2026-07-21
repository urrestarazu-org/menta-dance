# US-VIRTUAL-005: Guardar progreso de lección

**ID:** US-VIRTUAL-005
**Título:** Registro automático de progreso de visualización
**Módulo / API:** Virtual API
**Prioridad (MoSCoW):** Should Have
**Estado:** Draft
**Épica:** EP-02 Catálogo Virtual

---

## 1. Historia de Usuario

> **Como** usuario que está viendo un curso
> **Quiero** que mi progreso se guarde automáticamente
> **Para** continuar donde lo dejé sin perder mi avance.

---

## 2. Criterios de Aceptación (BDD)

**Escenario 1: Guardar progreso durante reproducción**

* **Dado que (Given):** Un usuario está reproduciendo una lección.
* **Cuando (When):** El player envía `POST /api/v1/virtual/progress` cada 30 segundos.
* **Entonces (Then):** El sistema debe actualizar el progreso del usuario.
* **Y (And):** Debe registrar segundos vistos y timestamp.
* **Y (And):** Debe devolver un código HTTP `200 OK`.

**Escenario 2: Marcar lección como completada**

* **Dado que (Given):** Un usuario ve más del 90% de una lección.
* **Cuando (When):** El progreso supera el umbral de completitud.
* **Entonces (Then):** El sistema debe marcar la lección como `completed = true`.
* **Y (And):** Debe recalcular el progreso del módulo y curso.

**Escenario 3: Continuar donde lo dejó**

* **Dado que (Given):** Un usuario vuelve a una lección que estaba viendo.
* **Cuando (When):** Accede al detalle de la lección.
* **Entonces (Then):** El sistema debe devolver `resumeAtSeconds` con el punto donde paró.
* **Y (And):** El player debe poder iniciar desde ese punto.

**Escenario 4: Progreso de curso completo**

* **Dado que (Given):** Un usuario completó todas las lecciones de un curso.
* **Cuando (When):** Consulta su progreso en el curso.
* **Entonces (Then):** El sistema debe mostrar 100% de progreso.
* **Y (And):** Debe indicar que el curso está completado.
* **Y (And):** Debe registrar la fecha de finalización.

**Escenario 5: Reiniciar progreso de lección**

* **Dado que (Given):** Un usuario quiere volver a ver una lección desde el inicio.
* **Cuando (When):** Envía `DELETE /api/v1/virtual/progress/{lessonHashId}`.
* **Entonces (Then):** El sistema debe reiniciar el progreso de esa lección a 0.
* **Y (And):** Debe recalcular el progreso del módulo y curso.
* **Y (And):** Debe devolver un código HTTP `200 OK`.

**Escenario 6: Usuario no autenticado**

* **Dado que (Given):** Un visitante ve una lección gratuita.
* **Cuando (When):** El sistema intenta guardar progreso.
* **Entonces (Then):** El progreso NO debe guardarse (sin usuario asociado).
* **Y (And):** No debe generar error (silenciosamente ignorar).

---

## 3. Requisitos No Funcionales y Restricciones

* **Seguridad / Autorización:**
  * Requiere access token válido.
  * El usuario solo puede modificar su propio progreso.
* **Rendimiento / Rate Limiting:**
  * Máximo 5 updates de progreso por minuto por lección.
  * Operación debe ser < 50ms (no bloquear reproducción).
* **Consistencia:**
  * Progreso debe ser idempotente (múltiples updates con mismo valor no generan error).
  * El porcentaje de módulo/curso se recalcula asincrónicamente.

---

## 4. Notas Técnicas (Arquitectura)

* **Endpoints Involucrados:**
  * `POST /api/v1/virtual/progress` - Actualizar progreso
  * `GET /api/v1/virtual/progress/me` - Obtener progreso general
  * `GET /api/v1/virtual/progress/course/{hashId}` - Progreso de un curso
  * `DELETE /api/v1/virtual/progress/{lessonHashId}` - Reiniciar progreso de lección
* **Request Body (actualizar progreso):**

  ```json
  {
    "lessonHashId": "les789",
    "watchedSeconds": 450,
    "totalSeconds": 900
  }
  ```

* **Response Body (progreso de curso):**

  ```json
  {
    "course": {
      "hashId": "xvb5D1e0",
      "title": "Tango Básico",
      "progress": {
        "percentComplete": 45,
        "completedLessons": 9,
        "totalLessons": 20,
        "completedModules": 2,
        "totalModules": 5,
        "startedAt": "2024-01-10T08:00:00Z",
        "lastActivityAt": "2024-01-15T10:30:00Z"
      }
    },
    "modules": [
      {
        "hashId": "mod123",
        "title": "Introducción al Tango",
        "percentComplete": 100,
        "lessons": [
          {
            "hashId": "les456",
            "title": "Historia del Tango",
            "completed": true,
            "watchedSeconds": 630,
            "totalSeconds": 630
          }
        ]
      }
    ],
    "currentLesson": {
      "hashId": "les999",
      "title": "Giros avanzados",
      "resumeAtSeconds": 180
    }
  }
  ```

* **Tablas de BD (Schemas):**
  * `menta_virtual.user_progress` - Progreso por lección
  * `menta_virtual.user_course_progress` - Progreso agregado por curso (cache)
  * `menta_virtual.lessons` - Para calcular totales
* **Background Jobs:**
  * Recalcular progreso de módulo/curso asincrónicamente.

---

## 5. Definition of Done (Criterios de Finalización)

* [ ] La lógica implementa todos los Criterios de Aceptación (6 escenarios).
* [ ] Se han escrito **Pruebas Unitarias** para cálculo de progreso.
* [ ] Se han escrito **Pruebas de Integración** para los endpoints REST.
* [ ] El endpoint está documentado en el contrato **OpenAPI/Swagger**.
* [ ] El código pasa la validación de Checkstyle y ArchUnit.
* [ ] No introduce nuevos "Issues" severos en SonarCloud.
* [ ] El progreso se guarda eficientemente sin afectar reproducción.
* [ ] La función "continuar donde lo dejé" funciona correctamente.
* [ ] El umbral del 90% para marcar completitud es configurable.
