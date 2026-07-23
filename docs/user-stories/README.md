# Backlog de Historias de Usuario

Las historias se implementarán después del scaffold de Fase 0. Ante conflicto,
prevalecen los contratos de API, ADRs y documentos de dominio vigentes.

## Reglas transversales

- Errores HTTP: `application/problem+json` (RFC 9457).
- Roles: ALUMNO, PROFESOR, RECEPCIONISTA, ADMIN; QR reader = dispositivo técnico.
- Web no expone tokens; Android usa refresh en Keystore y `X-Refresh-Token`.
- No hay reservas manuales de clases: pago confirmado asigna cupo.
- Un timeout de pago se concilia; no se reintenta crear el pago.
- Progreso de curso se deriva de la finalización explícita de lecciones.
- El catálogo de consulta es único (`/api/v1/catalog/courses`), pero compras y
  cotizaciones permanecen separadas por modalidad.

Las prioridades reflejan el campo MoSCoW de cada historia cuando está declarado;
para las historias concisas que no lo serializan, se derivan de sus criterios de
aceptación: los flujos necesarios para operar el producto son `Must Have` y los
complementarios son `Should Have`.

## Índice de Historias

### Auth (7 historias)

| ID | Título | Módulo | Prioridad |
|---|---|---|---|
| [US-AUTH-001](US-AUTH-001.md) | Registro de usuario | Auth | Must Have |
| [US-AUTH-002](US-AUTH-002.md) | Inicio de sesión | Auth | Must Have |
| [US-AUTH-003](US-AUTH-003.md) | Activación de cuenta | Auth | Must Have |
| [US-AUTH-004](US-AUTH-004.md) | Renovación de sesión | Auth | Should Have |
| [US-AUTH-005](US-AUTH-005.md) | Solicitud de recuperación de contraseña | Auth | Must Have |
| [US-AUTH-006](US-AUTH-006.md) | Reset de contraseña | Auth | Must Have |
| [US-AUTH-007](US-AUTH-007.md) | Logout y revocación | Auth | Must Have |

### Billing (9 historias)

| ID | Título | Módulo | Prioridad |
|---|---|---|---|
| [US-BILLING-001](US-BILLING-001.md) | Ver planes disponibles | Billing | Must Have |
| [US-BILLING-002](US-BILLING-002.md) | Confirmación de pago por webhook | Billing | Must Have |
| [US-BILLING-003](US-BILLING-003.md) | Suscripción con transferencia bancaria | Billing | Should Have |
| [US-BILLING-004](US-BILLING-004.md) | Consulta de estado de suscripción | Billing | Must Have |
| [US-BILLING-005](US-BILLING-005.md) | Verificación de pagos manuales (Admin) | Billing | Must Have |
| [US-BILLING-006](US-BILLING-006.md) | Cotización de curso presencial | Billing | Must Have |
| [US-BILLING-007](US-BILLING-007.md) | Compra presencial y pago individual | Billing, Physical y api:app | Must Have |
| [US-BILLING-008](US-BILLING-008.md) | Transferencia presencial y excepción de capacidad | Billing, Physical y api:app | Should Have |
| [US-BILLING-009](US-BILLING-009.md) | Administración y auditoría de pricing presencial | Billing | Must Have |

### Virtual (8 historias)

| ID | Título | Módulo | Prioridad |
|---|---|---|---|
| [US-VIRTUAL-001](US-VIRTUAL-001.md) | Ver catálogo de cursos | Virtual | Must Have |
| [US-VIRTUAL-002](US-VIRTUAL-002.md) | Ver detalle de curso | Virtual | Must Have |
| [US-VIRTUAL-003](US-VIRTUAL-003.md) | Ver lección gratuita | Virtual | Must Have |
| [US-VIRTUAL-004](US-VIRTUAL-004.md) | Reproducir video con suscripción | Virtual | Must Have |
| [US-VIRTUAL-005](US-VIRTUAL-005.md) | Guardar y completar progreso de lección | Virtual | Should Have |
| [US-VIRTUAL-006](US-VIRTUAL-006.md) | Gestión de cursos virtuales | Virtual | Must Have |
| [US-VIRTUAL-007](US-VIRTUAL-007.md) | Acceso de suscripción al contenido virtual | Virtual | Must Have |
| [US-VIRTUAL-008](US-VIRTUAL-008.md) | Continuar curso | Virtual | Should Have |

### Physical (8 historias)

| ID | Título | Módulo | Prioridad |
|---|---|---|---|
| [US-PHYSICAL-001](US-PHYSICAL-001.md) | Check-in con código QR | Physical | Must Have |
| [US-PHYSICAL-002](US-PHYSICAL-002.md) | Ver historial de asistencia | Physical | Should Have |
| [US-PHYSICAL-003](US-PHYSICAL-003.md) | Ver clases y sesiones disponibles | Physical | Must Have |
| [US-PHYSICAL-004](US-PHYSICAL-004.md) | Compra y asignación automática de cupo | Billing y Physical | Must Have |
| [US-PHYSICAL-005](US-PHYSICAL-005.md) | Gestión de cursos presenciales | Physical | Must Have |
| [US-PHYSICAL-006](US-PHYSICAL-006.md) | Gestión de sesiones de clase | Physical | Must Have |
| [US-PHYSICAL-007](US-PHYSICAL-007.md) | Gestión de dispositivos QR | Physical | Must Have |
| [US-PHYSICAL-008](US-PHYSICAL-008.md) | Check-in manual por recepcionista | Physical | Must Have |

## Resumen

| Módulo | Total | Must Have | Should Have |
|---|---:|---:|---:|
| Auth | 7 | 6 | 1 |
| Billing | 9 | 7 | 2 |
| Virtual | 8 | 6 | 2 |
| Physical | 8 | 7 | 1 |
| **Total** | **32** | **26** | **6** |
