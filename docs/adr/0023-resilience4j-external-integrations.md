# ADR-0023: Resiliencia para Integraciones Externas

**Estado:** Aceptado

## Decisión

Resilience4j se limita a integraciones externas y nunca conecta módulos internos.
Se permiten timeout, circuit breaker y rate limiter cuando el efecto es seguro.

La creación de un pago en un proveedor externo no tiene retry automático: un
timeout genera `PENDING/RECONCILIATION_REQUIRED`, conserva idempotency key/evidencia y se
consulta o resuelve administrativamente. Los reintentos sólo son admisibles para
lecturas idempotentes o para el worker de inbox/outbox ya persistido.

No se usa RabbitMQ ni colas externas en el MVP; el outbox MySQL lo procesa un
worker interno del monolito.
