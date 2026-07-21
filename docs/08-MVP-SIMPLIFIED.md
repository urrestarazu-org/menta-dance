# MVP Simplificado

[← Volver al índice](./README.md) | [← Consideraciones Técnicas](./07-TECHNICAL-CONSIDERATIONS.md)

> [!CAUTION]
> **HISTÓRICO — PENDIENTE DE ALINEACIÓN. NO IMPLEMENTAR.** Este documento
> conserva el diseño MVP anterior y puede describir microservicios, mensajería o
> despliegues incompatibles. La autoridad vigente está en
> [02-ARCHITECTURE.md](./02-ARCHITECTURE.md) y ADR-0020/0021. Sus contratos
> funcionales serán revisados en fase 2.

---

## 1. Contexto del MVP

### 1.1 Escala Objetivo

| Métrica | Valor MVP | Crecimiento Año 1 |
|---------|-----------|-------------------|
| **Usuarios activos** | 200 | 500 |
| **Requests/minuto** | 100 RPM | 300 RPM |
| **Requests/segundo** | ~1.67 RPS | ~5 RPS |
| **Concurrencia pico** | 20-30 usuarios | 50-80 usuarios |
| **Almacenamiento videos** | 50 GB | 200 GB |
| **Transacciones/mes** | ~200 | ~500 |

### 1.2 Principios del MVP

```
┌─────────────────────────────────────────────────────────────────┐
│                    PRINCIPIOS MVP                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ✅ SIMPLICIDAD antes que escalabilidad                        │
│  ✅ FUNCIONALIDAD antes que optimización                       │
│  ✅ MONITOREO antes que auto-scaling                           │
│  ✅ MANUAL antes que automatizado (cuando sea viable)          │
│  ✅ SINGLE INSTANCE antes que cluster                          │
│                                                                 │
│  ❌ NO: Kubernetes, service mesh, event sourcing               │
│  ❌ NO: Multi-región, auto-scaling, blue-green deploys         │
│  ❌ NO: Microservicios distribuidos sin necesidad              │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. Arquitectura MVP

### 2.1 Diagrama de Contenedores (Simplificado)

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         SERVIDOR VPS                                     │
│                      (4 vCPU, 8GB RAM)                                  │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌─────────────┐                                                       │
│  │   NGINX     │◀── HTTPS (443) ◀── Internet                          │
│  │  Reverse    │                                                       │
│  │   Proxy     │                                                       │
│  └──────┬──────┘                                                       │
│         │                                                              │
│         ▼                                                              │
│  ┌──────────────────────────────────────────────────────────────┐     │
│  │                    Docker Compose                             │     │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐          │     │
│  │  │  Auth API   │  │ Virtual API │  │Physical API │          │     │
│  │  │   :8081     │  │   :8082     │  │   :8083     │          │     │
│  │  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘          │     │
│  │         │                │                │                  │     │
│  │  ┌──────┴────────────────┴────────────────┴──────┐          │     │
│  │  │                                               │          │     │
│  │  │  ┌─────────────┐  ┌─────────────┐            │          │     │
│  │  │  │ Billing API │  │  BFF Web    │            │          │     │
│  │  │  │   :8084     │  │   :8080     │            │          │     │
│  │  │  └─────────────┘  └─────────────┘            │          │     │
│  │  │                                               │          │     │
│  │  └───────────────────────────────────────────────┘          │     │
│  │                                                              │     │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐          │     │
│  │  │   MySQL     │  │   Redis     │  │  RabbitMQ   │          │     │
│  │  │   :3306     │  │   :6379     │  │   :5672     │          │     │
│  │  │             │  │  (Opcional) │  │  (Opcional) │          │     │
│  │  └─────────────┘  └─────────────┘  └─────────────┘          │     │
│  └──────────────────────────────────────────────────────────────┘     │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Componentes MVP vs Full

| Componente | MVP | Full (Futuro) |
|------------|-----|---------------|
| **Auth API** | ✅ Single instance | Cluster + Redis sessions |
| **Virtual API** | ✅ Single instance | Cluster + CDN cache |
| **Physical API** | ✅ Single instance | Cluster + real-time |
| **Billing API** | ✅ Single instance | Cluster + queue |
| **BFF Web** | ✅ Spring Boot + Thymeleaf | Puede migrar a SPA |
| **Android App** | ✅ Native (Kotlin) | Flutter (multi-platform) |
| **MySQL** | ✅ Single instance | Primary-Replica |
| **Redis** | ⚠️ Opcional (in-memory cache) | Cluster |
| **RabbitMQ** | ⚠️ Opcional (HTTP sync) | Cluster |
| **CDN** | ✅ Bunny.net | Multi-CDN |
| **Load Balancer** | ❌ NGINX solo | AWS ALB / Cloud LB |

### 2.3 Qué NO incluye el MVP

```
┌─────────────────────────────────────────────────────────────────┐
│              EXCLUIDO DEL MVP (Fase 2+)                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  INFRAESTRUCTURA:                                              │
│  ❌ Kubernetes / Docker Swarm                                  │
│  ❌ Service Mesh (Istio, Linkerd)                              │
│  ❌ Auto-scaling                                               │
│  ❌ Multi-región                                               │
│  ❌ Blue-green / Canary deployments                            │
│                                                                 │
│  ARQUITECTURA:                                                  │
│  ❌ Event Sourcing                                             │
│  ❌ CQRS completo                                              │
│  ❌ Saga Orchestrator                                          │
│  ❌ API Gateway dedicado (Kong, Ambassador)                    │
│                                                                 │
│  OBSERVABILIDAD:                                                │
│  ❌ Distributed tracing (Jaeger, Zipkin)                       │
│  ❌ APM (DataDog, New Relic)                                   │
│  ❌ Log aggregation (ELK, Splunk)                              │
│                                                                 │
│  SEGURIDAD AVANZADA:                                            │
│  ❌ mTLS entre servicios                                       │
│  ❌ Vault para secrets                                         │
│  ❌ WAF dedicado                                               │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 3. Sincronización sin Event Bus

