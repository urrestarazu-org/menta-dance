# ADR-0033: Rate Limiting Atómico para Registro y Reenvío de Activación

**Estado:** Aceptado

## Contexto

Los endpoints públicos de registro (`POST /api/v1/users/register`) y reenvío
de activación (`POST /api/v1/auth/resend-activation`) disparan el envío de un
token de activación por email. Al ser rutas públicas no autenticadas, son un
vector directo de abuso: enumeración de emails registrados, email-bombing
contra una víctima, o agotamiento del proveedor de envío de correo mediante
requests automatizados.

`RedisActivationRateLimitPort` (`api/auth/src/main/java/com/menta/auth/infrastructure/activation/`)
implementa `ActivationRateLimitPort` para frenar ambos abusos antes de que el
caso de uso llegue a generar el token o encolar el email. La forma en que
cuenta los intentos y qué hace cuando Redis falla son decisiones de seguridad
explícitas, no detalles de implementación.

## Decisión

**Doble límite, ventana fija.** Cada `consume()` incrementa dos contadores en
paralelo: uno por `emailFingerprint` (límite bajo, `3` intentos) y otro por
`clientFingerprint` (límite más laxo, `10` intentos), ambos sobre la misma
ventana de `900` segundos (15 min), configurables vía
`auth.activation.rate-limit.*`. El límite por email frena el email-bombing
contra una víctima puntual; el límite por cliente frena a un mismo actor
rotando direcciones de email.

**Atomicidad vía script Lua.** `INCR` + `EXPIRE` (solo en el primer hit) +
comparación contra el límite se ejecutan como un único script `EVAL` en
Redis (`CONSUME_SCRIPT`). Esto evita la condición de carrera de leer el
contador, decidir en la aplicación, e incrementar en pasos separados, donde
dos requests concurrentes podrían leer el mismo valor y ambas pasar el
chequeo.

**Hash tag de cluster.** Las keys se arman como `prefix + "{" + fingerprint +
"}"`. El `{fingerprint}` fuerza a que la key de email y la de cliente
mapeen al mismo slot en Redis Cluster, requisito para que un mismo script
Lua pueda tocar ambas keys en una sola llamada `EVAL` (Redis Cluster no
permite operaciones multi-key entre slots distintos).

**Fingerprints en vez de datos crudos.** El puerto nunca recibe el email ni
la IP en texto plano — sólo su fingerprint SHA-256 (64 hex minúsculas,
validado en `validateFingerprint`). Esto evita persistir PII en las keys de
Redis.

**Fail-closed ante fallas de Redis.** Cualquier `RuntimeException` al hablar
con Redis (timeout, conexión caída) se traduce a `AuthDegradedException`
(HTTP 503, `AUTH_DEGRADED`, "retry after 30s") en vez de dejar pasar el
request sin límite. Se prioriza no perder la protección anti-abuso por sobre
la disponibilidad del flujo de activación.

## Consecuencias

### Positivas

* El script Lua atómico cierra la ventana de carrera que un
  check-then-increment en dos pasos dejaría abierta bajo concurrencia.
* El límite dual (email + cliente) cubre los dos patrones de abuso más
  probables sin necesitar dos adaptadores separados.
* Fail-closed evita que una caída de Redis se traduzca en abuso ilimitado
  del flujo de activación.

### Negativas / Deuda Técnica

* **Fail-closed también degrada disponibilidad**: si Redis cae, todo el
  flujo de registro/reenvío de activación queda bloqueado con 503, no sólo
  el rate limiting. Es un tradeoff deliberado (seguridad por sobre
  disponibilidad) que vale la pena revisar si Redis se vuelve menos
  confiable que el propio flujo de activación.
* **Ventana fija, no deslizante**: un actor puede concentrar sus intentos
  justo antes y después del límite de la ventana (ej. 3 intentos a los 14:59
  y 3 más a los 15:01) y efectivamente duplicar el throughput permitido en
  el borde. Una ventana deslizante (sliding window log/counter) lo evitaría
  a costa de más complejidad en el script Lua.

### Riesgos y Reversibilidad

* **Riesgo Principal:** una degradación prolongada de Redis bloquea por
  completo el registro y el reenvío de activación (ver fail-closed arriba).
* **Plan de Mitigación:** alertar sobre `AUTH_DEGRADED` en el flujo de
  activación como señal de incidente de Redis, no sólo de rate limiting
  legítimo.
* **Reversibilidad:** alta — los límites y la ventana son configurables por
  variable de entorno sin cambios de código; cambiar a ventana deslizante
  requeriría reemplazar sólo el script Lua, sin tocar el contrato del
  puerto.

## Diagrama: flujo de `consume()`

```mermaid
sequenceDiagram
    participant UC as UseCase<br/>(Register / Resend)
    participant Port as RedisActivationRateLimitPort
    participant Redis as Redis (EVAL Lua)

    UC->>Port: consume(emailFingerprint, clientFingerprint)
    Port->>Port: validar fingerprints (SHA-256 hex)

    Port->>Redis: EVAL CONSUME_SCRIPT<br/>KEYS[email, client] ARGV[emailLimit, clientLimit, windowSecs]

    activate Redis
    Redis->>Redis: INCR email; EXPIRE si es el 1er hit
    Redis->>Redis: INCR client; EXPIRE si es el 1er hit
    Redis->>Redis: TTL email; TTL client
    alt algún contador > su límite
        Redis-->>Port: {0, max(ttlEmail, ttlClient)}
    else ambos dentro del límite
        Redis-->>Port: {1, 0}
    end
    deactivate Redis

    alt Redis responde
        alt allowed == 1
            Port-->>UC: RateLimitDecision.allowed()
        else allowed == 0
            Port-->>UC: RateLimitDecision.limited(ttl)
        end
    else RuntimeException (timeout, conexión caída)
        Port-->>UC: throw AuthDegradedException (503)
    end
```

## Referencias y Decisiones Relacionadas

* NIST SP 800-38D no aplica aquí (esa referencia es de
  [ADR-0032](0032-activation-delivery-cipher-nonce-policy.md), cifrado del
  token, no de rate limiting).
* `api/auth/src/main/java/com/menta/auth/infrastructure/activation/RedisActivationRateLimitPort.java`
* `api/auth/src/main/java/com/menta/auth/application/port/out/ActivationRateLimitPort.java`
* Complementa a: [ADR-0026](0026-redis-caffeine-strategy.md) (estrategia
  general de Redis/Caffeine; no cubre este mecanismo de rate limiting) y
  [ADR-0032](0032-activation-delivery-cipher-nonce-policy.md) (misma feature
  de activación, distinta preocupación de seguridad).
