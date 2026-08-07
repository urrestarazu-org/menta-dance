# ADR-0031: Estrategia de Sesión Web del BFF

**Estado:** Aceptado
**Fecha:** 2026-08-07
**Decisores:** Arquitecto, Tech Lead

## Contexto y Problema

El BFF (Backend for Frontend) debe gestionar sesiones de usuario para la aplicación web mientras cumple con el contrato de seguridad establecido en ADR-0025: el navegador nunca debe recibir ni almacenar tokens JWT ni refresh tokens directamente.

El BFF necesita:
- Autenticar usuarios llamando a `/api/v1/auth/login` del API
- Almacenar el par de tokens (access + refresh) server-side
- Mantener sesión de usuario en el navegador mediante cookies seguras
- Refrescar tokens automáticamente cuando expiran
- Revocar sesiones en logout
- Escalar horizontalmente (múltiples instancias BFF)

## Factores Clave (Decision Drivers)

* **Seguridad contractual**: ADR-0025 exige que tokens nunca lleguen al navegador
* **Infraestructura existente**: Redis 7.4 ya operacional con persistencia y `noeviction`
* **Simplicidad MVP**: Debe ser implementable en 2-3 horas con deuda técnica cero
* **Escalabilidad horizontal**: Session store compartido entre múltiples instancias BFF
* **Revocación inmediata**: Logout debe invalidar sesión en todos los servidores
* **Consistencia arquitectónica**: Mismo storage usado por Auth para seguridad crítica (ADR-0026)
* **Fail-closed**: Indisponibilidad de session store debe rechazar requests autenticados

## Opciones Consideradas

### Opción 1: Spring Session + Redis (Seleccionada)

* **Descripción:** Usar `spring-boot-starter-session-data-redis` para gestionar sesiones automáticamente en Redis. Spring maneja serialización, cookies, TTL y revocación.
* **Pros:**
  * Infraestructura ya existe (Redis 7.4 operacional)
  * Auto-configuración: ~30 líneas de código vs ~300 manual
  * Production-ready: usado en miles de aplicaciones
  * Escalabilidad automática multi-servidor
  * Revocación: `session.invalidate()` borra de Redis
  * CSRF integrado con Spring Security
  * TTL automático en Redis
  * Zero deuda técnica
* **Contras:**
  * Tied to Spring Session naming conventions (namespace: `spring:session:*`)
  * Serialización Java por defecto (no cifrado explícito de tokens)

### Opción 2: Custom Redis Session Repository

* **Descripción:** Implementar manualmente un `SessionRepository` que almacena tokens cifrados (AES-256-GCM) en Redis con namespace `bff:session:{sessionId}`.
* **Pros:**
  * Control total del schema y serialización
  * Cifrado explícito de tokens (mayor seguridad)
  * Namespace customizable
  * Flexible para casos edge (SSO, multi-device)
* **Contras:**
  * ~300 líneas de código a mantener
  * Mayor complejidad para sincronización multi-servidor
  * Riesgo de race conditions en concurrencia
  * Overkill para MVP (2-3 días vs 2-3 horas)
  * Deuda técnica media

### Opción 3: Cookie Encriptada (Stateless)

* **Descripción:** Almacenar tokens cifrados (AES-256) directamente en cookie del navegador. No requiere Redis.
* **Pros:**
  * Zero dependencias externas
  * Escalable (no sincronización)
  * Más seguro que cookies simples
* **Contras:**
  * **Viola ADR-0025**: tokens llegan al navegador (aunque cifrados)
  * No permite revocación inmediata (logout no termina sesión activa)
  * Cookie size > 4KB (JWT + refresh + metadata)
  * Complejidad de rotación de claves
  * Sesiones no auditables server-side

## Decisión

Elegimos **Spring Session + Redis** porque cumple el contrato de seguridad (ADR-0025), usa infraestructura existente (Redis operacional), es production-ready sin deuda técnica, y es implementable en 2-3 horas para el MVP.

## Justificación (Rationale)

### Por qué Spring Session + Redis

1. **Cumple ADR-0025**: Tokens nunca llegan al navegador. El navegador solo recibe cookie `SESSION` con ID opaco.

