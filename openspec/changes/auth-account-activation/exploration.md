# Exploración: activación de cuentas

## Estado actual

`develop` ya contiene registro público, login JWT, rotación de refresh, logout,
outbox de revocación y custodia de sesión en el BFF. Sin embargo, el registro
actual persiste al usuario directamente como `ACTIVE` mediante `User.create()` y
no existe token, endpoint ni entrega de correo para activación.

Esto contradice `US-AUTH-001` y `US-AUTH-003`: una cuenta recién registrada debe
permanecer pendiente, recibir un token de un solo uso y no poder iniciar sesión
hasta activarse. También faltan el reenvío no enumerativo, rate limiting y las
rutas planificadas `GET /api/v1/auth/activate/{token}` y
`POST /api/v1/auth/resend-activation`.

## Áreas afectadas

- `api:auth/domain`: estado pendiente y ciclo de vida del token de activación.
- `api:auth/application`: registro transaccional, activación y reenvío.
- `api:auth/infrastructure`: JPA, hashing/generación de secretos, controladores,
  rate limiting Redis y adaptador SMTP.
- `api:app`: migración Flyway V3 y dispatch durable de eventos de activación.
- `docker-compose.yml`: SMTP local capturable para verificar entrega sin enviar
  correo real.
- `bruno/`: flujo registro → captura de correo → activación → login.
- `docs/03-AUTH-API.md`, `docs/22-DATA-MODEL.md` y OpenAPI: contrato final.

## Restricciones detectadas

1. `common_outbox_events` ya es durable, pero el worker actual procesa **toda**
   fila pendiente como una escritura de blacklist. Agregar un evento de correo
   sin routing corrompería Redis y marcaría la entrega como completada sin enviar
   ningún correo.
2. `UNIQUE (aggregate_id, event_type)` impide repetir el mismo evento para el
   mismo aggregate. Cada emisión deberá usar el `activationTokenId` como
   aggregate para permitir reenvíos sin perder idempotencia.
3. Flyway es forward-only: el esquema debe agregarse en V3 y cualquier reversión
   debe hacerse mediante una migración compensatoria posterior.
4. Los tokens en el path pueden aparecer en access logs. La implementación debe
   redaccionar la ruta de activación y nunca registrar token, hash ni correo en
   claro.
5. No hay infraestructura SMTP local ni puerto de notificación existente.

## Alternativas

### A. Envío SMTP sincrónico durante el registro

- Ventaja: menor cantidad de código.
- Desventaja: mezcla una llamada externa con la transacción; un timeout puede
  dejar una cuenta creada sin entrega o provocar reintentos duplicados.
- Decisión: descartada.

### B. Evento en memoria después del commit

- Ventaja: desacopla SMTP de la transacción.
- Desventaja: un crash entre commit y envío pierde definitivamente el correo.
- Decisión: descartada para una operación Must Have.

### C. Token + evento durable en la misma transacción

- Ventaja: registro, token y solicitud de entrega se confirman atómicamente; el
  worker puede reintentar de forma idempotente.
- Desventaja: requiere convertir el reconciler actual en dispatch por tipo.
- Decisión: recomendada.

## Recomendación

Implementar un slice vertical en `:api:auth` con token opaco aleatorio de 256
bits, hash SHA-256 en MySQL, TTL configurable de 24 horas y consumo de un solo
uso. Registro y reenvío escribirán `auth.AccountActivationRequested` en
`common_outbox_events` dentro de la misma transacción. `:api:app` despachará por
tipo a handlers explícitos: blacklist para eventos existentes y SMTP para
activación.

El registro público deberá usar `User.register()` y persistir
`PENDING_ACTIVATION`; `User.create()` se conservará temporalmente como factory de
provisión activa para no alterar fixtures y flujos administrativos. Login deberá
rechazar cuentas pendientes con el mismo error genérico usado para credenciales
inválidas.

## Riesgos

- Generalizar el outbox puede afectar revocaciones de seguridad existentes.
- Un endpoint GET con secreto exige redacción explícita en API/NGINX.
- El reenvío puede abusarse para spam o enumeración si el rate limiter no usa
  respuesta uniforme y claves derivadas.
- Concurrencia entre dos activaciones debe resolverse por update condicional y
  token de un solo uso, no sólo por validación en memoria.

## Preparado para propuesta

Sí. Los contratos funcionales están documentados y las brechas actuales fueron
verificadas en código y migraciones.
