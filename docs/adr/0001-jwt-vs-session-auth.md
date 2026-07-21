# ADR-0001: Autenticación Stateless con JWT y Refresh Tokens

**Estado:** Aceptado
**Fecha:** 2026-05-17
**Decisores:** Equipo de Arquitectura

## Vigencia actual

La elección de JWT para clientes externos continúa aceptada. La topología de
múltiples APIs, los audiences por servicio y el modelo de confianza M2M
descritos más abajo son históricos y fueron reemplazados por
[ADR-0020](./0020-modular-monolith.md).

**La estrategia de firma y distribución de claves fue simplificada en
[ADR-0022](./0022-jwt-monolith-strategy.md):**
- Algoritmo: HS256 (simétrico) en lugar de RS256.
- Audience: `"menta-api"` único.
- Sin endpoint JWKS.
- BFF reenvía el JWT del usuario; no existe autenticación M2M.


## Contexto y Problema

Al separar el monolito en múltiples APIs (Auth, Virtual, Physical, Billing) y sumar una aplicación móvil (Android), necesitamos un mecanismo de autenticación que permita a los distintos clientes autorizarse contra las APIs sin que estas tengan que consultar a una base de datos central en cada petición. El sistema actual basado en sesiones de Spring Security (JSESSIONID) acopla el estado al servidor.

## Factores Clave (Decision Drivers)

* Escalabilidad horizontal (stateless).
* Soporte nativo y amigable para aplicaciones móviles (Android).
* Evitar cuellos de botella en la base de datos de usuarios para validación de sesiones.
* **Restricción de Infraestructura:** El servidor consolida todos los servicios en un solo host (8GB RAM, 30GB SSD). Minimizar consultas a la base de datos es clave para el rendimiento global.

## Opciones Consideradas

### Opción 1: Spring Session con Redis (Session-based)

* **Descripción:** Mantener el modelo actual de cookies/sesiones, pero almacenando la sesión en Redis para que todas las APIs la compartan.
* **Pros:**
  * Integración nativa con Spring Security.
  * Invalidación de sesión (logout) inmediata y sencilla.
* **Contras:**
  * Las apps móviles no manejan cookies tan naturalmente como los tokens.
  * Requiere enviar la cookie de sesión cruzando dominios (potenciales problemas de CORS).

### Opción 2: JWT con Refresh Tokens

* **Descripción:** Emitir un Access Token (corta duración) para autorización sin estado y un Refresh Token (larga duración, en base de datos/cookie) para renovar el acceso.
* **Pros:**
  * Completamente *stateless* para las APIs de negocio.
  * Estándar en la industria para consumo desde aplicaciones móviles.
* **Contras:**
  * Mayor complejidad (renovación de tokens, manejo seguro en el cliente).
  * La revocación inmediata de un Access Token requiere una lista negra (Blacklist) en Redis.

## Decisión

Elegimos **Opción 2: JWT con Refresh Tokens** porque **facilita el consumo desde la nueva aplicación Android y permite escalar las APIs de negocio de forma independiente sin consultar la base de datos en cada request**.

## Justificación (Rationale)

Para un ecosistema Multi-Academia con clientes web y móviles, JWT proporciona la flexibilidad necesaria. Las APIs Virtual, Physical y Billing pueden validar la firma criptográfica del token localmente (usando la clave pública) sin acoplarse a la Auth API.

## Especificaciones Técnicas

### Algoritmo de Firma

* **Algoritmo:** RS256 (RSA Signature with SHA-256)
* **Justificación:** Permite verificación asimétrica. La Auth API firma con clave privada; las demás APIs verifican con clave pública sin necesidad de compartir secretos.

### Estructura del JWT (Claims)

```json
{
  "sub": "user_hashid",
  "email": "user@example.com",
  "roles": ["ROLE_USER"],
  "iat": 1716000000,
  "exp": 1716000900,
  "iss": "menta-auth-api",
  "aud": ["menta-virtual-api", "menta-physical-api", "menta-billing-api", "menta-bff"]
}
```

### Time-To-Live (TTL)

| Token | TTL | Almacenamiento |
|-------|-----|----------------|
| Access Token | 15 minutos | Memoria del cliente (no persistir) |
| Refresh Token | 7 días | Cookie HttpOnly (web) / EncryptedSharedPreferences (Android) |

### Distribución de Claves Públicas

* La Auth API expondrá un endpoint JWKS (JSON Web Key Set): `GET /api/v1/.well-known/jwks.json`
* Las APIs consumidoras cachearán las claves públicas con TTL de 24 horas.

## Consecuencias

### Positivas

* Menor carga en la base de datos de usuarios.
* Ecosistema unificado para clientes Web (BFF) y Móviles (Android).
* Verificación de tokens sin dependencia de red hacia Auth API.

### Negativas / Deuda Técnica

* Se debe implementar un mecanismo de Blacklist en Redis para invalidar tokens comprometidos o manejar logouts antes de la expiración.
* Necesidad de implementar rotación de claves RSA periódicamente (recomendado cada 90 días).

### Implicaciones de Costos e Infraestructura

* Requiere infraestructura de caché (Redis) para gestionar la lista negra de tokens revocados. En el host de 8GB RAM, se asignará a Redis un límite de memoria manejable (ej. `maxmemory 256mb`) para no afectar al resto de los microservicios.

### Riesgos y Reversibilidad

* **Riesgo Principal:** Robo de tokens por XSS.
* **Plan de Mitigación:** El Access Token será de corta duración (15 min) y el Refresh Token se manejará como una cookie `HttpOnly` en la web, y almacenamiento seguro en Android (EncryptedSharedPreferences).
* **Reversibilidad:** Difícil. Cambiar el esquema de autenticación una vez que los clientes (especialmente la app móvil) estén en producción requeriría forzar actualizaciones masivas de la app.

## Referencias y Decisiones Relacionadas

* **Complementa a:** [ADR-0007](./0007-cache-redis-vs-local.md) - Redis para Blacklist de tokens revocados
* **Alcance reemplazado por:** [ADR-0020](./0020-modular-monolith.md) - No existe confianza M2M entre módulos
* **RFC 7519:** JSON Web Token (JWT) - <https://tools.ietf.org/html/rfc7519>
* **RFC 7517:** JSON Web Key (JWK) - <https://tools.ietf.org/html/rfc7517>
