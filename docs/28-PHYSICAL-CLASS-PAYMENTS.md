# Pagos de Clases Presenciales

## Reglas de negocio

- Una **sesión** es la ocurrencia concreta de un curso con fecha, hora y cupo.
- El pago confirmado crea automáticamente una asignación por cada sesión
  alcanzada; no hay reserva manual ni lista de espera.
- Un plan mensual habilita desde la fecha de pago hasta el mismo día del mes
  siguiente. Sólo consume cupos de las sesiones incluidas en ese período.
- Un pago individual cubre una sola sesión.

## Precio mensual e individual

Cada profesor configura por curso `monthlyPrice` y
`individualSurchargePercent`. Para un período:

```text
sessionCount = sesiones SCHEDULED dentro del período
monthlyUnitPrice = monthlyPrice / sessionCount
individualPrice = monthlyUnitPrice * (1 + surchargePercent / 100)
```

Los importes se calculan en centavos y se redondean a dos decimales con
`HALF_UP`. Debe cumplirse `individualPrice >= roundedMonthlyUnitPrice + $0.01`.
El quote guarda un snapshot de precio y recargo. Ejemplo con recargo 10%:
septiembre 2026 tiene 5 martes; $5000/5 = $1000 mensual y $1100 individual.
octubre tiene 4 martes; $5000/4 = $1250 mensual y $1375 individual.

## Cotización, capacidad y pago

`POST /api/v1/billing/physical/quotes` crea una cotización informativa e
inmutable válida por una hora. Además calcula una **proyección no vinculante** de
disponibilidad para orientar la UI: no reserva cupos ni garantiza que sigan
disponibles al checkout. El snapshot fija sólo el importe y sus parámetros de
precio; nunca se recalcula al confirmar el pago. Una cotización vencida exige otra
nueva.

Al iniciar el checkout, `api:app` solicita a Physical crear atómicamente un
`CapacityHold` para la capacidad proyectada. Si no puede crearlo, responde
`409 CAPACITY_UNAVAILABLE` **sin** crear `Payment` ni iniciar un pago externo. El
hold es técnico, invisible al alumno, cuenta contra disponibilidad y su expiración
nunca supera el deadline informado por el proveedor de pago.

Tras crear el hold, `Payment` queda `PENDING`. Para un mensual, al confirmarse
el pago Billing fija
`coverageStartDate` con el timestamp de confirmación, calcula
`coverageEndExclusive = coverageStartDate.plusMonths(1)` y obtiene las sesiones
`SCHEDULED` elegibles de ese período. Luego revalida atómicamente capacidad y
conserva, ajusta o convierte el hold para esas sesiones reales en
`physical_capacity_assignment`. Un mensual reduce un cupo en cada sesión
elegible; un individual conserva su única sesión elegida. La confirmación del
proveedor marca Payment `COMPLETED` de inmediato como liquidación financiera y
la Purchase pasa a `PENDING_FULFILLMENT`. Sólo después de crear todas las
asignaciones la Purchase queda `ASSIGNED` y se habilita asistencia. Si no se puede
asignar todo el conjunto real, Payment permanece `COMPLETED`, la Purchase queda
`EXCEPTION` y no se habilita asistencia hasta una resolución administrativa. El
motivo distingue la capacidad insuficiente (`CAPACITY_BELOW_ASSIGNED`) de que el alumno
ya tenga la sesión (`ALREADY_ASSIGNED`); ver `docs/06-BILLING-API.md`.

Un mensual cubre las próximas N sesiones **que el alumno todavía no tiene**: se
saltean las sesiones donde ya tiene una asignación confirmada (por ejemplo, de una
compra individual) y la búsqueda se extiende hacia adelante, hasta 120 días desde el
instante de referencia, para completar N. El importe del quote no cambia. Si no hay N
sesiones sin asignar dentro de ese horizonte, el checkout responde
`409 CAPACITY_UNAVAILABLE`. Un individual no se ve afectado.

### Mensualidad sin sesiones nuevas (#541)

Decisión de producto: **una mensualidad que no agrega ninguna sesión al alumno no se
vende**. Si agrega al menos una, se cobra el precio completo del quote (sin prorrateo).

- "No agrega ninguna sesión" significa que el alumno ya tiene asignación confirmada en
  **todas** las sesiones que la mensualidad cubriría: las próximas N sesiones del curso
  desde el instante de referencia, contadas sin saltear ninguna y sin mirar el cupo. Que
  existan sesiones posteriores no cambia el resultado: si ya tiene las N que corresponden,
  no hay nada para comprar.
- **Cotización mensual**: se rechaza con `409 NO_NEW_SESSIONS` y no persiste ningún quote
  (ver `docs/06-BILLING-API.md`).