### 3.1 Estrategia HTTP Sync

Para el MVP, en lugar de RabbitMQ, usamos **llamadas HTTP síncronas** entre servicios:

```
┌─────────────────────────────────────────────────────────────────┐
│              SINCRONIZACIÓN MVP (HTTP)                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────┐         HTTP         ┌─────────────┐          │
│  │   BFF Web   │ ──────────────────▶  │  Auth API   │          │
│  │             │ ◀──────────────────  │             │          │
│  └─────────────┘                      └─────────────┘          │
│         │                                                       │
│         │  HTTP (paralelo)                                      │
│         ▼                                                       │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐             │
│  │ Virtual API │  │Physical API │  │ Billing API │             │
│  └─────────────┘  └─────────────┘  └─────────────┘             │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 Service-to-Service Communication

```java
// BFF llama a múltiples APIs en paralelo usando RestClient + Virtual Threads
@Service
public class DashboardAggregator {

    private final RestClient authClient;
    private final RestClient virtualClient;
    private final RestClient billingClient;

    // Se asume un Executor configurado con Java 21 Virtual Threads (Executors.newVirtualThreadPerTaskExecutor())
    private final Executor virtualThreadExecutor;

    public DashboardResponse getDashboard(String userId) {
        // Llamadas paralelas usando CompletableFuture respaldado por Virtual Threads
        CompletableFuture<UserProfile> userFuture = CompletableFuture.supplyAsync(
            () -> authClient.get()
                .uri("/api/v1/users/{id}", userId)
                .retrieve()
                .body(UserProfile.class),
            virtualThreadExecutor
        );

        CompletableFuture<List<CourseProgress>> coursesFuture = CompletableFuture.supplyAsync(
            () -> Arrays.asList(virtualClient.get()
                .uri("/api/v1/users/{id}/progress", userId)
                .retrieve()
                .body(CourseProgress[].class)),
            virtualThreadExecutor
        );

        CompletableFuture<SubscriptionStatus> subFuture = CompletableFuture.supplyAsync(
            () -> billingClient.get()
                .uri("/api/v1/users/{id}/subscription", userId)
                .retrieve()
                .body(SubscriptionStatus.class),
            virtualThreadExecutor
        );

        // Esperar todas las respuestas (bloqueo virtual, sin penalización de performance)
        CompletableFuture.allOf(userFuture, coursesFuture, subFuture).join();

        return new DashboardResponse(
            userFuture.join(),
            coursesFuture.join(),
            subFuture.join()
        );
    }
}
```

### 3.3 Scheduled Jobs para Sincronización

```java
// En lugar de eventos, usamos jobs programados
@Component
@RequiredArgsConstructor
public class SubscriptionSyncJob {

