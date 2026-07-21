# Historias de Usuario - Detalle

[← Volver al índice principal](../README.md)

---

Este directorio contiene historias detalladas por módulo del monolito. Para pagos
presenciales, la autoridad funcional es
[Pagos de Clases Presenciales](../28-PHYSICAL-CLASS-PAYMENTS.md).

Las historias de Billing y Physical están alineadas con esa autoridad. Las de
Auth y Virtual continúan pendientes de revisión funcional.

## Resumen

| API | Historias | Escenarios | Prioridad |
|-----|-----------|------------|-----------|
| Auth API | 7 | 36 | Must Have |
| Billing | 9 | Ver cada historia | Must/Should Have |
| Virtual API | 8 | 42 | Must/Should Have |
| Physical | 4 | Ver cada historia | Must/Should Have |
| **Total** | **28** | **Ver cada historia** | - |

---

## Auth API (EP-01: Autenticación)

| ID | Historia | Escenarios | Prioridad |
|----|----------|------------|-----------|
| [US-AUTH-001](./US-AUTH-001.md) | Registro de usuario | 4 | Must Have |
| [US-AUTH-002](./US-AUTH-002.md) | Inicio de sesión | 7 | Must Have |
| [US-AUTH-003](./US-AUTH-003.md) | Activación de cuenta | 5 | Must Have |
| [US-AUTH-004](./US-AUTH-004.md) | Refresh Token | 5 | Must Have |
| [US-AUTH-005](./US-AUTH-005.md) | Solicitud de recuperación de contraseña | 5 | Must Have |
| [US-AUTH-006](./US-AUTH-006.md) | Reset de contraseña | 6 | Must Have |
| [US-AUTH-007](./US-AUTH-007.md) | Logout y revocación de tokens | 4 | Should Have |

---

## Billing API (EP-03: Suscripciones)

| ID | Historia | Escenarios | Prioridad |
|----|----------|------------|-----------|
| [US-BILLING-001](./US-BILLING-001.md) | Ver planes disponibles | 5 | Must Have |
| [US-BILLING-002](./US-BILLING-002.md) | Suscripción con Mercado Pago | 6 | Must Have |
| [US-BILLING-003](./US-BILLING-003.md) | Suscripción con transferencia bancaria | 6 | Should Have |
| [US-BILLING-004](./US-BILLING-004.md) | Consulta de estado de suscripción | 6 | Must Have |
| [US-BILLING-005](./US-BILLING-005.md) | Verificación de pagos manuales | 6 | Must Have |
| [US-BILLING-006](./US-BILLING-006.md) | Cotización de curso presencial | — | Must Have |
| [US-BILLING-007](./US-BILLING-007.md) | Compra presencial y pago individual | — | Must Have |
| [US-BILLING-008](./US-BILLING-008.md) | Transferencia presencial y excepción | — | Should Have |
| [US-BILLING-009](./US-BILLING-009.md) | Administración y auditoría de pricing | — | Must Have |

---

## Virtual API (EP-02: Catálogo Virtual)

| ID | Historia | Escenarios | Prioridad |
|----|----------|------------|-----------|
| [US-VIRTUAL-001](./US-VIRTUAL-001.md) | Ver catálogo de cursos | 5 | Must Have |
| [US-VIRTUAL-002](./US-VIRTUAL-002.md) | Ver detalle de curso | 5 | Must Have |
| [US-VIRTUAL-003](./US-VIRTUAL-003.md) | Ver lección gratuita | 5 | Must Have |
| [US-VIRTUAL-004](./US-VIRTUAL-004.md) | Reproducir video con suscripción | 6 | Must Have |
| [US-VIRTUAL-005](./US-VIRTUAL-005.md) | Guardar progreso de lección | 6 | Should Have |
| [US-VIRTUAL-006](./US-VIRTUAL-006.md) | Gestión de cursos (Admin) | 7 | Must Have |
| [US-VIRTUAL-007](./US-VIRTUAL-007.md) | Vista de curso con suscripción cancelada | 4 | Must Have |
| [US-VIRTUAL-008](./US-VIRTUAL-008.md) | Continuar última lección desde el Dashboard | 3 | Should Have |

---

## Physical API (EP-04: Academia Física)

| ID | Historia | Escenarios | Prioridad |
|----|----------|------------|-----------|
| [US-PHYSICAL-001](./US-PHYSICAL-001.md) | Check-in con código QR | — | Must Have |
| [US-PHYSICAL-002](./US-PHYSICAL-002.md) | Ver historial de asistencia | — | Should Have |
| [US-PHYSICAL-003](./US-PHYSICAL-003.md) | Ver clases y sesiones disponibles | — | Must Have |
| [US-PHYSICAL-004](./US-PHYSICAL-004.md) | Compra y asignación automática de cupo | — | Must Have |

---

## Estructura de cada Historia

Cada historia de usuario sigue el formato estándar:

1. **Encabezado**: ID, título, módulo, prioridad, estado, épica
2. **Historia de Usuario**: Formato "Como... Quiero... Para..."
3. **Criterios de Aceptación (BDD)**: Escenarios Given-When-Then
4. **Requisitos No Funcionales**: Seguridad, rendimiento, rate limiting
5. **Notas Técnicas**: Endpoints, request/response, tablas de BD
6. **Definition of Done**: Checklist de finalización

---

## Priorización MoSCoW

### Must Have (MVP)

- Todas las historias de Auth API (autenticación es core)
- US-BILLING-001, US-BILLING-002, US-BILLING-004, US-BILLING-005
- US-BILLING-006, US-BILLING-007, US-BILLING-009
- US-VIRTUAL-001 a US-VIRTUAL-004, US-VIRTUAL-006
- US-PHYSICAL-001, US-PHYSICAL-003, US-PHYSICAL-004

### Should Have

- US-AUTH-007 (Logout)
- US-BILLING-003 (Transferencia bancaria)
- US-BILLING-008 (Transferencia presencial y excepción)
- US-VIRTUAL-005 (Progreso)
- US-PHYSICAL-002 (Historial asistencia)

### Could Have

- Notificaciones push
- Certificados de finalización
- Gamification (badges, rankings)

### Won't Have (MVP)

- App iOS
- Multi-idioma
- Chat en vivo

---

*Última actualización: Julio 2026*
