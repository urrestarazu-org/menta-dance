# ADR-0007: Estrategia de Caché Distribuido (Redis vs Caché Local)

**Estado:** Aceptado
**Fecha:** 2026-05-17
**Decisores:** Equipo de Arquitectura

## Vigencia actual

Redis continúa aceptado para los casos que requieren estado compartido entre procesos, como sesiones del BFF o revocación definida por la política de autenticación. No se usa para coordinar módulos internos, deduplicar eventos entre módulos ni implementar un bus. Las referencias a múltiples APIs, gateway y RabbitMQ son históricas y fueron reemplazadas por [ADR-0020](./0020-modular-monolith.md).


## Contexto y Problema

Para optimizar el rendimiento y soportar la arquitectura de seguridad basada en JWT (ADR-0001), necesitamos un sistema de caché. La Auth API necesita revocar Access Tokens antes de su expiración (Blacklist), y el BFF necesita cachear respuestas frecuentes de la Virtual API (ej. catálogo de cursos) para no saturarla. ¿Dónde y cómo almacenamos esta caché?

## Factores Clave (Decision Drivers)

* Sincronización de estado en un entorno escalable (múltiples réplicas de una misma API).
* Velocidad de lectura/escritura (baja latencia).
* Soporte para TTL (Time To Live) nativo para que los tokens expirados se limpien solos.
* **Límite de memoria:** Servidor consolidado de 8GB RAM, requiriendo un uso responsable de recursos compartidos.

## Opciones Consideradas

### Opción 1: Caché Local en Memoria (Caffeine / ConcurrentHashMap)

* **Descripción:** Cada instancia de Spring Boot mantiene su propia caché en su memoria RAM local.
* **Pros:**
  * Cero costos de infraestructura.
  * Latencia de lectura prácticamente nula.
* **Contras:**
  * **Inconsistencia de estado:** Si la instancia A de Auth API revoca un token, la instancia B no lo sabrá y seguirá permitiendo el acceso.
  * Limitado por la memoria RAM del servidor de aplicaciones.

### Opción 2: Caché Distribuido (Redis)

* **Descripción:** Desplegar un servidor Redis centralizado al que todas las APIs (Auth, Virtual, Physical, BFF) se conecten.
* **Pros:**
  * **Estado compartido:** Todas las instancias leen la misma Blacklist de JWT. Un token revocado es rechazado instantáneamente en toda la red.
  * Soporte nativo y preciso para TTL (limpieza automática).
  * Sirve para otros casos de uso (Rate limiting distribuido, sesiones distribuidas).
* **Contras:**
  * Introduce un nuevo componente de infraestructura crítico (punto único de fallo si no se clusteriza).
  * Latencia de red pequeña (~1-5ms) vs caché local.

## Decisión

Elegimos **Opción 2: Redis compartido** porque **es el único mecanismo viable para implementar de forma segura la revocación de tokens JWT (Blacklist) en un entorno de microservicios o múltiples réplicas**.

## Justificación (Rationale)

La seguridad es primordial. No podemos correr el riesgo de que un administrador bloquee a un usuario malicioso y que el usuario siga navegando porque la petición cayó en una réplica de la API que no tenía actualizada su caché local. Redis soluciona esto garantizando una única fuente de verdad rápida. Además, servirá para mitigar picos de tráfico almacenando el catálogo de cursos en el BFF.

## Especificaciones Técnicas

### Casos de Uso de Redis

| Caso de Uso | Key Pattern | TTL | Servicio |
|-------------|-------------|-----|----------|
| JWT Blacklist | `jwt:blacklist:{jti}` | 15 min (igual al Access Token) | Auth API |
| Idempotencia eventos | `event:processed:{eventId}` | 24 horas | Virtual, Physical, Billing |
| Caché catálogo cursos | `cache:courses:list` | 5 min | BFF |
| Rate limiting | `ratelimit:{ip}:{endpoint}` | 1 min | NGINX/Gateway |
| Sesión de refresh token | `session:refresh:{userId}` | 7 días | Auth API |