    private final SubscriptionRepository subscriptionRepository;
    private final NotificationService notificationService;

    /**
     * Revisa suscripciones próximas a vencer cada hora.
     */
    @Scheduled(cron = "0 0 * * * *") // Cada hora
    public void checkExpiringSubscriptions() {
        LocalDate warningDate = LocalDate.now().plusDays(7);

        List<Subscription> expiring = subscriptionRepository
            .findByStatusAndEndDateBefore(SubscriptionStatus.ACTIVE, warningDate);

        for (Subscription sub : expiring) {
            notificationService.sendExpirationWarning(sub);
        }

        log.info("Procesadas {} suscripciones próximas a vencer", expiring.size());
    }

    /**
     * Marca suscripciones vencidas cada 5 minutos.
     */
    @Scheduled(fixedRate = 300000) // 5 minutos
    public void expireSubscriptions() {
        LocalDate today = LocalDate.now();

        int expired = subscriptionRepository.expireOldSubscriptions(today);

        if (expired > 0) {
            log.info("Expiradas {} suscripciones", expired);
        }
    }
}
```

### 3.4 Migración a Event-Driven (Futuro)

Cuando el sistema crezca, la migración a eventos es sencilla:

```java
// ANTES (MVP): Llamada síncrona
@Service
public class PaymentServiceImpl implements PaymentService {

    @Override
    @Transactional
    public void confirmPayment(Long paymentId) {
        Payment payment = repository.findById(paymentId).orElseThrow();
        payment.setStatus(PaymentStatus.COMPLETED);
        repository.save(payment);

        // Llamada HTTP síncrona
        subscriptionClient.activateSubscription(payment.getSubscriptionId());
    }
}

// DESPUÉS (Full): Publicación de evento
@Service
public class PaymentServiceImpl implements PaymentService {

    private final EventPublisher eventPublisher;

    @Override
    @Transactional
    public void confirmPayment(Long paymentId) {
        Payment payment = repository.findById(paymentId).orElseThrow();
        payment.setStatus(PaymentStatus.COMPLETED);
        repository.save(payment);

        // Publicar evento (asíncrono)
        eventPublisher.publish(new PaymentCompleted(
            payment.getId(),
            payment.getSubscriptionId(),
            payment.getAmount()
        ));
    }
}
```

---

## 4. Infraestructura MVP

### 4.1 Requisitos de Hardware

| Componente | MVP (VPS) | Crecimiento (Cloud) |
|------------|-----------|---------------------|
| **Servidor App** | 4 vCPU, 8GB RAM | 2x (4 vCPU, 8GB) + LB |
| **MySQL** | 2 vCPU, 4GB RAM, 50GB SSD | RDS/CloudSQL (managed) |
| **Redis** | Embedded/1GB | ElastiCache/Memorystore |
| **Almacenamiento** | File System Local Seguro (Docker Volume) | S3/GCS + CDN |

### 4.2 Costos Estimados (USD/mes)

| Componente | MVP | Año 1 (500 users) | Año 2 (1000 users) |
|------------|-----|-------------------|---------------------|
| VPS/Cloud | $40-60 | $100-150 | $200-300 |
| MySQL Managed | $0 (incluido) | $30-50 | $50-100 |
| Redis Managed | $0 (in-memory) | $20-30 | $30-50 |
| CDN (Bunny.net) | $10-20 | $30-50 | $50-100 |
| Dominio + SSL | $15/año | $15/año | $15/año |
| **Total** | **$50-80** | **$180-280** | **$330-550** |

### 4.3 Docker Compose MVP

> [!WARNING]
> **OBSOLETO.** El docker-compose siguiente describe 5 contenedores de APIs
> separadas. La arquitectura vigente (ADR-0020) usa un único JAR API. Ver
> sección 4.4 para la configuración correcta.

```yaml
# docker-compose.yml (HISTÓRICO - NO USAR)
version: '3.8'

