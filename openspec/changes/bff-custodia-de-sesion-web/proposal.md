# Propuesta: BFF Custodia de Sesión Web

## Resumen Ejecutivo

Implementar custodia de sesión web en el BFF usando Spring Session + Redis para que el navegador nunca reciba ni almacene tokens JWT ni refresh tokens. El BFF será el único custodio de tokens, emitiendo solo cookies opacas y seguras al navegador.

## Contexto

### Estado Actual
- **Auth API reconciliado**: PR #14 implementó `/api/v1/auth/login`, `/api/v1/auth/refresh`, `/api/v1/auth/logout` con RFC 9457 y refresh tokens en headers `X-Refresh-Token`
- **BFF mínimo**: Solo `HomeController` sin autenticación, sin SecurityFilterChain, sin session store
- **Redis operacional**: Redis 7.4 con persistencia y `noeviction` ya usado por Auth para blacklist/heartbeat
- **Contrato de seguridad**: ADR-0025 establece que el BFF es custodio exclusivo de tokens para Web

### Problema a Resolver
El navegador no debe tener acceso a tokens (JWT access ni refresh opaco). El BFF debe:
1. Autenticar usuarios contra Auth API
2. Almacenar tokens server-side en Redis
3. Mantener sesión del navegador mediante cookie opaca
4. Refrescar tokens automáticamente cuando expiran
5. Revocar sesiones en logout
6. Fallar cerrado si Redis no está disponible

### Decisión Arquitectónica
ADR-0031 aprueba **Spring Session + Redis** como session store del BFF por:
- Infraestructura existente (Redis operacional)
- Implementación simple (2-3 horas)
- Production-ready sin deuda técnica
- Escalabilidad horizontal automática
- Consistencia con ADR-0026 (Auth usa Redis)

## Requerimientos Funcionales

### RF-1: Login Web
**Actor**: Usuario Web (navegador)
**Trigger**: Usuario envía credenciales desde formulario de login

**Flujo**:
1. Usuario POST `/login` con `email` y `password` (form-encoded)
2. BFF valida CSRF token
3. BFF llama Auth API `POST /api/v1/auth/login` con credenciales
4. Auth API retorna `200 OK` con body JSON:
   ```json
   {
     "accessToken": "eyJhbGc...",
     "expiresIn": 900
   }
   ```
   y header `X-Refresh-Token: <uuid-opaco>`
5. BFF crea sesión Spring en Redis:
   - Almacena `accessToken` + `refreshToken` + `expiresAt`
   - Session ID aleatorio (UUID)
   - TTL: 30 minutos de inactividad
6. BFF retorna cookie `SESSION` al navegador:
   ```
   Set-Cookie: SESSION=<session-id>; HttpOnly; Secure; SameSite=Lax; Path=/
   ```
7. BFF redirige a `/dashboard`

**Postcondiciones**:
- Sesión existe en Redis bajo `spring:session:sessions:<session-id>`
- Navegador tiene cookie `SESSION` (no contiene tokens)
- Usuario autenticado para requests subsecuentes

**Casos Edge**:
- Credenciales inválidas → Auth API retorna `401` → BFF muestra error de login
- Auth degraded → Auth API retorna `503 Retry-After: 30` → BFF muestra error de servicio
- Redis unavailable → BFF no puede crear sesión → rechaza login con `503`

---

### RF-2: Request Autenticado
**Actor**: Usuario Web autenticado
**Trigger**: Request HTTP con cookie `SESSION` válida

**Flujo**:
1. Navegador envía request con cookie `SESSION=<session-id>`
2. Spring Security intercepta request
3. Spring Session carga sesión desde Redis:
   - Key: `spring:session:sessions:<session-id>`
   - Value: serialized session con `accessToken` + `refreshToken` + `expiresAt`
4. BFF valida `expiresAt`:
   - Si access token NO expirado → usar access token existente
   - Si access token expirado → ejecutar RF-3 (refresh transparente)
5. BFF llama Auth API con header:
   ```
   Authorization: Bearer <access-token>
   ```
6. Auth API valida token y responde
7. BFF retorna respuesta al navegador

**Postcondiciones**:
- Request autenticado ejecutado
- Tokens nunca enviados al navegador
- Sesión TTL renovado en Redis (30 min desde última actividad)

**Casos Edge**:
- Sesión no existe en Redis → rechazar con `401 Unauthorized` + redirigir a `/login`
- Redis unavailable → fail-closed: rechazar request con `503 Service Unavailable`
- Access token revocado (Auth API retorna `401`) → ejecutar logout (RF-4)

---

### RF-3: Refresh Transparente
**Actor**: Sistema BFF
**Trigger**: Access token expirado durante request autenticado

**Flujo**:
1. BFF detecta `expiresAt` <= now
2. BFF carga `refreshToken` desde sesión Redis
3. BFF llama Auth API `POST /api/v1/auth/refresh` con header:
   ```
   X-Refresh-Token: <refresh-token>
   ```
