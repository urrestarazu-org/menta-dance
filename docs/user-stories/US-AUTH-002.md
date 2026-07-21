# US-AUTH-002: Login de usuario

**ID:** US-AUTH-002
**Título:** Login de usuario y generación de tokens JWT
**Módulo / API:** Auth API
**Prioridad (MoSCoW):** Must Have
**Estado:** Draft
**Épica:** EP-01 Autenticación

---

## 1. Historia de Usuario

> **Como** usuario registrado (alumno o administrador)  
> **Quiero** autenticarme en el sistema ingresando mi email y contraseña  
> **Para** obtener mis credenciales de sesión (JWT) y acceder a las rutas protegidas de las academias Virtual y Física.

---

## 2. Criterios de Aceptación (BDD)

**Escenario 1: Login exitoso**

* **Dado que (Given):** Un usuario con estado `ACTIVO` ingresa al endpoint `POST /api/v1/auth/login`.
* **Cuando (When):** Envía su `email` y `password` correctos.
* **Entonces (Then):** El sistema debe validar el cifrado BCrypt correctamente.
* **Y (And):** Debe reiniciar a 0 el campo `failedLoginAttempts`.
* **Y (And):** Debe devolver un código HTTP `200 OK` con un payload JSON que incluya el `accessToken` (JWT, válido por 15 min) y el `refreshToken` (válido por 7 días).

**Escenario 2: Credenciales inválidas**

* **Dado que (Given):** Un usuario intenta loguearse en `POST /api/v1/auth/login`.
* **Cuando (When):** Ingresa un `email` inexistente o un `password` incorrecto.
* **Entonces (Then):** El sistema debe incrementar el contador `failedLoginAttempts` (si el usuario existe).
* **Y (And):** El sistema debe devolver un código HTTP `401 Unauthorized` con el mensaje genérico "Email o contraseña incorrectos" (para prevenir enumeración de usuarios).

**Escenario 3: Cuenta pendiente de activación**

* **Dado que (Given):** Un usuario con estado `PENDIENTE` intenta loguearse.
* **Cuando (When):** Envía su `email` y `password` correctos.
* **Entonces (Then):** El sistema debe devolver un código HTTP `403 Forbidden`.
* **Y (And):** El mensaje de respuesta debe indicar "La cuenta no ha sido activada. Por favor revise su correo".

**Escenario 4: Bloqueo de cuenta temporal por intentos fallidos**

* **Dado que (Given):** Un usuario intenta loguearse de forma fallida repetidamente.
* **Cuando (When):** El contador `failedLoginAttempts` alcanza 5 intentos fallidos consecutivos.
* **Entonces (Then):** El sistema debe actualizar el campo `accountLockedUntil` sumándole 30 minutos a la hora actual.
* **Y (And):** Debe devolver un error HTTP `423 Locked` indicando que la cuenta ha sido bloqueada temporalmente.

**Escenario 5: Cuenta con estado BLOQUEADO (bloqueo administrativo)**

* **Dado que (Given):** Un usuario con estado `BLOQUEADO` (bloqueado por un administrador) intenta loguearse.
* **Cuando (When):** Envía su `email` y `password` correctos.
* **Entonces (Then):** El sistema debe devolver un código HTTP `403 Forbidden`.
* **Y (And):** El mensaje de respuesta debe indicar "Su cuenta ha sido suspendida. Contacte al soporte para más información".

**Escenario 6: Cuenta con estado INACTIVO**

* **Dado que (Given):** Un usuario con estado `INACTIVO` (cuenta desactivada) intenta loguearse.
* **Cuando (When):** Envía su `email` y `password` correctos.
* **Entonces (Then):** El sistema debe devolver un código HTTP `403 Forbidden`.
* **Y (And):** El mensaje de respuesta debe indicar "Su cuenta está inactiva. Contacte al soporte para reactivarla".

**Escenario 7: Cuenta bloqueada temporalmente (lockout activo)**

* **Dado que (Given):** Un usuario cuyo campo `accountLockedUntil` tiene una fecha futura intenta loguearse.
* **Cuando (When):** Envía su `email` y `password` correctos.
* **Entonces (Then):** El sistema debe devolver un código HTTP `423 Locked`.
* **Y (And):** El mensaje debe indicar el tiempo restante de bloqueo (ej. "Cuenta bloqueada. Intente nuevamente en X minutos").

---

## 3. Requisitos No Funcionales y Restricciones

* **Seguridad / Autorización:**
  * Endpoint público (no requiere token JWT).
  * Los errores nunca deben revelar si el que falló fue el email o la contraseña (prevención de OSINT/Enumeración).
* **Rendimiento / Rate Limiting:**
  * Máximo 5 intentos por IP cada 15 minutos en el firewall/NGINX.
* **JWT Specs:**
  * El JWT (Access Token) debe incluir en el payload: el `id` del usuario (o sub), su `email` y su array de `roles`.

---

## 4. Notas Técnicas (Arquitectura)

* **Endpoints Involucrados:** `POST /api/v1/auth/login`
* **Tablas de BD (Schemas):**
  * `menta_auth.users` (para validación y actualización de intentos fallidos).
* **Integraciones:** Se debe usar la librería `jjwt` u otra recomendada por Spring Security para la firma del token (preferiblemente con llave asimétrica RSA, o simétrica HMAC si es MVP).

---

## 5. Definition of Done (Criterios de Finalización)

Para considerar esta Historia de Usuario como terminada (lista para despliegue), debe cumplir de forma estricta:

* [ ] La lógica implementa todos los Criterios de Aceptación (Éxito, Error, Bloqueo).
* [ ] Se han escrito **Pruebas Unitarias** exhaustivas probando cada caso de error de login (Mockeando BD).
* [ ] Se han escrito **Pruebas de Integración** probando la ruta HTTP real y parseando el JWT generado.
* [ ] El endpoint está documentado en el contrato **OpenAPI/Swagger**.
* [ ] El código pasa la validación de Checkstyle y ArchUnit (Reglas de arquitectura).
* [ ] No introduce nuevos "Issues" severos en SonarCloud.
