# ADR-0037: Ruteo de courseId a su módulo dueño en la proyección de catálogo

**Fecha:** 2026-08-18
**Estado:** Aceptado

## Contexto

`docs/07-CATALOG-API.md` define `GET /api/v1/catalog/courses` y `GET /api/v1/catalog/courses/{courseId}` como la única lectura pública de cursos, compuesta por `api:app` vía puertos de Physical (#40) y Virtual (#46) — sin tabla compartida, FK ni JOIN entre módulos. `docs/22-DATA-MODEL.md` establece que el `courseId` es un UUID globalmente único entre modalidades, generado por cada módulo sin coordinación central (v4/v7), y que "`api:app` usa la modalidad de la proyección de catálogo para enrutar el ID al módulo dueño" — pero no especifica **cómo** `api:app` sabe la modalidad de un `courseId` dado sin consultar ambos módulos.

Esta decisión bloqueaba el inicio de #95 (composición de catálogo en `api:app`), que depende de #40 y #46 — ambos ya mergeados y con el formato de `courseId` (UUID opaco, sin codificación de modalidad) ya fijado en producción.

## Alternativas consideradas

| Alternativa | Ventajas | Desventajas |
|---|---|---|
| Consultar ambos puertos en paralelo | Sin componente nuevo, sin eventual consistency, cero cambios a #40/#46 ya mergeados | Duplica una consulta por cada request de detalle |
| Índice `courseId → modalidad` vía outbox | Evita la doble consulta | Componente nuevo (tabla + consumer de eventos), consistencia eventual, ¿qué pasa si el evento se pierde o llega tarde? |
| Codificar modalidad en el UUID (prefijo o bits reservados) | Sin lookup en absoluto | Acopla el formato del ID —ya usado por #40/#46 en producción— a una decisión de infraestructura; requeriría migrar datos ya escritos para adoptarlo ahora |

## Decisión

Adoptamos **consulta en paralelo a ambos puertos** para `GET /api/v1/catalog/courses/{courseId}`: `api:app` llama a `PhysicalCourseAvailabilityPort` y `VirtualCourseCatalogPort` simultáneamente y se queda con el que responde. Si ninguno responde, `404`. Si ambos responden (colisión de ID entre módulos, no debería ocurrir por diseño de generación de UUID pero no está garantizado por constraint alguno), es un bug a loggear, no un 500 — el sistema no debe romperse por un caso que sólo `podría` pasar por una colisión de probabilidad astronómicamente baja.

Para el listado (`GET /api/v1/catalog/courses`), no hay problema de ruteo: `api:app` pagina cada puerto por separado y mezcla los resultados client-side (orden por fecha/relevancia a resolver en el diseño técnico de #95, no en este ADR).

## Justificación

Ni Physical ni Virtual tienen hoy evidencia de volumen que justifique optimizar la consulta de detalle — es un catálogo de academia de danza, no un e-commerce a escala. Introducir un índice con su propio pipeline de eventos (outbox, consumer, tabla nueva, y toda la superficie de fallos de la eventual consistency que eso implica) es resolver un problema de performance que todavía no existe, a costa de un componente nuevo con sus propios bugs potenciales. Codificar la modalidad en el UUID hubiera sido la opción más elegante en abstracto, pero **ya no es viable sin romper compatibilidad**: #40 y #46 ya generan y persisten UUIDs opacos en producción: adoptar esa alternativa ahora exigiría una migración de datos que ninguno de los dos issues previó.

La consulta en paralelo es la opción más simple, no introduce estado nuevo que sincronizar, y dos PK lookups indexados en paralelo tienen una latencia despreciable frente al costo de mantener un índice consistente. Si en el futuro hay evidencia real de que el volumen lo justifica, migrar a un índice es un cambio aislado a `api:app` que no requiere tocar Physical ni Virtual otra vez.

## Consecuencias

### Positivas

- Cero cambios a #40/#46, ya mergeados.
- Sin componente nuevo, sin consistencia eventual que monitorear.
- Reversible: migrar a un índice más adelante no reabre el contrato de los puertos de Physical/Virtual, sólo la composición en `api:app`.

### Negativas

- El detalle de un curso siempre paga el costo de dos consultas, aunque una sea descartada.
- Si algún día el catálogo crece a un volumen que lo justifique, esta decisión debe revisarse — no hay mecanismo automático que avise cuándo eso ocurre.

### Riesgos y Reversibilidad

- **Riesgo principal:** ninguno crítico — el peor caso es latencia extra en el detalle, no un fallo funcional.
- **Reversibilidad:** alta. Migrar a un índice por outbox es aditivo (no requiere cambiar el contrato de `PhysicalCourseAvailabilityPort`/`VirtualCourseCatalogPort`, que ya exponen `courseId` como parámetro opaco).

## Referencias

- [07-CATALOG-API.md](../07-CATALOG-API.md)
- [22-DATA-MODEL.md](../22-DATA-MODEL.md)
- Issue #95 (composición de catálogo en `api:app`)
- Issues #40 (Physical) y #46 (Virtual), ya mergeados