services:
  # ============================================
  # Base de Datos
  # ============================================
  mysql:
    image: mysql:8.0
    container_name: menta-mysql
    environment:
      MYSQL_ROOT_PASSWORD: ${DB_ROOT_PASSWORD}
      MYSQL_DATABASE: menta_auth
    volumes:
      - mysql_data:/var/lib/mysql
      - ./init-db.sql:/docker-entrypoint-initdb.d/init.sql
    ports:
      - "3306:3306"
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      timeout: 5s
      retries: 5

  # ============================================
  # APIs
  # ============================================
  auth-api:
    build:
      context: ./auth-api
      dockerfile: Dockerfile
    container_name: menta-auth-api
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - DB_HOST=mysql
      - DB_NAME=menta_auth
    ports:
      - "8081:8081"
    depends_on:
      mysql:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8081/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3

  virtual-api:
    build:
      context: ./virtual-api
      dockerfile: Dockerfile
    container_name: menta-virtual-api
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - DB_HOST=mysql
      - DB_NAME=menta_virtual
    ports:
      - "8082:8082"
    depends_on:
      mysql:
        condition: service_healthy

  physical-api:
    build:
      context: ./physical-api
      dockerfile: Dockerfile
    container_name: menta-physical-api
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - DB_HOST=mysql
      - DB_NAME=menta_physical
    ports:
      - "8083:8083"
    depends_on:
      mysql:
        condition: service_healthy

  billing-api:
    build:
      context: ./billing-api
      dockerfile: Dockerfile
    container_name: menta-billing-api
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - DB_HOST=mysql
      - DB_NAME=menta_billing
    ports:
      - "8084:8084"
    depends_on:
      mysql:
        condition: service_healthy

  bff-web:
    build:
      context: ./bff-web
      dockerfile: Dockerfile
    container_name: menta-bff-web
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - AUTH_API_URL=http://auth-api:8081
      - VIRTUAL_API_URL=http://virtual-api:8082
      - PHYSICAL_API_URL=http://physical-api:8083
      - BILLING_API_URL=http://billing-api:8084
    ports:
      - "8080:8080"
    depends_on:
      - auth-api
      - virtual-api
      - physical-api
      - billing-api

  # ============================================
  # Reverse Proxy
  # ============================================
  nginx:
    image: nginx:alpine
    container_name: menta-nginx
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf
      - ./nginx/ssl:/etc/nginx/ssl
    ports:
      - "80:80"
      - "443:443"
    depends_on:
      - bff-web

volumes:
  mysql_data:
```

### 4.4 Docker Compose Correcto (Monolito Modular)

```yaml
# docker-compose.yml (VIGENTE - ADR-0020)
version: '3.8'

services:
  # ============================================
  # Base de Datos
  # ============================================
  mysql:
    image: mysql:8.0
    container_name: menta-mysql
    environment:
      MYSQL_ROOT_PASSWORD: ${DB_ROOT_PASSWORD}
      MYSQL_DATABASE: menta_auth
    volumes:
      - mysql_data:/var/lib/mysql
      - ./init-db.sql:/docker-entrypoint-initdb.d/init.sql
    ports:
      - "3306:3306"
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      timeout: 5s
      retries: 5

  # ============================================
  # API (Monolito Modular - único JAR)
  # ============================================
  api:
    build:
      context: ./api/app
      dockerfile: Dockerfile
    container_name: menta-api
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - DB_HOST=mysql
      - JWT_SECRET=${JWT_SECRET}
      - HASHIDS_SALT=${HASHIDS_SALT}
    ports:
      - "8081:8081"
    depends_on:
      mysql:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8081/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3

  # ============================================
  # BFF Web (Thymeleaf)
  # ============================================
  bff:
    build:
      context: ./bff
      dockerfile: Dockerfile
    container_name: menta-bff
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - API_URL=http://api:8081
    ports:
      - "8080:8080"
    depends_on:
      - api

  # ============================================
  # Reverse Proxy
  # ============================================
  nginx:
    image: nginx:alpine
    container_name: menta-nginx
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf
      - ./nginx/ssl:/etc/nginx/ssl
    ports:
      - "80:80"
      - "443:443"
    depends_on:
      - bff
      - api

volumes:
  mysql_data:
```

### 4.5 Script de Inicialización BD

```sql
-- init-db.sql
-- Crear schemas para cada servicio

CREATE DATABASE IF NOT EXISTS menta_auth;
CREATE DATABASE IF NOT EXISTS menta_virtual;
CREATE DATABASE IF NOT EXISTS menta_physical;
CREATE DATABASE IF NOT EXISTS menta_billing;

-- Usuario de aplicación con permisos limitados
CREATE USER IF NOT EXISTS 'menta_app'@'%' IDENTIFIED BY '${DB_APP_PASSWORD}';

