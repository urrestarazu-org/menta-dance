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
- **Idempotencia requerida**: Los consumidores deben tolerar eventos duplicados

### Mitigaciones Implementadas

- **Retención automática**: Un job diario (`cleanupProcessedEvents`) borra eventos
  COMPLETED más antiguos que el período de retención (default: 7 días). Configurable
  via `auth.outbox.retention-days` y `auth.outbox.cleanup-cron`. Eventos FAILED se
  mantienen para inspección manual.

### Neutrales

- La complejidad se concentra en infraestructura, no en dominio/aplicación

## Retención y Limpieza de Eventos

### Problema

Sin una política de retención, `common_outbox_events` crece indefinidamente. Un sistema
con 1000 eventos/día alcanzaría ~365k filas/año, impactando:

- **Performance de SELECT**: El índice `idx_status_retry` filtra PENDING, pero scans
  completos (ej: monitoreo, debugging) degradan con millones de filas.
- **Backups**: La tabla ocupa espacio en snapshots y replicas.
- **Storage**: MySQL InnoDB mantiene espacio incluso después de DELETE sin OPTIMIZE TABLE.

### Solución Implementada

**Job diario de limpieza** (`OutboxBlacklistReconciler.cleanupProcessedEvents`):

```java
@Scheduled(cron = "${auth.outbox.cleanup-cron:0 0 3 * * *}")
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void cleanupProcessedEvents() {
    Instant cutoff = Instant.now().minus(Duration.ofDays(retentionDays));
    long deleted = repository.deleteByStatusAndProcessedAtBefore(
        OutboxStatus.COMPLETED, cutoff);
    if (deleted > 0) {
        log.info("Outbox cleanup: deleted {} COMPLETED events older than {} days",
            deleted, retentionDays);
    }
}
```

**Query SQL ejecutado** (Spring Data JPA genera):

```sql
DELETE FROM common_outbox_events
WHERE status = 'COMPLETED'
  AND processed_at < ?  -- cutoff = now - retention_days
```

### Configuración

| Propiedad | Default | Descripción |
|-----------|---------|-------------|
| `auth.outbox.retention-days` | `7` | Días de retención para eventos COMPLETED |
| `auth.outbox.cleanup-cron` | `0 0 3 * * *` | Expresión cron (3:00 AM diario) |

**Ejemplo de override** (`application.yml`):

```yaml
auth:
  outbox:
    retention-days: 30        # 30 días de auditoría
    cleanup-cron: "0 30 2 * * *"  # 2:30 AM
```

### Política de Eventos FAILED

Los eventos con `status = 'FAILED'` **NO** se borran automáticamente. Razones:

1. **Post-mortem**: Investigar por qué falló el side-effect (ej: Redis caído por 3 días)
2. **Re-procesamiento manual**: Después de arreglar el issue, marcar como PENDING para retry
3. **Alerting**: Un contador de FAILED creciente indica un problema operacional

**Cleanup manual** (cuando sea necesario):

```sql
-- Revisar primero
SELECT id, event_type, last_error, attempts, created_at
FROM common_outbox_events
WHERE status = 'FAILED'
ORDER BY created_at DESC
LIMIT 100;

-- Borrar después de investigar
DELETE FROM common_outbox_events
WHERE status = 'FAILED'
  AND created_at < DATE_SUB(NOW(), INTERVAL 90 DAY);
```

### Consideraciones Operacionales

**Window de retención**: 7 días balancean:
- ✅ Auditoría reciente (útil para debugging de producción)
- ✅ Storage manejable (7k filas/semana con 1k eventos/día)
- ❌ Eventos antiguos no auditables (logs de aplicación + APM son la fuente de verdad)

**Horario del job**: 3:00 AM minimiza conflictos con el reconciler de eventos, que corre
cada 5 segundos. El DELETE usa un índice (`status, processed_at`) y es rápido incluso
con 100k filas COMPLETED.

**Lock contention**: Spring Data JPA usa `DELETE FROM ... WHERE`, que lockea las filas
a borrar. Con ~7k filas/semana, el lock es breve (<100ms). Si crece, considerar batch
delete:

```sql
-- Alternativa batch (manual, si el job tarda >1s)
DELETE FROM common_outbox_events
WHERE id IN (
    SELECT id FROM common_outbox_events
    WHERE status = 'COMPLETED' AND processed_at < ?
    LIMIT 1000
);
-- Repetir hasta affected_rows = 0
```

## Referencias

- [Microservices.io - Transactional Outbox](https://microservices.io/patterns/data/transactional-outbox.html)
- [Chris Richardson - Pattern: Transactional Outbox](https://chrisrichardson.net/post/microservices/2019/03/28/transactional-outbox.html)
- ADR-0027: MySQL Único y Flyway Forward-Only (estrategia de base de datos)
