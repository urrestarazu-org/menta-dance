# API de Autenticación y Sesiones

[← Volver al índice](./README.md) | [← Arquitectura](./02-ARCHITECTURE.md)

> [!NOTE]
> **Módulo Auth dentro del monolito.** Estos endpoints son parte de `:api:app`
> (ver [ADR-0020](./adr/0020-modular-monolith.md)). El módulo `:api:auth` expone
> sus casos de uso mediante interfaces Java; la capa HTTP reside en `:api:app`.

---

## Diseño de Endpoints

> **Convención de URLs:** `/api/v1/{module}/{resource}`
> Ver [ADR-0008](./adr/0008-api-versioning-url-vs-header.md).

```
POST   /api/v1/auth/register             # Registro de usuario
POST   /api/v1/auth/login                # Login (retorna JWT)
POST   /api/v1/auth/logout               # Logout (invalida refresh token)
POST   /api/v1/auth/refresh              # Refresh access token
POST   /api/v1/auth/activate             # Activar cuenta
POST   /api/v1/auth/resend-activation    # Reenviar email de activación

POST   /api/v1/auth/forgot-password      # Solicitar reset de password
POST   /api/v1/auth/reset-password       # Ejecutar reset con token
POST   /api/v1/auth/change-password      # Cambiar password (autenticado)

GET    /api/v1/auth/me                   # Perfil del usuario actual
PATCH  /api/v1/auth/me                   # Actualizar perfil
GET    /api/v1/auth/me/roles             # Roles del usuario actual
```

---

## Flujo de Autenticación JWT

> Los módulos Auth, Virtual, Physical y Billing residen en el mismo proceso
> (`:api:app`). El diagrama muestra la interacción HTTP desde el cliente.

```
┌─────────┐                         ┌─────────────────────────────────────┐
│ Cliente │                         │         :api:app (monolito)         │
│ (SPA/   │                         │  ┌───────┐ ┌───────┐ ┌───────────┐  │
│ Android)│                         │  │ Auth  │ │Virtual│ │Physical/  │  │
└────┬────┘                         │  │Module │ │Module │ │Billing    │  │
     │                              │  └───┬───┘ └───┬───┘ └─────┬─────┘  │
     │                              └──────┼─────────┼───────────┼────────┘
     │                                     │         │           │
     │  1. POST /api/v1/auth/login            │         │           │
     │     {email, password}               │         │           │
     │────────────────────────────────────>│         │           │
     │                                     │         │           │
     │  2. Validate credentials            │         │           │
     │     Generate JWT (HS256)            │         │           │
     │                                     │         │           │
     │  3. {accessToken, expiresIn}        │         │           │
     │     + refreshToken en HttpOnly      │         │           │
     │<────────────────────────────────────│         │           │
     │                                     │         │           │
     │  4. GET /api/v1/courses             │         │           │
     │     Authorization: Bearer {token}   │         │           │
     │─────────────────────────────────────┼────────>│           │
     │                                     │         │           │
     │     (Auth filter valida JWT         │         │           │
     │      antes de llegar al módulo)     │         │           │
     │                                     │         │           │
     │  5. {courses data}                  │         │           │
     │<────────────────────────────────────┼─────────│           │
     │                                     │         │           │
     │  6. Token expirado → refresh        │         │           │
     │     POST /api/v1/auth/refresh          │         │           │
     │     Cookie: refreshToken            │         │           │
     │────────────────────────────────────>│         │           │
     │                                     │         │           │
     │  7. {newAccessToken, expiresIn}     │         │           │
     │<────────────────────────────────────│         │           │
```

---

## Estructura de Tokens JWT

### Access Token (corta duración: 15 minutos)

> Ver [ADR-0025](./adr/0025-auth-token-strategy.md) para la estrategia completa.

```json
{
  "jti": "550e8400-e29b-41d4-a716-446655440000",
  "sub": "user_abc123",
  "iss": "menta-api",
  "aud": "menta-api",
  "roles": ["ROLE_ALUMNO"],
  "status": "ACTIVO",
  "iat": 1714123800,
  "exp": 1714124700
}
```

### Refresh Token (larga duración: 7 días)

El refresh token es un **UUID opaco** (no JWT). Se almacena hasheado en base de datos.

