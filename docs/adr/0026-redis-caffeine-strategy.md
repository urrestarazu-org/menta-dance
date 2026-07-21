# ADR-0026: Estrategia de Caché Redis vs Caffeine

**Estado:** Aceptado
**Fecha:** 2026-07-21
**Decisores:** Equipo de desarrollo

## Contexto y Problema

El sistema necesita diferentes tipos de caché y estado compartido:

| Necesidad | Característica requerida |
|-----------|--------------------------|
| Caché de cursos/planes | Lectura frecuente, invalidación ocasional |
| JWT blacklist | Consistencia entre instancias |
| Rate limiting | Contadores compartidos |
| Locks distribuidos (QR check-in) | Exclusión mutua entre instancias |
| Sesiones BFF | Estado compartido si escalamos |

La documentación actual menciona tanto Caffeine como Redis sin definir cuándo
usar cada uno.

## Factores Clave

* MVP corre en una sola instancia de API, pero debe prepararse para escalar
* Caffeine es local y rápido, pero no comparte estado entre instancias
* Redis permite estado compartido, pero agrega latencia de red (~1ms)
* El costo de Redis standalone es bajo (~256 MB RAM)

## Decisión

### Caffeine: Caché Local No Autoritativa

Usar para datos **read-heavy** que toleran inconsistencia temporal.

| Caso de uso | TTL | Invalidación |
|-------------|-----|--------------|
| Lista de cursos públicos | 5 min | Manual o por TTL |
| Detalle de plan | 5 min | Manual o por TTL |
| Configuración de sistema | 10 min | Reinicio o manual |
| Datos de usuario (perfil) | 2 min | Por evento de update |

**Configuración:**

```java
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .recordStats());
        return manager;
    }
}
```

**Uso:**

```java
@Service
public class CourseQueryService {

    @Cacheable(value = "courses", key = "#hashId")
    public CourseDto findByHashId(String hashId) {
        return courseRepository.findByHashId(hashId)
            .map(courseMapper::toDto)
            .orElseThrow(() -> new CourseNotFoundException(hashId));
    }

    @CacheEvict(value = "courses", key = "#hashId")
    public void invalidateCourse(String hashId) {
        // Llamar cuando se actualiza un curso
    }
}
```

### Redis: Estado Compartido Autoritativo

Usar para datos que **requieren consistencia** entre instancias o tienen
semántica de **lock/contador**.

| Caso de uso | Estructura | TTL |
|-------------|------------|-----|
| JWT blacklist | String `blacklist:jti:{jti}` | = token exp |
| Rate limiting | Bucket4j Redis | Según ventana |
| Lock QR check-in | String `lock:session:{id}:checkin` | 30 seg |
| Sesión BFF | Hash `session:{id}` | 30 min |
| Caché de permisos | Set `user:{id}:permissions` | 5 min |

**Configuración:**

```java
@Configuration
public class RedisConfig {

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName("${REDIS_HOST:localhost}");
        config.setPort(6379);
        return new LettuceConnectionFactory(config);
    }

    @Bean
    public RedisTemplate<String, String> redisTemplate(
            RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        return template;
    }
}
```

### Lock Distribuido para QR Check-in

Usa **nonce + compare-and-delete** para evitar liberar locks ajenos:

```java
@Service
@RequiredArgsConstructor
public class CheckInLockService {

    private final StringRedisTemplate redis;
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);

    /**
     * Intenta adquirir lock exclusivo para check-in.
     * @return nonce si adquirió, null si ya está ocupado
     */
    public String tryAcquireLock(Long sessionId, Long userId) {
        String key = lockKey(sessionId, userId);
        String nonce = UUID.randomUUID().toString();
        Boolean acquired = redis.opsForValue()
            .setIfAbsent(key, nonce, LOCK_TTL);
        return Boolean.TRUE.equals(acquired) ? nonce : null;
    }

    /**
     * Libera lock solo si el nonce coincide (evita borrar locks ajenos).
     */
    public boolean releaseLock(Long sessionId, Long userId, String nonce) {
        String key = lockKey(sessionId, userId);
        // Lua script atómico: compara y borra
        String script = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else
                return 0
            end
            """;
        Long result = redis.execute(
            RedisScript.of(script, Long.class),
            List.of(key),
            nonce
        );
        return result != null && result == 1;
    }

    private String lockKey(Long sessionId, Long userId) {
        return "lock:session:" + sessionId + ":checkin:" + userId;
    }
}
```

