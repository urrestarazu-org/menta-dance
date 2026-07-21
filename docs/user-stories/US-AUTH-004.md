# US-AUTH-004: Refresh Token

**ID:** US-AUTH-004
**Título:** Renovación de Access Token mediante Refresh Token
**Módulo / API:** Auth API
**Prioridad (MoSCoW):** Must Have
**Estado:** Draft
**Épica:** EP-01 Autenticación

---

## 1. Historia de Usuario

> **Como** usuario autenticado
> **Quiero** renovar mi sesión automáticamente sin tener que volver a ingresar mis credenciales
> **Para** mantener una experiencia de usuario fluida mientras mi sesión esté activa.

---

## 2. Criterios de Aceptación (BDD)

**Escenario 1: Refresh exitoso**

* **Dado que (Given):** Un usuario autenticado tiene un `refreshToken` válido (no expirado, no revocado).
* **Cuando (When):** Envía una petición `POST /api/v1/auth/refresh` con el `refreshToken` en el body.
* **Entonces (Then):** El sistema debe validar el refresh token contra la base de datos.
* **Y (And):** Debe generar un nuevo `accessToken` (válido por 15 minutos).
* **Y (And):** Debe generar un nuevo `refreshToken` (válido por 7 días) - rotación de tokens.
* **Y (And):** Debe invalidar el refresh token anterior (single-use).
* **Y (And):** Debe devolver un código HTTP `200 OK` con los nuevos tokens.

**Escenario 2: Refresh Token expirado**

* **Dado que (Given):** Un usuario intenta renovar su sesión.
* **Cuando (When):** El `refreshToken` tiene más de 7 días desde su generación.
* **Entonces (Then):** El sistema debe devolver un código HTTP `401 Unauthorized`.
* **Y (And):** El mensaje debe indicar "Sesión expirada. Por favor inicie sesión nuevamente".
* **Y (And):** Debe eliminar el refresh token de la base de datos.

**Escenario 3: Refresh Token inválido o inexistente**

* **Dado que (Given):** Un usuario intenta renovar su sesión.
* **Cuando (When):** El `refreshToken` no existe en la base de datos o tiene formato inválido.
* **Entonces (Then):** El sistema debe devolver un código HTTP `401 Unauthorized`.
* **Y (And):** El mensaje debe indicar "Token de refresh inválido".

**Escenario 4: Refresh Token ya utilizado (posible robo de token)**

* **Dado que (Given):** Un refresh token ya fue utilizado previamente (rotación activada).
* **Cuando (When):** Alguien intenta usar el mismo refresh token de nuevo.
* **Entonces (Then):** El sistema debe devolver un código HTTP `401 Unauthorized`.
* **Y (And):** Debe invalidar TODOS los refresh tokens del usuario (revocar familia de tokens).
* **Y (And):** Debe registrar el evento como posible compromiso de seguridad en logs.

**Escenario 5: Usuario bloqueado o inactivo**

* **Dado que (Given):** Un usuario cuyo estado cambió a `BLOQUEADO` o `INACTIVO` después de loguearse.
* **Cuando (When):** Intenta renovar su sesión con un refresh token válido.
* **Entonces (Then):** El sistema debe devolver un código HTTP `403 Forbidden`.
* **Y (And):** Debe invalidar todos los refresh tokens del usuario.
* **Y (And):** El mensaje debe indicar "Su cuenta ha sido suspendida".

---

## 3. Requisitos No Funcionales y Restricciones

* **Seguridad / Autorización:**
  * Endpoint público (el refresh token actúa como credencial).
  * Implementar **rotación de tokens** (cada refresh genera un nuevo par).
  * Implementar **detección de reuso** para identificar tokens robados.
  * El refresh token debe almacenarse hasheado en la base de datos.
* **Rendimiento / Rate Limiting:**
  * Máximo 10 refreshes por minuto por usuario.
* **Tokens:**
  * Access Token: JWT firmado, 15 minutos de validez.
  * Refresh Token: String aleatorio (UUID v4), 7 días de validez.

---

## 4. Notas Técnicas (Arquitectura)

* **Endpoints Involucrados:**
  * `POST /api/v1/auth/refresh` - Renovar tokens
* **Tablas de BD (Schemas):**
  * `menta_auth.refresh_tokens` - Almacenar y validar refresh tokens
    * Campos: `id`, `user_id`, `token_hash`, `expires_at`, `created_at`, `revoked_at`
* **Request Body:**

  ```json
  {
    "refreshToken": "uuid-v4-string"
  }
  ```

* **Response Body:**

  ```json
  {
    "accessToken": "jwt-string",
    "refreshToken": "new-uuid-v4-string",
    "expiresIn": 900
  }
  ```

---

## 5. Definition of Done (Criterios de Finalización)

* [ ] La lógica implementa todos los Criterios de Aceptación (5 escenarios).
* [ ] Se han escrito **Pruebas Unitarias** para rotación y detección de reuso.
* [ ] Se han escrito **Pruebas de Integración** para el endpoint REST.
* [ ] El endpoint está documentado en el contrato **OpenAPI/Swagger**.
* [ ] El código pasa la validación de Checkstyle y ArchUnit.
* [ ] No introduce nuevos "Issues" severos en SonarCloud.
* [ ] La tabla `refresh_tokens` está creada con índices apropiados.
