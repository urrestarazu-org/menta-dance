# API de Facturación y Pagos

Billing es dueño de planes, precios, suscripciones, quotes, compras, pagos,
webhooks y conciliación. Physical conserva sesiones/cupos; Virtual conserva
contenido/progreso.

## Rutas canónicas

```text
GET  /api/v1/billing/plans
GET  /api/v1/billing/plans/{planId}
POST /api/v1/billing/subscriptions
GET  /api/v1/billing/subscriptions/me
GET  /api/v1/billing/subscriptions/me/history
GET  /api/v1/billing/payments
GET  /api/v1/billing/payments/{paymentId}
POST /api/v1/billing/physical/quotes
POST /api/v1/billing/physical/purchases
GET  /api/v1/billing/physical/courses/{courseId}/pricing
PUT  /api/v1/billing/physical/courses/{courseId}/pricing
POST /api/v1/billing/payments/mercadopago/webhook
GET  /api/v1/billing/admin/reconciliation
POST /api/v1/billing/admin/reconciliation/{paymentId}/resolve
GET  /api/v1/billing/admin/payments
GET  /api/v1/billing/admin/payments/{paymentId}
POST /api/v1/billing/admin/payments/{paymentId}/approve
POST /api/v1/billing/admin/payments/{paymentId}/reject
POST /api/v1/billing/admin/payments/{paymentId}/corrections
POST /api/v1/billing/payments/{paymentId}/proof
```

Las rutas `/me` y `/payments` son autoservicio del alumno; las de conciliación
son exclusivas de `ADMIN`. Los errores usan `application/problem+json`.

## Contrato HTTP implementado (v1)

| Endpoint | Autenticación de entrada | Respuesta exitosa |
| --- | --- | --- |
| `GET /plans` | Ninguna (público) | `200` con `{ plans: [...] }`, `[]` si no hay planes activos |
| `GET /plans/{planId}` | Ninguna (público) | `200` con el detalle completo del plan |
| `POST /subscriptions` | Access token válido | `201` con la suscripción pendiente y la URL de Checkout Pro |

`GET /plans` sólo devuelve planes con `status = ACTIVE`, ordenados por precio
ascendente; el destacado se indica con el campo `featured`, no con el orden.
`GET /plans/{planId}` responde `404 PLAN_NOT_FOUND` tanto para un id
inexistente como para uno inactivo — el motivo real nunca es distinguible
desde afuera. Ambos endpoints comparten un presupuesto de 60 solicitudes por
minuto por IP (`429 PLAN_RATE_LIMITED` al agotarse); si el limitador no puede
alcanzar Redis, responden `503 BILLING_DEGRADED` en vez de dejar pasar
tráfico sin límite.

