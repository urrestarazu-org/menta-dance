# ADR-0026: Redis Reflejado y Caffeine de Caché

**Estado:** Aceptado

## Decisión

MySQL es la fuente de verdad de sesiones: `tokenVersion`, `jti` revocados y el
estado global `AUTH_DEGRADED`. Redis refleja blacklist y versión, además de rate
limits y locks de concurrencia. Se configura con `maxmemory-policy noeviction`;
Caffeine contiene sólo datos reconstruibles (catálogo, planes y lecturas no
sensibles). Redis no es caché de pagos/cupos.

Toda ruta autenticada consulta MySQL y exige Redis reconciliado. Si MySQL no está
disponible o Redis no refleja el estado, `AUTH_DEGRADED` bloquea login, refresh y
tráfico autenticado hasta reparación por outbox. Los locks de QR usan `SET key
nonce NX EX ttl` y Lua compare-and-delete para no liberar el lock de otro proceso.
No se utiliza Redis como bus ni RabbitMQ.

## Consecuencias

La capacidad y seguridad se preservan por sobre disponibilidad parcial. Alertas
operativas deben detectar MySQL/Redis no reconciliados, `AUTH_DEGRADED` y la
recuperación de outbox.
