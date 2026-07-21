# ADR-0008: Estrategia de Versionado de APIs (URL Path vs HTTP Header)

**Estado:** Aceptado
**Fecha:** 2026-05-17
**Decisores:** Equipo de Arquitectura

## Vigencia actual

El versionado por URL continúa aceptado para la API pública única. Las ventajas históricas relacionadas con enrutar contenedores o servicios independientes no forman parte de la arquitectura vigente. NGINX puede actuar como reverse proxy perimetral, pero no como mecanismo de comunicación entre módulos. Ver [ADR-0020](./0020-modular-monolith.md).


## Contexto y Problema

Al construir las nuevas APIs (Virtual, Physical, Auth, Billing), es fundamental diseñar una estrategia de versionado desde el día cero. Cuando cambien los contratos (payloads) en el futuro, los clientes antiguos (especialmente la aplicación Android) no deben romperse de inmediato. Debemos elegir cómo indicará el cliente qué versión de la API desea consumir.

## Factores Clave (Decision Drivers)

* Facilidad de uso para desarrolladores (Developer Experience - DX).
* Visibilidad y facilidad para depurar (Debugging).
* Compatibilidad con ruteo estándar en API Gateways.

## Opciones Consideradas

### Opción 1: Versionado por Content Negotiation (HTTP Headers)

* **Descripción:** El cliente envía un header específico o modifica el `Accept` header. Ej: `Accept: application/vnd.menta.v1+json`. La URL se mantiene limpia: `/api/courses`.
* **Pros:**
  * Cumple estrictamente con los principios REST puros (la URL identifica el recurso, no la versión de la representación).
  * URLs más limpias y estables en el tiempo.
* **Contras:**
  * Difícil de probar rápidamente en un navegador sin herramientas como Postman/cURL.
  * El ruteo en el API Gateway es más complejo (hay que analizar headers en lugar de regex de rutas).

### Opción 2: Versionado en la URL (URL Path)

* **Descripción:** La versión se incluye explícitamente en el path. Ej: `/api/v1/courses`.
* **Pros:**
  * Altamente visible y fácil de entender de un vistazo.
  * Muy fácil de enrutar a diferentes servicios/contenedores en el API Gateway (Kong/NGINX) basándose en un simple prefijo de ruta.
  * Fácil de probar en cualquier navegador.
* **Contras:**
  * Viola purismos RESTful.
  * Cuando se sube la versión (ej. a v2), la URL cambia, lo que puede requerir actualizar referencias en múltiples clientes.

## Decisión

Elegimos **Opción 2: Versionado por URL path (`/api/v1/`)** porque **ofrece la mayor simplicidad pragmática, facilidad de depuración y enrutamiento transparente en nuestra infraestructura de API Gateway**.

## Justificación (Rationale)

Para un equipo pequeño, el pragmatismo supera a la pureza teórica. El versionado por URL es el estándar *de facto* más utilizado en la industria por su simplicidad. Permitirá que NGINX/Kong ruteen peticiones a contenedores heredados (ej. `v1`) o nuevos (`v2`) de forma trivial evaluando solo el string de la URL, reduciendo la carga de procesamiento del Gateway.

## Especificaciones Técnicas

### Formato de URLs

| Servicio | Base URL | Ejemplo |
|----------|----------|---------|
| Auth API | `/api/v1/auth/` | `POST /api/v1/auth/login` |
| Virtual API | `/api/v1/virtual/` | `GET /api/v1/virtual/courses` |
| Physical API | `/api/v1/physical/` | `GET /api/v1/physical/locations` |
| Billing API | `/api/v1/billing/` | `POST /api/v1/billing/subscriptions` |

### Política de Compatibilidad

Para minimizar la necesidad de crear nuevas versiones:

1. **Additive Changes (No rompen):**
   * Agregar nuevos campos opcionales a responses
   * Agregar nuevos endpoints
   * Agregar nuevos query parameters opcionales

2. **Breaking Changes (Requieren nueva versión):**
   * Eliminar campos de responses
   * Cambiar tipo de dato de un campo
   * Cambiar significado semántico de un campo
   * Eliminar endpoints

### Política de Deprecación

| Fase | Duración | Acción |
|------|----------|--------|
| Anuncio | Inmediato | Header `Deprecation: true` + `Sunset: {fecha}` |
| Coexistencia | 6 meses | v1 y v2 funcionan en paralelo |
| Migración forzada | 3 meses | v1 retorna warnings en logs |
| Eliminación | Después de 9 meses | v1 retorna 410 Gone |

### Headers de Deprecación

```http
HTTP/1.1 200 OK
Deprecation: true
Sunset: Sat, 17 Nov 2026 00:00:00 GMT
Link: </api/v2/courses>; rel="successor-version"
```

### Configuración en Spring Boot

```java
@RestController
@RequestMapping("/api/v1/virtual/courses")
public class CourseControllerV1 {
    // Implementación v1
}

@RestController
@RequestMapping("/api/v2/virtual/courses")
public class CourseControllerV2 {
    // Implementación v2 (cuando sea necesaria)
}
```

## Consecuencias

### Positivas

* Rápido onboarding de desarrolladores front/mobile que entienden el patrón inmediatamente.
* Reglas de enrutamiento y proxy reverso (NGINX) triviales.
* Fácil testing en navegador y herramientas.

### Negativas / Deuda Técnica

* Incremento en la longitud de las URLs.
* Potencial duplicación de código entre versiones si no se abstrae correctamente.

### Implicaciones de Costos

* Cero implicaciones directas. Reducción de tiempo invertido en configuración de infraestructura.

### Riesgos y Reversibilidad

* **Riesgo Principal:** Proliferación masiva de controladores con prefijos de versión si la API cambia con demasiada frecuencia.
* **Plan de Mitigación:** Se mantendrá compatibilidad hacia atrás en los contratos (agregando campos, no quitando) el mayor tiempo posible para evitar la creación de una versión `v2` salvo cambios drásticos (breaking changes). Política clara de deprecación.
* **Reversibilidad:** Difícil. Cambiar la estrategia de versionado más adelante requeriría que los clientes actualicen su código de red base (Retrofit/Axios).

## Referencias y Decisiones Relacionadas

* **Complementa a:** [ADR-0002](./0002-bff-vs-api-gateway.md) - BFF consume APIs versionadas
* **Alcance reemplazado por:** [ADR-0020](./0020-modular-monolith.md) - Una única API pública
* **API Versioning Best Practices:** <https://www.troyhunt.com/your-api-versioning-is-wrong-which-is/>
* **Sunset Header RFC:** <https://datatracker.ietf.org/doc/html/rfc8594>
