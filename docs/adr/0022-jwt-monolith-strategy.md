# ADR-0022: Estrategia JWT Simplificada para Monolito

**Estado:** Aceptado
**Fecha:** 2026-07-20
**Decisores:** Equipo de Arquitectura

## Contexto y Problema

ADR-0001 definió JWT con RS256 y JWKS para un ecosistema de múltiples APIs
independientes. Tras adoptar el monolito modular (ADR-0020), esa complejidad
ya no se justifica: existe un único JAR API donde todos los módulos comparten
el mismo proceso y la misma configuración de seguridad.

La estrategia original incluía:
- RS256 con par de claves pública/privada
- Endpoint JWKS para distribución de claves
- Rotación de claves cada 90 días
- Audiences múltiples (`menta-virtual-api`, `menta-physical-api`, etc.)
- Caché de claves públicas con TTL de 24 horas

En un monolito, estas características añaden complejidad operativa sin
beneficio real.

## Factores Clave (Decision Drivers)

* **Simplicidad operativa:** Un único JAR no requiere distribución de claves.
* **Restricciones de infraestructura:** Host de 8GB RAM; minimizar overhead.
* **Seguridad equivalente:** HS256 con secreto seguro ofrece la misma
  protección para verificación local.
* **Compatibilidad móvil:** Android sigue consumiendo JWT estándar.
* **Preparación para escala:** Si en el futuro se extrae un módulo, se puede
  migrar a RS256 con Spring Authorization Server.

## Opciones Consideradas

### Opción 1: Mantener RS256 con JWKS

* **Descripción:** Conservar la configuración actual de ADR-0001.
* **Pros:**
  * No requiere cambios.
  * Preparado para múltiples servicios futuros.
* **Contras:**
  * Complejidad innecesaria para monolito.
  * Rotación de claves requiere coordinación.
  * Endpoint JWKS expuesto sin consumidores reales.

### Opción 2: HS256 con Secreto Simétrico

* **Descripción:** Usar algoritmo simétrico; el mismo secreto firma y verifica.
* **Pros:**
  * Configuración mínima: una variable de entorno.
  * Sin endpoint JWKS.
  * Rotación simplificada (cambiar secreto, invalidar tokens existentes).
  * Menor latencia de verificación.
* **Contras:**
  * Si se extrae un módulo a servicio separado, hay que compartir secreto o
    migrar a RS256.
  * El secreto debe ser suficientemente largo (256+ bits).

### Opción 3: Spring Authorization Server con RS256

* **Descripción:** Usar Spring Authorization Server como emisor OAuth2/OIDC.
* **Pros:**
  * Estándar OAuth2/OIDC completo.
  * JWKS y rotación automática.
  * Preparado para múltiples clientes y servicios.
* **Contras:**
  * Overhead significativo para MVP.
  * Curva de aprendizaje adicional.
  * Infraestructura más compleja.

## Decisión

Elegimos **Opción 2: HS256 con Secreto Simétrico** porque **elimina la
complejidad de distribución de claves sin sacrificar seguridad, y se alinea
con la realidad de un único JAR API**.

## Justificación (Rationale)

En un monolito modular:
1. No hay red entre módulos; la verificación ocurre en el mismo proceso.
2. Un secreto simétrico de 256+ bits es criptográficamente seguro.
3. La rotación se simplifica a cambiar el secreto y forzar re-login.
4. Si el sistema escala a microservicios, migrar a RS256 es factible sin
   afectar clientes externos (el token sigue siendo JWT estándar).

## Especificaciones Técnicas

### Algoritmo y Secreto

| Aspecto | Valor |
|---------|-------|
| Algoritmo | HS256 (HMAC-SHA256) |
| Longitud mínima | 256 bits (32 bytes) |
| Almacenamiento | Variable de entorno `JWT_SECRET` |
| Generación | `openssl rand -base64 32` |

### Estructura del JWT (Claims)

```json
{
  "sub": "user_hashid",
  "email": "user@example.com",
  "roles": ["ROLE_USER"],
  "iat": 1716000000,
  "exp": 1716000900,
  "iss": "menta-api",
  "aud": "menta-api"
}
```

**Cambios respecto a ADR-0001:**
- `iss`: `"menta-api"` (antes `"menta-auth-api"`)
- `aud`: `"menta-api"` (antes array de múltiples APIs)

### TTL (sin cambios respecto a ADR-0001)

| Token | TTL | Almacenamiento |
|-------|-----|----------------|
| Access Token | 15 minutos | Memoria del cliente |
| Refresh Token | 7 días | Cookie HttpOnly (web) / EncryptedSharedPreferences (Android) |

### Autenticación BFF → API

El BFF **reenvía el JWT del usuario** en el header `Authorization`. No existe
token de servicio ni autenticación M2M; el BFF actúa como proxy autenticado.

```
Usuario → BFF (con JWT) → API (mismo JWT)
```

Si una operación requiere contexto de sistema (batch, scheduler), se usará un
usuario técnico con rol `ROLE_SYSTEM`.

### Blacklist de Tokens

Redis mantiene la blacklist de tokens revocados (sin cambios). La clave es el
`jti` (JWT ID) con TTL igual al tiempo restante de expiración del token.

## Consecuencias

### Positivas

* Configuración reducida a una variable de entorno.
* Sin endpoint JWKS que mantener ni cachear.
* Verificación más rápida (HMAC vs RSA).
* Menor superficie de ataque (sin claves públicas expuestas).

### Negativas / Deuda Técnica

* Si se extrae un módulo, habrá que compartir el secreto o migrar a RS256.
* El secreto debe protegerse estrictamente (no commitear, rotar ante
  exposición).

### Implicaciones de Costos

* Sin costo adicional; simplifica la configuración existente.

### Riesgos y Reversibilidad

* **Riesgo Principal:** Exposición del secreto compromete todos los tokens.
* **Plan de Mitigación:** Secreto en vault o variable de entorno protegida;
  rotación inmediata ante sospecha de exposición.
* **Reversibilidad:** Migrar a RS256 requiere regenerar tokens pero no afecta
  la estructura del JWT ni los clientes.

## Referencias y Decisiones Relacionadas

* **Reemplaza alcance de:** [ADR-0001](./0001-jwt-vs-session-auth.md) (firma y
  distribución de claves)
* **Reemplaza:** [ADR-0016](./0016-validacion-jwt-simetrica.md) (HS256
  compartido entre servicios, ya no aplica)
* **Complementa a:** [ADR-0020](./0020-modular-monolith.md) (monolito modular)
* **Complementa a:** [ADR-0007](./0007-cache-redis-vs-local.md) (Redis para
  blacklist)
