# Diagramas de Estados

Ciclos de vida de las entidades principales del sistema.

## Índice

1. [Payment](#1-payment)
2. [Purchase](#2-purchase)
3. [Session](#3-session)
4. [QR Device](#4-qr-device)
5. [Refresh Token](#5-refresh-token)
6. [Capacity Hold](#6-capacity-hold)

---

## 1. Payment

Estado principal del pago con subestados para `PENDING`.

```mermaid
stateDiagram-v2
    [*] --> PENDING: Crear compra

    state PENDING {
        [*] --> AWAITING_PROVIDER: Checkout MP
        [*] --> AWAITING_MANUAL_VERIFICATION: Transferencia

        AWAITING_PROVIDER --> RECONCILIATION_REQUIRED: Timeout/Error consulta
        AWAITING_MANUAL_VERIFICATION --> RECONCILIATION_REQUIRED: Mismatch
    }

    PENDING --> COMPLETED: Proveedor confirma / Admin aprueba
    PENDING --> REJECTED: Proveedor rechaza / Admin rechaza
    PENDING --> CANCELLED: Usuario cancela
    PENDING --> EXPIRED: Deadline sin confirmación

    COMPLETED --> [*]
    REJECTED --> [*]
    CANCELLED --> [*]
    EXPIRED --> [*]

    note right of COMPLETED
        Estado terminal.
        Representa liquidación financiera.
        La asignación de cupo es independiente.
    end note

    note right of PENDING
        Solo PENDING tiene subestados.
        RECONCILIATION_REQUIRED requiere
        intervención de ADMIN.
    end note
```

### Reglas de Transición

| Desde | Hacia | Trigger | Actor |
|-------|-------|---------|-------|
| - | PENDING/AWAITING_PROVIDER | Checkout MP | Sistema |
| - | PENDING/AWAITING_MANUAL_VERIFICATION | Checkout Transferencia | Sistema |
| PENDING | COMPLETED | Webhook `approved` / Admin approve | Proveedor/Admin |
| PENDING | REJECTED | Webhook `rejected` / Admin reject | Proveedor/Admin |
| PENDING | CANCELLED | Webhook `cancelled` | Proveedor |
| PENDING | EXPIRED | Deadline sin confirmación | Sistema |
| AWAITING_* | RECONCILIATION_REQUIRED | Timeout, mismatch, error | Sistema |

**Monotonía**: Una vez en estado terminal, no hay retroceso. Webhooks tardíos o duplicados se ignoran.

---

## 2. Purchase

Estado de fulfillment (asignación de cupo), independiente del Payment.

> **IMPORTANTE**: Purchase **nunca existe** sin un Payment en estado `COMPLETED`.
> No se crea al iniciar el checkout — se crea únicamente después de la confirmación del pago.

```mermaid
stateDiagram-v2
    [*] --> PENDING_FULFILLMENT: Payment → COMPLETED (único trigger)

    PENDING_FULFILLMENT --> ASSIGNED: Todas las sesiones asignadas
    PENDING_FULFILLMENT --> EXCEPTION: Falla asignación parcial/total

    ASSIGNED --> [*]
    EXCEPTION --> ASSIGNED: Admin resuelve manualmente
    EXCEPTION --> [*]: Sin resolución

    note right of PENDING_FULFILLMENT
        Purchase NO EXISTE durante checkout.
        Se crea SOLO tras Payment COMPLETED.
        Sin pago confirmado = sin Purchase.
    end note

    note right of ASSIGNED
        Habilita asistencia.
        El alumno puede hacer check-in.
    end note

    note right of EXCEPTION
        Payment COMPLETED pero sin cupo.
        Requiere tarea administrativa.
    end note
```

### Relación Payment ↔ Purchase

```mermaid
flowchart LR
    subgraph Payment
        P_PENDING[PENDING]
        P_COMPLETED[COMPLETED]
    end

    subgraph Purchase
        PU_PENDING[PENDING_FULFILLMENT]
        PU_ASSIGNED[ASSIGNED]
        PU_EXCEPTION[EXCEPTION]
    end

    P_PENDING -->|"Webhook approved"| P_COMPLETED
    P_COMPLETED -->|"Trigger fulfillment"| PU_PENDING
    PU_PENDING -->|"Capacity OK"| PU_ASSIGNED
    PU_PENDING -->|"Capacity FAIL"| PU_EXCEPTION

    style P_COMPLETED fill:#c8e6c9
    style PU_ASSIGNED fill:#c8e6c9
    style PU_EXCEPTION fill:#ffcdd2
```

**Secuencia**: Payment PENDING → Payment COMPLETED → Purchase PENDING_FULFILLMENT → Purchase ASSIGNED/EXCEPTION

---

## 3. Session

Estado de una sesión de clase presencial.

```mermaid
stateDiagram-v2
    [*] --> DRAFT: Crear sesión

    DRAFT --> SCHEDULED: Publicar
    SCHEDULED --> IN_PROGRESS: Hora de inicio
    IN_PROGRESS --> COMPLETED: Hora de fin
    SCHEDULED --> CANCELLED: Admin/Profesor cancela

    COMPLETED --> [*]
    CANCELLED --> [*]

    note right of SCHEDULED
        Visible en catálogo.
        Acepta compras y asignaciones.
    end note

    note right of IN_PROGRESS
        Acepta check-ins.
        No acepta nuevas compras.
    end note

    note right of CANCELLED
        No permite check-in.
        Políticas de compensación
        fuera del alcance.
    end note
```

### Ventana de Check-in

La ventana de check-in es **configurable por sesión**. No tiene valores fijos predeterminados.

```mermaid
flowchart LR
    subgraph Timeline
        A["beforeMinutes<br/>(configurable)"] --> B["Inicio sesión"]
        B --> C["afterMinutes<br/>(configurable)"]
        C --> D["Fin sesión"]
    end

    subgraph CheckIn["Ventana Check-in"]
        V["Emisión y validación del QR\nefímero"]
    end

    A -.-> V
    C -.-> V

    style V fill:#e3f2fd
```

**Nota**: Los valores `beforeMinutes` y `afterMinutes` son configurables por sesión o a nivel de sistema. Dentro de esa ventana Android obtiene y renderiza una credencial QR efímera firmada; el lector la valida contra la API. El diagrama ilustra el concepto, no valores específicos.

---

## 4. QR Device

Ciclo de vida de un lector QR.

```mermaid
stateDiagram-v2
    [*] --> ACTIVE: Registrar dispositivo

    ACTIVE --> ACTIVE: Rotar secreto
    ACTIVE --> EXPIRED: Fecha expiración alcanzada
    ACTIVE --> REVOKED: Admin revoca

    EXPIRED --> REVOKED: Admin revoca (cleanup)

    REVOKED --> [*]

    note right of ACTIVE
        Puede procesar check-ins.
        Secreto entregado una sola vez.
    end note

    note right of EXPIRED
        No puede procesar check-ins.
        Solo puede ser revocado.
        Requiere nuevo dispositivo.
    end note

    note right of REVOKED
        Estado terminal irreversible.
        Requiere nuevo dispositivo.
    end note
```

### Operaciones de Seguridad

| Operación | Estado requerido | Efecto |
|-----------|------------------|--------|
| `rotate-secret` | ACTIVE | Nuevo secreto, invalida anterior |
| `revoke` | ACTIVE, EXPIRED | → REVOKED (irreversible) |
| Check-in | ACTIVE (no expirado) | Procesa QR |

---

## 5. Refresh Token

Ciclo de vida de un token de refresco.

```mermaid
stateDiagram-v2
    [*] --> ACTIVE: Login exitoso

    ACTIVE --> ROTATED: Refresh usado
    ACTIVE --> REVOKED: Logout / Reutilización detectada
    ACTIVE --> REVOKED: Logout-all / Reset password

    ROTATED --> [*]
    REVOKED --> [*]

    note right of ACTIVE
        Puede usarse para obtener
        nuevo access + refresh.
    end note

    note right of ROTATED
        Token reemplazado por uno nuevo.
        El anterior ya no es válido.
    end note

    note right of REVOKED
        Revocación explícita.
        Si fue por reutilización,
        TODA la familia se revoca.
    end note
```

### Detección de Reutilización

```mermaid
flowchart TD
    A[Refresh request con token T1] --> B{T1 existe?}
    B -->|No| C[401 INVALID_TOKEN]
    B -->|Sí| D{T1 está ROTATED?}
    D -->|Sí| E[Reutilización detectada!]
    E --> F[Revocar TODA la familia]
    F --> G[401 SESSION_REVOKED]
    D -->|No| H{tokenVersion match?}
    H -->|No| I[401 SESSION_REVOKED]
    H -->|Sí| J[Rotar T1 → T2]
    J --> K[Emitir nuevo access + T2]
```

---

## 6. Capacity Hold

Hold técnico temporal obligatorio durante todo checkout presencial, sea
asíncrono o síncrono.

```mermaid
stateDiagram-v2
    [*] --> ACTIVE: Checkout con cupo disponible

    ACTIVE --> CONVERTED: Pago confirmado
    ACTIVE --> RELEASED: Pago rechazado/expirado
    ACTIVE --> EXPIRED: TTL alcanzado sin confirmación

    CONVERTED --> [*]
    RELEASED --> [*]
    EXPIRED --> [*]

    note right of ACTIVE
        Cuenta contra disponibilidad.
        TTL < deadline proveedor.
        Invisible para el alumno.
        Se crea antes de Payment.
    end note

    note right of CONVERTED
        Se transforma en
        CapacityAssignment real.
    end note

    note right of EXPIRED
        Libera cupo.
        El pago puede seguir PENDING
        pero sin reserva de capacidad.
    end note
```

### Hold vs Assignment

| Aspecto | CapacityHold | CapacityAssignment |
|---------|--------------|-------------------|
| Visibilidad | Interno | Alumno puede ver |
| Duración | TTL corto (minutos) | Permanente |
| Permite check-in | No | Sí |
| Cuenta en disponibilidad | Sí | Sí |
| Creado por | Checkout presencial, antes de Payment/proveedor | Confirmación de pago |

---

## Estados Combinados: Flujo Completo de Compra

```mermaid
flowchart TB
    subgraph Checkout
        Q[Quote válido + disponibilidad proyectada] --> C{¿Crear hold atómico?}
        C -->|Sin cupo| R[409 CAPACITY_UNAVAILABLE\nSin Payment]
        C -->|Con cupo| H[Hold ACTIVE]
        H --> P1[Payment PENDING]
    end

    subgraph Confirmation
        W[Webhook approved] --> P2[Payment COMPLETED]
        P2 --> PU1[Purchase PENDING_FULFILLMENT]
    end

    subgraph Fulfillment
        PU1 --> |Capacidad OK| CA[CapacityAssignment]
        CA --> PU2[Purchase ASSIGNED]
        PU1 --> |Capacidad FAIL| PU3[Purchase EXCEPTION]
    end

    subgraph Alternative["Caminos alternativos"]
        H -.-> |Expirado| HR[Hold RELEASED]
        P1 --> |Rechazado| PR[Payment REJECTED]
        PR -.-> HR2[Hold RELEASED]
    end

    H --> |Confirma| Confirmation
    PU2 --> CI[Check-in habilitado ✓]

    style PU2 fill:#c8e6c9
    style CI fill:#c8e6c9
    style PU3 fill:#ffcdd2
    style PR fill:#ffcdd2
    style H fill:#fff3e0,stroke:#ef6c00,stroke-dasharray: 5 5
```

**Nota**: El Hold (línea punteada) se crea obligatoriamente antes de crear un
`Payment` o iniciar un proveedor, incluso para métodos de pago síncronos. Si no
se puede crear, el checkout responde `409 CAPACITY_UNAVAILABLE` sin pago.

---

## Invariantes de Estado

### Payment
- Estados terminales: `COMPLETED`, `REJECTED`, `CANCELLED`, `EXPIRED`
- Solo `PENDING` tiene subestados
- Transiciones monotónicas (no hay retroceso)

### Purchase
- `ASSIGNED` es el único estado que habilita check-in
- `Payment.COMPLETED` + `Purchase.EXCEPTION` = dinero recibido pero sin cupo

### Session
- `CANCELLED` bloquea check-ins aunque existan asignaciones
- La ventana de check-in es configurable por sesión

### Device
- `REVOKED` es irreversible
- El secreto solo se entrega al crear o rotar

### Refresh Token
- Reutilización revoca toda la familia (paranoid rotation)
- `tokenVersion` global invalida todos los tokens del usuario