**Uso en el caso de uso:**

```java
public void checkIn(Long sessionId, Long userId) {
    String nonce = lockService.tryAcquireLock(sessionId, userId);
    if (nonce == null) {
        throw new ConcurrentCheckInException("Check-in en proceso");
    }
    try {
        // Lógica de check-in...
        attendanceRepository.save(attendance);
    } finally {
        lockService.releaseLock(sessionId, userId, nonce);
    }
}
```

### Rate Limiting con Bucket4j + Redis

```java
@Configuration
public class RateLimitConfig {

    @Bean
    public ProxyManager<String> proxyManager(RedisConnectionFactory factory) {
        return Bucket4jRedis.casBasedBuilder(
            RedisCommands.create(factory))
            .build();
    }
}

@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final ProxyManager<String> buckets;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String ip = request.getRemoteAddr();
        Bucket bucket = buckets.builder()
            .addLimit(Bandwidth.classic(100, Refill.intervally(100, Duration.ofMinutes(1))))
            .build(ip);

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.getWriter().write("{\"error\":\"Too many requests\"}");
        }
    }
}
```

## Matriz de Decisión

| Necesidad | Tecnología | Razón |
|-----------|------------|-------|
| Cursos/planes (lectura) | **Caffeine** | Local, rápido, tolera stale |
| JWT blacklist | **Redis** | Debe ser autoritativo |
| Rate limiting | **Redis** | Contadores compartidos |
| Locks check-in | **Redis** | Exclusión mutua |
| Sesiones BFF | **Redis** | Preparación para escalar |
| Configuración sistema | **Caffeine** | Solo lectura, reinicio OK |

## Infraestructura

```yaml
# docker-compose.yml
services:
  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data
    command: redis-server --appendonly yes --maxmemory 256mb --maxmemory-policy noeviction
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 3

volumes:
  redis_data:
```

**Producción:** Redis standalone, 256 MB RAM, persistencia AOF.

**Política de memoria:** `noeviction`. Este Redis del MVP almacena estado
autoritativo: blacklist JWT, locks, rate limiting y sesiones BFF. Las cachés de
lectura frecuentes pertenecen a Caffeine, no a Redis. Los TTL controlan el
ciclo de vida de las claves, pero no garantizan su retención con
`volatile-lru`: bajo presión de memoria Redis puede expulsar una clave con TTL
antes de que expire. Con `noeviction`, al alcanzar el límite Redis rechaza
escrituras en lugar de perder una blacklist o un lock activo.

Se debe monitorear `used_memory`, errores de escritura y `evicted_keys` (debe
permanecer en cero), y dimensionar la instancia para el pico esperado de
claves con TTL. Si Redis no está disponible o no puede escribir, las rutas que
validan blacklist o adquieren locks deben fallar de forma cerrada y alertar; no
deben admitir un token posiblemente revocado ni continuar una operación
concurrente sin lock.

## Consecuencias

### Positivas

* Caffeine acelera lecturas frecuentes sin costo de red
* Redis garantiza consistencia para datos críticos
* Separación clara de responsabilidades
* Bajo costo de infraestructura (Redis standalone)

### Negativas / Deuda Técnica

* Dos sistemas de caché requieren conocer cuándo usar cada uno
* Invalidación de Caffeine es manual (no automática entre instancias)

### Riesgos y Reversibilidad

* **Riesgo Principal:** Redis no disponible o sin capacidad bloquea validación
  de blacklist, locks y rate limiting.
* **Plan de Mitigación:** Health check, alertas de memoria/escritura y
  dimensionamiento para el pico de claves con TTL; las rutas críticas fallan
  cerradas, no permiten tráfico como fallback.
* **Reversibilidad:** Alta — migrar de Caffeine a Redis o viceversa es local

## Referencias

* [Caffeine GitHub](https://github.com/ben-manes/caffeine)
* [Bucket4j Redis](https://bucket4j.com/8.1.0/toc.html#redis)
* [ADR-0024](./0024-technology-baseline.md) — Technology Baseline
* [ADR-0025](./0025-auth-token-strategy.md) — JWT Blacklist en Redis
* Actualiza: [ADR-0007](./0007-cache-redis-vs-local.md)
