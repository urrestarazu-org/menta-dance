# Diagramas de Secuencia

Flujos principales del sistema Menta Dance en sintaxis Mermaid.

## Índice

1. [Autenticación Web (BFF)](#1-autenticación-web-bff)
2. [Autenticación Android](#2-autenticación-android)
3. [Refresh Token](#3-refresh-token)
4. [Compra Presencial Completa](#4-compra-presencial-completa)
5. [Webhook de Mercado Pago](#5-webhook-de-mercado-pago)
6. [Check-in QR](#6-qr-del-alumno-y-check-in)
7. [Check-in Manual](#7-check-in-manual)
8. [Transferencia Bancaria](#8-transferencia-bancaria)
9. [Revocación Global (Logout-All)](#9-revocación-global-logout-all)

---

## 1. Autenticación Web (BFF)

El navegador nunca ve tokens JWT; solo recibe cookies de sesión del BFF.

```mermaid
sequenceDiagram
    autonumber
    participant Browser
    participant BFF
    participant API as API (Auth)
    participant MySQL
    participant Redis

    Browser->>BFF: POST /login {email, password}
    BFF->>API: POST /api/v1/auth/login

    API->>MySQL: Check AUTH_DEGRADED state
    alt AUTH_DEGRADED active
        API-->>BFF: 503 SERVICE_UNAVAILABLE
        BFF-->>Browser: Error: servicio no disponible
    else NORMAL
        API->>MySQL: Validate credentials
        MySQL-->>API: User + tokenVersion

        API->>API: Generate JWT (15min) + Refresh UUID (7d)
        API->>MySQL: Store refresh_token_hash
        API->>Redis: SET version:{userId} = tokenVersion

        alt Redis write fails
            API->>MySQL: Set AUTH_DEGRADED = true
            API-->>BFF: 503 SERVICE_UNAVAILABLE
            Note over API: All auth blocked until reconciled
        else Redis OK
            API-->>BFF: {accessToken, refreshToken}
            BFF->>BFF: Create session, store tokens server-side
            BFF-->>Browser: Set-Cookie: session_id (HttpOnly, Secure, SameSite)
        end
    end

    Note over Browser: Browser only has session cookie (success path)
    Note over BFF: BFF holds JWT internally (success path)
```

---

## 2. Autenticación Android

Android guarda el refresh en Keystore y lo envía via header seguro.

```mermaid
sequenceDiagram
    autonumber
    participant App as Android App
    participant Keystore as Android Keystore
    participant API as API (Auth)
    participant MySQL
    participant Redis

    App->>API: POST /api/v1/auth/login {email, password}

    API->>MySQL: Check AUTH_DEGRADED state
    alt AUTH_DEGRADED active
        API-->>App: 503 SERVICE_UNAVAILABLE
    else NORMAL
        API->>MySQL: Validate credentials
        MySQL-->>API: User + tokenVersion

        API->>API: Generate JWT + Refresh UUID
        API->>MySQL: Store refresh_token_hash
        API->>Redis: SET version:{userId}

        alt Redis write fails
            API->>MySQL: Set AUTH_DEGRADED = true
            API-->>App: 503 SERVICE_UNAVAILABLE
            Note over API: All auth blocked until reconciled
        else Redis OK
            API-->>App: {accessToken, refreshToken}
            App->>Keystore: Store refreshToken (encrypted)
            App->>App: Store accessToken in memory
        end
    end

    Note over App: Access token expires in 15 min (success path)
    Note over Keystore: Refresh token secure for 7 days (success path)
```

---

## 3. Refresh Token

Rotación obligatoria: el refresh usado se revoca y se emite uno nuevo.

```mermaid
sequenceDiagram
    autonumber
    participant Client
    participant API as API (Auth)
    participant MySQL
    participant Redis

    Client->>API: POST /api/v1/auth/refresh<br/>X-Refresh-Token: old_refresh

    API->>MySQL: Check AUTH_DEGRADED state
    alt AUTH_DEGRADED active
        API-->>Client: 503 SERVICE_UNAVAILABLE
    else NORMAL
        API->>MySQL: Find refresh by hash
        alt Token not found or revoked
            API-->>Client: 401 INVALID_REFRESH_TOKEN
        else Token valid
            API->>MySQL: Check tokenVersion match
            alt Version mismatch (revoked globally)
                API-->>Client: 401 SESSION_REVOKED
            else Version OK
                API->>API: Generate new Refresh UUID
                API->>API: Hash new UUID (SHA-256)

                API->>MySQL: BEGIN TRANSACTION
                API->>MySQL: Revoke old refresh_token
                API->>MySQL: Insert new refresh_token_hash
                API->>MySQL: COMMIT

                API->>Redis: Reflect new state
                alt Redis write fails
                    API->>MySQL: Set AUTH_DEGRADED = true
                    API-->>Client: 503 SERVICE_UNAVAILABLE
                    Note over API: All auth blocked until reconciled
                else Redis OK
                    API->>API: Generate new JWT (same tokenVersion)
                    API-->>Client: {accessToken, refreshToken}
                    Note over Client: Old refresh is now invalid
                end
            end
        end
    end
```

---

## 4. Compra Presencial Completa

Flujo completo: cotización → checkout → pago → asignación de cupo.

```mermaid
sequenceDiagram
    autonumber
    participant Alumno
    participant BFF
    participant App as api:app
    participant Billing
    participant Physical
    participant MP as Mercado Pago
    participant MySQL

    %% Cotización
    Alumno->>BFF: Ver curso y solicitar precio
    BFF->>App: POST /api/v1/billing/physical/quotes<br/>{courseId, type: MONTHLY}
    App->>Physical: getCourseInfo(courseId)
    Physical-->>App: Course details
    App->>Billing: createQuote(course, type)
    Billing->>MySQL: Store quote with price snapshot
    Billing-->>App: Quote {quoteId, amount, validUntil}
    App-->>BFF: Quote response
    BFF-->>Alumno: Mostrar precio y opciones

    %% Checkout
    Alumno->>BFF: Confirmar compra
    BFF->>App: POST /api/v1/billing/physical/purchases<br/>{quoteId, paymentMethod, idempotencyKey}

    App->>Physical: createCapacityHold(projected sessions)
    alt No capacity
        Physical-->>App: CAPACITY_UNAVAILABLE
        App-->>BFF: 409 CAPACITY_UNAVAILABLE
        BFF-->>Alumno: Informar que ya no hay cupo
    else Hold created
        Physical->>MySQL: Create temporary hold
        Physical-->>App: HoldCreated {expiresAt}
        App->>Billing: initiatePayment(quote)
        Billing->>MySQL: Create Payment PENDING
        Billing->>MP: Create payment preference
        MP-->>Billing: {preferenceId, initPoint}
        Billing-->>App: {paymentId, redirectUrl}
        App-->>BFF: Redirect info
        BFF-->>Alumno: Redirect to Mercado Pago

        %% Pago externo
        Alumno->>MP: Complete payment
        MP-->>Alumno: Payment confirmed

        %% Webhook (ver diagrama 5) — Billing es dueño del endpoint
        MP->>Billing: POST /api/v1/billing/payments/mercadopago/webhook
        Billing->>Billing: Validate HMAC, persist inbox
        Billing-->>MP: 200 OK

        Note over Billing: Worker procesa inbox → Payment COMPLETED
        Billing->>App: triggerFulfillment(paymentId)

        %% Purchase se crea AQUÍ, después de COMPLETED
        App->>Billing: createPurchase(paymentId)
        Billing->>MySQL: Create Purchase PENDING_FULFILLMENT
        Billing-->>App: Purchase created

        App->>Physical: assignCapacity(userId, sessions)
        Physical->>MySQL: Convert hold → assignments
        Physical-->>App: CapacityAssigned

        App->>Billing: completePurchase(purchaseId)
        Billing->>MySQL: Purchase → ASSIGNED

        Alumno->>BFF: Ver mis clases
        BFF->>App: GET /api/v1/physical/attendance/me
        App-->>BFF: Sessions with confirmed spots
    end
```

---

## 5. Webhook de Mercado Pago

Validación HMAC, inbox durable, verificación con proveedor.

```mermaid
sequenceDiagram
    autonumber
    participant MP as Mercado Pago
    participant API as API (Billing)
    participant MySQL
    participant Worker
    participant App as api:app

    MP->>API: POST /api/v1/billing/payments/mercadopago/webhook<br/>x-signature, x-request-id, body

    API->>API: Validate HMAC-SHA256 (timing-safe)
    alt Invalid signature
        API-->>MP: 401 Unauthorized
    else Valid signature
        API->>API: Check timestamp < 5 minutes
        alt Timestamp too old
            API-->>MP: 400 Bad Request
        else Timestamp OK
            API->>MySQL: INSERT billing_webhook_inbox<br/>(data_id, status: RECEIVED)
            alt Duplicate (already exists)
                API-->>MP: 200 OK (idempotent)
            else New event
                API-->>MP: 200 OK
            end
        end
    end

    %% Async processing
    Worker->>MySQL: SELECT FROM inbox WHERE status = RECEIVED
    Worker->>MP: GET /v1/payments/{data.id}
    MP-->>Worker: Payment details

    Worker->>Worker: Verify: reference, merchant, amount, currency

    alt Mismatch
        Worker->>MySQL: Payment → RECONCILIATION_REQUIRED
        Worker->>MySQL: Create admin task
    else Match
        Worker->>Worker: Map status: approved→COMPLETED, rejected→REJECTED, etc.

        Worker->>MySQL: BEGIN TRANSACTION
        Worker->>MySQL: Update Payment state (monotonic)
        Worker->>MySQL: inbox → PROCESSED
        Worker->>MySQL: COMMIT

        alt Payment COMPLETED
            Worker->>App: triggerFulfillment(paymentId)
            Note over App: Assign capacity, complete purchase
        end
    end
```

---

## 6. QR del alumno y check-in

Android obtiene una credencial efímera, la muestra como QR y el lector la procesa.

```mermaid
sequenceDiagram
    autonumber
    participant Alumno
    participant Android
    participant QRReader as Lector QR
    participant API as API (Physical)
    participant Redis
    participant MySQL

    Alumno->>Android: Abrir "Mi QR" para la sesión
    Android->>API: POST /api/v1/physical/sessions/{sessionId}/access-qr
    API->>MySQL: Validar sesión, ventana y capacity_assignment
    API-->>Android: qrCredentials firmadas {studentId, sessionId, jti, exp}
    Android->>Android: Renderizar QR y renovarlo antes de expirar
    Alumno->>QRReader: Mostrar QR en pantalla
    QRReader->>QRReader: Escanear qrCredentials
    QRReader->>API: POST /api/v1/physical/sessions/{sessionId}/check-ins<br/>{type: QR, qrCredentials, deviceId, deviceToken}

    %% Device validation
    API->>MySQL: Find device by deviceId
    alt Device not found
        API-->>QRReader: 401 DEVICE_NOT_FOUND
    else Device found
        API->>API: Verify deviceToken hash
        alt Invalid token
            API-->>QRReader: 401 INVALID_DEVICE_TOKEN
        else Token valid
            API->>MySQL: Check device status and expiration
            alt Expired or revoked
                API-->>QRReader: 401 DEVICE_EXPIRED / DEVICE_REVOKED
            else Device active
                %% QR validation
                API->>API: Verify QR signature
                alt Invalid signature
                    API-->>QRReader: 401 INVALID_QR_SIGNATURE
                else Signature valid
                    API->>API: Check QR expiration and time window
                    alt Expired or outside window
                        API-->>QRReader: 401 QR_EXPIRED / OUTSIDE_WINDOW
                    else Within window
                        API->>API: Extract studentId from QR claims

                        %% Capacity check
                        API->>MySQL: Check capacity_assignment(studentId, sessionId)
                        alt No assignment
                            API-->>QRReader: 403 CAPACITY_ASSIGNMENT_REQUIRED
                        else Has assignment
                            %% Idempotency check
                            API->>MySQL: Check existing attendance
                            alt Already checked in
                                API-->>QRReader: 200 OK {existing attendance}
                            else Not checked in
                                %% Redis locks
                                API->>Redis: SET checkin:qr:{qrJti} NX EX 300
                                alt QR already used (replay)
                                    API-->>QRReader: 409 QR_ALREADY_USED
                                else QR unused
                                    API->>Redis: SET checkin:attendance:{sessionId}:{studentId} {nonce} NX EX 30

                                    %% Persist attendance
                                    API->>MySQL: INSERT physical_attendance
                                    alt Insert success
                                        MySQL-->>API: OK
                                        API-->>QRReader: 201 Created {attendance}
                                    else Insert fails
                                        API->>Redis: Compare-and-delete lock (if nonce matches)
                                        API-->>QRReader: 500 / retry
                                    end
                                end
                            end
                        end
                    end
                end
            end
        end
    end
```

---

## 7. Check-in Manual

Recepcionista registra asistencia sin QR. Physical valida identidad mediante puerto de Auth.

```mermaid
sequenceDiagram
    autonumber
    participant Recep as Recepcionista
    participant BFF
    participant App as api:app
    participant Physical as Physical Module
    participant UserPort as UserQueryPort (shared)
    participant Auth as Auth (implements port)
    participant Redis
    participant MySQL as MySQL (physical_*)

    Recep->>BFF: Buscar alumno, seleccionar sesión
    BFF->>App: POST /api/v1/physical/sessions/{sessionId}/check-ins<br/>{type: MANUAL, studentId}

    %% Role validation
    App->>App: Validate JWT roles
    alt Not RECEPCIONISTA or ADMIN
        App-->>BFF: 403 INSUFFICIENT_ROLE
    else Has required role
        App->>Physical: registerManualCheckIn(sessionId, studentId)

        %% Student validation via port in shared (implemented by Auth)
        Physical->>UserPort: existsActiveUser(studentId)
        UserPort->>Auth: [implementation]
        Auth-->>UserPort: true/false
        UserPort-->>Physical: true/false
        alt Student not found or inactive
            Physical-->>App: STUDENT_NOT_FOUND
            App-->>BFF: 404 STUDENT_NOT_FOUND
        else Student active
            %% Session validation (Physical's own tables)
            Physical->>MySQL: Check session status
            alt Session cancelled or past
                Physical-->>App: SESSION_NOT_ACTIVE
                App-->>BFF: 409 SESSION_NOT_ACTIVE
            else Session active
                %% Capacity check (Physical's own tables)
                Physical->>MySQL: Check capacity_assignment(studentId, sessionId)
                alt No assignment
                    Physical-->>App: CAPACITY_ASSIGNMENT_REQUIRED
                    App-->>BFF: 403 CAPACITY_ASSIGNMENT_REQUIRED
                else Has assignment
                    %% Idempotency
                    Physical->>MySQL: Check existing attendance
                    alt Already checked in
                        Physical-->>App: existing attendance
                        App-->>BFF: 200 OK {existing attendance}
                        BFF-->>Recep: Ya registrado
                    else Not checked in
                        %% Lock and persist
                        Physical->>Redis: SET checkin:attendance:{sessionId}:{studentId} {nonce} NX EX 30
                        Physical->>MySQL: INSERT physical_attendance

                        alt Success
                            Physical-->>App: AttendanceRecorded
                            App-->>BFF: 201 Created
                            BFF-->>Recep: Asistencia registrada ✓
                        else Failure
                            Physical->>Redis: Compare-and-delete (nonce match)
                            Physical-->>App: Error
                            App-->>BFF: Error
                        end
                    end
                end
            end
        end
    end
```

**Nota**: Physical nunca accede a `auth_*` directamente. La validación de identidad se hace mediante `UserQueryPort`, un contrato definido en `api:shared` e implementado por Auth.

---

## 8. Transferencia Bancaria

Pago manual con aprobación de administrador.

```mermaid
sequenceDiagram
    autonumber
    participant Alumno
    participant BFF
    participant App as api:app
    participant Billing
    participant Physical
    participant MySQL
    participant Admin

    %% Crear compra con transferencia
    Alumno->>BFF: Seleccionar pago por transferencia
    BFF->>App: POST /api/v1/billing/physical/purchases<br/>{quoteId, paymentMethod: TRANSFER}

    App->>Physical: createCapacityHold(projected sessions)
    alt Sin cupo
        Physical-->>App: CAPACITY_UNAVAILABLE
        App-->>BFF: 409 CAPACITY_UNAVAILABLE
        BFF-->>Alumno: Informar que ya no hay cupo
    else Hold creado
        Physical-->>App: HoldCreated
        App->>Billing: initiatePayment()
        Billing->>MySQL: Payment PENDING/AWAITING_MANUAL_VERIFICATION
        Billing-->>App: {paymentId, bankDetails}

        App-->>BFF: Mostrar datos bancarios
        BFF-->>Alumno: "Transferir a CBU xxx, referencia: {paymentId}"

        %% Alumno realiza transferencia y sube comprobante
        Alumno->>Alumno: Realizar transferencia bancaria

        Alumno->>BFF: Subir comprobante
        BFF->>App: POST /api/v1/billing/payments/{paymentId}/proof<br/>{file: comprobante.pdf}
        App->>Billing: attachProof()
        Billing->>MySQL: Store proof reference (private)
        Billing-->>App: OK
        App-->>BFF: Comprobante recibido
        BFF-->>Alumno: "Esperando verificación"

        %% Admin verifica
        Admin->>BFF: Ver pagos pendientes
        BFF->>App: GET /api/v1/billing/admin/payments?status=PENDING&substatus=AWAITING_MANUAL_VERIFICATION
        App-->>BFF: List of pending payments

        Admin->>BFF: Ver detalle y comprobante
        BFF->>App: GET /api/v1/billing/admin/payments/{paymentId}
        App-->>BFF: Payment details + proof

        alt Aprobar
            Admin->>BFF: Aprobar pago
            BFF->>App: POST /api/v1/billing/admin/payments/{paymentId}/approve
            App->>Billing: approvePayment()
            Billing->>MySQL: Payment → COMPLETED
            Billing-->>App: PaymentCompleted

            %% Purchase se crea AQUÍ, después de COMPLETED
            App->>Billing: createPurchase(paymentId)
            Billing->>MySQL: Purchase PENDING_FULFILLMENT

            App->>Physical: assignCapacity(userId, sessions)
            Physical-->>App: CapacityAssigned

            App->>Billing: completePurchase(purchaseId)
            Billing->>MySQL: Purchase → ASSIGNED

            App-->>BFF: Aprobado
            BFF-->>Admin: ✓
        else Rechazar
            Admin->>BFF: Rechazar pago
            BFF->>App: POST /api/v1/billing/admin/payments/{paymentId}/reject<br/>{reason: "Monto incorrecto"}
            App->>Billing: rejectPayment()
            Billing->>MySQL: Payment → REJECTED (with reason)
            Billing-->>App: Rejected

            %% api:app coordina la liberación mediante el contrato de Physical
            App->>Physical: releaseCapacityHold(paymentId)
            Physical-->>App: CapacityHoldReleased

            App-->>BFF: Rechazado
            BFF-->>Admin: ✓
        end
    end
```

---

## 9. Revocación Global (Logout-All)

Invalida todas las sesiones del usuario en todos los dispositivos.

```mermaid
sequenceDiagram
    autonumber
    participant Client
    participant API as API (Auth)
    participant MySQL
    participant Redis
    participant OtherDevices as Otros Dispositivos

    Client->>API: POST /api/v1/auth/logout-all<br/>Authorization: Bearer {jwt}

    API->>API: Validate JWT signature and claims

    API->>MySQL: BEGIN TRANSACTION
    API->>MySQL: Revoke ALL refresh_tokens for userId
    API->>MySQL: INCREMENT auth_users.token_version
    API->>MySQL: Insert revocation event in auth_outbox
    API->>MySQL: COMMIT

    API->>Redis: SET version:{userId} = newVersion
    API->>Redis: SET revocation_fence:{userId} TTL

    alt Redis write fails
        API->>MySQL: Set AUTH_DEGRADED flag
        API-->>Client: 503 SERVICE_UNAVAILABLE
        Note over API: All auth blocked until reconciled
    else Redis OK
        API-->>Client: 200 OK {message: "All sessions revoked"}
    end

    Note over OtherDevices: Next request with old JWT...

    OtherDevices->>API: GET /api/v1/any-protected-route<br/>Authorization: Bearer {old_jwt}

    API->>API: Validate JWT signature ✓
    API->>MySQL: Check AUTH_DEGRADED
    API->>MySQL: Check tokenVersion
    alt JWT.tokenVersion < user.tokenVersion
        API-->>OtherDevices: 401 SESSION_REVOKED
    end

    Note over OtherDevices: Must login again
```

---

## Notas de Implementación

### Principios Transversales

1. **MySQL es fuente de verdad**: Toda operación crítica se confirma primero en MySQL.
2. **Redis es réplica**: Acelera validaciones pero no es autoritativo.
3. **AUTH_DEGRADED fail-closed**: Si Redis no puede reflejar cambios de auth, todo se bloquea.
4. **Idempotencia**: Todos los endpoints de pago y check-in son idempotentes por diseño.
5. **Monotonía de estados**: Los pagos nunca retroceden de estado.

### Locks Redis

| Lock Key | Propósito | TTL |
|----------|-----------|-----|
| `checkin:qr:{jti}` | Prevenir replay de QR | 5 min |
| `checkin:attendance:{sessionId}:{studentId}` | Serializar check-in | 30 sec |
| `blacklist:jti:{jti}` | JWT revocado | hasta exp |
| `version:{userId}` | Token version cache | 24h |
| `revocation_fence:{userId}` | Deny fence post-revocation | 15 min |

### Compensación Redis

Cuando falla el INSERT después de adquirir un lock:
1. Comparar nonce almacenado con nonce propio
2. Solo borrar si coinciden (no borrar lock de otro proceso)
3. Permitir reintento seguro
