# ADR-0006: Estrategia de Ofuscación de IDs (Mantener Hashids vs Migrar a UUID)

**Estado:** Aceptado
**Fecha:** 2026-05-17
**Decisores:** Equipo de Arquitectura

## Vigencia actual

La decisión sobre Hashids sigue vigente en los adaptadores HTTP externos. Las menciones históricas a librerías compartidas entre APIs deben interpretarse como código reutilizable dentro del monorepo, sin crear servicios ni comunicación de red interna. La organización vigente está definida por [ADR-0020](./0020-modular-monolith.md).


## Contexto y Problema

El monolito actual utiliza **Hashids** (ej. `xvb5D1e0`) para ofuscar los IDs numéricos (ej. `123`) autoincrementales de la base de datos en las URLs públicas. Esto previene ataques de enumeración y scraping. Al migrar a una nueva arquitectura de APIs, surge la duda de si debemos mantener este enfoque algorítmico o migrar al uso de identificadores universales (UUIDs v4).

## Factores Clave (Decision Drivers)

* Mantenimiento de SEO e indexación (URLs actuales de cursos en buscadores).
* Impacto en el esquema de base de datos.
* Experiencia del usuario al compartir URLs (longitud).

## Opciones Consideradas

### Opción 1: Migrar a UUIDs (v4 o v7)

* **Descripción:** Agregar una columna `uuid` a todas las entidades expuestas públicamente y usarla en las URLs (ej. `/courses/123e4567-e89b-12d3-a456-426614174000`).
* **Pros:**
  * Estándar de la industria para microservicios.
  * Los IDs se pueden generar en el cliente o servicios sin riesgo de colisión.
* **Contras:**
  * **Rompe TODAS las URLs públicas actuales**, causando una penalización crítica en SEO.
  * Requiere un esfuerzo masivo de refactorización en la BD y en código.
  * URLs muy largas y feas estéticamente.

### Opción 2: Mantener Hashids

* **Descripción:** Continuar usando el algoritmo Hashids (con el salt seguro actual) para codificar/decodificar IDs numéricos al vuelo en la capa de Controladores/BFF.
* **Pros:**
  * **Cero impacto en SEO.** Las URLs públicas permanecen idénticas.
  * Cero cambios estructurales en la base de datos MySQL (los Primary Keys siguen siendo BIGINT).
  * URLs cortas y amigables.
* **Contras:**
  * Los IDs siguen siendo autoincrementales en BD, lo que a futuro puede complicar migraciones a bases de datos distribuidas.
  * Requiere importar e inicializar el servicio de Hashids en las nuevas APIs.

## Decisión

Elegimos **Opción 2: Mantener Hashids** porque **protege el posicionamiento SEO actual (que es crítico para el negocio) y evita un costoso refactor de base de datos durante la fase MVP**.

## Justificación (Rationale)

Para una plataforma educativa que depende de la adquisición orgánica de alumnos, romper las URLs indexadas de los cursos es inaceptable. El costo técnico de importar la librería de Hashids en el BFF y la Virtual API es despreciable en comparación con el esfuerzo de migrar datos a UUIDs y montar sistemas de redirección 301 masivos.

## Especificaciones Técnicas

### Configuración Compartida

El **SALT de Hashids** debe ser idéntico en todos los servicios que manejen URLs públicas:

| Servicio | Necesita Hashids |
|----------|-----------------|
| BFF | Sí (URLs de navegación) |
| Virtual API | Sí (cursos, módulos, lecciones) |
| Auth API | No (IDs internos) |
| Physical API | Sí (sedes, horarios) |
| Billing API | No (IDs de pago son internos) |

### Inyección del SALT

```yaml
# application.yml
app:
  security:
    hashids:
      salt: ${HASHIDS_SALT}  # Inyectado desde variable de entorno
      min-length: 8
```

### Librería Compartida

Se creará un módulo Maven/Gradle compartido `menta-common` que contenga:

* `HashidsService` (encode/decode)
* Constantes y configuración

```xml
<!-- pom.xml de virtual-api -->
<dependency>
    <groupId>com.mentavirtual</groupId>
    <artifactId>menta-common</artifactId>
    <version>${project.version}</version>
</dependency>
```

## Consecuencias

### Positivas

* Preservación total del SEO.
* El panel de administración (Herramienta HashIds Decoder) seguirá funcionando igual.
* URLs amigables y fáciles de compartir.

### Negativas / Deuda Técnica

* La lógica de ofuscación de Hashids deberá compartirse vía librería común entre el BFF, Virtual API y Physical API.
* Si el SALT se compromete, todas las URLs serían predecibles (mitigado con rotación de emergencia).

### Implicaciones de Costos

* Nulo. Reduce drásticamente el tiempo de desarrollo frente a una migración a UUIDs.

### Riesgos y Reversibilidad

* **Riesgo Principal:** Exposición accidental del SALT de Hashids en el nuevo ecosistema.
* **Plan de Mitigación:** Inyectar el SALT como variable de entorno segura a nivel de clúster/contenedores, nunca commitear en el repositorio. Usar gestión de secretos (Docker secrets o Vault en el futuro).
* **Reversibilidad:** Compleja a largo plazo. Si la base de datos crece y requiere sharding real, los IDs autoincrementales serán un cuello de botella, obligando a migrar a UUIDs en el futuro.

## Referencias y Decisiones Relacionadas

* **Complementa a:** [ADR-0002](./0002-bff-vs-api-gateway.md) - BFF maneja URLs públicas con Hashids
* **Relacionado con:** Documentación existente en `CLAUDE.md` sobre HashidsService
* **Hashids Library:** <https://hashids.org/java/>
