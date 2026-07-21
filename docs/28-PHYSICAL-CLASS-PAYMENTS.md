# Pagos de Clases Presenciales

[← Volver al índice](./README.md)

Esta es la única autoridad funcional vigente para precios, quotes, Purchases,
Payments y asignación de cupos presenciales. El documento 09 es histórico.

## Glosario

- **Curso presencial:** oferta recurrente de un profesor con día, horario y
  capacidad máxima.
- **Sesión:** ocurrencia concreta del curso con fecha, hora y cupo.
- **Quote:** cotización persistida para una única modalidad `MONTHLY` o
  `INDIVIDUAL`.
- **Purchase:** intención de compra que referencia un quote inmutable.
- **Payment:** movimiento financiero de una Purchase.
- **CapacityHold:** retención técnica interna, breve y expirable; no es una
  reserva visible o accionable por el alumno.
- **CapacityAssignment:** cupo confirmado para una sesión.

No existen reservas manuales ni listas de espera.

## Ownership y colaboración

- **Billing:** `monthlyPrice`, `individualSurchargePercent`, historial de pricing,
  quotes, Purchases, Payments, inbox de webhooks y auditoría financiera.
- **Physical:** curso, profesor, recurrencia, sesiones, capacidad, CapacityHolds,
  CapacityAssignments y asistencia.
- **shared:** IDs, comandos, resultados y contratos neutrales de colaboración;
  nunca entidades o repositorios de un módulo.
- **api:app:** composition root y orquestador de los casos de uso cross-module.

Ningún core depende de otro core. Billing y Physical implementan sus contratos y
`api:app` coordina las llamadas dentro del único JAR, sin HTTP ni mensajería.

## Configuración e historial de pricing

El profesor responsable define por curso `monthlyPrice` e
`individualSurchargePercent`. Billing exige ambos mayores que cero, versiona la
configuración y agrega una revisión append-only con:

- valores anterior y nuevo de precio mensual;
- valores anterior y nuevo del porcentaje;
- curso, profesor, motivo, versión y timestamp.

Un cambio nunca modifica quotes o Purchases existentes.

## Período mensual y fórmula

Billing fija `coverageStartDate` al crear el quote; el cliente no puede enviarla.
El fin se obtiene con aritmética calendario `plusMonths(1)`: conserva el día
cuando existe y, si no existe en el mes siguiente, ajusta al último día válido.

```text
coverageEndExclusive = coverageStartDate.plusMonths(1)
scheduledSessionCount = sesiones SCHEDULED con inicio en
                        [coverageStartDate, coverageEndExclusive)
effectiveRounded = HALF_UP2(monthlyPrice / scheduledSessionCount)
calculatedIndividual = HALF_UP2(
    (monthlyPrice / scheduledSessionCount)
    × (1 + individualSurchargePercent / 100)
)
currencyMinorUnit = 0.01  // ARS
individualPrice = max(
    calculatedIndividual,
    effectiveRounded + currencyMinorUnit
)
```

La división y el recargo se calculan en memoria con una precisión alta y una
política explícita; no se persiste un cociente periódico como si fuera exacto.
Cada resultado monetario se redondea `HALF_UP` a dos decimales. El recargo sigue
siendo configurable y estrictamente positivo. El `max` garantiza, incluso tras
redondear, que `individualPrice` supere a `effectiveRounded` por al menos una
unidad mínima de la moneda.

Si el período no contiene sesiones programadas, no existe quote pagable. Feriados,
cancelaciones y reprogramaciones cuentan según las sesiones efectivamente
programadas al cotizar.

### Ejemplos normativos

Curso de los martes, `monthlyPrice = $5000,00` y recargo `10%`:

1. Septiembre de 2026: 01, 08, 15, 22 y 29; cinco sesiones.
   - Efectivo redondeado: `HALF_UP2($5000,00 / 5) = $1000,00`.
   - Calculado y final: `HALF_UP2(($5000,00 / 5) × 1,10) = $1100,00`.
2. Octubre de 2026: 06, 13, 20 y 27; cuatro sesiones.
   - Efectivo redondeado: `HALF_UP2($5000,00 / 4) = $1250,00`.
   - Calculado y final: `HALF_UP2(($5000,00 / 4) × 1,10) = $1375,00`.
3. Contraejemplo de redondeo: `monthlyPrice = $1,00`, 100 sesiones y recargo
   `1%`.
   - `effectiveRounded = HALF_UP2($1,00 / 100) = $0,01`.
   - `calculatedIndividual = HALF_UP2(($1,00 / 100) × 1,01) = $0,01`.
   - `individualPrice = max($0,01, $0,01 + $0,01) = $0,02`.

## Quotes persistibles y snapshots

Cada fila es una sola modalidad:

- `MONTHLY`: prohíbe `selectedSessionId`, exige `scheduledSessionCount > 0` y
  `sessionIds` no vacío con todas las sesiones del período.
- `INDIVIDUAL`: exige `selectedSessionId` y un único ID en `sessionIds`.
  `scheduledSessionCount` conserva la cantidad total de sesiones programadas del
  período usada como divisor; no representa la cantidad de cupos adquiridos.

