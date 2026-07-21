# ADR-0025: Estrategia de Tokens de Autenticación

**Estado:** Aceptado
**Fecha:** 2026-07-21
**Decisores:** Equipo de desarrollo

## Contexto y Problema

La documentación actual presenta contradicciones sobre el manejo de tokens:

| Aspecto | Contradicción |
|---------|---------------|
| Refresh token | ¿JWT o token opaco (UUID)? |
| Transporte refresh | ¿Cookie HttpOnly o body JSON? |
| Claim `jti` | Requerido para blacklist, pero ausente en ejemplos |
| Comportamiento | ¿Mismo flujo para Web y Android? |

Este ADR unifica la estrategia de autenticación para eliminar ambigüedades.

## Factores Clave

* El access token debe ser stateless y verificable sin BD
* El refresh token debe ser revocable individualmente
* Web necesita protección contra XSS (HttpOnly cookies)
* Android no puede usar cookies HttpOnly de forma práctica
* La blacklist debe funcionar en un entorno multi-instancia (Redis)

## Decisión

### Access Token (JWT)

| Propiedad | Valor |
|-----------|-------|
| Formato | JWT firmado |
| Algoritmo | HS256 (ver [ADR-0022](./0022-jwt-monolith-strategy.md)) |
| Duración | 15 minutos |
| Claim `jti` | **Obligatorio** — UUID único por token |
| Almacenamiento cliente | Memoria (Web), Memory/Variable (Android) |

**Estructura del Access Token:**

```json
{
  "jti": "550e8400-e29b-41d4-a716-446655440000",
  "sub": "user_abc123",
  "iss": "menta-api",
  "aud": "menta-api",
  "iat": 1714123800,
  "exp": 1714124700,
  "roles": ["ROLE_ALUMNO"],
  "status": "ACTIVO"
}
```

### Refresh Token (Opaco)

| Propiedad | Valor |
|-----------|-------|
| Formato | UUID v4 (opaco, NO JWT) |
| Duración | 7 días |
| Almacenamiento servidor | Hash SHA-256 en tabla `refresh_tokens` |
| Revocación | Por registro en BD |

**¿Por qué opaco y no JWT?**

1. Permite revocación individual sin blacklist global
2. No expone claims al cliente
3. Rotación transparente (nuevo UUID en cada refresh)
4. Menor tamaño en cookies

### Transporte por Plataforma

| Plataforma | Access Token | Refresh Token |
|------------|--------------|---------------|
| **Web (BFF)** | Header `Authorization: Bearer {token}` | Cookie `HttpOnly, Secure, SameSite=Strict` |
| **Android** | Header `Authorization: Bearer {token}` | Header `X-Refresh-Token` + EncryptedSharedPreferences |

### Blacklist de Access Tokens

| Propiedad | Valor |
|-----------|-------|
| Storage | Redis |
| Key | `blacklist:jti:{jti}` |
| TTL | Igual al tiempo restante del token |
| Cuándo agregar | Logout, cambio de password, revocación manual |

**Flujo de validación:**

```
1. Verificar firma JWT (HS256)
2. Verificar exp > now
3. Verificar jti NOT IN Redis blacklist
4. Extraer claims y continuar
```

## Implementación

### Tabla refresh_tokens

```sql
CREATE TABLE refresh_tokens (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,      -- SHA-256 del UUID
    device_info VARCHAR(255),
    ip_address VARCHAR(45),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP NULL,

    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE INDEX idx_token_hash (token_hash),
    INDEX idx_user_tokens (user_id, revoked_at)
);
```

### Flujo de Login

```
┌─────────┐                              ┌─────────────┐
│ Cliente │                              │   API       │
└────┬────┘                              └──────┬──────┘
     │  POST /api/v1/auth/login                 │
     │  {email, password}                       │
     │─────────────────────────────────────────>│
     │                                          │
     │  1. Validar credenciales                 │
     │  2. Generar access JWT con jti           │
     │  3. Generar refresh UUID                 │
     │  4. Guardar hash(refresh) en BD          │
     │                                          │
     │  Response:                               │
     │  Body: {accessToken, expiresIn, user}    │
     │  Cookie: refreshToken (Web only)         │
     │<─────────────────────────────────────────│
```

### Flujo de Refresh

