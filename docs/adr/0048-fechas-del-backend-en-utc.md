# ADR-0048: Todas las fechas del backend van en UTC

**Estado:** Aceptado
**Fecha:** 2026-11-12
**Decisores:** Dueño del proyecto / Arquitectura

## Contexto y Problema

Cada módulo del backend resolvía las fechas a su manera y no había un criterio escrito:

- `physical` crea las sesiones interpretando la hora de inicio del curso como UTC
  (`ZoneOffset.UTC`), pero tres lecturas deciden qué día es "hoy" con la zona de la academia
  (`physical.attendance.zone-id`, por defecto `America/Argentina/Buenos_Aires`). Un curso a las
  20:00 queda guardado a las 20:00Z, o sea las 17:00 en Buenos Aires (#729).
- `auth` toma la hora con `LocalDateTime.now()`, que usa la zona de la JVM y no UTC de forma
  explícita.
- Varios contratos y documentos dicen que las fechas están en UTC (`07-CATALOG-API`,
  `06-BILLING-API`, `26-LOCAL-DEV-SETUP-HOWTO`), pero ninguno lo establece como regla general.

La persistencia ya está en UTC (`hibernate.jdbc.time_zone: UTC` en `application.yml` y
`serverTimezone=UTC` en los `docker-compose`). El backend lo consumen más de un cliente (BFF
web y Android), cada uno con la zona de su usuario. La discusión surgió al diseñar el listado
de sesiones del día para recepción (#355), que necesitaba definir qué es "el día".

## Factores Clave (Decision Drivers)

* Una sola referencia de tiempo en todo el backend, sin que cada módulo elija su zona.
* Evitar errores de zona y de horario de verano en la lógica de negocio y en las consultas.
* Los clientes ya conocen la zona del usuario; el servidor, no.
* La base de datos y los contratos ya trabajan en UTC en la mayoría de los casos.

## Opciones Consideradas

### Opción 1: Todo el backend en UTC; los clientes convierten a la zona del usuario

* **Descripción:** el backend recibe, calcula, guarda y devuelve instantes en UTC. Los clientes
  (BFF y Android) convierten para mostrar y calculan el día local de la persona, enviando al
  backend un rango de instantes.
* **Pros:**
  * Una única referencia, sin ambigüedad entre módulos.
  * El backend no necesita saber la zona de nadie ni mantener configuración de zona.
  * Las comparaciones y los ordenamientos entre módulos son directos.
* **Contras:**
  * Cada cliente debe convertir y calcular el día local.
  * Las recurrencias semanales ("los lunes a las 20:00") no se expresan de forma natural en UTC
    (ver "Decisión pendiente").

### Opción 2: El backend trabaja en la zona de la academia

* **Descripción:** el backend interpreta y calcula "el día" con una zona configurada
  (`physical.attendance.zone-id`), como hacen hoy tres lecturas de `physical`.
* **Pros:**
  * Los clientes reciben el día ya resuelto.
* **Contras:**
  * Supone una sola zona para todos los usuarios y para todos los clientes.
  * Obliga a cada módulo a conocer la zona y el horario de verano.
  * Es la causa de la inconsistencia descrita en #729.

### Opción 3: Dejar que cada módulo decida (status quo)

* **Descripción:** sin regla común.
* **Pros:** no hay trabajo de migración.
* **Contras:** la inconsistencia actual se mantiene y crece con cada módulo nuevo.

## Decisión

Elegimos **la Opción 1**: todas las fechas y horas del backend están en **UTC**, y los clientes
son los responsables de convertirlas a la zona del usuario.

## Justificación (Rationale)

Es la única opción que no obliga al servidor a conocer la zona de quien consulta, y la que ya
usan la base de datos y la mayoría de los contratos. La Opción 2 funciona mientras haya una sola
zona y un solo cliente, pero el backend atiende a la web y a Android, y una zona fija en el
servidor es justo lo que produjo la inconsistencia de #729. La desventaja de la Opción 1, que
cada cliente calcule el día local, se acepta porque los clientes ya conocen la zona de su
usuario y es un cálculo simple y estándar.

## Reglas

1. **Instantes.** Todo momento del tiempo se representa con `Instant` en el código y como
   ISO-8601 con zona (`Z`) en los contratos (`type: string`, `format: date-time`). En la base de
   datos se guarda en UTC (`DATETIME`).
2. **El código del backend no usa una zona.** No se usan `ZoneId`, `ZonedDateTime` ni
   `LocalDateTime.now()` / `LocalDate.now()` en `main`, porque dependen de la zona de la JVM
   (`ZoneOffset.UTC` sí es válido). Si hace falta "ahora" o "hoy", se toma del puerto `Clock` del
   módulo (existe en `auth`, `billing`, `physical` y `virtual`), que devuelve un `Instant`.
3. **Las consultas "del día" reciben un rango.** El cliente calcula el día local y envía
   `from` y `to` (instantes UTC, rango semiabierto `[from, to)`). Ejemplo, el 12 de noviembre en
   Buenos Aires: `from=2026-11-12T03:00:00Z&to=2026-11-13T03:00:00Z`. Es el caso de
   `GET /api/v1/admin/physical/sessions` (#355).
4. **Los contratos lo declaran.** Todo campo de fecha y hora de OpenAPI dice que está en UTC. Un
   `+` en un offset numérico viaja codificado como `%2B`.
5. **Fechas de calendario sin hora.** Un valor como la fecha de nacimiento no es un instante: se
   transporta como `LocalDate` tal como lo envía el cliente. Cuando el backend necesita compararlo
   con "hoy", usa el día UTC del `Clock`.

## Decisión pendiente: recurrencia de los cursos

Un curso presencial se define por `dayOfWeek` y `startTime` ("los lunes a las 20:00"), y de ahí
salen las sesiones. En UTC, la hora del día y el día de la semana cambian según la zona, así que
no hay una traducción directa. Hoy `startTime` se interpreta como UTC al crear las sesiones, que
es coherente con esta regla pero hace que quien cargue "20:00" piense en hora local. Queda por
decidir cómo se expresa y se documenta la recurrencia (por ejemplo, que el cliente envíe la hora
ya convertida a UTC). Se trata en #729.

## Consecuencias

### Positivas

* Un solo criterio para todos los módulos, escrito y revisable.
* El backend no necesita configuración de zona ni lógica de horario de verano.
* Las comparaciones entre módulos (por ejemplo, ventanas de check-in y vencimientos) son directas.

### Negativas / Deuda Técnica

Hoy hay código que no cumple la regla; se corrige de forma gradual y cada punto necesita su
propio cambio:

* `ListMyPhysicalSessionsUseCaseImpl` y `GetPhysicalAttendanceHistoryUseCaseImpl` calculan "hoy"
  con `physical.attendance.zone-id`. Esa propiedad se elimina cuando ambas pasen a recibir un
  rango.
* La documentación del contrato de `physical` y los clientes que muestran la hora de las sesiones
  deben confirmar que convierten desde UTC (#729).
* `User` (9 usos) y `DemoUserFixture` (1) toman la hora con `LocalDateTime.now()`, que usa la
  zona de la JVM. En los contenedores da UTC porque es el valor por defecto de la imagen, pero
  ningún `docker-compose` lo fija; en una máquina de desarrollo en otra zona guardaría hora local.
* `UserProfile` valida la fecha de nacimiento con `LocalDate.now()`.
* El módulo `billing` no se auditó para este ADR.

La regla **todavía no se verifica de forma automática**. Una regla de ArchUnit que prohíba
`ZoneId`, `LocalDateTime.now()` y `LocalDate.now()` en `main` es el paso siguiente, pero solo
puede activarse cuando la deuda anterior esté saldada, para no dejar CI en rojo.

### Implicaciones de Costos

* Sin costo de infraestructura. El costo es el trabajo de ajustar los puntos de deuda y de que
  cada cliente calcule el día local.

### Riesgos y Reversibilidad

* **Riesgo principal:** que un cliente muestre la hora sin convertir, o que calcule mal el día
  local. **Mitigación:** los contratos declaran UTC de forma explícita y llevan un ejemplo del
  rango para un día en Buenos Aires.
* **Reversibilidad:** media. Los datos ya están en UTC, así que volver a una zona en el servidor
  cambiaría contratos y consultas, pero no la base de datos.

## Referencias y Decisiones Relacionadas

* #355 (listado de sesiones del día para staff, que pasó de `date` a `from`/`to`), #728 y #729
  (las sesiones se crean en UTC pero se leen en la zona de la academia).
* [ADR-0027](0027-mysql-flyway-strategy.md): MySQL y Flyway.
* [ADR-0021](0021-clean-architecture-mandatory.md): Clean Architecture (el puerto `Clock` vive en
  `application`).
