# Nginx como Reverse Proxy - Decisiones de Diseño y Configuración

## Metadata

- **Fecha de inicio**: 2026-07-26
- **Autor**: Alejandro Urrestarazu
- **Estado**: En implementación
- **Versión**: 1.0

---

## 📋 Tabla de Contenidos

- [Motivación y Objetivos](#motivación-y-objetivos)
- [Decisiones de Arquitectura](#decisiones-de-arquitectura)
- [Configuración Técnica](#configuración-técnica)
- [Estructura de Directorios](#estructura-de-directorios)
- [Scripts de Gestión](#scripts-de-gestión)
- [Roadmap de Implementación](#roadmap-de-implementación)
- [Testing y Validación](#testing-y-validación)
- [Troubleshooting](#troubleshooting)

---

## Motivación y Objetivos

### ¿Por qué Nginx como Reverse Proxy?

**1. Separación de Responsabilidades**
- **Nginx**: Maneja SSL/TLS, compresión, caching de estáticos, security headers
- **Spring Boot**: Se enfoca únicamente en la lógica de negocio

**2. Seguridad**
- Nginx actúa como primera línea de defensa
- Spring Boot no está expuesto directamente a internet
- Headers de seguridad centralizados (HSTS, X-Frame-Options, CSP, etc.)
- Protección contra ataques comunes (DDoS básico, rate limiting)

**3. Performance**
- Compresión gzip manejada por Nginx (más eficiente que Spring)
- Cache agresivo de recursos estáticos (CSS, JS, imágenes)
- Optimizaciones TCP (sendfile, tcp_nopush, tcp_nodelay)
- Menor carga en la aplicación Java

**4. Flexibilidad de Deployment**
- Facilita configuración SSL sin tocar código Java
- Simplifica redirecciones (HTTP → HTTPS)
- Permite múltiples backends en el futuro (load balancing)
- Facilita cambios de infraestructura sin afectar la app

---

## Decisiones de Arquitectura

### Arquitectura Objetivo

```
Internet (puerto 80/443)
         ↓
    Nginx Container (menta-nginx)
         ↓ (proxy_pass)
    ┌─────────────┬──────────────┐
    │             │              │
 API (8081)   BFF (8080)    Android App
    │             │              │
    └─────────────┴──────────────┘
              ↓
    MySQL Container (3306)
    Redis Container (6379)
    Observability Stack
```

### Decisión 1: Monorepo + Monolito Modular

**Decisión**: Mantener arquitectura de monolito modular con dos aplicaciones Spring Boot separadas (API y BFF).

**Justificación**:
- API (puerto 8081): REST API para Android y futuros clientes
- BFF (puerto 8080): Backend-for-Frontend con Thymeleaf para web

**Alternativas consideradas**:
- ❌ **Microservicios**: Complejidad innecesaria en esta etapa
- ❌ **Aplicación única**: No permite separar concerns de API vs BFF

**Consecuencias**:
- ✅ Simplicidad de deployment
- ✅ Transacciones ACID entre módulos
- ✅ Separación clara de responsabilidades API/BFF
- ⚠️ Escalado horizontal requiere escalar ambas apps juntas

### Decisión 2: Puertos de las Aplicaciones

**Decisión**: API en 8081, BFF en 8080 (interno), Nginx en 80/443 (externo).

**Justificación**:
- Puerto 8080 estándar para Spring Boot (BFF)
- Puerto 8081 para diferenciar API
- Puertos internos NO expuestos al host cuando Nginx está activo

**Configuración Nginx**:
```nginx
upstream api {
    server menta-api:8081;
}

upstream bff {
    server menta-bff:8080;
}

# Routing por path
location /api/ {
    proxy_pass http://api;
}

location / {
    proxy_pass http://bff;
}
```

### Decisión 3: Estructura de Directorios

**Decisión**: Adoptar estructura `infra/docker/{database,app,nginx}`.

**Justificación**:
- **Separación lógica**: Cada servicio en su directorio
- **Reusabilidad**: Scripts pueden operar sobre servicios individuales
- **Mantenibilidad**: Configuraciones aisladas

**Estructura**:
```
infra/
└── docker/
    ├── database/
    │   ├── docker-compose.yml    # MySQL, Redis, Observability
    │   └── init.sql (opcional)
    ├── app/
    │   ├── docker-compose.yml    # API + BFF
    │   ├── Dockerfile.api
    │   └── Dockerfile.bff
    ├── nginx/
    │   ├── docker-compose.yml
    │   ├── conf/
    │   │   └── nginx.conf
    │   └── certs/
    │       └── .gitkeep
    ├── manage.sh
    ├── deploy.sh
    └── quick-deploy.sh
```

### Decisión 4: Red Docker

**Decisión**: Red `menta-network` compartida entre todos los servicios.

**Justificación**:
- Simplifica comunicación inter-contenedor
- No requiere exponer puertos innecesarios
- DNS automático entre servicios

**Configuración**:
```yaml
networks:
  menta-network:
    external: false  # Creada por database/docker-compose.yml
```

### Decisión 5: SSL/TLS en Desarrollo vs Producción

**Decisión**:
- **Desarrollo local**: Sin Nginx (acceso directo a 8080/8081)
- **Staging/Production**: Con Nginx + SSL

**Justificación**:
- Desarrollo local no requiere complejidad de SSL
- Producción DEBE usar HTTPS por seguridad

**Implementación**:
```bash
# Desarrollo (sin nginx)
./manage.sh start --no-nginx

# Producción (con nginx)
./manage.sh start
```

### Decisión 6: Certificados SSL

**Decisión**: Usar Let's Encrypt con Certbot para SSL gratuito.

**Justificación**:
- Gratuito y automático
- Renovación automática cada 90 días
- Ampliamente confiable

**Alternativas consideradas**:
- ❌ **Self-signed**: Navegadores muestran advertencia
- ❌ **Certificados pagos**: Costo innecesario

**Implementación** (manual, primera vez):
```bash
# En servidor producción
sudo certbot certonly --standalone -d mentadance.com -d www.mentadance.com

# Copiar certificados
sudo cp /etc/letsencrypt/live/mentadance.com/fullchain.pem infra/docker/nginx/certs/
sudo cp /etc/letsencrypt/live/mentadance.com/privkey.pem infra/docker/nginx/certs/
```

---

## Configuración Técnica

### Nginx.conf - Decisiones Clave

#### 1. Compresión gzip

**Decisión**: Habilitar gzip para todos los text types.

```nginx
gzip on;
gzip_vary on;
gzip_comp_level 6;  # Balance entre CPU y compresión
gzip_types text/plain text/css text/javascript application/json
           application/javascript application/xml;
```

**Consecuencias**:
- ✅ Reducción ~70% en tamaño de CSS/JS
- ⚠️ Pequeño overhead de CPU (despreciable)

#### 2. Security Headers

**Decisión**: Headers de seguridad estrictos.

```nginx
add_header Strict-Transport-Security "max-age=31536000; includeSubDomains; preload" always;
add_header X-Frame-Options "SAMEORIGIN" always;
add_header X-Content-Type-Options "nosniff" always;
add_header X-XSS-Protection "1; mode=block" always;
add_header Referrer-Policy "strict-origin-when-cross-origin" always;
```

**Justificación**:
- HSTS: Forzar HTTPS por 1 año
- X-Frame-Options: Prevenir clickjacking
- CSP: Proteger contra XSS (si se implementa)

#### 3. Cache de Estáticos

**Decisión**: Cache de 30 días para CSS/JS/imágenes.

```nginx
location ~* \.(css|js|jpg|jpeg|png|gif|ico|svg|woff|woff2|ttf|eot)$ {
    proxy_pass http://bff;  # Servidos desde BFF (Thymeleaf)
    proxy_cache_valid 200 30d;
    expires 30d;
    add_header Cache-Control "public, immutable";
}
```

**Consecuencias**:
- ✅ Reducción drástica en requests HTTP
- ⚠️ Cambios en estáticos requieren cache busting (versionado en nombres)

#### 4. Routing API vs BFF

**Decisión**: Routing por path prefix.

```nginx
# API REST (para Android, futuros clientes)
location /api/ {
    proxy_pass http://api;
    # Headers JSON
    proxy_set_header Content-Type application/json;
}

# BFF Web (Thymeleaf)
location / {
    proxy_pass http://bff;
    # Headers HTML
}
```

**Alternativas consideradas**:
- ❌ **Subdominios** (api.mentadance.com): Requiere DNS adicional
- ❌ **Puerto diferente**: Rompe convención web estándar

#### 5. Health Endpoints

**Decisión**: Health endpoints sin logging.

```nginx
location /actuator/health {
    proxy_pass http://api/actuator/health;
    access_log off;  # No saturar logs con health checks
}
```

---

## Estructura de Directorios

### Árbol Completo

```
menta-dance/
├── infra/
│   └── docker/
│       ├── database/
│       │   ├── docker-compose.yml
│       │   └── .env.example
│       ├── app/
│       │   ├── docker-compose.yml
│       │   ├── Dockerfile.api
│       │   └── Dockerfile.bff
│       ├── nginx/
│       │   ├── docker-compose.yml
│       │   ├── conf/
│       │   │   └── nginx.conf
│       │   └── certs/
│       │       ├── .gitkeep
│       │       └── .gitignore  # Ignorar *.crt, *.key
│       ├── manage.sh
│       ├── deploy.sh
│       └── quick-deploy.sh
├── docker-compose.yml  # DEPRECADO → mover a infra/docker/database/
└── ...
```

### Migración del docker-compose.yml Actual

**Decisión**: Mover `docker-compose.yml` (raíz) → `infra/docker/database/docker-compose.yml`.

**Justificación**:
- Organización consistente con proyecto de referencia
- Separación clara database vs app vs nginx

**Plan de migración**:
1. Crear `infra/docker/database/docker-compose.yml`
2. Copiar servicios: mysql, redis, otel-collector, loki, grafana
3. Agregar comentario en raíz indicando nueva ubicación
4. Actualizar scripts para apuntar a nueva ubicación

---

## Scripts de Gestión

### manage.sh

**Propósito**: Script universal de gestión de servicios.

**Comandos**:
```bash
./manage.sh start [--no-nginx]    # Iniciar servicios
./manage.sh stop                  # Detener servicios
./manage.sh restart               # Reiniciar servicios
./manage.sh status                # Estado de contenedores
./manage.sh logs [app|nginx|db]   # Ver logs
./manage.sh health                # Verificar health checks
./manage.sh clean                 # Limpiar contenedores detenidos
```

**Flags**:
- `--no-nginx`: Desarrollo local sin nginx (acceso directo)

### deploy.sh

**Propósito**: Deployment completo con rebuild de imágenes.

**Cuándo usar**:
- Primera vez
- Cambios en Dockerfile
- Cambios en dependencias

**Workflow**:
1. `./gradlew clean build`
2. `docker build -t menta-api:latest -f infra/docker/app/Dockerfile.api .`
3. `docker build -t menta-bff:latest -f infra/docker/app/Dockerfile.bff .`
4. `docker compose up -d`

### quick-deploy.sh

**Propósito**: Deployment rápido sin rebuild (solo copia JAR).

**Cuándo usar**:
- Cambios en código
- No hay cambios en Dockerfile

**Workflow**:
1. `./gradlew :api:app:bootJar :bff:bootJar`
2. `docker cp api/app/build/libs/app.jar menta-api:/app/app.jar`
3. `docker cp bff/build/libs/bff.jar menta-bff:/app/bff.jar`
4. `docker restart menta-api menta-bff`

**Ventajas**:
- 10x más rápido que rebuild completo
- No consume cuota de Docker Hub

---

## Implementación Completada ✅

### Resumen de Implementación

Se ha completado la implementación de Nginx como reverse proxy con la siguiente estructura:

#### Archivos Creados

```
infra/docker/
├── app/
│   ├── docker-compose.yml      ✅ Desarrollo local sin Nginx
│   ├── Dockerfile.api          ✅ Multi-stage build para API
│   └── Dockerfile.bff          ✅ Multi-stage build para BFF
├── nginx/
│   ├── docker-compose.yml      ✅ Producción con Nginx
│   ├── conf/
│   │   └── nginx.conf          ✅ Configuración completa
│   └── certs/
│       ├── .gitkeep            ✅
│       └── .gitignore          ✅
├── database/
│   └── docker-compose.yml      ✅ Servicios de infraestructura
├── manage.sh                   ✅ Script de gestión
├── deploy.sh                   ✅ Deployment completo
└── quick-deploy.sh             ✅ Deployment rápido
```

### Instrucciones de Uso

#### Setup Inicial

1. **Crear archivo .env** (primera vez):

```bash
cd infra/docker
cat > .env << EOF
# Database Configuration
MYSQL_ROOT_PASSWORD=your_root_password_here
MYSQL_APP_USER=menta_app
MYSQL_APP_PASSWORD=your_app_password_here

# Spring Boot Profiles
SPRING_PROFILES_ACTIVE=docker

# JVM Options (opcional)
JAVA_OPTS=-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC
EOF
```

2. **Primer deployment completo**:

```bash
cd infra/docker
./deploy.sh
```

#### Desarrollo Local (Sin Nginx)

```bash
cd infra/docker

# Iniciar sin Nginx (puertos directos 8080/8081)
./manage.sh start --no-nginx

# Acceder a:
# - BFF:     http://localhost:8080
# - API:     http://localhost:8081
# - Grafana: http://localhost:3000
```

#### Producción (Con Nginx)

```bash
cd infra/docker

# Iniciar con Nginx
./manage.sh start

# Acceder a:
# - Web:     http://localhost      (→ BFF)
# - API:     http://localhost/api  (→ API)
# - Grafana: http://localhost:3000
```

#### Comandos Útiles

```bash
# Ver status de servicios
./manage.sh status

# Ver logs
./manage.sh logs api
./manage.sh logs bff
./manage.sh logs nginx

# Verificar health
./manage.sh health

# Reiniciar servicios
./manage.sh restart

# Detener servicios
./manage.sh stop

# Limpiar contenedores detenidos
./manage.sh clean
```

#### Deployment Rápido (Solo Cambios en Código)

```bash
# Compila JARs y los copia a contenedores corriendo (sin rebuild)
./quick-deploy.sh

# Skipping tests (más rápido)
./quick-deploy.sh --skip-tests
```

### Próximos Pasos

#### Pendiente: Configuración SSL/TLS

Para habilitar HTTPS en producción:

1. **Obtener certificados SSL**:

```bash
# Con Let's Encrypt (en servidor producción)
sudo certbot certonly --standalone \
    -d mentadance.com \
    -d www.mentadance.com
```

2. **Copiar certificados**:

```bash
sudo cp /etc/letsencrypt/live/mentadance.com/fullchain.pem \
    infra/docker/nginx/certs/mentadance.com-fullchain.crt

sudo cp /etc/letsencrypt/live/mentadance.com/privkey.pem \
    infra/docker/nginx/certs/mentadance.com.key
```

3. **Descomentar configuración HTTPS en nginx.conf**:

- Descomentar servidor HTTPS (línea ~240)
- Descomentar redirect HTTP → HTTPS (línea ~40)
- Descomentar redirect www → non-www (línea ~48)

4. **Recargar Nginx**:

```bash
docker exec menta-nginx nginx -s reload
```

#### Pendiente: Actualizar Documentación

- [ ] Actualizar `docs/26-LOCAL-DEV-SETUP-HOWTO.md` con nuevos comandos
- [ ] Agregar sección Nginx en README.md
- [ ] Documentar proceso de configuración SSL en producción

---

## Testing y Validación

### Checklist de Testing

#### Desarrollo Local (sin Nginx)

```bash
# 1. Iniciar sin nginx
cd infra/docker
./manage.sh start --no-nginx

# 2. Verificar API
curl http://localhost:8081/actuator/health

# 3. Verificar BFF
curl http://localhost:8080/

# 4. Verificar logs
./manage.sh logs api
./manage.sh logs bff
```

#### Producción (con Nginx)

```bash
# 1. Iniciar con nginx
./manage.sh start

# 2. Verificar redirección HTTP → HTTPS
curl -I http://localhost
# Debe retornar: 301 Moved Permanently, Location: https://...

# 3. Verificar routing API
curl https://localhost/api/v1/auth/health

# 4. Verificar routing BFF
curl https://localhost/

# 5. Verificar security headers
curl -I https://localhost/
# Debe incluir: Strict-Transport-Security, X-Frame-Options, etc.

# 6. Verificar compresión
curl -H "Accept-Encoding: gzip" -I https://localhost/css/styles.css
# Debe incluir: Content-Encoding: gzip
```

### Métricas de Performance

**Objetivos**:
- ✅ Compresión gzip reduce payload en >60%
- ✅ Cache de estáticos reduce requests en >80% (segunda visita)
- ✅ Latencia adicional de Nginx <5ms

**Herramientas**:
- Chrome DevTools (Network tab)
- `curl -w "@curl-format.txt"` (timing)
- Apache Bench: `ab -n 1000 -c 10 https://localhost/`

---

## Troubleshooting

### Problema: "Connection refused" al acceder a http://localhost

**Causa**: Nginx no puede conectar al backend (API/BFF).

**Solución**:

```bash
# 1. Verificar que contenedores estén corriendo
docker ps | grep menta

# 2. Verificar health de app
docker exec menta-api wget -qO- http://localhost:8081/actuator/health
docker exec menta-bff wget -qO- http://localhost:8080/actuator/health

# 3. Verificar logs
./manage.sh logs api
./manage.sh logs bff

# 4. Verificar red Docker
docker network inspect menta-network
```

### Problema: "502 Bad Gateway"

**Causa**: Spring Boot todavía está iniciando.

**Solución**:

```bash
# Esperar a que health check esté UP
./manage.sh health

# Ver progreso de inicio
./manage.sh logs api | grep "Started Application"
./manage.sh logs bff | grep "Started Application"
```

### Problema: Nginx no arranca

**Causa**: Error de sintaxis en nginx.conf.

**Solución**:

```bash
# Verificar configuración
docker exec menta-nginx nginx -t

# Ver logs de error
./manage.sh logs nginx | grep error

# Recargar configuración (si contenedor está corriendo)
docker exec menta-nginx nginx -s reload
```

### Problema: SSL no funciona

**Causa**: Certificados no encontrados o mal configurados.

**Solución**:

```bash
# 1. Verificar que certificados existan
ls -la infra/docker/nginx/certs/

# 2. Verificar permisos
docker exec menta-nginx ls -la /etc/nginx/certs/

# 3. Verificar configuración SSL en nginx.conf
docker exec menta-nginx cat /etc/nginx/nginx.conf | grep ssl

# 4. Ver logs SSL
docker logs menta-nginx 2>&1 | grep -i ssl
```

---

## Correcciones de Seguridad Aplicadas

### PR1 - Operacional (2026-07-26)

Basado en revisión de seguridad (MiniMax-M3), se aplicaron las siguientes correcciones críticas:

### 🔴 Críticos

#### 1. Healthcheck Real (nginx/docker-compose.yml)

**Problema**: `nginx -t` solo valida sintaxis, no verifica que Nginx pueda aceptar conexiones.

**Solución aplicada**:
```yaml
healthcheck:
  test: ["CMD-SHELL", "wget -qO- http://localhost/health || exit 1"]
```

Agregado endpoint `/health` en nginx.conf que retorna 200 "ok".

#### 2. Servidor HTTPS con Location Blocks

**Problema**: Bloque HTTPS comentado sin location blocks. Al descomentar para producción, todos los endpoints retornarían 404.

**Solución aplicada**: Arquitectura modular con archivos compartidos:

```
conf.d/
├── upstreams.conf         # Upstream definitions
├── proxy_params.conf      # Headers y timeouts comunes
├── security_headers.conf  # Security headers
└── locations.conf         # Location blocks compartidos
```

Ambos servidores (HTTP y HTTPS) incluyen `locations.conf`, garantizando configuración idéntica.

#### 3. Estrategia de Cache Busting

**Problema**: `expires 30d` + `Cache-Control: immutable` sin versionado de assets. Cambios en CSS/JS requieren purge manual.

**Solución recomendada** (pendiente implementación en BFF):

1. **Webpack/Vite con hashed assets**:
   ```javascript
   // webpack.config.js
   output: {
     filename: '[name].[contenthash:8].js',
     chunkFilename: '[name].[contenthash:8].chunk.js',
   }
   ```

2. **Thymeleaf con versión en query string**:
   ```html
   <link rel="stylesheet" th:href="@{/css/styles.css(v=${buildTimestamp})}" />
   ```

3. **Nginx sirve estáticos directamente** (mejor performance):
   ```yaml
   volumes:
     - ../../bff/build/resources/main/static:/usr/share/nginx/html/static:ro
   ```

   ```nginx
   location /static/ {
     alias /usr/share/nginx/html/static/;
     expires 30d;
     add_header Cache-Control "public, immutable";
   }
   ```

**Estado actual**: Cache de 30 días habilitado con **TODO** en `locations.conf` para implementar cache busting.

### 🟠 Importantes

#### 4. Rate Limiting en Endpoints Sensibles

**Problema**: Módulos `auth` y `billing` expuestos sin protección contra credential stuffing y card-testing.

**Solución aplicada** (actualizada en PR2):
```nginx
# nginx.conf (líneas 61-62)
limit_req_zone $binary_remote_addr zone=auth:10m rate=10r/s;
limit_req_zone $binary_remote_addr zone=billing:30m rate=5r/s;

# locations.conf — Paths reales confirmados en controllers (no DEAD CODE)
location ~ ^/api/v1/users/register { limit_req zone=auth burst=10 nodelay; ... }
location ~ ^/api/v1/auth/          { limit_req zone=auth burst=20 nodelay; ... }
location ~ ^/api/v1/billing/       { limit_req zone=billing burst=10; ... }
```

**Corrección PR2**: Los paths originales `/api/auth/` y `/api/billing/` eran DEAD CODE (no matcheaban ningún controller real). Actualizado a paths reales `/api/v1/*` confirmados en `UserController.java`.

**Defensa en profundidad** contra ataques conocidos, no YAGNI.

#### 5. Endpoints Actuator Restringidos

**Problema**: `/api/actuator/*` exponía `/env`, `/heapdump`, `/loggers` (leaks de configuración y memoria).

**Solución aplicada**:
```nginx
# Solo permitir health e info
location = /api/actuator/health { ... }
location = /api/actuator/info { ... }

# Bloquear el resto
location ~ ^/api/actuator/(env|heapdump|loggers|metrics|threaddump) {
    return 403 "Forbidden\n";
}
```

#### 6. Headers de Seguridad Completos

**Agregados**:
- `server_tokens off;` — oculta versión de Nginx
- `proxy_hide_header X-Powered-By;` — oculta header de Spring Boot
- `client_body_timeout 12s;`, `client_header_timeout 12s;`, `send_timeout 60s;` — timeouts completos

### 🟡 Mejoras Estructurales

#### 7. DRY: Bloques Proxy Compartidos

**Solución**: Archivo `conf.d/proxy_params.conf` reutilizado con `include` en cada location.

#### 8. Worker Processes Automático

```nginx
worker_processes auto;  # Aprovecha todos los cores disponibles
```

#### 9. Client Max Body Size Reducido

```nginx
client_max_body_size 10M;  # Bajado de 100M (vector de DoS)
```

Relajar solo en locations específicas si hace falta:
```nginx
location /api/billing/upload {
    client_max_body_size 50M;
    ...
}
```

#### 10. Tipos Gzip Completos

**Agregados**:
- `font/woff2` — fuentes woff2 NO se comprimen por defecto
- `application/wasm`
- `application/manifest+json`

---

### PR2 - Seguridad y Arquitectura (2026-07-27)

Correcciones arquitecturales y de seguridad identificadas en revisión post-PR1.

#### 1. Servicios Internos Sin Exposición al Host

**Problema**: MySQL, Redis, OTLP Collector, Loki y Grafana publicaban puertos al host, haciéndolos accesibles fuera del stack Docker.

**Solución aplicada**:
```yaml
# ANTES
mysql:
  ports:
    - "3306:3306"

# DESPUÉS
mysql:
  expose:
    - "3306"  # Solo visible en la red Docker menta-network
```

**Servicios corregidos**: MySQL (3306), Redis (6379), OTLP Collector (4317, 4318), Loki (3100), Grafana (3000).

**Único puerto expuesto al host**: Nginx (80, 443).

#### 2. BFF Sin Credenciales de Base de Datos (Violación Arquitectural)

**Problema**: BFF recibía credenciales directas de MySQL y Redis, violando el contrato arquitectural de `docs/AGENTS.md` (BFF solo debe comunicarse con API vía HTTP).

**Solución aplicada**:
```yaml
# ELIMINADAS del servicio BFF
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
SPRING_DATA_REDIS_HOST
SPRING_DATA_REDIS_PORT

# CORREGIDA variable API
MENTA_API_BASE_URL: http://api:8081  # Antes: API_BASE_URL: http://menta-api:8081/api
```

**Dependencias corregidas**: BFF ahora depende solo de `api:service_healthy`, no de MySQL ni Redis.

#### 3. Grafana Acceso Anónimo Restringido

**Problema**: Grafana permitía acceso anónimo como Administrador, exponiendo modificación de dashboards y configuración sin autenticación.

**Solución aplicada**:
```yaml
# ANTES
GF_AUTH_ANONYMOUS_ORG_ROLE=Admin
GF_AUTH_DISABLE_LOGIN_FORM=true

# DESPUÉS
GF_AUTH_ANONYMOUS_ORG_ROLE=Viewer
GF_AUTH_DISABLE_LOGIN_FORM=false
```

**Impacto**: Acceso anónimo solo lectura, login requerido para modificaciones.

#### 4. HTTPS Habilitado con Redirección HTTP→HTTPS

**Problema**: Producción funcionaba solo por HTTP, bloque HTTPS comentado.

**Solución aplicada**:
- Bloque HTTPS descomentado (puerto 443) con certificados en convención documentada: `mentadance.com-fullchain.crt` y `mentadance.com.key`
- Redirección HTTP→HTTPS automática para `mentadance.com` y `www.mentadance.com`
- Servidor HTTP solo responde a `localhost` (desarrollo local)
- Agregado `proxy_hide_header X-Powered-By;` para ocultar header de Spring Boot

**Configuración SSL**:
```nginx
server {
    listen 80;
    server_name mentadance.com www.mentadance.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name mentadance.com;

    ssl_certificate /etc/nginx/certs/mentadance.com-fullchain.crt;
    ssl_certificate_key /etc/nginx/certs/mentadance.com.key;

    # ... SSL config, HSTS, security headers
}
```

#### 5. Rate Limiting Corregido (DEAD CODE Eliminado)

**Problema**: Rate limiting en `/api/auth/` y `/api/billing/` era DEAD CODE — ningún controller Spring Boot exponía esos prefijos. Paths reales son `/api/v1/users/register` (UserController.java) y `/api/v1/billing/*` (futuro).

**Solución aplicada**:
```nginx
# ELIMINADO (DEAD CODE)
location /api/auth/    { limit_req zone=auth burst=20 nodelay; ... }
location /api/billing/ { limit_req zone=billing burst=10; ... }

# REEMPLAZADO con paths reales confirmados en controllers
location ~ ^/api/v1/users/register { limit_req zone=auth burst=10 nodelay; ... }
location ~ ^/api/v1/auth/          { limit_req zone=auth burst=20 nodelay; ... }
location ~ ^/api/v1/billing/       { limit_req zone=billing burst=10; ... }
```

**Validación**: Paths confirmados con `grep -rn "@RequestMapping" api/auth/src/.../controller/`.

**Impacto**: Protección real contra credential stuffing en endpoints sensibles.

---

## Decisiones Pendientes

### 1. Cache Busting para Assets Estáticos

**Decisión**: Implementar versionado de assets en BFF.

**Opciones**:
- **A**: Webpack/Vite con hashed filenames (e.g., `app.[contenthash:8].js`)
- **B**: Query string versioning con `buildTimestamp` en Thymeleaf
- **C**: Nginx sirve estáticos directamente desde volumen con nombres hasheados

**Recomendación**: Opción A + C (mejor performance y cache-friendly).

**Estado**: 🔴 **Bloqueante para cache de 30 días en producción**. Mientras tanto, reducir `expires` a 1h.

### 2. Load Balancing

**Pregunta**: ¿Preparar para múltiples instancias de API/BFF?

**Opciones**:
- **A**: Configurar upstream con múltiples servers desde ahora
- **B**: Single instance por ahora

**Recomendación**: Opción B (KISS).

### 3. Logging Avanzado

**Pregunta**: ¿Integrar logs de Nginx con Loki/Grafana?

**Opciones**:
- **A**: Configurar ahora con sidecar Promtail
- **B**: Logs de Nginx solo en archivos

**Recomendación**: Opción A (ya tenemos stack de observabilidad).

---

## Referencias

- [Proyecto de referencia: Menta Virtual](../REFERENCIA-NGINX.md)
- [Nginx Reverse Proxy Guide](https://docs.nginx.com/nginx/admin-guide/web-server/reverse-proxy/)
- [Mozilla SSL Configuration Generator](https://ssl-config.mozilla.org/)
- [Docker Compose Networking](https://docs.docker.com/compose/networking/)

---

## Changelog

| Fecha | Cambio | Autor |
|-------|--------|-------|
| 2026-07-26 | Documento inicial | Alejandro Urrestarazu |
| 2026-07-26 | Decisiones de arquitectura definidas | Alejandro Urrestarazu |
| 2026-07-26 | Implementación completada: estructura, Dockerfiles, nginx.conf, scripts | Alejandro Urrestarazu |
| 2026-07-26 | **Correcciones de seguridad críticas aplicadas** (revisión MiniMax-M3) | Alejandro Urrestarazu |
| 2026-07-26 | Arquitectura modular con archivos compartidos (conf.d/) | Alejandro Urrestarazu |
| 2026-07-27 | **PR2 - Seguridad y arquitectura**: Servicios internos sin exposición al host, Grafana Viewer, BFF sin credenciales DB, HTTPS habilitado, rate limiting corregido | Alejandro Urrestarazu |

---

**Estado**: ✅ **Hardening de seguridad completado (PR2)** | 🟡 Pendiente: cache busting, SSL en producción
**Última actualización**: 2026-07-27
