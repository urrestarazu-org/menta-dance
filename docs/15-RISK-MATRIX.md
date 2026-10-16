# Matriz de Riesgos del MVP

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Redis indisponible | rutas autenticadas bloqueadas | `noeviction`, alertas, runbook y recuperación de outbox |
| Resultado incierto de pago | acceso/cupo incorrecto | `PENDING/RECONCILIATION_REQUIRED`, inbox idempotente y revisión ADMIN |
| Incidente MySQL | pérdida de hasta RPO acordado | backups/PITR y conciliación manual de pagos |
| Migración Flyway fallida | release detenida | migraciones forward-only e intervención manual |
| Violación modular | acoplamiento futuro | ArchUnit y revisión de migraciones |
| Health check post-release fallido | indisponibilidad/regresión | rollback manual al digest anterior (se vuelve a ejecutar el deploy); si la migración es incompatible, se restaura el backup previo (ADR-0018, ADR-0045) |

Los riesgos se revisan antes de cada release; cambios de estrategia requieren ADR.