- **Checkout** (Mercado Pago y transferencia bancaria): se aplica la misma regla, con el
  mismo código `409 NO_NEW_SESSIONS`, antes de crear el hold o el `Payment`. Cubre la ventana
  entre la cotización y el checkout y las cotizaciones emitidas antes de esta regla.
- **Propiedad parcial**: si el alumno tiene algunas de las sesiones cubiertas, pero no todas,
  la compra sigue igual que hoy: precio completo, `scheduledSessionCount` sin cambios y
  cobertura completada hacia adelante (#523).
- **Atomicidad**: la lectura de las sesiones que el alumno ya tiene y la creación del hold no
  son una única transacción; la ventana restante es la misma que ya describe la sección
  siguiente (#533), no se agrega una nueva.
- Las compras **individuales** no cambian con esta decisión.

### Ventana entre checkout y confirmación

El conjunto de sesiones de una mensualidad se fija en el checkout: allí se leen las
sesiones que el alumno ya tiene y se crean los holds. Si **entre el checkout y la
confirmación del pago** el alumno recibe una asignación sobre una de las sesiones
retenidas (por ejemplo, una compra individual de esa sesión o una asignación
administrativa), el conjunto ya no es válido. Al confirmar, la conversión del hold choca
con la unicidad `(session_id, student_id)` y se reporta como `ALREADY_ASSIGNED`.

La ventana es angosta: sólo existe mientras el pago está pendiente (el hold nunca supera
el deadline del proveedor) y exige que el mismo alumno obtenga por otra vía una sesión
que justo estaba retenida para él. Reabrir el conjunto al confirmar obligaría a reservar
capacidad de reemplazo y rompería la regla de que el conjunto queda fijo en el checkout;
por eso no se hace (decisión de #533, V0).

Resultado, sin cambios de comportamiento:

- Payment queda `COMPLETED`: el dinero fue acreditado.
- Purchase queda `EXCEPTION` y `billing.PurchaseExceptioned` lleva `reason = ALREADY_ASSIGNED`.
- No se crea ninguna asignación de la compra (todo o nada): las demás sesiones retenidas
  tampoco se asignan y los holds no se convierten. El alumno conserva únicamente la
  asignación previa que provocó el conflicto.
- No se habilita asistencia por esta compra.

Resolución: no se resuelve automáticamente. El pasaje a `EXCEPTION` envía el aviso al
alumno y a la dirección operativa (esta última con el `reason`) y la compra aparece en
`GET /api/v1/admin/billing/purchases?status=EXCEPTION`, inventario de sólo lectura para
`ADMIN`. Hoy el sistema no ofrece un endpoint para reasignar ni para cerrar la compra:
el equipo de operaciones decide el tratamiento (por ejemplo, devolución o compensación)
fuera del producto, y el motivo `ALREADY_ASSIGNED` indica que el problema no es de
capacidad. Ver `docs/06-BILLING-API.md` y la prueba de integración
`PhysicalPurchaseIntegrationTest`.

La compra debe iniciarse antes del vencimiento del quote. La confirmación posterior
conserva el precio snapshot, pero cobertura y sesiones elegibles se derivan del
timestamp confirmado y se revalidan; no quedan congelados por el quote ni por su
proyección de disponibilidad. Por eso el hold reduce excepciones, pero no elimina
el caso excepcional de cambio de cobertura, vencimiento del hold o concurrencia.

## Estados y conciliación

`Payment` usa principal `PENDING`, `COMPLETED`, `REJECTED`, `CANCELLED` o
`EXPIRED`; los pendientes pueden tener `AWAITING_PROVIDER`,
`AWAITING_MANUAL_VERIFICATION` o `RECONCILIATION_REQUIRED`. Sólo mismatch,
resultado desconocido o falla de proveedor usan conciliación. `EXPIRED` no asigna
cupo ni acceso.

Las transiciones aplicadas por el proveedor son monotónicas. Un webhook tardío o
duplicado no puede cambiar un estado final ni reducirlo. Una corrección sólo la
puede realizar `ADMIN` mediante el endpoint auditado de correcciones, con motivo
y evidencia.

Un timeout externo nunca se reintenta automáticamente. Se persiste la clave de
idempotencia/evidencia y se crea una tarea
`PENDING/RECONCILIATION_REQUIRED` para ADMIN.
Los webhooks pasan por inbox idempotente; la recuperación tras incidente de BD
se concilia manualmente con Mercado Pago, auditoría y logs correlacionados.

## QR y asistencia

El check-in exige asignación confirmada. Los lectores QR son dispositivos con
secreto hasheado, expiración, revocación y rotación. Redis usa lock nonce + Lua
compare-and-delete; al estar caído, no se procesa la solicitud autenticada.
