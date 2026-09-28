# Investigación: `Connection refused` intermitente en `:api:app:test`

**Estado:** investigación en curso, sin causa raíz confirmada al 100%. Este documento
es una bitácora de referencia — no es documentación "vigente" del diseño (no está
indexado en [README.md](README.md)) — para que el próximo intento no repita pasos ya
descartados.

**Rama:** `feature/api-app-test-context-consolidation` (PR #275).

## Origen

`api/app/build.gradle.kts` subía `spring.test.context.cache.maxSize` a 64 y
`maxHeapSize` a `"2g"` porque el módulo tenía 40+ configuraciones `@SpringBootTest`
distintas, cada una con su propio `@Testcontainers`/`@Container`/`@DynamicPropertySource`
redeclarado — Spring nunca las trataba como cache-equal (`Method.equals()` incluye la
clase declarante) y cada clase pagaba su propio container MySQL desde cero.

El plan (`~/.claude/plans/adaptive-tumbling-dove.md`) proponía reducir la cantidad de
containers y de contextos distintos en vez de seguir subiendo cache/heap:

- **PR1**: patrón singleton container de Testcontainers, un `MySQLContainer` por
  dominio (auth/billing/physical/virtual) en vez de uno por clase.
- **PR2**: fusionar en clases base Tier-2 los grupos de clases con `@MockBean`/
  `@SpringBootTest` idénticos pero contexto forzado a ser distinto.

PR1 se mergeó como parte de PR #275. Durante la verificación de PR2 apareció un patrón
de fallos que **no se explica por el diseño de PR1/PR2 en sí** y que esta investigación
todavía no cerró.

## Síntoma

`HikariPool ... total=0, active=0, idle=0, waiting=0` + `java.net.ConnectException:
Connection refused` — no es agotamiento del pool (eso daría un error distinto), es un
rechazo a nivel TCP: no hay nada escuchando en ese puerto. Una vez que empieza para una
clase, **no se recupera**: todos los tests restantes de esa clase (y de cualquier otra
que comparta el mismo contexto cacheado) fallan igual hasta el final del run.

```
org.springframework.transaction.CannotCreateTransactionException: Could not open JPA EntityManager for transaction
  Caused by: org.hibernate.exception.JDBCConnectionException: Unable to acquire JDBC Connection
    [HikariPool-2 - Connection is not available, request timed out after 30005ms (total=0, active=0, idle=0, waiting=0)]
  Caused by: java.sql.SQLTransientConnectionException: HikariPool-2 - Connection is not available...
    Caused by: com.mysql.cj.jdbc.exceptions.CommunicationsException: Communications link failure
      Caused by: java.net.ConnectException: Connection refused
```

## Bug real encontrado y corregido en el camino (no es la causa raíz de lo anterior)

`WebhookInboxReconciler` (billing) no tenía el toggle `enabled` que sus 3 hermanos
(`SubscriptionExpiryReconciler`, `HoldExpiryReconciler`, `OutboxBlacklistReconcilerTrigger`)
ya tienen desde el issue #172. `@Scheduled(fixedRateString = "...5000")` sin
`initialDelayString` dispara casi de inmediato al levantar el contexto y repite cada 5s
indefinidamente mientras el contexto siga vivo — y bajo el diseño de containers
compartidos por dominio, los contextos quedan cacheados el resto del build, así que
seguía disparando contra un container compartido con tests de otros dominios.

**Fix**: `@ConditionalOnProperty(name = "billing.webhook.reconcile.enabled", havingValue
= "true", matchIfMissing = true)` en la clase + `billing.webhook.reconcile.enabled:
false` en `application-integration-test.yml`, mismo patrón que sus 3 hermanos. Bajó los
fallos locales de 18 a 10 en una corrida completa — real, pero no cerró el problema de
`Connection refused` de fondo.

## Teorías descartadas (con evidencia)

| Teoría | Por qué se descartó |
|---|---|
| Contención de recursos de la máquina local (Docker Desktop compitiendo con el resto del entorno de dev) | **Descartada.** El mismo fallo, con la misma firma (`Connection refused`), se reprodujo en un runner de GitHub Actions limpio y dedicado (2 vCPU/7GB), sin ningún otro proceso corriendo. |
| Subir `Hikari connection-timeout` de 10s (default) a 30s ayudaría a tolerar la contención | **Descartada — empeoró las cosas.** Ver sección siguiente: cada test que falla ahora tarda ~60s (dos timeouts de 30s, uno por operación) en vez de fallar rápido. Con 32 fallos, son ~32 de los ~57 minutos totales del job puramente esperando timeouts. |
| El fix del `WebhookInboxReconciler` resolvería el problema de fondo | **Parcial.** Bajó los fallos de 18 a 10 en una corrida, pero corridas posteriores (con PR2 aplicado encima) volvieron a mostrar 32 fallos — el reconciler no era la causa completa. |

## `connection-timeout: 30000` amplifica el costo, no lo causa

Log de CI (`gh api .../actions/jobs/<id>/logs`), un test fallando por vez, separados por
**exactamente 60 segundos**:

```
18:50:08 CatalogIntegrationTest > the_public_catalog_endpoint_requires_no_authentication() FAILED
18:51:08 CatalogIntegrationTest > get_resolves_a_virtual_course_with_modules_lessons... FAILED
```

Con 32 tests fallando así, ~32 de los 57 minutos del job son puramente timeouts
duplicados (setup + `@AfterEach` cleanup, 30s cada uno). El timeout de 30s no causa el
problema — lo hace mucho más caro cuando ocurre. Bajarlo a algo corto (ej. 5s) es una
mejora de "fail fast" pendiente, independiente de la causa raíz.

## Hallazgo confirmado: el patrón singleton container no se sostiene en la práctica

Se dejó corriendo en background un poller de `docker stats --no-stream` cada 5s desde
una sesión anterior (¡sin darme cuenta, quedó corriendo ~24hs acumulando datos!). Acotado
a la ventana exacta de una corrida local real de `:api:app:test` (14:30:54–15:25:05,
confirmado por el `mtime` del log de la corrida):

- **41 nombres de container MySQL distintos** aparecieron en esos ~54 minutos — no 4
  (uno por dominio, como debería ser con el patrón singleton de PR1).
- La mayoría vive **0–11 segundos** antes de desaparecer, con uno nuevo apareciendo
  cada ~15 segundos — cadencia mucho más consistente con "un container nuevo por clase
  de test" que con "un container por dominio, reusado para todas sus clases".
- Algunos pocos viven cientos de segundos (322s, 361s, 484s, 723s) — probablemente
  clases con más tests o más lentas (ej. la prueba de concurrencia de 10 repeticiones
  en `HoldCapacityAdapterIntegrationTest`).
- **5 instancias distintas de `testcontainers-ryuk-*`** aparecieron en la misma corrida.
  Normalmente hay exactamente una por proceso JVM que arranca containers — ver 5 sugiere
  que en algún punto hubo múltiples "sesiones" de Testcontainers dentro de una sola
  invocación de `:api:app:test`, no solo una por dominio.

Esto contradice la premisa de PR1: el código implementa correctamente el patrón
singleton documentado por Testcontainers (`static final MySQLContainer` +
`@Testcontainers` en la clase base abstracta, heredado por las subclases) pero **en la
práctica no se está comportando como singleton**. No se confirmó todavía el mecanismo
exacto — hipótesis abiertas:

1. El worker de test de Gradle se está reiniciando a mitad de la corrida (crash/OOM del
   propio proceso JVM del worker, no del container), y cada reinicio reinicializa los
   campos `static` desde cero. Gradle no tiene `forkEvery`/`maxParallelForks`
   configurados explícitamente en ningún `build.gradle.kts` de este repo (ni en
   `api/app` ni en el root) — corre con el default (`maxParallelForks=1`), así que si
   esto es cierto, sería el propio Gradle recuperándose de un crash de worker de forma
   silenciosa (no hay `OutOfMemoryError` ni "Gradle Test Executor" visibles en el log de
   consola por defecto — habría que correr con `--info`/`--debug` para verlo, o revisar
   si Java deja algún `hs_err_pid*.log`).
2. Alguna otra causa (todavía no identificada) que rompe la reutilización del campo
   estático sin que el proceso JVM completo muera.

**No se pudo confirmar el mecanismo exacto solo con `docker stats`** — esa herramienta
ve containers aparecer/desaparecer pero no dice *por qué* (no hay logs de Testcontainers
capturados en las corridas ya hechas: Gradle no muestra stdout capturado en consola sin
`testLogging.showStandardStreams = true`, que este repo no tiene configurado).

## Confirmación directa (logs DEBUG de Testcontainers)

Corrida acotada a `com.menta.app.integration.physical.*` (11 clases reales, dejando
afuera ~300 tests no relacionados) con un `logback-test.xml` temporal en
`api/app/src/test/resources/` subiendo `org.testcontainers`/`com.github.dockerjava`/`tc`
a `DEBUG`. Resultado: **97 tests completados, 4 fallos** — y, mirando el
`<system-out>` de cada XML fresco en `build/test-results/test/`, esto:

```
AssignCapacityAdapterIntegrationTest:                 17:04:46 Creating container ... started in PT9.6s
HoldCapacityAdapterIntegrationTest:                   17:04:19 Creating container ... started in PT13.6s
HoldExpiryWorkerIntegrationTest:                      17:08:56 Creating container ... started in PT11.5s
PhysicalAttendanceHistoryIntegrationTest:             17:09:11 Creating container ... started in PT9.1s
PhysicalAttendanceHistoryMigrationIntegrationTest:    17:09:25 Creating container ... started in PT8.2s
PhysicalCapacityHoldMigrationIntegrationTest:         17:09:38 Creating container ... started in PT8.0s
PhysicalCheckInIntegrationTest:                       17:09:52 Creating container ... started in PT8.5s
PhysicalCourseAvailabilityIntegrationTest:            17:10:03 Creating container ... started in PT7.2s
PhysicalDeviceManagementIntegrationTest:              17:10:14 Creating container ... started in PT18.7s
PhysicalDeviceManagementMigrationIntegrationTest:     17:10:37 Creating container ... started in PT11.9s
PhysicalSessionManagementIntegrationTest:             17:10:55 Creating container ... started in PT11.8s
```

**Las 11 clases crearon un container nuevo. Cero reutilización, ni siquiera entre dos
clases consecutivas del mismo dominio/container compartido.** Esto incluye clases que
deberían compartir el container singleton de `AbstractPhysicalMySqlIntegrationTest`
(`PhysicalCheckInIntegrationTest`, `PhysicalCourseAvailabilityIntegrationTest`,
`PhysicalSessionManagementIntegrationTest`, etc. — no solo las 3 "Migration" que sí
tienen container propio por diseño).

Se descartó que el propio código esté matando el container entre clases: no hay ningún
`.stop()`/`@AfterAll` sobre el container en todo `api/app/src/test/java/com/menta/app/integration`.
Eso deja una única explicación consistente con el patrón: el container **muere por su
cuenta** (crash, o se vuelve inalcanzable) entre el final de una clase y el arranque de
la siguiente, y el propio Testcontainers — cuyo `@Testcontainers`/`@Container` chequea
`isRunning()` antes de decidir si reusar o arrancar — encuentra el container ya muerto y
silenciosamente levanta uno nuevo. Esto explica todo el patrón observado a la vez:

- Por qué el `docker stats` de la corrida completa mostró 41 containers en una sola
  invocación en vez de 4: no es un bug del código de fusión (PR1/PR2), es Testcontainers
  recuperándose de una caída repetida, una y otra vez.
- Por qué el fallo puede aparecer en el primer test de la primera clase de una corrida
  chica (visto en esta misma corrida diagnóstica) y no solo "después de una hora de
  carga acumulada" — no depende de acumulación, depende de cuántas veces el ciclo
  muerte→recreación se repite, que puede pasar temprano.
- Por qué migrar a un solo container por dominio (PR1) no bajó el conteo real de
  containers ni resolvió `Connection refused`: la causa nunca fue "demasiados
  containers por diseño", fue "el container no sobrevive entre clases", algo que un
  singleton correctamente implementado no puede compensar si lo que hay debajo se cae
  solo.
- Costo adicional no visto hasta ahora: cada recreación paga 7–18s de arranque de MySQL
  *además* de los ~60s de timeouts en los tests que sí fallan — con ~30 clases
  `integration-test` en el módulo, son minutos extra en cada corrida completa incluso
  en las clases que terminan pasando.

**Lo que sigue sin confirmarse**: *por qué* el container muere. No se llegó a esa capa
todavía — candidatos sin descartar: el daemon de Docker Desktop (macOS, solo aplica
localmente) matando containers bajo el patrón de creación/destrucción rápida que este
mismo comportamiento genera; algún límite de recursos del runner/máquina; o algo
específico de cómo Ryuk gestiona la sesión.

## Intento de confirmar en CI: mismo fallo, sin la evidencia extra

Se pusheó `logback-test.xml` (commit `13fa4e2`) y corrió en CI (run `36473723127`,
job `109102096582`, 56m40s). Resultado: **364 tests, 32 fallos — idéntico al patrón
sin el logging de diagnóstico**, confirmando que el archivo en sí no cambia nada del
comportamiento (como se esperaba, solo agrega visibilidad).

Pero el log de consola de CI (`gh api repos/.../actions/jobs/<id>/logs`) **no muestra
ninguna línea de `Creating container for image: mysql` ni de los WARN de Hikari** que sí
aparecieron en la consola local. Cero coincidencias de ambos patrones en 27.500 líneas
de log. Esto no significa que CI se comporte distinto — significa que **no tenemos
forma de verlo con el setup actual**:

- El workflow (`.github/workflows/pr-develop.yml`) no tiene ningún paso
  `actions/upload-artifact` — los reportes HTML/XML de test (donde localmente sí
  estaba toda la evidencia) se generan en el runner efímero y se pierden apenas
  termina el job, sin forma de descargarlos después.
- El fallback de Gradle que volcó las líneas WARN/ERROR "huérfanas" directo a la
  consola local (ver sección de arriba) aparentemente no se activa igual bajo el modo
  de consola no interactivo que Gradle usa en CI (`--console=plain`, automático sin
  TTY) — o se activa pero no llega al log que captura `gh api`.

**Conclusión de este intento**: confirma que el fallo en CI es el mismo (mismo conteo,
mismo tiempo, mismo patrón), pero no aporta evidencia nueva sobre el mecanismo. Para
conseguirla en CI hace falta una de estas dos cosas, ninguna aplicada todavía:

1. `testLogging.showStandardStreams = true` en `api/app/build.gradle.kts` (temporal),
   que fuerza a Gradle a volcar TODO el stdout capturado a consola sin depender del
   fallback de "huérfanos".
2. Un paso `actions/upload-artifact` en el workflow que suba
   `api/app/build/reports/tests/test/` y `api/app/build/test-results/test/` al
   terminar el job, para poder bajarlos y leerlos como se hizo localmente.

Cualquiera de las dos implica otra corrida completa de CI (~57 min) para obtener el
dato.

**Decisión**: se aplicó la opción 2 — paso `actions/upload-artifact` agregado a
`quick-build-and-test` en `.github/workflows/pr-develop.yml`, con `if: always()` para
que suba `api/app/build/reports/tests/test/` y `api/app/build/test-results/test/` tanto
si el build falla como si pasa. Mismo TODO que `logback-test.xml`: sacar este paso una
vez cerrada la causa raíz, no es infraestructura de CI permanente.

## Confirmado también en CI: mismo patrón, no es Docker Desktop

Run `36485656168`, job `109141810833` (57m44s, 369 tests, 32 fallos — patrón normal).
Se bajó el artifact `api-app-test-reports` (`gh run download <run> -n
api-app-test-reports`) y se buscó `Creating container for image: mysql` en los XML de
`test-results/test/`:

**37 clases, cada una con exactamente 1 evento de creación de container — mismo 1:1
que localmente, cero reutilización.** Ejemplo, dos clases de billing que corrieron
50 segundos aparte, cada una con su propio container recién creado:

```
SubscriptionCheckoutIntegrationTest:  21:28:36 Creating container ... started in PT8.27s
SubscriptionTrialIntegrationTest:     21:29:26 Creating container ... started in PT8.27s
```

Esto cierra una pregunta abierta importante: el runner de CI es Linux nativo (GitHub
Actions `ubuntu-latest`), sin la capa de red de Docker Desktop que sí existe en la
máquina local (macOS). Ver el mismo patrón exacto en ambos entornos **descarta Docker
Desktop como causa** — lo que está matando el container entre clases es algo que ocurre
igual en ambos sistemas operativos, no una particularidad de la máquina de desarrollo.

Los candidatos que quedan (worker de Gradle reiniciándose, o algo en cómo Ryuk gestiona
la sesión) siguen sin descartarse — este hallazgo solo reduce el espacio de búsqueda,
no lo cierra.

## Callejones sin salida ya explorados (para no repetirlos)

- **`Slf4jLogConsumer` en los 4 containers de dominio**: no produjo ninguna salida
  visible. Causa: Gradle no muestra stdout capturado en consola sin
  `testLogging.showStandardStreams = true` (este repo no lo tiene). El stdout capturado
  sí llega al reporte HTML por-método (`build/reports/tests/test/<paquete>/<Clase>/
  <método>().html`, pestaña "standard output"), pero ahí tampoco apareció nada útil
  porque el consumer nunca llegó a loguear la causa de la caída del container. Se
  revirtió para no dejar instrumentación sin verificar en el código.
- **Inspeccionar el container muerto post-mortem**: Ryuk (el reaper de Testcontainers)
  borra los containers automáticamente cuando el JVM que los inició termina — para
  cuando el build ya falló y devolvió control, `docker ps -a` no muestra rastro de
  ningún container de Testcontainers, solo los persistentes del entorno de dev
  (`menta-mysql`, etc.). No hay forma de hacer `docker logs`/`docker inspect` después
  del hecho con el setup actual.
- **`gh run view --log-failed` en un job todavía corriendo**: devuelve "logs will be
  available when it is complete". Usar `gh api repos/.../actions/jobs/<id>/logs` en su
  lugar — sí devuelve logs parciales de un job en progreso.

## Evidencia cruda (rutas locales, no versionadas)

Estos archivos viven en el scratchpad de la sesión y no sobreviven más allá de ella —
las secciones de arriba ya extrajeron lo relevante, pero se listan por si hace falta
reprocesarlos en la misma sesión:

- `docker-stats-during-run.log` — poller crudo de `docker stats`, ~24hs de datos.
- `pr2-full-suite.log` — corrida local completa post-PR2 (364 tests, 32 fallos, 54m10s).
- `ci-run2-log.txt` — log completo del job de CI fallido (run `36466966025`).
