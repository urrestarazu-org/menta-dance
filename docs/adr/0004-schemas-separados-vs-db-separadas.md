# ADR-0004: Separación Lógica de Bases de Datos (Esquemas separados)

**Estado:** Aceptado
**Fecha:** 2026-05-17
**Decisores:** Equipo de Arquitectura

## Vigencia actual

Se conserva la separación lógica de datos por dominio, ahora propiedad de los módulos Auth, Virtual, Physical y Billing dentro del único JAR API. `api:app` ensambla las migraciones y la configuración de persistencia. Las APIs independientes, la replicación por eventos y la sincronización mediante RabbitMQ que aparecen en el contexto histórico fueron reemplazadas por puertos Java según [ADR-0020](./0020-modular-monolith.md).


## Contexto y Problema

Actualmente, Menta Virtual Academy utiliza una única base de datos MySQL 8.0 donde todas las tablas residen juntas. Para avanzar hacia una arquitectura distribuida (microservicios), la regla de oro es que cada servicio debe ser dueño exclusivo de sus datos. Debemos decidir cómo aislar los datos de *Auth*, *Virtual*, *Physical* y *Billing*.

## Factores Clave (Decision Drivers)

* Garantizar que las APIs no se acoplen a nivel de base de datos (evitar consultas cruzadas o JOINs directos entre dominios).
* **Restricciones presupuestarias y de hardware:** Por rentabilidad del MVP, todas las bases de datos deben coexistir en un único host de 8GB RAM y 30GB SSD.
* Facilidad de mantenimiento y migraciones.

## Opciones Consideradas

### Opción 1: Bases de Datos Físicamente Separadas (Instancias MySQL independientes)

* **Descripción:** Levantar 4 contenedores MySQL diferentes, uno para cada API.
* **Pros:**
  * Aislamiento total (CPU, memoria, IOPS). Si Virtual API sufre carga pesada, Auth API no se ve afectada.
* **Contras:**
  * **Inviable por hardware:** Correr 4 instancias de MySQL 8.0 colapsaría la memoria de 8GB RAM del servidor, ya que cada instancia reserva agresivamente memoria para su buffer pool.

### Opción 2: Esquemas Lógicos Separados en el Mismo Servidor MySQL

* **Descripción:** Mantener 1 solo servidor MySQL, pero crear 4 esquemas (bases de datos lógicas): `auth_schema`, `virtual_schema`, `physical_schema`, `billing_schema`. Cada API tiene credenciales restringidas solo a su esquema.
* **Pros:**
  * Cumple con la regla de aislamiento (no se permiten JOINs cruzados desde la aplicación si los usuarios están restringidos).
  * Costo mínimo: aprovecha la capacidad subutilizada de la base de datos actual.
* **Contras:**
  * Riesgo del "vecino ruidoso" (noisy neighbor): una query pesada en `virtual_schema` puede consumir la CPU del servidor, afectando a `auth_schema`.

## Decisión

Elegimos **Opción 2: Esquemas separados (mismo MySQL)** porque **brinda el aislamiento lógico necesario a nivel de código y arquitectura, manteniendo los costos controlados para el MVP**.

## Justificación (Rationale)

A nivel de arquitectura de software, lo importante es que el código de *Virtual API* no pueda hacer un SELECT a la tabla `users` directamente. Separar por esquemas logra este objetivo disciplinario. Dado el tráfico proyectado (200 usuarios, ~1.67 RPS), el riesgo de agotamiento de recursos del servidor de BD es ínfimo, por lo que el aislamiento físico es un sobrecosto innecesario ahora mismo.

## Especificaciones Técnicas

### Esquemas y Ownership

| Esquema | API Owner | Tablas Principales |
|---------|-----------|-------------------|
| `auth_schema` | Auth API | users, roles, users_roles, password_reset_tokens, refresh_tokens |
| `virtual_schema` | Virtual API | courses, modules, lessons, videos, user_progress, certificates |
| `physical_schema` | Physical API | locations, schedules, attendances, instructors |
| `billing_schema` | Billing API | plans, subscriptions, payments, mercadopago_redirections, invoices |