4. Auth API retorna `200 OK` con nuevo par de tokens:
   - Body: `{"accessToken": "eyJhbGc...", "expiresIn": 900}`
   - Header: `X-Refresh-Token: <nuevo-refresh-token>`
5. BFF actualiza sesión en Redis atómicamente:
   - Reemplaza `accessToken` con nuevo
   - Reemplaza `refreshToken` con nuevo
   - Actualiza `expiresAt = now + expiresIn`
6. BFF continúa request original con nuevo access token

**Postcondiciones**:
- Sesión actualizada en Redis con nuevos tokens
- Usuario no percibe interrupción (transparente)
- Rotación de refresh token completada

**Casos Edge**:
- Refresh token inválido/revocado → Auth API retorna `401` → ejecutar logout (RF-4)
- Refresh token usado dos veces (familia revocada) → Auth API retorna `423 Locked` → ejecutar logout (RF-4)
- Redis write failure durante actualización → fail-closed: rechazar request con `503`

---

### RF-4: Logout
**Actor**: Usuario Web
**Trigger**: Usuario hace clic en "Cerrar sesión"

**Flujo**:
1. Usuario POST `/logout` con cookie `SESSION` y CSRF token
2. BFF valida CSRF token
3. BFF carga sesión desde Redis
4. BFF extrae `refreshToken` de sesión
5. BFF llama Auth API `POST /api/v1/auth/logout` con header:
   ```
   X-Refresh-Token: <refresh-token>
   ```
6. Auth API revoca refresh token en MySQL + blacklist JTI en Redis
7. BFF invalida sesión Spring: `session.invalidate()`
   - Redis ejecuta DEL `spring:session:sessions:<session-id>`
8. BFF borra cookie del navegador:
   ```
   Set-Cookie: SESSION=; Max-Age=0; HttpOnly; Secure; SameSite=Lax; Path=/
   ```
9. BFF redirige a `/login`

**Postcondiciones**:
- Sesión borrada de Redis
- Refresh token revocado en Auth API
- Access token blacklisted en Redis
- Cookie borrada del navegador
- Usuario no autenticado

**Casos Edge**:
- Sesión ya expirada en Redis → logout exitoso (idempotente)
- Auth API unavailable → BFF invalida sesión local anyway (fail-open para logout)
- Redis unavailable → BFF no puede invalidar sesión → retornar `503` (fail-closed)

---

## Requerimientos No Funcionales

### NFR-1: Seguridad (Contractual)
- **Custody Boundary**: Tokens NUNCA deben llegar al navegador (ADR-0025)
- **Cookie Attributes**: `HttpOnly; Secure; SameSite=Lax`
- **CSRF Protection**: Tokens CSRF en formularios POST/PUT/DELETE
- **Encryption at Rest**: Redis persistence cifrada (future, infra-level)
- **Session Fixation**: Spring Session regenera session ID post-login
- **No Logging**: `accessToken`, `refreshToken`, `SESSION` cookie nunca logueados

### NFR-2: Disponibilidad (Fail-Closed)
- **Redis Required**: Si Redis unavailable → rechazar requests autenticados con `503`
- **Auth API Degraded**: Si Auth API retorna `503 Retry-After: 30` → propagar a navegador
- **No Fallback**: No existe modo fail-open (consistente con ADR-0026)
- **Healthcheck**: BFF healthcheck incluye Redis connectivity

### NFR-3: Performance
- **Session Lookup**: Redis GET < 5ms p99
- **Session Write**: Redis SET < 10ms p99
- **TTL**: 30 minutos de inactividad (renovable en cada request)
- **Redis Memory**: ~1KB/session × 10,000 sesiones = 10MB (negligible)

### NFR-4: Escalabilidad
- **Horizontal Scaling**: Múltiples instancias BFF comparten Redis session store
- **Session Replication**: Spring Session replica automáticamente entre instancias
- **Sticky Sessions**: NO requerido (session store compartido)

### NFR-5: Observabilidad
- **Logs**:
  - Login exitoso: `level=INFO event=user_logged_in userId=<uuid> sessionId=<redacted>`
  - Logout exitoso: `level=INFO event=user_logged_out userId=<uuid>`
  - Refresh transparente: `level=DEBUG event=token_refreshed userId=<uuid>`
  - Redis failure: `level=ERROR event=redis_unavailable cause=<error>`
- **Métricas**:
  - `bff.session.created.count`
  - `bff.session.invalidated.count`
  - `bff.token.refresh.count`
  - `bff.redis.error.count`
- **Traces**: OpenTelemetry spans para login/logout/refresh

### NFR-6: Clean Architecture
- **Capas**:
  - `domain/`: `Session`, `TokenPair` (POJOs, sin Spring)
  - `application/`: `LoginUseCase`, `LogoutUseCase`, `SessionRepository` port
  - `infrastructure/`: `SpringSessionAdapter`, `SecurityConfig`, `LoginController`
- **ArchUnit**: Validar dependencias (domain no importa infrastructure)

---

## Riesgos y Mitigaciones

