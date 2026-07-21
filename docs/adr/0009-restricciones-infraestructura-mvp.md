# ADR-0009: Despliegue Consolidado de Microservicios (Requerimientos Base 8GB RAM)

**Estado:** Aceptado para restricciones; topología reemplazada por ADR-0020
**Fecha:** 2026-05-17
**Decisores:** Equipo de Arquitectura / DevOps

## Vigencia actual

Las restricciones de un único host de 8 GB y el control de recursos continúan aceptados. La topología de cinco aplicaciones Spring Boot, RabbitMQ y contenedores por dominio fue reemplazada por [ADR-0020](./0020-modular-monolith.md): el despliegue vigente ejecuta un JAR API, un JAR BFF y sus dependencias de infraestructura. Las tablas y ejemplos de contenedores de este ADR se conservan solo como historia y no deben implementarse.


## Contexto y Problema

Hemos decidido separar la plataforma en una arquitectura distribuida (Auth API, Virtual API, Physical API, Billing API, BFF, Gateway). A fin de equilibrar costos y rendimiento, y considerando que un entorno de 4GB/15GBSSD demostró ser altamente propenso a errores *Out Of Memory (OOM)*, el despliegue del MVP operará sobre un host con mejores capacidades pero aún consolidado:

* **Cloud Server único:** Ubuntu 24.04 LTS (64 bits) usando la imagen `Ubuntu2404-uefi-docker`.
* **Procesamiento:** 2 a 4 vCPUs.
* **Memoria RAM:** 8 GB.
* **Almacenamiento:** 30+ GB SSD.
* **Transferencia Mensual:** Tope de 1000 GB.

A pesar de tener 8GB de RAM, correr 5 servicios Spring Boot junto con MySQL 8.0, Redis, RabbitMQ y NGINX sigue siendo un desafío que requiere configuración deliberada para garantizar estabilidad en producción.

## Factores Clave (Decision Drivers)

* Maximizar el rendimiento sin exceder un solo servidor cloud (control de costos del MVP).
* Asignar memoria de forma equilibrada para prevenir caídas del sistema por OOM Killer de Linux.
* Mantenerse dentro del espacio SSD mediante rotación de logs e imágenes.
* Evitar sobrepasar los 1000GB de transferencia mensual.

## Opciones Consideradas

### Opción 1: Despliegue en Instancias Separadas (Kubernetes/Varios Servidores)

* **Pros:** Aislamiento total y escalabilidad independiente natural.
* **Contras:** Aumento drástico de costos de infraestructura en el Día 1. No justificado para los volúmenes del MVP inicial.

### Opción 2: Múltiples Contenedores en un Solo Servidor de 8GB RAM

* **Descripción:** Implementar límites razonables de recursos para cada contenedor en Docker Compose y afinar las JVMs.
* **Pros:** Permite probar el diseño de microservicios sin excesivos costos de servidor y asegura márgenes operativos seguros en RAM y disco.
* **Contras:** En picos muy bruscos de tráfico (ej. miles de sesiones simultáneas), el servidor único podría mostrar latencias debido a CPU compartida.

## Decisión

Elegimos **Opción 2: Múltiples Contenedores en un Solo Servidor (8GB RAM)** con afinamiento equilibrado de la JVM y bases de datos.

## Justificación y Plan de Mitigación (Rationale)

Con 8GB de memoria total, hay suficiente holgura técnica, pero implementaremos las siguientes configuraciones obligatorias para maximizar la estabilidad.

## Especificaciones Técnicas

### Distribución de Memoria (8GB Totales)

| Componente | Memoria Asignada | Configuración |
|------------|------------------|---------------|
| **Auth API** | 512 MB | `-Xmx512m -Xms256m` |
| **Virtual API** | 512 MB | `-Xmx512m -Xms256m` |
| **Physical API** | 512 MB | `-Xmx512m -Xms256m` |
| **Billing API** | 512 MB | `-Xmx512m -Xms256m` |
| **BFF** | 512 MB | `-Xmx512m -Xms256m` |
| **MySQL 8.0** | 1024 MB | `innodb_buffer_pool_size=1G` |
| **Redis** | 256 MB | `maxmemory 256mb` |
| **RabbitMQ** | 512 MB | `vm_memory_high_watermark.relative=0.4` |
| **NGINX** | 64 MB | Default |
| **Sistema Operativo** | 512 MB | Buffer del kernel |
| **Total Asignado** | **5408 MB** | ~5.3 GB |
| **Margen de Seguridad** | **2688 MB** | ~2.6 GB (~33%) |

### Configuración Docker Compose

