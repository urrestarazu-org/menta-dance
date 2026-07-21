# US-VIRTUAL-001: Ver catálogo de cursos

**ID:** US-VIRTUAL-001
**Título:** Consulta del catálogo de cursos virtuales
**Módulo / API:** Virtual API
**Prioridad (MoSCoW):** Must Have
**Estado:** Draft
**Épica:** EP-02 Catálogo Virtual

---

## 1. Historia de Usuario

> **Como** visitante o usuario autenticado
> **Quiero** ver el catálogo de cursos disponibles
> **Para** explorar las opciones de aprendizaje y decidir qué cursos tomar.

---

## 2. Criterios de Aceptación (BDD)

**Escenario 1: Listar cursos públicos**

* **Dado que (Given):** Un visitante accede a la página de cursos.
* **Cuando (When):** Envía `GET /api/v1/virtual/courses`.
* **Entonces (Then):** El sistema debe devolver todos los cursos con `status = PUBLISHED`.
* **Y (And):** Cada curso debe incluir: título, descripción corta, imagen, indicador de gratuito/premium.
* **Y (And):** No debe incluir módulos ni lecciones (solo resumen).
* **Y (And):** Debe devolver un código HTTP `200 OK`.

**Escenario 2: Filtrar cursos por categoría**

* **Dado que (Given):** El usuario quiere ver cursos de una categoría específica.
* **Cuando (When):** Envía `GET /api/v1/virtual/courses?category=tango`.
* **Entonces (Then):** El sistema debe devolver solo cursos de esa categoría.
* **Y (And):** Debe mantener el orden por relevancia o fecha.

**Escenario 3: Buscar cursos por texto**

* **Dado que (Given):** El usuario quiere buscar un curso específico.
* **Cuando (When):** Envía `GET /api/v1/virtual/courses?search=básico`.
* **Entonces (Then):** El sistema debe buscar en título y descripción.
* **Y (And):** Debe devolver coincidencias ordenadas por relevancia.

**Escenario 4: Catálogo vacío**

* **Dado que (Given):** No hay cursos publicados en el sistema.
* **Cuando (When):** El usuario consulta el catálogo.
* **Entonces (Then):** El sistema debe devolver una lista vacía.
* **Y (And):** Debe devolver un código HTTP `200 OK`.

**Escenario 5: Paginación de resultados**

* **Dado que (Given):** Hay más de 10 cursos publicados.
* **Cuando (When):** El usuario consulta sin especificar página.
* **Entonces (Then):** El sistema debe devolver máximo 10 cursos por defecto.
* **Y (And):** Debe incluir información de paginación (total, páginas, actual).
* **Y (And):** Debe permitir navegar con `?page=2`.

---

## 3. Requisitos No Funcionales y Restricciones

* **Seguridad / Autorización:**
  * Endpoint público (no requiere autenticación).
  * Solo cursos con `status = PUBLISHED` son visibles.
* **Rendimiento / Rate Limiting:**
  * Máximo 100 requests por minuto por IP.
  * Respuesta debe ser < 300ms para primera página.
* **Caché:**
  * Resultados cacheables por 5 minutos.
  * Invalidar caché cuando se publique/despublique un curso.
* **SEO:**
  * Los hashIds deben usarse en URLs públicas.

---

## 4. Notas Técnicas (Arquitectura)

* **Endpoints Involucrados:**
  * `GET /api/v1/virtual/courses` - Listar cursos
* **Query Parameters:**
  * `category` (string, opcional) - Filtrar por categoría
  * `search` (string, opcional) - Búsqueda de texto
  * `page` (int, default 1) - Número de página
  * `size` (int, default 10, max 50) - Tamaño de página
* **Response Body:**

  ```json
  {
    "courses": [
      {
        "hashId": "xvb5D1e0",
        "title": "Tango Básico",
        "shortDescription": "Aprende los pasos fundamentales del tango argentino",
        "thumbnailUrl": "https://cdn.bunny.net/courses/tango-basico.jpg",
        "category": "tango",
        "level": "beginner",
        "isPremium": true,
        "moduleCount": 5,
        "lessonCount": 20,
        "totalDuration": "2h 30m"
      }
    ],
    "pagination": {
      "page": 1,
      "size": 10,
      "total": 25,
      "totalPages": 3
    }
  }
  ```

* **Tablas de BD (Schemas):**
  * `menta_virtual.courses` - Cursos
  * `menta_virtual.modules` - Para contar módulos
  * `menta_virtual.lessons` - Para contar lecciones

---

## 5. Definition of Done (Criterios de Finalización)

* [ ] La lógica implementa todos los Criterios de Aceptación (5 escenarios).
* [ ] Se han escrito **Pruebas Unitarias** para filtros y paginación.
* [ ] Se han escrito **Pruebas de Integración** para el endpoint REST.
* [ ] El endpoint está documentado en el contrato **OpenAPI/Swagger**.
* [ ] El código pasa la validación de Checkstyle y ArchUnit.
* [ ] No introduce nuevos "Issues" severos en SonarCloud.
* [ ] El caché funciona correctamente.
* [ ] Los hashIds se generan consistentemente.