GRANT SELECT, INSERT, UPDATE, DELETE ON menta_auth.* TO 'menta_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON menta_virtual.* TO 'menta_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON menta_physical.* TO 'menta_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON menta_billing.* TO 'menta_app'@'%';

FLUSH PRIVILEGES;
```

---

## 5. Monitoreo MVP

### 5.1 Stack de Monitoreo Simplificado

```
┌─────────────────────────────────────────────────────────────────┐
│                   MONITOREO MVP                                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐             │
│  │  Actuator   │  │   Logs      │  │   UptimeBot │             │
│  │  /health    │  │   (file)    │  │   (externo) │             │
│  │  /metrics   │  │             │  │             │             │
│  └─────────────┘  └─────────────┘  └─────────────┘             │
│                                                                 │
│  ✅ Spring Boot Actuator (health, info, metrics)               │
│  ✅ Logs a archivo + rotación                                  │
│  ✅ UptimeRobot o similar (gratis)                             │
│  ✅ Alertas por email                                          │
│                                                                 │
│  ❌ NO: Prometheus, Grafana, ELK, APM                          │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 5.2 Configuración Actuator

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics
      base-path: /actuator
  endpoint:
    health:
      show-details: when_authorized
  health:
    db:
      enabled: true
    redis:
      enabled: true

info:
  app:
    name: ${spring.application.name}
    version: ${APP_VERSION:1.0.0}
    environment: ${SPRING_PROFILES_ACTIVE:dev}
```

### 5.3 Script de Health Check

```bash
#!/bin/bash
# health-check.sh

SERVICES=("auth-api:8081" "virtual-api:8082" "physical-api:8083" "billing-api:8084" "bff-web:8080")
ALERT_EMAIL="admin@mentavirtual.com"

for SERVICE in "${SERVICES[@]}"; do
    NAME="${SERVICE%%:*}"
    PORT="${SERVICE##*:}"

    STATUS=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:$PORT/actuator/health")

    if [ "$STATUS" != "200" ]; then
        echo "ALERTA: $NAME no responde (HTTP $STATUS)"
        echo "Servicio $NAME caído a las $(date)" | mail -s "ALERTA: $NAME DOWN" $ALERT_EMAIL
    else
        echo "OK: $NAME (HTTP $STATUS)"
    fi
done
```

### 5.4 Logging Simplificado

```yaml
# application-logging.yml
logging:
  level:
    root: INFO
    com.menta: DEBUG
  file:
    name: /var/log/menta/${spring.application.name}.log
  logback:
    rollingpolicy:
      max-file-size: 10MB
      max-history: 7
      total-size-cap: 100MB