```
┌─────────┐                              ┌─────────────┐
│ Cliente │                              │   API       │
└────┬────┘                              └──────┬──────┘
     │  POST /api/v1/auth/refresh               │
     │  Cookie: refreshToken (Web)              │
     │  Header: X-Refresh-Token (Android)       │
     │─────────────────────────────────────────>│
     │                                          │
     │  1. Buscar hash(token) en BD             │
     │  2. Validar no revocado y no expirado    │
     │  3. Generar NUEVO access JWT con jti     │
     │  4. (Opcional) Rotar refresh token       │
     │                                          │
     │  Response:                               │
     │  Body: {accessToken, expiresIn}          │
     │  Cookie: nuevo refreshToken (si rotó)    │
     │<─────────────────────────────────────────│
```

### Flujo de Logout

```
┌─────────┐                              ┌─────────────┐       ┌───────┐
│ Cliente │                              │   API       │       │ Redis │
└────┬────┘                              └──────┬──────┘       └───┬───┘
     │  POST /api/v1/auth/logout                │                  │
     │  Authorization: Bearer {accessToken}     │                  │
     │  Cookie/Header: refreshToken             │                  │
     │─────────────────────────────────────────>│                  │
     │                                          │                  │
     │  1. Extraer jti del access token         │                  │
     │  2. Agregar jti a blacklist              │                  │
     │     SET blacklist:jti:{jti} 1 EX ttl ───────────────────────>
     │  3. Revocar refresh en BD                │                  │
     │     UPDATE refresh_tokens SET revoked_at │                  │
     │                                          │                  │
     │  Response: 204 No Content                │                  │
     │  Clear-Cookie: refreshToken              │                  │
     │<─────────────────────────────────────────│                  │
```

### Código de Ejemplo

```java
@Service
@RequiredArgsConstructor
public class TokenService {

    private final RefreshTokenRepository refreshTokenRepo;
    private final RedisTemplate<String, String> redis;
    private final JwtEncoder jwtEncoder;

    public AccessToken createAccessToken(User user) {
        String jti = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant exp = now.plus(15, ChronoUnit.MINUTES);

        JwtClaimsSet claims = JwtClaimsSet.builder()
            .id(jti)
            .subject(user.getHashId())
            .issuer("menta-api")
            .audience(List.of("menta-api"))
            .issuedAt(now)
            .expiresAt(exp)
            .claim("roles", user.getRoles())
            .claim("status", user.getStatus())
            .build();

        String token = jwtEncoder.encode(JwtEncoderParameters.from(claims))
            .getTokenValue();

        return new AccessToken(token, jti, exp);
    }

    public RefreshToken createRefreshToken(User user, String deviceInfo, String ip) {
        String rawToken = UUID.randomUUID().toString();
        String hash = sha256(rawToken);

        RefreshTokenEntity entity = RefreshTokenEntity.builder()
            .userId(user.getId())
            .tokenHash(hash)
            .deviceInfo(deviceInfo)
            .ipAddress(ip)
            .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
            .build();

        refreshTokenRepo.save(entity);
        return new RefreshToken(rawToken, entity.getExpiresAt());
    }

    public void blacklistAccessToken(String jti, Instant expiresAt) {
        long ttlSeconds = Duration.between(Instant.now(), expiresAt).getSeconds();
        if (ttlSeconds > 0) {
            redis.opsForValue().set(
                "blacklist:jti:" + jti,
                "1",
                ttlSeconds,
                TimeUnit.SECONDS
            );
        }
    }

    public boolean isBlacklisted(String jti) {
        return redis.hasKey("blacklist:jti:" + jti);
    }
}
```

## Consecuencias

### Positivas

* Access token stateless — validación sin BD (excepto blacklist check)
* Refresh token revocable individualmente
* Seguridad diferenciada por plataforma (cookie vs header)
* Blacklist con TTL automático (no crece indefinidamente)

### Negativas / Deuda Técnica

* Requiere Redis para blacklist (ya necesario para rate limiting)
* Dos mecanismos de transporte según plataforma

### Riesgos y Reversibilidad

* **Riesgo Principal:** Redis no disponible impide validar blacklist
* **Plan de Mitigación:** Si Redis falla, tokens válidos pasan; monitorear y alertar
* **Reversibilidad:** Alta — cambiar a refresh JWT solo requiere migrar tokens activos

## Referencias

* [ADR-0022](./0022-jwt-monolith-strategy.md) — Estrategia JWT HS256
* [ADR-0024](./0024-technology-baseline.md) — Technology Baseline
* [OWASP JWT Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_for_Java_Cheat_Sheet.html)
* Actualiza: [03-AUTH-API.md](../03-AUTH-API.md)
