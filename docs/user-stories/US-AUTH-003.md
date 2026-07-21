# US-AUTH-003: Activación de cuenta

**ID:** US-AUTH-003
**Título:** Activación de cuenta mediante link de email
**Módulo / API:** Auth API
**Prioridad (MoSCoW):** Must Have
**Estado:** Draft
**Épica:** EP-01 Autenticación

---

## 1. Historia de Usuario

> **Como** usuario recién registrado
> **Quiero** activar mi cuenta haciendo clic en el enlace enviado a mi email
> **Para** poder iniciar sesión y acceder a las funcionalidades de la academia.

---

## 2. Criterios de Aceptación (BDD)

**Escenario 1: Activación exitosa**

* **Dado que (Given):** Un usuario con estado `PENDIENTE` recibió un email con el link de activación.
* **Cuando (When):** Hace clic en el enlace `GET /api/v1/auth/activate/{token}` dentro del período de validez (48 horas).
* **Entonces (Then):** El sistema debe cambiar el estado del usuario de `PENDIENTE` a `ACTIVO`.
* **Y (And):** Debe invalidar el `activationToken` (marcarlo como usado o eliminarlo).
* **Y (And):** Debe devolver un código HTTP `200 OK` con mensaje de éxito.
* **Y (And):** Debe redirigir al usuario a la página de login (si es web) o mostrar mensaje de éxito (si es API).

**Escenario 2: Token expirado**

* **Dado que (Given):** Un usuario intenta activar su cuenta.
* **Cuando (When):** El `activationToken` tiene más de 48 horas desde su generación.
* **Entonces (Then):** El sistema debe devolver un código HTTP `410 Gone`.
* **Y (And):** El mensaje debe indicar "El enlace de activación ha expirado. Solicite uno nuevo".
* **Y (And):** Debe ofrecer la opción de reenviar el email de activación.

**Escenario 3: Token inválido o inexistente**

* **Dado que (Given):** Un usuario intenta activar su cuenta.
* **Cuando (When):** El `activationToken` no existe en la base de datos o tiene formato inválido.
* **Entonces (Then):** El sistema debe devolver un código HTTP `404 Not Found`.
* **Y (And):** El mensaje debe indicar "Enlace de activación inválido".

**Escenario 4: Token ya utilizado (cuenta ya activada)**

* **Dado que (Given):** Un usuario intenta usar un link de activación.
* **Cuando (When):** El token ya fue utilizado y la cuenta está en estado `ACTIVO`.
* **Entonces (Then):** El sistema debe devolver un código HTTP `200 OK`.
* **Y (And):** El mensaje debe indicar "Su cuenta ya se encuentra activa. Puede iniciar sesión".

**Escenario 5: Reenvío de email de activación**

* **Dado que (Given):** Un usuario con estado `PENDIENTE` solicita reenvío del email.
* **Cuando (When):** Envía su email a `POST /api/v1/auth/resend-activation`.
* **Entonces (Then):** El sistema debe generar un nuevo `activationToken` (invalidando el anterior).
* **Y (And):** Debe enviar un nuevo email de activación.
* **Y (And):** Debe devolver un código HTTP `200 OK` con mensaje genérico (sin revelar si el email existe).

---

## 3. Requisitos No Funcionales y Restricciones

* **Seguridad / Autorización:**
  * Endpoint público (no requiere token JWT).
  * El token de activación debe ser un UUID o string aleatorio de al menos 32 caracteres.
  * El token debe ser de un solo uso (single-use).
* **Rendimiento / Rate Limiting:**
  * Máximo 3 reenvíos de email por hora por dirección de email.
  * Máximo 10 intentos de activación por IP por hora.
* **Expiración:**
  * El token expira a las 48 horas de su generación.

---

## 4. Notas Técnicas (Arquitectura)

* **Endpoints Involucrados:**
  * `GET /api/v1/auth/activate/{token}` - Activar cuenta
  * `POST /api/v1/auth/resend-activation` - Reenviar email
* **Tablas de BD (Schemas):**
  * `menta_auth.users` - Actualizar `status`, `activation_token`, `activation_token_expires`
* **Integraciones:**
  * Servicio de email (MailService) para envío de correos.
  * En MVP sin RabbitMQ, la llamada es síncrona.

---

## 5. Definition of Done (Criterios de Finalización)

* [ ] La lógica implementa todos los Criterios de Aceptación (5 escenarios).
* [ ] Se han escrito **Pruebas Unitarias** para la lógica de validación de tokens.
* [ ] Se han escrito **Pruebas de Integración** para los endpoints REST.
* [ ] El endpoint está documentado en el contrato **OpenAPI/Swagger**.
* [ ] El código pasa la validación de Checkstyle y ArchUnit.
* [ ] No introduce nuevos "Issues" severos en SonarCloud.
* [ ] El template de email de activación está creado y funcional.
