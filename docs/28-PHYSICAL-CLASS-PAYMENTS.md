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
`EXCEPTION` y no se habilita asistencia hasta una resolución administrativa.

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
