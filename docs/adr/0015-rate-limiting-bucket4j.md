# 15. Uso de Bucket4j para Rate Limiting

Date: 2026-05-18

## Status

Accepted

## Vigencia actual

Bucket4j continúa aceptado en los adaptadores de entrada HTTP que requieran límites. No existe rate limiting entre módulos del JAR API y las referencias a réplicas de `auth-api`, Spring Cloud Gateway o almacenamiento distribuido son escenarios históricos/futuros, no la topología MVP. Ver [ADR-0020](./0020-modular-monolith.md).

## Context

Como parte de la implementación de la **Auth API** (US-AUTH-001) nos encontramos con el requerimiento no funcional (NFR) de aplicar *rate limiting* al endpoint de registro para mitigar posibles ataques de fuerza bruta y agotamiento de recursos (3 intentos por IP por hora).

Inicialmente se consideró una implementación básica "In-Memory" utilizando estructuras de datos simples como `ConcurrentHashMap`. Sin embargo, dada la arquitectura orientada a microservicios en la que este sistema podrá evolucionar hacia un entorno multi-instancia, una implementación básica sufriría de problemas de estado local (cada instancia tendría su propia ventana de rate-limit) y condiciones de carrera (Race Conditions) al actualizar los contadores.

## Decision

Se ha decidido integrar **Bucket4j**, una biblioteca Java madura, que implementa el algoritmo de **Token Bucket**. Esta decisión aporta las siguientes ventajas:

1. **Eficiencia en Ráfagas y Reabastecimiento**: En lugar de usar "ventanas fijas" (Fixed Windows) que bloquean y desbloquean rígidamente en bloques de una hora, la abstracción de *Token Bucket* permite un reabastecimiento progresivo de los intentos (tokens) en función del tiempo.
2. **Concurrencia Segura**: Bucket4j maneja la sustracción de tokens y la inspección atómicamente, lo que evita condiciones de carrera, incluso bajo una carga alta.
3. **Escalabilidad (Distributed Rate Limiting)**: Al estar diseñada para escenarios multi-instancia, Bucket4j tiene soporte pre-construido para ser acoplada con tecnologías de almacenamiento centralizadas como **Redis**, Hazelcast o bases de datos relacionales, simplificando una futura migración cuando haya múltiples réplicas de `auth-api` balanceadas.

## Implementation Details

### API del Servicio

La interfaz `RateLimitService` expone dos métodos:

```java
public interface RateLimitService {
  /**
   * Intenta consumir un token atómicamente.
   * @return true si la acción está permitida
   */
  boolean tryConsume(String ip, String action);

  /**
   * Estima segundos hasta que haya tokens disponibles.
   */
  long getSecondsUntilReset(String ip, String action);
}
```

### Gestión de Memoria

Para evitar crecimiento indefinido del `ConcurrentHashMap`, se implementó:

1. **BucketEntry Wrapper**: Cada bucket se envuelve con un timestamp de último acceso.
2. **Limpieza Programada**: Un `@Scheduled` job cada hora elimina buckets sin actividad en las últimas 2 horas.
3. **Método de Monitoreo**: `getActiveBucketsCount()` permite observar el uso de memoria.

```java
@Scheduled(fixedRate = 3600000) // cada hora
public void cleanupInactiveBuckets() {
  Instant threshold = Instant.now().minus(Duration.ofHours(2));
  buckets.entrySet().removeIf(entry ->
      entry.getValue().getLastAccess().isBefore(threshold));
}
```

### Configuración de Límites

Los límites son configurables via `application.properties`:

| Propiedad | Default | Descripción |
|-----------|---------|-------------|
| `app.security.rate-limit.register-per-hour` | 3 | Intentos de registro por hora |
| `app.security.rate-limit.login-attempts` | 5 | Intentos de login por ventana de 30 min |

## Consequences

### Positivas

- Código altamente legible y mantenible gracias a la DSL limpia y fluida de configuración de límites provista por Bucket4j.
- Mitigación temprana y efectiva de ataques DoS y fuerza bruta.
- Futura migración transparente hacia clústeres distribuidos apoyados por Redis (alineado a las futuras arquitecturas con Spring Cloud Gateway o un Load Balancer).
- **Uso de memoria controlado** gracias a la limpieza automática de buckets inactivos.
- **API atómica** con `tryConsume()` que elimina race conditions entre verificación y registro.

### Negativas

- Se añade una nueva dependencia de terceros al build (`com.bucket4j:bucket4j-core:8.10.1`).
- Para la Fase 1, se mantendrá con almacenamiento en memoria interna (`ConcurrentHashMap` de los *buckets*), lo que significa que el balanceo estricto no se cumplirá hasta que no se configure el almacenamiento distribuido en fases posteriores si agregamos múltiples réplicas del servicio.

## Migration Path to Distributed Rate Limiting

Cuando se requiera escalar `auth-api` a múltiples instancias:

1. Agregar dependencia `bucket4j-redis` o `bucket4j-hazelcast`
2. Configurar el `ProxyManager` con el backend distribuido
3. Reemplazar `ConcurrentHashMap` por el proxy distribuido
4. Los límites y la lógica de negocio permanecen sin cambios
