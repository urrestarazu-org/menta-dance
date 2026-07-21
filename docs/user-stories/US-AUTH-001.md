# US-AUTH-001: Registro de nuevo usuario

**ID:** US-AUTH-001
**Título:** Registro de nuevo usuario con activación de cuenta
**Módulo / API:** Auth API
**Prioridad (MoSCoW):** Must Have
**Estado:** Draft
**Épica:** EP-01 Autenticación

---

## 1. Historia de Usuario

> **Como** usuario anónimo (potencial alumno)  
> **Quiero** poder crear una cuenta proporcionando mis datos personales y validar mi correo electrónico  
> **Para** poder acceder a las plataformas de la academia (Virtual y Física) y adquirir planes.

---

## 2. Criterios de Aceptación (BDD)

**Escenario 1: Registro exitoso y envío de código de activación**

* **Dado que (Given):** Un usuario anónimo se encuentra en el endpoint `POST /api/v1/auth/register`.
* **Cuando (When):** Envía un payload válido con `firstName`, `lastName`, `birthday`, `email`, `password` y `phoneNumber`.
* **Entonces (Then):** El sistema debe crear el usuario en la tabla `menta_auth.users` con estado `PENDIENTE`.
* **Y (And):** El sistema debe generar un `activationCode` único (válido por 48h).
* **Y (And):** El sistema debe devolver un código HTTP `201 Created`.
* **Y (And):** El sistema debe publicar el evento de registro en RabbitMQ (o llamada síncrona según MVP) para enviar el email con el código de activación.

**Escenario 2: Email ya registrado**

* **Dado que (Given):** Un usuario anónimo intenta registrarse en `POST /api/v1/auth/register`.
* **Cuando (When):** Envía un payload con un `email` que ya existe en la base de datos (sin importar su estado).
* **Entonces (Then):** El sistema no debe crear el registro.
* **Y (And):** El sistema debe devolver un error HTTP `409 Conflict` con el mensaje "El correo electrónico ya se encuentra registrado".

**Escenario 3: Validación de campos requeridos**

* **Dado que (Given):** Un usuario anónimo intenta registrarse.
* **Cuando (When):** Falta enviar campos obligatorios (ej. `firstName`, `email`) o el email tiene formato inválido.
* **Entonces (Then):** El sistema debe devolver un HTTP `400 Bad Request` indicando qué campos son inválidos.

**Escenario 4: Contraseña débil (no cumple requisitos de seguridad)**

* **Dado que (Given):** Un usuario anónimo intenta registrarse en `POST /api/v1/auth/register`.
* **Cuando (When):** Envía un `password` que no cumple los requisitos mínimos (menos de 8 caracteres, sin mayúscula, o sin número).
* **Entonces (Then):** El sistema no debe crear el registro.
* **Y (And):** El sistema debe devolver un HTTP `400 Bad Request` con mensaje detallando los requisitos de contraseña no cumplidos.

---

## 3. Requisitos No Funcionales y Restricciones

* **Seguridad / Autorización:**
  * Endpoint público (no requiere token JWT).
  * La contraseña debe guardarse obligatoriamente cifrada utilizando **BCrypt** (factor 10/12).
* **Rendimiento / Rate Limiting:**
  * Máximo 3 intentos de registro por hora por IP para prevenir spam.
* **Datos y Validaciones:**
  * El password debe tener políticas de seguridad básicas (ej. mínimo 8 caracteres).
  * Se deben resetear a 0 los contadores `failedActivationAttempts` y `failedLoginAttempts`.

---

## 4. Notas Técnicas (Arquitectura)

* **Endpoints Involucrados:** `POST /api/v1/auth/register`
* **Tablas de BD (Schemas):**
  * `menta_auth.users` (donde se guarda la entidad principal).
  * `menta_auth.users_roles` (asigna por defecto el rol `ROLE_USER`).
* **Integraciones:** En la Fase 1 del MVP (sin RabbitMQ), se debe integrar de forma síncrona el servicio de envío de correos (ej. `MailService`) para notificar el `activationCode`.

---

## 5. Definition of Done (Criterios de Finalización)

Para considerar esta Historia de Usuario como terminada (lista para despliegue), debe cumplir de forma estricta:

* [ ] La lógica implementa todos los Criterios de Aceptación.
* [ ] Se han escrito **Pruebas Unitarias** para la lógica de registro (Mockeando BD y MailService).
* [ ] Se han escrito **Pruebas de Integración** probando la ruta HTTP real.
* [ ] El endpoint cumple con la guía de estilo REST y está documentado en el contrato **OpenAPI/Swagger**.
* [ ] El código pasa la validación de Checkstyle y ArchUnit (Reglas de arquitectura).
* [ ] No introduce nuevos "Issues" severos en SonarCloud.
