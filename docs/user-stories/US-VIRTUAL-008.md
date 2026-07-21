# US-VIRTUAL-008: Continuar última lección visitada desde el Dashboard

**ID:** US-VIRTUAL-008
**Título:** Continuar última lección visitada desde el Dashboard
**Módulo / API:** BFF Web / Virtual API
**Prioridad (MoSCoW):** Should Have
**Estado:** Draft
**Épica:** EP-02 Catálogo Virtual

---

## 1. Historia de Usuario

> **Como** alumno activo de la academia virtual
> **Quiero** ver un acceso directo rápido a la última lección que estuve reproduciendo al ingresar al Dashboard
> **Para** retomar mis clases de manera inmediata desde donde las dejé, sin necesidad de navegar por todo el catálogo de cursos.

---

## 2. Análisis de Opciones de Almacenamiento

Evaluamos tres alternativas técnicas para registrar cuál fue la última lección visitada por el Alumno:

| Opción | Mecanismo | Pros | Contras | Decisión |
|---|---|---|---|---|
| **A. Cookies** | Cliente | • Cero consumo de base de datos o memoria en el servidor.<br/>• Lectura y escritura ultra rápida en navegador. | • Se pierde si el usuario cambia de dispositivo (ej: de PC a móvil).<br/>• Se borra si el usuario limpia historial/cookies.<br/>• No permite analíticas de negocio en backend. | **Descartado** por limitaciones multidispositivo. |
| **B. Sesión HTTP** | Servidor (Redis) | • Escritura/lectura rápida en caché de sesión de Redis (ADR-0007).<br/>• No sobrecarga MySQL. | • La sesión expira tras cierto tiempo de inactividad (ej: 7 días).<br/>• Si el usuario pasa 2 semanas sin entrar, el botón desaparece. | **Descartado** por expiración temporal de datos. |
| **C. Base de Datos (MySQL / Redis)** | Tabla `lesson_progress` | • **Persistente para siempre:** No expira.<br/>• **Multidispositivo:** Funciona si el usuario cambia de PC a móvil.<br/>• **Reutilización:** Ya contamos con la tabla `lesson_progress` en `virtual_schema` para guardar el avance del video.<br/>• Permite analíticas de abandono. | • Requiere una consulta SQL extra (`ORDER BY updatedAt DESC LIMIT 1`) al renderizar el Dashboard. | **Seleccionado:** Es el enfoque estándar y más robusto, mitigando la consulta SQL mediante caché de lectura corta en Redis. |

**Decisión adoptada:** Utilizar la tabla `lesson_progress` de la **Virtual API** como fuente de verdad. El endpoint del Dashboard consultará la lección con la actualización más reciente (`updatedAt`) que no esté completada al 100% (o la última interactuada).

---

## 3. Criterios de Aceptación (BDD)

**Escenario 1: Mostrar el botón "Continuar última lección" en el Dashboard**

* **Dado que (Given):** Un alumno autenticado tiene registros de progreso en al menos una lección.
* **Cuando (When):** Accede a la página principal o Dashboard (`GET /` o `GET /home`).
* **Entonces (Then):** El BFF debe solicitar a la Virtual API la última lección del usuario: `GET /api/v1/virtual/users/{userId}/latest-progress`.
* **Y (And):** Debe renderizar en la UI un botón flotante o sección destacada con la etiqueta: *"Continuar: [Título de la Lección]"*.
* **Y (And):** Al hacer clic en el botón, el alumno debe ser redirigido directamente a la vista de la lección (`GET /lessons/{hashId}`).

**Escenario 2: Ocultar el botón si el alumno es nuevo**

* **Dado que (Given):** Un alumno autenticado no ha ingresado a ninguna lección (no hay registros en la tabla `lesson_progress`).
* **Cuando (When):** Carga el Dashboard.
* **Entonces (Then):** El sistema no debe mostrar el botón flotante de continuar última lección.

**Escenario 3: Actualización automática del último progreso**

* **Dado que (Given):** Un alumno está visualizando el video de una lección.
* **Cuando (When):** El reproductor de video (Alpine.js / CDN Player) envía eventos periódicos de progreso de reproducción a la API.
* **Entonces (Then):** La Virtual API debe actualizar el registro correspondiente en la tabla `lesson_progress` guardando `progress_percent`, `last_position_seconds` y estableciendo `updated_at = NOW`.

---

## 4. Requisitos No Funcionales y Restricciones

* **Rendimiento / Caché:**
  * La consulta `GET /api/v1/virtual/users/{userId}/latest-progress` se ejecutará en cada carga del Dashboard. Debe optimizarse en el backend con una clave de caché de corta duración en Redis (ej: 5 minutos, con invalidación en eventos de escritura de progreso).
* **Seguridad:**
  * El endpoint de consulta de progreso debe validar que el `userId` en el token JWT coincida con el consultado o que el usuario posea rol de administrador.

---

## 5. Notas Técnicas (Arquitectura)

* **Endpoints Involucrados:**
  * `GET /api/v1/virtual/users/{userId}/latest-progress` - Obtener lección más reciente
  * `POST /api/v1/virtual/lessons/{hashId}/progress` - Guardar progreso del video
* **Query SQL para obtener la última lección:**

  ```sql
  SELECT lp.* 
  FROM virtual_schema.lesson_progress lp
  WHERE lp.user_id = :userId
  ORDER BY lp.updated_at DESC
  LIMIT 1;
  ```

* **Response Body (`GET /latest-progress`):**

  ```json
  {
    "lessonHashId": "les456",
    "lessonTitle": "Postura básica en Bachata",
    "courseHashId": "crs111",
    "progressPercent": 45,
    "lastPositionSeconds": 180,
    "updatedAt": "2026-05-23T15:00:00Z"
  }
  ```

---

## 6. Definition of Done (Criterios de Finalización)

* [ ] El backend de la Virtual API expone el endpoint `/latest-progress` y actualiza `updated_at` en el guardado de progreso.
* [ ] El frontend renderiza dinámicamente el botón flotante si `lastLessonId` no es nulo, respetando el diseño adaptativo.
* [ ] Se han escrito **Pruebas Unitarias** para la query de ordenamiento del último progreso en el repositorio.
* [ ] Se han verificado las pruebas de integración en el BFF Web consumiendo el endpoint.