### Usuarios de Base de Datos

```sql
-- Crear usuarios con permisos restringidos
CREATE USER 'auth_user'@'%' IDENTIFIED BY '${AUTH_DB_PASSWORD}';
GRANT ALL PRIVILEGES ON auth_schema.* TO 'auth_user'@'%';

CREATE USER 'virtual_user'@'%' IDENTIFIED BY '${VIRTUAL_DB_PASSWORD}';
GRANT ALL PRIVILEGES ON virtual_schema.* TO 'virtual_user'@'%';

CREATE USER 'physical_user'@'%' IDENTIFIED BY '${PHYSICAL_DB_PASSWORD}';
GRANT ALL PRIVILEGES ON physical_schema.* TO 'physical_user'@'%';

CREATE USER 'billing_user'@'%' IDENTIFIED BY '${BILLING_DB_PASSWORD}';
GRANT ALL PRIVILEGES ON billing_schema.* TO 'billing_user'@'%';

FLUSH PRIVILEGES;
```

### Estrategia de Migraciones (Flyway)

Cada API manejará sus propias migraciones de forma independiente:

```
auth-api/
  └── src/main/resources/db/migration/
      ├── V001__create_users_table.sql
      └── V002__create_roles_table.sql

virtual-api/
  └── src/main/resources/db/migration/
      ├── V001__create_courses_table.sql
      └── V002__create_modules_table.sql

billing-api/
  └── src/main/resources/db/migration/
      ├── V001__create_plans_table.sql
      └── V002__create_subscriptions_table.sql
```

**Configuración Flyway por servicio:**

```yaml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration
    schemas: ${DB_SCHEMA}  # auth_schema, virtual_schema, etc.
```

### Datos Compartidos (Desnormalización Controlada)

Para evitar JOINs cross-schema, se aplicará **desnormalización controlada**:

| Dato | Fuente de Verdad | Réplica en | Sincronización |
|------|------------------|------------|----------------|
| `user_id` + `email` | Auth API | Virtual, Physical, Billing | Evento `user.created` |
| `user_status` | Auth API | Virtual, Physical | Evento `user.status.changed` |
| `plan_id` + `name` | Billing API | Virtual (cache) | Evento `plan.updated` |

## Consecuencias

### Positivas

* Costos de infraestructura sin incrementos significativos.
* Arquitectura preparada para el futuro (desacoplada).
* Cada equipo/API puede evolucionar su schema sin afectar a otros.

### Negativas / Deuda Técnica

* Monitoreo compartido. Si la BD colapsa, caen todos los servicios simultáneamente.
* Datos desnormalizados pueden quedar inconsistentes si fallan los eventos.

### Implicaciones de Costos e Infraestructura

* $0 en infraestructura adicional. Es el único enfoque viable para un servidor de 8GB RAM. Configuraremos el contenedor único de MySQL con un `innodb_buffer_pool_size` de ~1GB para tener buen rendimiento sin ahogar a los demás servicios.

### Riesgos y Reversibilidad

* **Riesgo Principal:** Que los desarrolladores evadan la restricción y hagan consultas cross-schema manualmente.
* **Plan de Mitigación:** Crear usuarios de MySQL estrictos: `virtual_user` solo tendrá permisos sobre `virtual_schema`. Además, reglas ArchUnit prohibirán imports de entidades de otros dominios.
* **Reversibilidad:** Alta y fácil. Si en el futuro un esquema requiere escalado físico, simplemente se hace un dump de ese esquema y se restaura en un servidor MySQL nuevo, actualizando la cadena de conexión de la API correspondiente.

## Referencias y Decisiones Relacionadas

* **Alcance reemplazado por:** [ADR-0020](./0020-modular-monolith.md) - Los módulos colaboran mediante puertos Java
* **Complementa a:** [ADR-0009](./0009-restricciones-infraestructura-mvp.md) - Restricciones de infraestructura del MVP
* **Complementa a:** [ADR-0010](./0010-centralizacion-pagos-billing-api.md) - Billing API y su esquema dedicado
* **Flyway Documentation:** <https://flywaydb.org/documentation/>