| Riesgo | Probabilidad | Impacto | Mitigación |
|--------|--------------|---------|------------|
| Redis unavailable → BFF down | Media | Alto | Fail-closed es feature (ADR-0026). Redis con persistencia + healthcheck + alertas |
| Session serialization failure | Baja | Alto | Spring Session maneja serialización. Tests de integración con Redis real |
| Race condition en refresh | Media | Medio | Spring Session + Redis transacciones atómicas |
| Cookie size > 4KB | Muy Baja | Bajo | Session ID es UUID (36 bytes), no tokens |
| CSRF bypass | Baja | Alto | Spring Security CSRF habilitado + origin validation |
| Session fixation | Baja | Alto | Spring Session regenera session ID post-login |

---

## Fuera de Alcance (Post-MVP)

- **Remember Me**: Cookie persistente con TTL extendido (7 días)
- **Multi-Device Sessions**: Gestionar múltiples sesiones por usuario
- **Session Revocation UI**: Admin panel para revocar sesiones
- **Encryption at Rest**: Cifrar tokens en Redis (AES-256-GCM)
- **Session Analytics**: Dashboard de sesiones activas, geolocalización

---

## Plan de Implementación

### Fase 1: Infraestructura (Estimado: 1 hora)
1. Agregar dependencia `spring-boot-starter-session-data-redis` en `bff/build.gradle.kts`
2. Configurar Spring Session en `application.yml`:
   - `spring.session.store-type: redis`
   - `spring.session.redis.namespace: spring:session`
   - `spring.session.timeout: 30m`
3. Configurar Redis connection:
   - `spring.data.redis.host: ${REDIS_HOST:localhost}`
   - `spring.data.redis.port: ${REDIS_PORT:6379}`

### Fase 2: Seguridad (Estimado: 1 hora)
1. Crear `BffSecurityConfig`:
   - `@EnableSpringHttpSession`
   - `SecurityFilterChain` con form login
   - CSRF habilitado
   - Cookie config: `HttpOnly; Secure; SameSite=Lax`
2. Crear `LoginController`:
   - POST `/login` con credenciales
   - Llama Auth API `/api/v1/auth/login`
   - Guarda tokens en session
   - Redirige a `/dashboard`
3. Crear `LogoutController`:
   - POST `/logout` con CSRF token
   - Llama Auth API `/api/v1/auth/logout`
   - Invalida sesión: `session.invalidate()`
   - Redirige a `/login`

### Fase 3: Token Refresh (Estimado: 1 hora)
1. Crear `AuthFilter`:
   - Intercepta requests autenticados
   - Valida `expiresAt` de session
   - Si expirado → llama Auth API `/api/v1/auth/refresh`
   - Actualiza session con nuevos tokens
2. Crear `SessionTokenService`:
   - `getAccessToken(session)`: retorna access token válido (refresh si necesario)
   - `refreshTokens(session)`: llama Auth API y actualiza session

### Fase 4: Testing (Estimado: 2 horas)
1. Tests unitarios:
   - `LoginUseCaseTest`: mocks de Auth API
   - `LogoutUseCaseTest`: validar revocación
   - `SessionTokenServiceTest`: refresh transparente
2. Tests de integración:
   - `LoginIntegrationTest`: Redis real + Auth API mock
   - `LogoutIntegrationTest`: validar session borrada en Redis
   - `RefreshIntegrationTest`: access token expirado → refresh → continuar
3. Tests E2E:
   - `LoginE2ETest`: browser automation (Playwright)
   - `LogoutE2ETest`: validar cookie borrada
   - `SessionExpirationE2ETest`: validar redirect a login

---

## Criterios de Aceptación

- [ ] Usuario puede login con email/password desde formulario web
- [ ] Navegador recibe cookie `SESSION` (HttpOnly, Secure, SameSite=Lax)
- [ ] Navegador NUNCA recibe `accessToken` ni `refreshToken` en respuesta HTTP
- [ ] Sesión almacenada en Redis bajo `spring:session:sessions:<session-id>`
- [ ] Requests autenticados incluyen `Authorization: Bearer <access-token>` al API
- [ ] Access token expirado → refresh transparente sin interrupción de usuario
- [ ] Logout revoca refresh token en Auth API + invalida sesión en Redis
- [ ] Redis unavailable → BFF rechaza login y requests autenticados con `503`
- [ ] CSRF tokens protegen POST/PUT/DELETE
- [ ] Tests: 100% cobertura en `bff/application`, 80% en `bff/infrastructure`
- [ ] ArchUnit valida Clean Architecture (domain no importa infrastructure)
- [ ] Logs no contienen tokens ni session IDs sin redactar

---

## Referencias

- **ADR-0025**: Auth Token Strategy (BFF como custodio exclusivo)
- **ADR-0026**: Redis Reflejado y Caffeine (fail-closed, noeviction)
- **ADR-0031**: BFF Session Strategy (Spring Session + Redis)
- **Exploration**: `openspec/changes/bff-custodia-de-sesion-web/exploration.md`
- **Auth API Contract**: `/api/v1/auth/login`, `/api/v1/auth/refresh`, `/api/v1/auth/logout` (PR #14)
