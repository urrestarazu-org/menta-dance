# Especificación: activación de cuentas

## ADDED Requirements

### Requirement: Registro pendiente y entrega durable

El sistema **MUST (DEBE)** persistir todo registro público como
`PENDING_ACTIVATION`. Usuario, token hasheado y evento de entrega **DEBEN**
confirmarse en la misma transacción.

#### Scenario: Registro exitoso

- **Dado** un email válido no registrado.
- **Cuando** el cliente registra una cuenta pública.
- **Entonces** el usuario queda pendiente y no puede iniciar sesión.
- **Y** se persiste un token de activación con expiración.
- **Y** se agrega exactamente un evento durable de entrega.

#### Scenario: Rollback atómico

- **Dado** que no puede persistirse el token o el evento.
- **Cuando** se intenta registrar la cuenta.
- **Entonces** no se confirma ni el usuario, ni el token, ni el evento.

### Requirement: Secreto de activación

El token **MUST (DEBE)** tener al menos 256 bits de entropía, **DEBE** codificarse de
forma URL-safe y **NUNCA DEBE** persistirse ni registrarse en claro. Su hash
SHA-256 **DEBE** ser único.

#### Scenario: Almacenamiento seguro

- **Dado** un token recién emitido.
- **Cuando** se consulta MySQL y los logs.
- **Entonces** sólo existe el hash en MySQL y el valor en claro no aparece en
  logs, errores ni métricas.

### Requirement: Activación de un solo uso

`GET /api/v1/auth/activate/{token}` **MUST (DEBE)** validar sin efectos una cuenta
pendiente asociada a un token válido, vigente y no consumido. `POST /api/v1/auth/activate`
con un cuerpo JSON que contiene `token` **MUST (DEBE)** ejecutar la activación y consumir el
token. Retirar el token de la URI requiere una pantalla intermedia y queda fuera de alcance.

#### Scenario: Token válido

- **Dado** un token vigente de una cuenta pendiente.
- **Cuando** se presenta el token.
- **Entonces** el usuario pasa a `ACTIVE` y el token queda consumido en la misma
  transacción.

#### Scenario: Reutilización

- **Dado** un token ya consumido.
- **Cuando** se presenta nuevamente.
- **Entonces** el sistema responde un Problem Details genérico y no modifica la
  cuenta.

#### Scenario: Expiración o invalidación

- **Dado** un token expirado o invalidado por reenvío.
- **Cuando** se presenta.
- **Entonces** el sistema rechaza la activación y no modifica la cuenta.

#### Scenario: Activación concurrente

- **Dado** un token válido presentado simultáneamente dos veces.
- **Cuando** ambas transacciones compiten.
- **Entonces** sólo una transición puede confirmarse.

### Requirement: Reenvío no enumerativo

`POST /api/v1/auth/resend-activation` **MUST (DEBE)** devolver la misma respuesta para
email inexistente, activo o pendiente. Sólo una cuenta pendiente **PUEDE**
recibir un nuevo token.

#### Scenario: Cuenta pendiente

- **Dado** un usuario pendiente dentro del límite permitido.
- **Cuando** solicita reenvío.
- **Entonces** los tokens vigentes anteriores quedan invalidados.
- **Y** se emite un nuevo token y evento durable.

#### Scenario: Email inexistente o activo

- **Dado** un email inexistente o una cuenta ya activa.
- **Cuando** solicita reenvío.
- **Entonces** la respuesta observable coincide con la de una cuenta pendiente.
- **Y** no se crea token ni evento.

### Requirement: Rate limiting

Registro y reenvío **MUST (DEBEN)** limitarse por fingerprint de email e IP. Las keys
Redis **NO DEBEN** contener email en claro. Si Redis no está disponible, estas
operaciones **DEBEN** fallar cerradas con `503`.

#### Scenario: Límite excedido

- **Dado** que una identidad o IP supera el límite configurado.
- **Cuando** intenta registrar o reenviar.
- **Entonces** recibe `429` con `Retry-After` sin revelar existencia de cuenta.

### Requirement: Login pendiente

Una cuenta `PENDING_ACTIVATION` **MUST NOT (NO DEBE)** recibir tokens de sesión. La respuesta
**DEBE** ser indistinguible de credenciales inválidas.

#### Scenario: Login antes de activar

- **Dado** un usuario pendiente con contraseña correcta.
- **Cuando** intenta iniciar sesión.
- **Entonces** recibe el mismo `401 application/problem+json` que una contraseña
  incorrecta y no se escribe refresh ni outbox de login.

### Requirement: Dispatch seguro del outbox

Cada evento **MUST (DEBE)** ser procesado sólo por su handler declarado. Un tipo
desconocido **NO DEBE** producir efectos secundarios ni marcarse `COMPLETED`.

#### Scenario: Evento de activación

- **Dado** `auth.AccountActivationRequested` pendiente.
- **Cuando** corre el reconciler.
- **Entonces** se invoca el puerto de notificación y no se escribe una blacklist.

#### Scenario: Evento desconocido

- **Dado** un `event_type` sin handler.
- **Cuando** corre el reconciler.
- **Entonces** queda `FAILED` con diagnóstico sanitizado y backoff.