`billing_plan_courses` referencia cursos de Virtual/Physical por `course_id`
sin FK ni JOIN (`docs/25-ARCHITECTURE-RULES.md`). El nombre de cada curso se
resuelve vía `CourseCatalogPort` (#108): `api:app` consulta Virtual y Physical
en una sola búsqueda por módulo por solicitud, sin importar cuántos planes o
cursos tenga la respuesta. `courses[].name` es el título del curso cuando es un
curso virtual `PUBLISHED` o un curso presencial `ACTIVE`; si el mismo id
existe en ambos módulos gana el virtual (Physical sólo se consulta por los ids
que Virtual no resolvió).

Cualquier otro caso — id inexistente, curso virtual `DRAFT` o `ARCHIVED`,
curso presencial inactivo o un id que no es un UUID — devuelve `name: null`
con el `id` guardado: el curso no se quita de `courses` y la respuesta sigue
siendo `200` con el plan completo. Si un módulo falla, sólo se degradan a
`null` los cursos que ese módulo habría respondido (se registra un `WARN` con
el módulo y los ids afectados); el otro módulo conserva sus nombres.

## Checkout de suscripción virtual (US-BILLING-010)

`POST /api/v1/billing/subscriptions` inicia un checkout para el usuario del
access token. El body es `{ "planId": UUID, "paymentMethod": "MERCADO_PAGO",
"idempotencyKey": string }`; no acepta `userId`. La clave tiene un máximo de
128 caracteres y una repetición con el mismo usuario devuelve el checkout ya
creado, sin abrir otro cobro.

Este endpoint implementa exclusivamente Checkout Pro: `BANK_TRANSFER` es
inválido aquí (`400 INVALID_REQUEST`) hasta que US-BILLING-003 incorpore su
flujo propio de datos bancarios y comprobantes. Nunca se transforma una
transferencia en una preferencia de Mercado Pago.

La respuesta `201` es `{ subscriptionId, paymentId, planId, status,
providerPreferenceId, externalReference, checkoutUrl }`. `status` comienza en
`PENDING`; `providerPreferenceId` identifica la preferencia de Checkout Pro y
`checkoutUrl` es la URL a la que el cliente debe redirigir al alumno.
`paymentId`, `providerPreferenceId` y `externalReference` son identificadores
distintos. El `providerPaymentId` todavía no existe y nunca se expone aquí.

Errores: `400 INVALID_REQUEST` para un body inválido, `401` sin autenticación,
`409 SUBSCRIPTION_ALREADY_ACTIVE` si el usuario ya tiene un checkout pendiente
o una suscripción activa, `422 PLAN_NOT_AVAILABLE` para un plan inexistente o
inactivo y `422 PAYMENT_METHOD_NOT_ACCEPTED` (incluye
`acceptedPaymentMethods`). Si Mercado Pago no puede crear la preferencia,
responde `503 PAYMENT_PREFERENCE_UNAVAILABLE`; la transacción local se revierte
y no se reintenta automáticamente.

La URL de retorno de Mercado Pago sólo devuelve navegación al cliente. La fuente
de verdad es el webhook firmado: Billing consulta el pago, lo correlaciona con
`externalReference`, valida cuenta, importe y moneda, y recién entonces activa
la suscripción y congela su snapshot de cursos.

## Estados e idempotencia

`Payment` usa un estado principal y, cuando está pendiente, un subestado.
Estados principales: `PENDING`, `COMPLETED`, `REJECTED`, `CANCELLED` y `EXPIRED`;
los últimos cuatro son terminales. Subestados de `PENDING`:
`AWAITING_PROVIDER`, `AWAITING_MANUAL_VERIFICATION` y
`RECONCILIATION_REQUIRED`. Transferencia inicia como
`PENDING/AWAITING_MANUAL_VERIFICATION`; un rechazo del proveedor o de ADMIN
termina en `REJECTED`, una cancelación explícita en `CANCELLED` y un deadline sin
confirmación en `EXPIRED`. La respuesta del proveedor se mapea así: `approved` a
`COMPLETED`, `rejected` a `REJECTED`, `cancelled` a `CANCELLED`, `expired` a
`EXPIRED` y `pending`/`in_process` a `PENDING/AWAITING_PROVIDER`.
`RECONCILIATION_REQUIRED` sólo representa mismatch, resultado desconocido o falla
al consultar al proveedor. Un pago confirmado que no logra asignar capacidad queda
`COMPLETED` porque representa liquidación financiera. Su Purchase inicia
`PENDING_FULFILLMENT` y sólo luego pasa a `ASSIGNED` o `EXCEPTION`; no se habilita
asistencia hasta `ASSIGNED`. `EXPIRED` no habilita suscripción ni asigna cupo.

Cuando una Purchase presencial pasa a `EXCEPTION`, el evento `billing.PurchaseExceptioned`
(y la notificación operativa) lleva el campo `reason` con el motivo codificado:
`CAPACITY_BELOW_ASSIGNED` (la capacidad real de una sesión no alcanza para asignar al
alumno), `ALREADY_ASSIGNED` (el alumno ya tiene confirmada una de las sesiones a asignar,
aunque sobre cupo; lo detecta la unicidad `(session_id, student_id)`), `HOLD_EXPIRED`,
`COVERAGE_CHANGED`, `TARGET_NOT_SCHEDULED` y `UNIQUE_COLLISION`. Quien investiga una compra
en `EXCEPTION` debe usar ese motivo: `ALREADY_ASSIGNED` no indica falta de capacidad.

Las transiciones originadas por proveedor son monotónicas: un webhook tardío o
duplicado nunca puede retroceder ni reemplazar un estado ya aplicado. Sólo
`POST /api/v1/billing/admin/payments/{paymentId}/corrections` puede aplicar una
corrección excepcional; requiere `ADMIN`, motivo, evidencia y auditoría
append-only.

La creación de un pago externo **nunca** se reintenta automáticamente. Ante
incertidumbre se persiste la clave de idempotencia, correlación del proveedor,
request seguro redactado y estado `PENDING/RECONCILIATION_REQUIRED`; se consulta al
proveedor y un administrador resuelve el caso. Al recibir un webhook,
Billing verifica HMAC-SHA256 de `x-signature` para el manifiesto que incluye
`data.id`, `x-request-id` y timestamp mediante comparación timing-safe, y rechaza
timestamps con más de cinco minutos. Inmediatamente después persiste y deduplica
la notificación validada como `RECEIVED` en `billing_webhook_inbox`; un duplicado
responde `200 OK` sólo tras comprobar ese inbox durable. Un worker consulta el
pago en Mercado Pago usando `data.id` y exige, para **todo** estado del proveedor,
coincidencia exacta con la compra esperada: referencia externa, cuenta/merchant,
importe y moneda. Luego mapea el estado recibido según la tabla anterior. Los
fallos de consulta quedan retenidos en inbox para reintento; una discrepancia crea
`PENDING/RECONCILIATION_REQUIRED` sin aplicar efectos. Sólo una verificación
exitosa y la transición monotónica se aplican transaccionalmente.
El inbox conserva `attempt_count` y `next_attempt_at`: al agotar los reintentos,
el pago asociado pasa a `PENDING/RECONCILIATION_REQUIRED` y se crea una tarea
administrativa; si no existe pago local asociado, la tarea referencia el inbox y
el evento del proveedor. Redis puede acelerar protección contra replay, pero no
es la fuente de verdad.

## Incidentes de base de datos

No se promete RPO 0 financiero. Después de un incidente de BD, un administrador
reconcilia manualmente pagos contra Mercado Pago usando `providerPaymentId`,
correlation IDs, inbox/outbox y auditoría. Se retienen por 90 días logs
estructurados y redactados, auditoría de pagos e incidentes.

## Pagos presenciales

La fórmula, snapshots, capacidad y casos de borde están definidos en
[28-PHYSICAL-CLASS-PAYMENTS.md](28-PHYSICAL-CLASS-PAYMENTS.md). Billing coordina
con Physical mediante puertos/eventos internos; no hay HTTP ni broker interno.
El quote expone una proyección informativa de disponibilidad, sin reserva. Antes
de crear un `Payment` o iniciar el proveedor, `api:app` solicita a Physical un
`CapacityHold` atómico. Si no hay cupo, responde `409 CAPACITY_UNAVAILABLE` y no
se crea pago. El hold reduce, pero no elimina, la excepción posterior si la
cobertura mensual calculada al confirmar cambia o el hold expira. La cobertura
mensual saltea las sesiones donde el alumno ya tiene asignación confirmada y se
extiende hacia adelante (hasta 120 días) para completar las N sesiones compradas.
Si el alumno ya tiene **todas** las sesiones que la mensualidad cubriría, el checkout
(Mercado Pago o transferencia) responde `409 NO_NEW_SESSIONS` antes de crear hold o
`Payment`, igual que la cotización (#541).

## Pricing presencial

`GET /billing/physical/courses/{courseId}/pricing` consulta la configuración
vigente. `PUT /billing/physical/courses/{courseId}/pricing` permite a `PROFESOR`
propietario o `ADMIN` cambiar `monthlyPrice` e `individualSurchargePercent` con
motivo. Billing valida ownership mediante puerto, versiona el pricing y registra
una revisión append-only; los quotes existentes no cambian.

## Cotización de curso presencial (US-BILLING-006)

`POST /billing/physical/quotes` requiere cualquier usuario autenticado (sin
restricción de rol) y crea un quote inmutable, válido durante **una hora**.
Body: `{ "courseId": string, "purchaseType": "MONTHLY" | "INDIVIDUAL",
"selectedSessionId": string | null }`. `selectedSessionId` es obligatorio para
`INDIVIDUAL` y no debe enviarse para `MONTHLY` (`422
SELECTED_SESSION_REQUIRED` / `422 SELECTED_SESSION_NOT_ALLOWED`).

El período cotizado es siempre un mes calendario según el reloj del servidor
(`ZoneOffset.UTC`, sin campo de fecha en el request): para `MONTHLY` es el mes
calendario "actual"; para `INDIVIDUAL` es el mes calendario que contiene el
`scheduledAt` de la sesión elegida (no necesariamente el mes "actual" cerca de
un cambio de mes).

Respuesta (`201`): `{ id, courseId, purchaseType, scheduledSessionCount,
selectedSessionId, amount, currency, availability, pricingVersion, createdAt,
expiresAt }`. `MONTHLY` no fija `selectedSessionId` ni cobertura — sólo el
importe mensual snapshot y una proyección informativa de `availability`.
`INDIVIDUAL` divide el precio mensual entre `scheduledSessionCount` con alta
precisión, aplica el recargo y redondea `HALF_UP` a dos decimales; si el
resultado no supera por al menos un centavo el precio por sesión sin recargo,
responde `422 INDIVIDUAL_SURCHARGE_TOO_SMALL` y no persiste nada.

Errores: `404 PHYSICAL_COURSE_PRICING_NOT_FOUND` (sin pricing publicado),
`404 PHYSICAL_SESSION_NOT_FOUND` (`selectedSessionId` fuera del período),
`422 NO_SCHEDULED_SESSIONS` (sin sesiones `SCHEDULED` en el período),
`409 NO_NEW_SESSIONS` (sólo `MONTHLY`: el alumno autenticado ya tiene asignación
confirmada en todas las sesiones que la mensualidad cubriría; no persiste el quote).
La disponibilidad (`AVAILABLE`/`UNAVAILABLE`) es sólo informativa para la UI — el
checkout (fuera de esta historia) es quien reserva o rechaza cupo de verdad.

## Cotizar y pagar clases presenciales desde la web (BFF, #365)

Un alumno autenticado cotiza y paga desde el BFF (plantillas Thymeleaf, sin JavaScript):

1. `/classes/{courseId}` consulta el precio con `GET /api/v1/billing/physical/courses/{courseId}/pricing`
   (público: no envía token y no se cachea, porque el precio es por curso y un admin puede
   editarlo) y muestra el precio mensual en es-AR con su moneda ("$ 25.000,00 por mes"). No
   muestra un precio individual: depende de las sesiones del período y lo calcula Billing al
   cotizar. Con precio publicado, el alumno ve "Cotizar clases del mes" y, por cada sesión con
   lugares, "Cotizar solo esta clase" (una sesión llena no tiene botón); un visitante anónimo ve
   el enlace "Ingresá para comprar" hacia `/login`. Sin precio publicado (`404
   PHYSICAL_COURSE_PRICING_NOT_FOUND`) la página se muestra igual con el aviso "Todavía no hay un
   precio publicado para esta clase." y sin botones de cotizar (tampoco "Ingresá para comprar");
   si el precio no se puede consultar (`429`, `5xx`, cuerpo ilegible o error de red) se muestra
   un aviso breve, también sin botones. En ningún caso se rompe el detalle ni se muestra el
   cuerpo del error de Billing.
2. `POST /classes/{courseId}/cotizar` (`purchaseType`, y `selectedSessionId` si es individual)
   llama a `POST /api/v1/billing/physical/quotes` con el access token del alumno y muestra la
   cotización: importe en formato es-AR, sesiones del período y vencimiento (zona
   `menta.classes.display-zone`). Si `availability` es `UNAVAILABLE` muestra "Sin lugares
   disponibles" y no hay botón de pago; si hay lugares, el botón "Pagar con Mercado Pago".
   `422 NO_SCHEDULED_SESSIONS`, los `404` de precio o sesión, los `400` y los errores de
   servicio se muestran como avisos en español con el enlace de vuelta al detalle.
3. `POST /compras` (`quoteId`, `courseId` y un `idempotencyKey` UUID que el servidor genera al
   renderizar la cotización) llama a `POST /api/v1/billing/physical/purchases` con
   `MERCADO_PAGO`. `409 CAPACITY_UNAVAILABLE` ("Ya no hay lugares", no se creó ningún pago) y
   `410 PHYSICAL_COURSE_QUOTE_EXPIRED` ("La cotización venció, pedí una nueva") tienen aviso
   propio; `400`, `429` y `5xx` se muestran con un mensaje genérico. Repetir la misma
   `idempotencyKey` devuelve el checkout original.
4. El BFF **guarda el `paymentId` en la sesión del servidor (Redis/Spring Session)** y redirige
   (302) a `checkoutUrl`.
5. Mercado Pago devuelve al alumno a `GET /compras/retorno`. El BFF lee el `paymentId` **de la
   sesión** (nunca de la URL), consulta `GET /api/v1/billing/payments/{paymentId}` con el token
   del alumno y muestra el estado: `COMPLETED` "¡Pago confirmado! Estamos asignando tus
   lugares" (no afirma que las sesiones ya estén asignadas); `AWAITING_PROVIDER`,
   `AWAITING_MANUAL_VERIFICATION`, `RECONCILIATION_REQUIRED` o cualquier estado desconocido
   "Estamos confirmando tu pago" con el enlace "Actualizar" a la misma URL (un estado
   desconocido nunca se muestra como éxito); `REJECTED`, `CANCELLED` y `EXPIRED` con mensajes
   propios y enlace a `/classes`. Un estado final borra el `paymentId` de la sesión; uno
   pendiente lo conserva. Sin `paymentId` en la sesión se redirige a `/classes`, y un `5xx` o
   `429` de la API muestra un aviso en español sin tocar la sesión. Las respuestas llevan
   `Cache-Control: no-store`.

Decisiones:

- **Sesión en vez de `state` firmado.** El BFF ya custodia una sesión de servidor por alumno,
  así que el vínculo "este navegador pagó este id" no necesita un parámetro firmado en la URL.
  El retorno **nunca decide el pago**: solo navega y muestra lo que informa la API, e ignora
  `status`, `payment_id` y cualquier otro parámetro que agregue Mercado Pago (la confirmación
  real llega por el webhook). El control de propiedad lo da la API: pedir el pago de otro
  usuario responde siempre 404 (nunca 403); el BFF entonces limpia la sesión y redirige a
  `/classes`. Limitación: una sola
  compra en curso por sesión; iniciar otra reemplaza el `paymentId` guardado.
- **Política de `checkoutUrl`.** El navegador se redirige a lo que informe la API, así que solo
  se sigue una URL absoluta `https` con host, o `http` si el host es `localhost`, `127.0.0.1`
  o `[::1]` (simulador local). Cualquier otra se rechaza como servicio no disponible y no se
  guarda nada en la sesión.
- El access token viaja solo en el header `Authorization` y nunca se registra en logs.

Configuración: `billing.mercadopago.checkout-back-url` (variable
`BILLING_MERCADOPAGO_CHECKOUT_BACK_URL`) debe apuntar a `/compras/retorno` del BFF; en local
`http://localhost:8080/compras/retorno` (ver `.env.example`). Vacía, la preferencia no lleva
`back_urls`. En producción debe ser el origen público `https` del BFF.

Fuera de este corte: mostrar las sesiones asignadas tras el pago y el pago por transferencia
bancaria.

## Transferencias bancarias

Una transferencia crea `PENDING/AWAITING_MANUAL_VERIFICATION` y recibe un comprobante privado.
Sólo `ADMIN` puede listar, ver detalle, aprobar o rechazar estos pagos; las
decisiones son idempotentes y auditadas. Aprobar habilita los efectos de compra;
rechazar requiere motivo y no habilita acceso ni cupo.
