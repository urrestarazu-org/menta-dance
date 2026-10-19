# API de Catálogo

## Propósito

El catálogo muestra cursos de danza con una experiencia de lectura única, sin
unificar las reglas de compra de sus modalidades.

```text
GET /api/v1/catalog/courses
GET /api/v1/catalog/courses/{courseId}
```

Cada curso tiene exactamente una modalidad: `PHYSICAL` o `VIRTUAL`. La respuesta
incluye información común (nombre, profesor, nivel y descripción) y un bloque de
modalidad: sesiones/capacidad para presencial o contenido/acceso para virtual.
`courseId` es un UUID globalmente único; su modalidad permite a `api:app` enrutar
la consulta al módulo dueño sin tabla ni acceso directo entre módulos.
Estas son las únicas rutas de lectura de cursos para alumnos y visitantes. Las
rutas de cursos de Physical y Virtual son exclusivamente de gestión
administrativa/interna.

## Límites

La proyección es compuesta por `api:app`/BFF mediante puertos de los módulos
Physical y Virtual. No crea una tabla compartida ni utiliza FK, `JOIN`, HTTP
interno o repositorios entre módulos. Sólo es lectura: cotizaciones presenciales
se solicitan a `POST /api/v1/billing/physical/quotes`; suscripciones virtuales
usan los flujos de Billing correspondientes.

## Detalle de un curso

`GET /api/v1/catalog/courses/{courseId}` resuelve el id primero contra el
catálogo virtual y, si no hay un curso virtual publicado con ese id, contra
Physical. La modalidad no se indica en la ruta.

- **Id virtual:** detalle virtual (módulos, lecciones y estadísticas), sin
  `sessions`. Su forma no cambia.
- **Id presencial activo:** `200` con `courseId`, `modality` (`PHYSICAL`),
  `title`, `level` y el bloque `physical` (`professorName`, `dayOfWeek`,
  `startTime`, `capacity`) más `sessions`, las próximas sesiones programadas.
- **Id desconocido o curso presencial inactivo:** el mismo
  `404 COURSE_NOT_FOUND`, indistinguible en estado y forma del cuerpo.

La respuesta `200` es `oneOf` de los dos detalles, sin discriminador: los
campos requeridos hacen las formas excluyentes (el detalle presencial lleva
`modality` y `physical`; el virtual, `modules`, `stats` e `isPremium`).

### Sesiones del detalle presencial

Cada sesión expone exactamente `sessionId`, `scheduledAt` (instante ISO-8601
en UTC, termina en `Z`), `capacity` y `availableSpots`.

```json
{
  "courseId": "b6bb98d6-179e-49d0-9dda-6c03a16998f0",
  "modality": "PHYSICAL",
  "title": "Salsa inicial",
  "level": "BEGINNER",
  "physical": {
    "professorName": "María García",
    "dayOfWeek": "TUESDAY",
    "startTime": "19:00",
    "capacity": 20,
    "sessions": [
      {
        "sessionId": "0b5d2f7e-6f5c-4e0e-9a43-1d8e6c7a9b10",
        "scheduledAt": "2026-10-06T22:00:00Z",
        "capacity": 20,
        "availableSpots": 17
      }
    ]
  }
}
```

- **Ventana:** sólo sesiones programadas (`SCHEDULED`) en `[ahora, ahora + W)`.
  Una sesión que ya empezó no se lista; una sesión exactamente en `ahora` sí.
  `W` son los días de la propiedad `catalog.physical.sessions.window-days`
  (variable de entorno `CATALOG_PHYSICAL_SESSIONS_WINDOW_DAYS`), por defecto
  `30`, rango válido `1` a `90`. Un valor fuera de rango impide el arranque.
- **Sin `from`/`to`:** el endpoint no acepta esos parámetros; si se envían se
  ignoran y la ventana no cambia.
- **Orden y tope:** ascendente por `scheduledAt`, como máximo 100 sesiones. Si
  califican más, se devuelven las 100 más próximas, sin señal de truncado ni
  paginación. Sin sesiones, `sessions` es un arreglo vacío (nunca nulo).
- **Agotadas y canceladas:** una sesión agotada se lista con
  `availableSpots` `0`; una cancelada no se lista.
- **Datos internos:** `assignedSpots` y `activeCapacityHolds` no se publican;
  sólo `availableSpots`. Tampoco hay precios ni `quoteEndpoint`: las
  cotizaciones siguen en `POST /api/v1/billing/physical/quotes`.
- **Fallos:** si falla la consulta del curso presencial o la de sesiones, la
  respuesta es `503 CATALOG_DEGRADED` con `Retry-After: 30` y no se devuelve un
  detalle parcial.

El listado `GET /api/v1/catalog/courses` no cambia: sus ítems presenciales no
llevan `sessions` y no consulta disponibilidad de sesiones.

Todos los errores usan `application/problem+json`.
