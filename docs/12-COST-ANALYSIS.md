# Análisis de Costos de Infraestructura MVP

[← Volver al índice](./README.md) | [← Apéndices](./11-APPENDICES.md)

---

**Fecha del Análisis:** Julio de 2026
**Arquitectura:** Monolito modular ([ADR-0020](./adr/0020-modular-monolith.md))
**Moneda:** USD (estimaciones internacionales)

> [!NOTE]
> Este documento reemplaza el análisis anterior basado en microservicios (5 JVMs
> + RabbitMQ). La arquitectura vigente es un monolito modular con 1 JAR API +
> 1 JAR BFF.

## 1. Componentes de Infraestructura MVP

| Componente | Tecnología | RAM estimada | Notas |
|------------|------------|--------------|-------|
| **API** | Spring Boot 3 (`:api:app`) | 512 MB - 1 GB | Todos los módulos en un JAR |
| **BFF** | Spring Boot 3 (`:bff`) | 256 MB - 512 MB | Thymeleaf + assets |
| **MySQL** | 8.0 | 512 MB - 1 GB | Buffer pool configurable |
| **Redis** | 7.x standalone | 128 MB - 256 MB | Blacklist JWT, rate limiting |
| **Nginx** | 1.25.x | 64 MB | Reverse proxy, SSL |
| **SO** | Ubuntu 24.04 LTS | 256 MB | Base mínima |

**Total RAM estimada (idle):** ~1.7 GB
**Total RAM estimada (carga):** ~3-4 GB

## 2. Opciones de Hosting

### Opción A: VPS Económico (Recomendado MVP)

| Proveedor | Plan | Specs | Precio/mes |
|-----------|------|-------|------------|
| **Hetzner** | CX22 | 2 vCPU, 4 GB RAM, 40 GB SSD | ~$5.50 USD |
| **DigitalOcean** | Basic | 2 vCPU, 4 GB RAM, 80 GB SSD | ~$24 USD |
| **Vultr** | Cloud Compute | 2 vCPU, 4 GB RAM, 80 GB SSD | ~$24 USD |
| **Contabo** | VPS S | 4 vCPU, 8 GB RAM, 200 GB SSD | ~$7 USD |

**Recomendación:** Hetzner CX22 o Contabo VPS S para MVP.

### Opción B: Cloud Managed (Escalamiento futuro)

| Proveedor | Servicio | Precio estimado/mes |
|-----------|----------|---------------------|
| **Railway** | 2 apps + MySQL + Redis | ~$20-30 USD |
| **Render** | 2 web services + managed DB | ~$40-50 USD |
| **Fly.io** | 2 machines + Postgres | ~$15-25 USD |

## 3. Servicios Externos (Gratuitos/Bajo costo)

| Servicio | Uso | Costo MVP |
|----------|-----|-----------|
| **Bunny.net CDN** | Streaming de videos | ~$0.01/GB (pay-as-you-go) |
| **Grafana Cloud** | Observabilidad (OTel) | Gratis (10k series) |
| **SonarCloud** | Análisis estático | Gratis (open source) |
| **GitHub Actions** | CI/CD | Gratis (2000 min/mes) |
| **Mercado Pago** | Procesamiento de pagos | Comisión por transacción |

## 4. Comparativa: Microservicios vs Monolito

| Aspecto | Microservicios (anterior) | Monolito (actual) |
|---------|---------------------------|-------------------|
| JVMs | 5 (BFF + 4 APIs) | 2 (API + BFF) |
| RAM mínima | 8 GB | 4 GB |
| RabbitMQ | Requerido | No necesario |
| Redis | Cluster | Standalone |
| Costo infra | ~$20-40 USD/mes | **~$5-15 USD/mes** |
| Complejidad ops | Alta | Baja |

**Ahorro estimado:** 50-70% en costos de infraestructura.

## 5. Configuración Recomendada MVP

```yaml
# docker-compose.yml (producción)
services:
  api:
    image: menta/api:latest
    deploy:
      resources:
        limits:
          memory: 1G
        reservations:
          memory: 512M
    environment:
      - JAVA_OPTS=-Xmx768m -Xms256m

  bff:
    image: menta/bff:latest
    deploy:
      resources:
        limits:
          memory: 512M
        reservations:
          memory: 256M
    environment:
      - JAVA_OPTS=-Xmx384m -Xms128m

  mysql:
    image: mysql:8.0
    deploy:
      resources:
        limits:
          memory: 1G
    command: --innodb-buffer-pool-size=256M

  redis:
    image: redis:7-alpine
    deploy:
      resources:
        limits:
          memory: 256M
    command: redis-server --maxmemory 128mb --maxmemory-policy allkeys-lru

  nginx:
    image: nginx:alpine
    deploy:
      resources:
        limits:
          memory: 64M
```

## 6. Resumen de Costos MVP

| Concepto | Costo mensual |
|----------|---------------|
| VPS (Hetzner CX22) | ~$5.50 USD |
| Dominio (.com) | ~$1 USD (prorrateado) |
| SSL (Let's Encrypt) | Gratis |
| Bunny CDN (estimado 50 GB) | ~$0.50 USD |
| **Total MVP** | **~$7 USD/mes** |

## 7. Escalamiento Futuro

Cuando el tráfico supere 500 RPM o 1000 usuarios activos:

1. **Vertical:** Subir a VPS de 8 GB RAM (~$10-15/mes)
2. **Horizontal:** Agregar segundo nodo API + load balancer
3. **Managed:** Migrar a Railway/Render para reducir ops

---

## Referencias

* [ADR-0020](./adr/0020-modular-monolith.md) — Monolito modular
* [ADR-0024](./adr/0024-technology-baseline.md) — Technology baseline
* [Hetzner Cloud Pricing](https://www.hetzner.com/cloud)