### Comportamiento en Caso de Fallo de Redis

| Caso de Uso | Comportamiento | Justificación |
|-------------|----------------|---------------|
| JWT Blacklist | **Fail Closed** - Rechazar petición | Seguridad > Disponibilidad |
| Caché catálogo | **Fail Open** - Consultar BD | Disponibilidad > Performance |
| Rate limiting | **Fail Open** - Permitir petición | Disponibilidad > Protección DDoS |
| Idempotencia | **Fail Open** - Procesar (puede duplicar) | Disponibilidad, idempotencia es best-effort |

### Implementación Fail Closed/Open

```java
@Service
public class JwtBlacklistService {

    private final RedisTemplate<String, String> redisTemplate;

    public boolean isTokenRevoked(String jti) {
        try {
            return Boolean.TRUE.equals(
                redisTemplate.hasKey("jwt:blacklist:" + jti)
            );
        } catch (RedisConnectionFailureException e) {
            // FAIL CLOSED: Si Redis no está disponible, rechazar por seguridad
            log.error("Redis no disponible, rechazando token por seguridad", e);
            return true; // Asumir revocado
        }
    }
}

@Service
public class CourseCacheService {

    public List<CourseDto> getCourses() {
        try {
            List<CourseDto> cached = redisTemplate.opsForValue()
                .get("cache:courses:list");
            if (cached != null) return cached;
        } catch (RedisConnectionFailureException e) {
            // FAIL OPEN: Si Redis no está disponible, ir a BD
            log.warn("Redis no disponible, consultando BD directamente", e);
        }
        return courseRepository.findAll();
    }
}
```

### Configuración de Redis

```yaml
# docker-compose.yml
redis:
  image: redis:7-alpine
  command: >
    redis-server
    --maxmemory 256mb
    --maxmemory-policy volatile-lru
    --appendonly yes
  volumes:
    - redis-data:/data
  deploy:
    resources:
      limits:
        memory: 300M
```

## Consecuencias

### Positivas

* Seguridad robusta y centralizada para invalidación de sesiones.
* Base sólida para implementar Rate Limiting global a nivel de API Gateway.
* Reducción de carga en base de datos mediante caché de consultas frecuentes.

### Negativas / Deuda Técnica

* Complejidad operativa añadida.
* Necesidad de monitorear la salud de Redis.

### Implicaciones de Costos e Infraestructura

* Requiere un contenedor extra de Redis. Para un host de 8GB RAM, un límite de `maxmemory 256mb` con política `volatile-lru` es más que suficiente para garantizar alta disponibilidad de tokens sin afectar al resto del sistema.

### Riesgos y Reversibilidad

* **Riesgo Principal:** Caída del nodo Redis detiene las validaciones de seguridad o degrada el rendimiento.
* **Plan de Mitigación:** Para el MVP usamos Redis Standalone con persistencia AOF. En producción futura, considerar Redis Sentinel o un servicio gestionado (AWS ElastiCache, Redis Cloud).
* **Reversibilidad:** Alta. Se abstraerá detrás de las interfaces `CacheManager` de Spring, permitiendo cambiar de proveedor si fuese necesario.

## Referencias y Decisiones Relacionadas

* **Complementa a:** [ADR-0001](./0001-jwt-vs-session-auth.md) - JWT Blacklist requiere Redis
* **Alcance reemplazado por:** [ADR-0020](./0020-modular-monolith.md) - No existe bus de eventos entre módulos
* **Complementa a:** [ADR-0009](./0009-restricciones-infraestructura-mvp.md) - Restricciones de memoria del servidor
* **Redis Documentation:** <https://redis.io/documentation>
* **Spring Data Redis:** <https://spring.io/projects/spring-data-redis>
