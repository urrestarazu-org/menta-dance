# Estimación de Costos del MVP

La topología vigente utiliza un JAR API, un JAR BFF, MySQL, Redis y
observabilidad. No incluye microservicios ni broker.

| Componente | Uso estimado |
|---|---|
| VPS/compute | API, BFF y proxy |
| MySQL 8.4 | schema único `menta`, backups/PITR según proveedor |
| Redis 7 | blacklist, locks y rate limiting; `noeviction` |
| GHCR y GitHub Releases | imágenes versionadas por digest y JARs adjuntos a cada release |
| Grafana/Loki/OTel | logs y trazas del MVP |

El proveedor y el costo del servidor se decidieron en
[ADR-0045](adr/0045-despliegue-vps-donweb.md) (VPS de DonWeb con Docker Compose).
La estimación no es un SLA: pagos se concilian manualmente ante incidente de BD.
