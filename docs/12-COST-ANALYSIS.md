# Estimación de Costos del MVP

La topología vigente utiliza un JAR API, un JAR BFF, MySQL, Redis y
observabilidad. No incluye microservicios ni broker.

| Componente | Uso estimado |
|---|---|
| VPS/compute | API, BFF y proxy |
| MySQL 8.0 | schema único `menta`, backups/PITR según proveedor |
| Redis 7 | blacklist, locks y rate limiting; `noeviction` |
| GHCR/GitHub Packages | JARs e imágenes versionadas |
| Grafana/Loki/OTel | logs y trazas del MVP |

La selección de proveedor y presupuesto se decide antes de la primera release.
La estimación no es un SLA: pagos se concilian manualmente ante incidente de BD.