```
Formato: UUID v4
Ejemplo: 7c9e6679-7425-40de-944b-e07fc1f90ae7

Almacenamiento:
- Cliente Web: HttpOnly cookie (SameSite=Strict)
- Cliente Android: Header X-Refresh-Token
- Base de datos: SHA-256(token) en tabla refresh_tokens
```

---

## Almacenamiento de Tokens (Cliente)

| Tipo | Almacenamiento | Razón |
|------|----------------|-------|
| Access Token | Memory (variable JS) | Más seguro contra XSS |
| Refresh Token | HttpOnly Cookie | Protegido de JS, enviado automáticamente |

---

## Configuración de Seguridad JWT

```java
@Configuration
public class JwtSecurityConfig {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration; // 15 minutos

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration; // 7 días

    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(
            new ImmutableSecret<>(jwtSecret.getBytes())
        );
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder
            .withSecretKey(new SecretKeySpec(jwtSecret.getBytes(), "HmacSHA256"))
            .build();
    }
}
```

---

## Manejo de Sesiones

### Tabla de Refresh Tokens

```sql
CREATE TABLE refresh_tokens (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,  -- SHA-256 del token
    device_info VARCHAR(255),
    ip_address VARCHAR(45),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP NULL,

    FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_user_tokens (user_id),
    INDEX idx_token_hash (token_hash)
);
```

### Invalidación de Tokens

```java
public interface RefreshTokenService {
    // Crear nuevo refresh token
    RefreshToken createRefreshToken(User user, String deviceInfo, String ip);

    // Validar y obtener usuario
    Optional<User> validateRefreshToken(String token);

    // Revocar token específico
    void revokeToken(String token);

    // Revocar todos los tokens de un usuario (logout global)
    void revokeAllUserTokens(Long userId);

    // Limpiar tokens expirados (scheduled)
    @Scheduled(cron = "0 0 3 * * *")  // 3 AM diario
    void cleanupExpiredTokens();
}
```

---

## Protección contra Ataques

| Ataque | Mitigación |
|--------|------------|
| **Brute Force** | Rate limiting (5 intentos/30 min), reCAPTCHA |
| **Token Theft** | Access token corto (15 min), refresh en HttpOnly cookie |
| **CSRF** | SameSite=Strict en cookies, CORS restrictivo |
| **XSS** | CSP headers, HttpOnly cookies |
| **Replay Attack** | Token único por sesión, blacklist en Redis |

### Blacklist de Access Tokens (Redis)

Cuando se revoca un access token (logout, cambio de password, etc.), su `jti` se
almacena en Redis hasta que expire naturalmente:

```
Key:    blacklist:jti:{jti}
Value:  "1"
TTL:    tiempo restante hasta exp del token

Ejemplo:
SET blacklist:jti:550e8400-e29b-41d4-a716-446655440000 "1" EX 900
```

El filtro de autenticación verifica en cada request:

```java
if (redis.hasKey("blacklist:jti:" + jti)) {
    throw new TokenRevokedException("Token revocado");
}
```

> Ver [ADR-0025](./adr/0025-auth-token-strategy.md) y
> [ADR-0026](./adr/0026-redis-caffeine-strategy.md) para detalles de implementación.

---

## Respuestas de Auth

### Login Exitoso

```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIs...",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "user": {
      "id": 123,
      "email": "user@example.com",
      "firstName": "Juan",
      "lastName": "Pérez",
      "roles": ["ROLE_ALUMNO"],
      "status": "ACTIVO"
    }
  }
}
```

*Nota: refreshToken se envía en cookie HttpOnly, no en body*

### Login Fallido

```json
{
  "success": false,
  "error": {
    "code": "INVALID_CREDENTIALS",
    "message": "Email o contraseña incorrectos",
    "details": {
      "remainingAttempts": 3,
      "requiresCaptcha": false
    }
  }
}
```

### Cuenta Bloqueada

```json
{
  "success": false,
  "error": {
    "code": "ACCOUNT_LOCKED",
    "message": "Cuenta bloqueada temporalmente",
    "details": {
      "unlocksAt": "2026-04-26T11:00:00Z",
      "reason": "FAILED_ATTEMPTS"
    }
  }
}
```

---

[Siguiente: API Academia Virtual →](./04-VIRTUAL-API.md)