```

---

## 6. Seguridad MVP

### 6.1 Checklist de Seguridad Mínimo

```
┌─────────────────────────────────────────────────────────────────┐
│                  SEGURIDAD MVP (Mínimo)                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ✅ HTTPS obligatorio (Let's Encrypt)                          │
│  ✅ JWT con expiración corta (15 min access, 7 días refresh)   │
│  ✅ Passwords con BCrypt (strength 12)                         │
│  ✅ Rate limiting básico (5 login/15min)                       │
│  ✅ CORS restrictivo                                           │
│  ✅ Headers de seguridad (X-Frame-Options, CSP básico)         │
│  ✅ SQL injection prevention (JPA/prepared statements)         │
│  ✅ XSS prevention (Thymeleaf escaping)                        │
│  ✅ Secrets en variables de entorno (no en código)             │
│                                                                 │
│  ⚠️ DIFERIDO (Fase 2):                                         │
│  ⏳ WAF                                                         │
│  ⏳ mTLS entre servicios                                        │
│  ⏳ Vault para secrets                                          │
│  ⏳ Audit logging completo                                      │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 6.2 Configuración NGINX con SSL

```nginx
# nginx.conf
server {
    listen 80;
    server_name mentavirtual.com www.mentavirtual.com;
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl http2;
    server_name mentavirtual.com www.mentavirtual.com;

    ssl_certificate /etc/letsencrypt/live/mentavirtual.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/mentavirtual.com/privkey.pem;

    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256;
    ssl_prefer_server_ciphers off;

    # Security headers
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-XSS-Protection "1; mode=block" always;
    add_header Strict-Transport-Security "max-age=31536000" always;

    location / {
        proxy_pass http://bff-web:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # Rate limiting para login BFF
    location /auth/login {
        limit_req zone=login burst=5 nodelay;
        proxy_pass http://bff-web:8080;
    }

    # --- RUTEO PARA APPs MÓVILES Y TERCEROS (Directo a APIs) ---
    location /api/v1/auth/ {
        proxy_pass http://auth-api:8081/;
    }

    location /api/v1/virtual/ {
        proxy_pass http://virtual-api:8082/;
    }

    location /api/v1/physical/ {
        proxy_pass http://physical-api:8083/;
    }
    
    location /api/v1/billing/ {
        proxy_pass http://billing-api:8084/;
    }
}

# Rate limit zone
limit_req_zone $binary_remote_addr zone=login:10m rate=5r/m;
```

---

## 7. Plan de Crecimiento

### 7.1 Umbrales de Escalamiento

| Umbral | Acción |
|--------|--------|
| **200 → 500 usuarios** | Agregar Redis para caché y sesiones |
| **500 → 1000 usuarios** | Migrar MySQL a managed (RDS/CloudSQL) |
| **1000 → 2000 usuarios** | Agregar segundo servidor + load balancer |
| **2000+ usuarios** | Event-driven con RabbitMQ, considerar Kubernetes |

### 7.2 Diagrama de Evolución

```
MVP (200 users)              Fase 2 (500 users)           Fase 3 (1000+ users)
┌─────────────┐              ┌─────────────┐              ┌─────────────┐
│   Single    │              │   Single    │              │  Cluster    │
│   VPS       │───────────▶  │   VPS +     │───────────▶  │  + LB +     │
│             │              │   Redis     │              │  Managed DB │
└─────────────┘              └─────────────┘              └─────────────┘

Costo: $50-80/mes            Costo: $100-150/mes          Costo: $300-500/mes
```

### 7.3 Señales para Escalar

| Señal | Umbral | Acción |
|-------|--------|--------|
| CPU > 70% sostenido | 1 hora | Revisar queries, considerar cache |
| Memoria > 80% | 30 min | Aumentar RAM o agregar instancia |
| Latencia P95 > 500ms | 1 hora | Agregar cache, optimizar queries |
| Error rate > 1% | 15 min | Investigar, rollback si necesario |
| Disk > 80% | - | Limpiar logs, aumentar disco |

---

## 8. Checklist de Lanzamiento MVP

### 8.1 Pre-Lanzamiento

```
□ Todos los tests pasan
□ Cobertura > 80% en servicios críticos
□ Secrets configurados en variables de entorno
□ SSL certificado instalado y funcionando
□ Backups automáticos configurados (diarios)
□ Health checks funcionando
□ Monitoring externo configurado (UptimeRobot)
□ Dominio apuntando correctamente
□ Emails de notificación funcionando
□ Mercado Pago en modo producción
□ CDN configurado para videos
```

### 8.2 Post-Lanzamiento (Primeras 48h)

```
□ Monitorear logs por errores
□ Verificar todas las funcionalidades críticas
□ Probar flujo completo de suscripción
□ Verificar emails llegando
□ Confirmar webhooks de MP funcionando
□ Revisar tiempos de respuesta
□ Verificar acceso a videos en CDN
□ Probar login desde móvil
```

### 8.3 Rutina Semanal

```
□ Revisar logs por errores/warnings
□ Verificar espacio en disco
□ Revisar backups exitosos
□ Verificar certificado SSL (>30 días)
□ Revisar métricas de uso
□ Actualizar dependencias si hay security patches
```

---

## 9. Resumen MVP

| Aspecto | Decisión MVP | Justificación |
|---------|--------------|---------------|
| **Arquitectura** | 4 APIs + BFF | Separación de dominios sin over-engineering |
| **Comunicación** | HTTP síncrono | Simple, suficiente para 100 RPM |
| **Cache** | In-memory (Caffeine) | Suficiente hasta 500 usuarios |
| **Base de datos** | MySQL single instance | Económico, respaldos simples |
| **Infraestructura** | Docker Compose en VPS | ~$60/mes, fácil de mantener |
| **Monitoreo** | Actuator + UptimeRobot | Gratis, cubre lo esencial |
| **Logging** | Archivos con rotación | Simple, suficiente para debug |
| **Seguridad** | HTTPS + JWT + rate limit | Cubre OWASP básico |

---

[Siguiente: Diagramas C4 →](./diagrams/C4-DIAGRAMS.md)