Para mostrar ambas modalidades, Billing emite un quote mensual y un quote
individual por cada sesión; no devuelve un quote ambiguo con alternativas.

El snapshot incluye `purchaseType`, curso, profesor, fechas de cobertura,
`sessionIds`, `monthlyPrice`, `scheduledSessionCount`, porcentaje de recargo,
`effectiveRounded`, `individualPrice`, configuración/version de pricing, total
final, moneda, creación y expiración. Esos operandos permiten auditar y recomputar
la cotización sin afirmar exactitud sobre cocientes periódicos. Nace con el quote
y no cambia al confirmar el pago.

Una Purchase debe iniciarse antes de `quote.expiresAt`; de lo contrario requiere
otro quote. Si el pago ya fue iniciado o cobrado, se conserva el snapshot. Una
excepción posterior pasa a revisión manual y no dispara automáticamente
reembolso, reemplazo de sesión ni compensación.

## Autómatas separados

```text
PaymentStatus:
PENDING -> COMPLETED
        -> FAILED
        -> REQUIRES_MANUAL_REVIEW

PurchaseStatus:
QUOTED -> PAYMENT_PENDING -> CAPACITY_ASSIGNED
                           -> EXCEPTION
       -> CANCELLED

CapacityHoldStatus:
ACTIVE -> CONVERTED
       -> EXPIRED
       -> RELEASED
```

Payment solo pasa a `COMPLETED` después de crear todas las
CapacityAssignments. Si ya hubo movimiento externo y falla la asignación,
Payment queda `REQUIRES_MANUAL_REVIEW` y Purchase queda `EXCEPTION`.

## Flujo y capacidad

1. Billing solicita a través de `api:app` las sesiones y capacidad de Physical.
2. Billing persiste el quote y snapshot de una modalidad.
3. Al iniciar Purchase, Physical puede crear un CapacityHold para pagos
   asíncronos; cuenta contra disponibilidad y tiene TTL configurable.
4. El proveedor confirma el movimiento.
5. `api:app` ordena sesiones de forma estable y solicita conversión idempotente.
6. Physical crea todas las CapacityAssignments y convierte/elimina el hold.
7. Solo entonces Billing completa Payment y Purchase.

```text
availableSpots = capacity - confirmedAssignments - activeCapacityHolds
```

Un pago mensual asigna todas las sesiones del snapshot; uno individual asigna
solo `selectedSessionId`.

## Webhook e idempotencia

- La API valida firma y **persiste/deduplica el evento en un inbox antes del ACK**.
- La clave única es proveedor + ID de evento.
- El worker procesa el inbox con reintentos e idempotencia por `paymentId`,
  `purchaseId` y `quoteId`.
- `confirmCapacity(paymentId, quoteId)` devuelve las mismas asignaciones en un
  reintento y no consume cupos adicionales.
- Si la persistencia del inbox falla, no se confirma recepción al proveedor.

El callback del BFF solo consulta estado. Debe correlacionar `purchaseId` con un
`state` firmado, validar ambos y nunca confiar en importe o estado enviados por
el navegador.

## QR y asistencia

Physical primero autentica el lector y valida firma, expiración, ventana,
coincidencia de usuario/sesión, CapacityAssignment y estado de la sesión. Solo
después adquiere atómicamente:

```text
SET checkin:{qrJti}:{userId}:{sessionId} {lockNonce} NX EX <ttl>
```

Con el lock adquirido inserta `attendance`, protegida por unicidad
`(user_id, session_id)`, y devuelve `201 Created`. Si la inserción o transacción
falla, ejecuta una compensación compare-and-delete: borra el lock únicamente si
su valor todavía coincide con `lockNonce`. Un reintento del mismo `qrJti` con una
asistencia ya persistida devuelve esa asistencia idempotentemente; no intenta
insertarla otra vez. Un lock concurrente sin asistencia persistida responde
`QR_ALREADY_USED`.

## Constraints e invariantes

- capacidad mayor que cero y `endsAt > startsAt`;
- recargo mayor que cero;
- cobertura `[start, endExclusive)` con fin posterior al inicio;
- coherencia de `purchaseType` y `selectedSessionId`;
- `sessionIds` mensual no vacío;
- unicidad de Purchase por usuario/quote y clave idempotente;
- unicidad de Payment por Purchase y evento externo;
- unicidad de CapacityAssignment por Payment/sesión y usuario/sesión;
- unicidad de asistencia por usuario/sesión y de `qrJti` cuando existe;
- unicidad del inbox por proveedor/evento.

## Casos límite

- **Cero sesiones:** `NO_SCHEDULED_SESSIONS`.
- **Recargo no positivo:** `INVALID_INDIVIDUAL_SURCHARGE`.
- **Sin cupo antes de iniciar:** no se crea Purchase pagable.
- **Quote expirado sin pago iniciado:** nueva cotización.
- **Dinero movido y capacidad fallida:** revisión manual y Purchase `EXCEPTION`.
- **Calendario modificado después del quote:** snapshot conservado y excepción
  manual si impide cumplirlo.
- **Webhook duplicado:** ACK idempotente después de persistir/detectar inbox.

Este documento no define políticas automáticas de reembolso.