```yaml
services:
  auth-api:
    image: menta/auth-api:latest
    environment:
      - JAVA_OPTS=-Xmx512m -Xms256m -XX:+UseG1GC -XX:MaxGCPauseMillis=200
    deploy:
      resources:
        limits:
          memory: 600M
        reservations:
          memory: 300M
    logging:
      driver: "json-file"
      options:
        max-size: "50m"
        max-file: "3"

  # Repetir para virtual-api, physical-api, billing-api, bff...

  mysql:
    image: mysql:8.0
    command: >
      --innodb-buffer-pool-size=1G
      --max-connections=100
      --innodb-log-file-size=256M
    deploy:
      resources:
        limits:
          memory: 1200M

  redis:
    image: redis:7-alpine
    command: redis-server --maxmemory 256mb --maxmemory-policy volatile-lru
    deploy:
      resources:
        limits:
          memory: 300M

  rabbitmq:
    image: rabbitmq:3-management-alpine
    environment:
      - RABBITMQ_VM_MEMORY_HIGH_WATERMARK=0.4
    deploy:
      resources:
        limits:
          memory: 600M
```

### Gestión de Almacenamiento (SSD - 30+ GB)

| Componente | Límite | Política de Rotación |
|------------|--------|---------------------|
| Logs Docker | 50MB x 3 archivos por servicio | Automática |
| MySQL binlogs | 7 días | `expire_logs_days=7` |
| Imágenes Docker antiguas | N/A | Cron semanal: `docker image prune -a -f` |
| RabbitMQ queues | 7 días TTL en DLQ | Configurado en ADR-0003 |

### Tope de Transferencia (1000 GB mensuales)

* **Video Streaming:** Los videos seguirán alojados y despachados **exclusivamente a través de Bunny.net CDN**. El servidor (NGINX) nunca debe emitir un stream de video.
* **Estimación de consumo:**
  * API JSON: ~1KB promedio por request
  * 1.67 RPS *86400 seg/día* 30 días * 1KB = ~4.3 GB/mes
  * **Margen disponible:** >995 GB para assets estáticos y picos.

### Alertas y Monitoreo

```yaml
# Alertas recomendadas en Grafana Cloud
alerts:
  - name: MemoryHighUsage
    condition: container_memory_usage_bytes > 0.85 * container_spec_memory_limit_bytes
    for: 5m
    severity: warning

  - name: DiskSpaceLow
    condition: node_filesystem_avail_bytes < 5GB
    for: 10m
    severity: critical

  - name: OOMKillDetected
    condition: increase(container_oom_events_total[5m]) > 0
    severity: critical
```

## Consecuencias

### Positivas

* El sistema contará con suficiente memoria (~33% de margen) para rendir fluidamente en todos los frentes.
* Se logra escalar la arquitectura sin sacrificar la viabilidad financiera del proyecto en etapas iniciales.
* Configuración explícita y documentada de recursos.

### Negativas / Deuda Técnica

* **Punto Único de Fallo (SPOF):** Aunque los servicios están distribuidos, el hardware subyacente sigue siendo uno. Si el servidor se apaga, toda la plataforma se detiene.
* **Monitoreo limitado:** Por ser un solo nodo, se recomiendan agentes de monitoreo externos ligeros antes que un clúster de ElasticSearch propio.

### Implicaciones de Costos

* Costo estimado del servidor: $20-40 USD/mes (dependiendo del proveedor).
* Sin costos adicionales de infraestructura para el MVP.

### Riesgos y Reversibilidad

* **Riesgo Principal:** Picos de tráfico inesperados pueden saturar el servidor único.
* **Plan de Mitigación:** Monitoreo proactivo con alertas en Grafana Cloud. Si el tráfico crece, escalar verticalmente (16GB RAM) o migrar a arquitectura multi-nodo.
* **Reversibilidad:** Alta. Docker Compose permite migrar los contenedores a múltiples hosts con Docker Swarm o Kubernetes con cambios mínimos.

## Referencias y Decisiones Relacionadas

* **Topología reemplazada por:** [ADR-0020](./0020-modular-monolith.md) - Un JAR API y un JAR BFF
* **Complementa a:** [ADR-0004](./0004-schemas-separados-vs-db-separadas.md) - MySQL único con esquemas separados
* **Complementa a:** [ADR-0007](./0007-cache-redis-vs-local.md) - Redis con límite de 256MB
* **Complementa a:** [ADR-0010](./0010-centralizacion-pagos-billing-api.md) - Billing API como quinto servicio
* **Complementa a:** [ADR-0013](./0013-observabilidad-otel-grafana-cloud.md) - Monitoreo con Grafana Cloud
* **Docker Resource Constraints:** <https://docs.docker.com/config/containers/resource_constraints/>
