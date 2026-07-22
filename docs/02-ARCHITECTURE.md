# Arquitectura del Proyecto

## Topología

```text
Browser ──HTTP──> BFF (JAR) ──HTTP──> API (JAR)
Android ──HTTP──> API (JAR)
                              ├─ Auth
                              ├─ Billing
                              ├─ Virtual
                              └─ Physical
                                   │
                        MySQL `menta` + Redis + Caffeine
```

La API es un **monolito modular**. `api:app` sólo compone módulos; no contiene
lógica de dominio. El BFF es la única frontera web: el navegador no recibe ni
almacena access ni refresh tokens.

## Módulos y ownership

| Módulo | Es dueño de | Colabora mediante |
|---|---|---|
| Auth | identidad, roles y sesiones de personas | puertos de identidad/autorización |
| Billing | planes, suscripciones, pagos, quotes y conciliación | puertos/eventos internos |
| Virtual | cursos virtuales, contenido y progreso | puertos de acceso a suscripciones |
| Physical | cursos presenciales, sesiones, capacidad, asistencia y lectores QR | puertos/eventos internos |

MySQL usa un único schema `menta` y prefijos `auth_`, `billing_`, `virtual_` y
`physical_`. Una tabla sólo puede tener FK hacia tablas de su propio módulo.
No se permiten consultas `JOIN`, repositorios ni acceso JPA entre módulos.

## Catálogo de lectura

`GET /api/v1/catalog/courses` y `GET /api/v1/catalog/courses/{courseId}` son la
experiencia unificada de consulta para cursos de danza. `api:app`/BFF compone esta
proyección de lectura mediante puertos de Virtual y Physical; no existe tabla,
FK ni `JOIN` compartido. Cada curso tiene exactamente una modalidad (`VIRTUAL` o
`PHYSICAL`) y un `courseId` UUID globalmente único; `api:app` lo enruta mediante
la modalidad al módulo dueño. Las cotizaciones y compras siguen siendo operaciones separadas de
Billing según modalidad.

## Dependencias

```text
domain <- application <- infrastructure
```

Los módulos pueden depender de contratos Java mínimos en `api:shared`. La
orquestación de un caso de uso que involucra varios módulos ocurre en `api:app`
a través de puertos o eventos internos persistidos en outbox. Está prohibido
usar HTTP interno, RabbitMQ, service discovery, M2M o circuit breakers entre
módulos.

## Persistencia y estado

- Flyway vive en `:api:app` y es la única fuente del esquema.
- Hibernate usa `ddl-auto:validate`; nunca crea ni modifica el esquema.
- Redis contiene blacklist, rate limiting y locks. Su política es `noeviction`.
  Si Redis no está disponible, toda ruta autenticada falla cerrada.
- Caffeine sólo almacena caché reconstruible; nunca seguridad, cupos ni pagos.

## Observabilidad y evolución

Logback emite JSON estructurado con `correlationId`. OpenTelemetry exporta trazas
para Grafana/Loki. La retención mínima de logs de errores, auditoría de pagos e
incidentes de base de datos es 90 días, sin secretos ni datos de tarjeta.

La separación futura en microservicios requiere un ADR nuevo y migración
explícita de datos; no se anticipa con infraestructura distribuida en el MVP.