2. **Infraestructura ready**: Redis 7.4 ya configurado con:
   - Persistencia: `--appendonly yes`
   - Política: `--maxmemory-policy noeviction` (fail-closed)
   - Healthcheck activo en docker-compose
   - Volumen: `redis_data:/data`

3. **Consistencia con Auth**: El API ya usa Redis para blacklist/heartbeat (ADR-0026). El BFF reutiliza la misma infraestructura segura.

4. **Simplicidad MVP**:
   - 1 dependencia: `spring-boot-starter-session-data-redis`
   - ~30 líneas de configuración
   - Auto-configuración: serialización, cookies, TTL, revocación
   - 2-3 horas de implementación

5. **Production-ready**:
   - Spring Session probado en miles de apps
   - Escalabilidad horizontal automática
   - Session replication entre instancias BFF
   - TTL automático en Redis (30 min por defecto)

### Por qué NO Custom Redis

- MVP no justifica 300 líneas de código custom
- Spring Session ya resuelve race conditions, TTL, revocación
- Si necesitas cifrado explícito → agregar `SessionSerializer` después sin reescribir
- Future-proof: puedes migrar a custom después del MVP

### Por qué NO Cookie Encriptada

- Logout debe ser seguro e inmediato (revocación server-side)
- Tokens en navegador (aunque cifrados) viola el principio de custodia
- Sesiones server-side permiten auditoría y debugging
- Future-proof para multi-device sessions

## Consecuencias

### Positivas

* Tokens nunca llegan al navegador (cumple ADR-0025)
* Redis ya operacional (zero overhead de infraestructura)
* Implementación en 2-3 horas (vs 2-3 días custom)
* Zero deuda técnica (Spring Session es estándar)
* Escalabilidad horizontal automática (session replication)
* Revocación inmediata: `session.invalidate()` borra Redis
* CSRF integrado con Spring Security
* Fail-closed: Redis unavailable → rechaza requests autenticados
* Auditoría: sesiones visibles en Redis (`spring:session:*`)

### Negativas / Deuda Técnica

* Serialización Java por defecto (no cifrado explícito de tokens en Redis)
  - **Mitigación**: Redis ya protegido (red interna, autenticación)
  - **Future**: agregar `SessionSerializer` custom si necesario
* Namespace Spring: `spring:session:*` (no `bff:session:*`)
  - **Mitigación**: configurable via `spring.session.redis.namespace`
* Tokens en memoria Redis (no cifrados at-rest)
  - **Mitigación**: Redis persistence cifrada a nivel infra (future)

### Implicaciones de Costos

* **Infraestructura**: Zero (Redis ya existe)
* **Desarrollo**: 2-3 horas (1 dev)
* **Mantenimiento**: Mínimo (Spring Session estándar)
* **Redis memory**: ~1KB/session × max sessions
  - Estimación MVP: 100 sesiones concurrentes = 100KB
  - Escala: 10,000 sesiones = 10MB (negligible)

### Riesgos y Reversibilidad

* **Riesgo Principal**: Redis unavailable → BFF rechaza todos los requests autenticados
* **Plan de Mitigación**:
  - Redis con persistencia (`--appendonly yes`)
  - Healthcheck en docker-compose
  - Alertas de Redis down (integrar con monitoring)
  - Fail-closed es feature, no bug (consistente con ADR-0026)

* **Reversibilidad**:
  - **Alta**: Migrar a custom Redis en 1-2 días sin cambiar contrato
  - **Media**: Migrar a stateful DB (MySQL) requiere schema + migrations
  - **Baja**: Migrar a stateless (cookie) viola ADR-0025

## Referencias y Decisiones Relacionadas

* **Complementa**: ADR-0025 (Auth Token Strategy)
* **Complementa**: ADR-0026 (Redis Reflejado y Caffeine)
* **Complementa**: ADR-0021 (Clean Architecture Mandatory)
* **Relacionado**: `openspec/changes/bff-custodia-de-sesion-web/exploration.md`
* **Relacionado**: `openspec/changes/bff-custodia-de-sesion-web/proposal.md`
