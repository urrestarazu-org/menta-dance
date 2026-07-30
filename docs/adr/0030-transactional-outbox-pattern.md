# ADR-0030: Transactional Outbox Pattern

**Estado:** Aceptado

## Contexto

Los sistemas distribuidos enfrentan el problema de la **consistencia dual**: cuando una
operación de negocio requiere tanto mutar el estado local (base de datos) como notificar
a otros sistemas (eventos, mensajes), surge un dilema:

1. **Commit primero, publicar después**: Si el commit pasa pero la publicación falla,
   el evento se pierde y los consumidores nunca se enteran del cambio.

2. **Publicar primero, commit después**: Si la publicación pasa pero el commit falla,
   los consumidores reciben un evento sobre un cambio que nunca ocurrió.

3. **Two-Phase Commit (2PC)**: Coordinación distribuida que garantiza atomicidad, pero
   introduce latencia significativa, puntos únicos de fallo, y complejidad operacional.

En el contexto de Menta Dance, el módulo `auth` necesita:
- Persistir tokens de refresh en MySQL
- Notificar eventos (`auth.AuthUserLoggedIn`, `auth.RefreshRevoked`, etc.) para que
  otros módulos puedan reaccionar (ej: blacklist de tokens, auditoría)

## Decisión

Adoptamos el **Transactional Outbox Pattern** con procesamiento interno.

### El Patrón

```
┌─────────────────────────────────────────────────────────────────┐
│  Use Case (ej: LoginUseCaseImpl)                                │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  @Transactional                                           │  │
│  │  BEGIN TRANSACTION                                        │  │
│  │    1. refreshTokenRepository.save(token)    ← dominio     │  │
│  │    2. outboxAppender.append(event)          ← outbox      │  │
│  │  COMMIT                                                   │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                 │
│  Garantía: ambas escrituras comparten el mismo COMMIT.          │
│  Si falla cualquiera, se hace ROLLBACK de todo.                 │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼ (asíncrono, después del commit)
┌─────────────────────────────────────────────────────────────────┐
│  Outbox Reconciler (OutboxBlacklistReconciler)                  │
│                                                                 │
│  @Scheduled(fixedDelay = 5000)                                  │
│  1. SELECT * FROM common_outbox_events WHERE status = 'PENDING' │
│  2. Para cada evento:                                           │
│     a. Procesar (ej: agregar jti a blacklist in-memory)         │
│     b. UPDATE status = 'PROCESSED', processed_at = NOW()        │
│  3. Retry con backoff exponencial si falla                      │
└─────────────────────────────────────────────────────────────────┘
```

### Componentes Clave

| Componente | Capa | Responsabilidad |
|------------|------|-----------------|
| `OutboxAppender` | application/port/out | Puerto de salida que define el contrato |
| `OutboxJpaAppender` | infrastructure/outbox | Implementación JPA que escribe en `common_outbox_events` |
| `OutboxRowJpaEntity` | infrastructure/persistence | Entidad JPA mapeada a la tabla outbox |
| `OutboxBlacklistReconciler` | api:app | Worker que procesa eventos pendientes |
| `OutboxStatus` | shared/outbox | Enum con estados del ciclo de vida |

### Ciclo de Vida de un Evento

```
PENDING → PROCESSED
    │
    └──(si falla)──→ PENDING (retry con backoff)
                         │
                         └──(max retries)──→ FAILED (requiere intervención)
```

### Esquema de la Tabla

```sql
CREATE TABLE common_outbox_events (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,  -- PK para write performance
    event_id      CHAR(26) NOT NULL UNIQUE,           -- ULID, idempotencia productor
    event_type    VARCHAR(100) NOT NULL,              -- ej: "auth.AuthUserLoggedIn"
    aggregate_id  VARCHAR(64) NOT NULL,               -- ej: jti del token
    payload       JSON NOT NULL,                      -- cuerpo serializado
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts      INT NOT NULL DEFAULT 0,
    last_error    VARCHAR(1000),
    next_retry_at DATETIME(6),
    created_at    DATETIME(6) NOT NULL,
    processed_at  DATETIME(6),

    INDEX idx_status_retry (status, next_retry_at)
);
```

## Justificación

### ¿Por qué Outbox y no publicación directa?

| Enfoque | Consistencia | Complejidad | Latencia | Recuperación |
|---------|--------------|-------------|----------|--------------|
| Publicar directo | Eventual (con pérdida) | Baja | Baja | Manual |
| 2PC | Fuerte | Alta | Alta | Automática |
| **Outbox** | **Eventual (sin pérdida)** | **Media** | **Baja** | **Automática** |

El outbox garantiza **at-least-once delivery**: el evento puede procesarse más de una
vez si el reconciler falla entre procesar y marcar como PROCESSED, pero nunca se pierde.

### ¿Por qué worker interno y no message broker?

Para el MVP, un worker interno (`@Scheduled`) es suficiente:

- **Sin infraestructura adicional**: No requiere RabbitMQ, Kafka, o Redis Streams
- **Debugging simple**: Los eventos están en MySQL, consultables con SQL
- **Suficiente para monolito**: Los "consumidores" son otros módulos en el mismo proceso

Cuando escale (múltiples instancias, alta frecuencia de eventos), migrar a un broker
externo será transparente: solo cambia el reconciler, no los productores.

## Consecuencias

### Positivas

- **Consistencia garantizada**: El evento existe si y solo si la mutación se commitió
- **Recuperación automática**: El reconciler reintenta eventos fallidos
- **Auditoría gratis**: `common_outbox_events` es un log de todos los eventos del sistema
- **Testeable**: El puerto `OutboxAppender` es mockeable en tests unitarios

### Negativas

- **Latencia de propagación**: Los consumidores ven el evento después del siguiente tick
  del reconciler (configurable, default 5s)
- **Tabla crece**: Requiere política de retención (cleanup de eventos PROCESSED antiguos)
- **Idempotencia requerida**: Los consumidores deben tolerar eventos duplicados

### Neutrales

- La complejidad se concentra en infraestructura, no en dominio/aplicación

## Referencias

- [Microservices.io - Transactional Outbox](https://microservices.io/patterns/data/transactional-outbox.html)
- [Chris Richardson - Pattern: Transactional Outbox](https://chrisrichardson.net/post/microservices/2019/03/28/transactional-outbox.html)
- ADR-0027: MySQL Único y Flyway Forward-Only (estrategia de base de datos)
