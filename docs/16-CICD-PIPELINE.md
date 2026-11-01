# Pipeline CI/CD

## GitHub Actions y permisos

GitHub Actions es la única plataforma de CI/CD. El workflow de Pull Request usa
`contents: read` y no recibe secretos de despliegue ni `packages:write`. El de
release recibe permisos mínimos, incluido `packages:write`, y secretos sólo
mediante GitHub Secrets.

Todas las actions se fijan a un **SHA de commit completo**; dependencias,
plugins e imágenes se fijan a versiones exactas. No se permiten `latest`,
`master` ni rangos de versión.

### Imagen del runner

Todos los jobs usan `runs-on: ubuntu-24.04` y **no** `ubuntu-latest`. La etiqueta
`latest` es flotante: GitHub la migra a Ubuntu 26.04 de forma gradual entre el
2026-10-19 y el 2026-11-19 ([actions/runner-images#14748](https://github.com/actions/runner-images/issues/14748)),
y durante ese período el mismo workflow podría ejecutarse en dos sistemas
operativos distintos, con fallos intermitentes difíciles de diagnosticar.

**Estado de la prueba en 26.04 (2026-10-06, #439):** los 7 jobs de
`pr-develop.yml` pasaron en `ubuntu-26.04` (ShellCheck con su `apt-get install`,
Hadolint, Gitleaks, los jobs con Docker y el build de Gradle con Testcontainers),
sin diferencias. **No se probaron** los jobs de `pr-main.yml` (`full-build-and-test`,
`spotbugs`, `nginx-integration`, construcción de imágenes) ni `project-sync.yml`:
un PR contra `develop` no los ejecuta. Por eso la imagen sigue fijada en 24.04.

**Cómo actualizar la imagen:** cambiar el `runs-on` de los tres workflows a la
etiqueta nueva en un PR de prueba, revisar cada job y, en especial, los de
`pr-main.yml`, que solo se ejercitan contra `main`. Recién entonces mergear. Para
verificar que no quedó ninguna etiqueta flotante:

```bash
rg -n "ubuntu-latest" .github/workflows   # no debe devolver nada
```

## CI de Pull Request

En cada PR contra `develop` o `main`:

1. compilar backend, BFF y Android;
2. ejecutar Checkstyle, tests, JaCoCo, SonarCloud y ArchUnit;
3. construir assets Node/Tailwind con lockfile;
4. ejecutar Playwright, Gatling acordado y Trivy;
5. publicar resultados sin publicar artefactos ni desplegar.

Además, el job `gitleaks` corre en `pr-develop.yml` y en `pr-main.yml` (ver
[Detección de secretos](#detección-de-secretos)).

### Job `nginx-integration`

Corre solo en PRs a `main` (`pr-main.yml`). Compila los JAR, construye `menta-api:ci` y
`menta-bff:ci`, levanta `infra/docker/nginx/docker-compose.yml` completo y comprueba por
Nginx `/health`, `/api/actuator/health` y el header `X-Frame-Options`. Como solo corre en
el PR de release, un error de este stack no aparece en `develop` y llega recién cuando se
quiere publicar.

El job hace `cp .env.example .env` y levanta el compose con ese `--env-file`. `.env.example`
está pensado para correr la API en la máquina del desarrollador (`SMTP_HOST=localhost`), y
una variable definida en el `.env` le gana al default `${VAR:-valor}` del compose; dentro de
un contenedor, `localhost` es el propio contenedor. Por eso el compose de `nginx` **fija** los
hosts internos del stack y no los lee del `.env`: `SMTP_HOST: menta-mailpit` en la API y
`REDIS_HOST: menta-redis` en el BFF (#625, #627). Lo mismo vale para los secretos sin
default: la API exige `BILLING_BANK_TRANSFER_PROOF_TOKEN_SECRET` (#622) y el stack incluye
`mailpit` para que el indicador de correo de `/actuator/health` no dé DOWN (#623).

Al cambiar ese compose, probarlo como lo hace el CI: con `--env-file .env.example` (o uno
equivalente), no con las variables exportadas a mano, y con el stack levantado. Validar solo
con `docker compose config` no alcanza: pasaron tres PR (#624, #626, #628) hasta que el
stack quedó sano.

### Validación de contratos OpenAPI

El job `openapi-validation` de `pr-develop.yml` corre `redocly lint` (imagen
`redocly/cli` con versión fijada, nunca `latest`) sobre los 5 contratos de
`api/openapi/`: `auth-v1`, `billing-v1`, `catalog-v1`, `physical-v1` y
`virtual-v1`. Los errores rompen el job; los warnings (`info-license`,
`no-server-example.com`) quedan visibles pero no bloquean.

La lista de archivos es **explícita**: para validar un contrato nuevo hay que
agregarlo al comando `lint` del job. Si no, queda sin validar aunque el job
pase. Todo contrato debe declarar su `securitySchemes` y su `security` raíz,
con `security: []` en las operaciones públicas.

### Presupuesto de tiempo

El job `quick-build-and-test` de `pr-develop.yml` declara un presupuesto de
**<15 min**. El objetivo original (<5 min) dejó de ser realista: en 7 corridas
exitosas recientes, con el flujo anterior (`./gradlew clean build` sin caché de
build), el job tardó entre 5,6 y 12,2 min, casi todo en el paso de Gradle
(336–733 s, ≈95 % del job).

Flujo actual del job:

| Paso | Qué hace |
|---|---|
| `Set up JDK 21` | `actions/setup-java` (solo JDK, sin `cache`) |
| `Set up Gradle` | `gradle/actions/setup-gradle`: caché de Gradle User Home y validación del wrapper |
| `Run Checkstyle` | Las 14 tareas `checkstyleMain`/`checkstyleTest`, primero, para fallar rápido por estilo |
| `Build with Gradle` | `./gradlew build` (sin `clean`) |

`build` ya ejecuta tests unitarios, ArchUnit (corre dentro de `test`), Checkstyle
y los umbrales de cobertura JaCoCo, así que se eliminaron los pasos separados
`Run unit tests` y `Run architecture tests`. El segundo, además, usaba
`--tests "*ArchitectureTest"`, que cambia las entradas de la tarea `test` y la
volvía a ejecutar. Checkstyle se conserva **antes** de `build` y no se duplica:
`build` encuentra esas tareas `UP-TO-DATE`.

`build` compila también las variantes debug y release de Android (la corrida más
larga con el flujo anterior ejecutó 188 de 202 tareas y tardó 9 min 43 s). Se
decidió **subir el presupuesto en vez de recortar verificación**: el job sigue
cubriendo build, tests unitarios, ArchUnit y Checkstyle de todos los módulos.

**Medición con la caché activa** (run posterior al merge de #475, mismo commit,
una muestra por caso): corrida en frío 12 min 17 s; re-run con la caché que dejó
`develop` 1 min 15 s (`build`: 192 tareas, 63 ejecutadas, 95 `FROM-CACHE`, 34
`UP-TO-DATE`). El re-run es el mejor caso; en un PR real cambian algunos módulos.
El presupuesto se mantiene en <15 min (ver [Caché de Gradle en CI](#caché-de-gradle-en-ci)).
Si el tiempo vuelve a exceder los 15 min, la opción restante es mover la
verificación de la variante release de Android a `pr-main.yml`.

En `pr-main.yml`, el job `full-build-and-test` sigue la misma lógica: Checkstyle
y un único `./gradlew build --no-build-cache`. En los 7 módulos JVM `build`
incluye `test`, `jacocoTestReport` (reporte HTML/XML que se sube como artefacto)
y `jacocoTestCoverageVerification` con las verificaciones por capa, por lo que se
quitaron los pasos separados de tests, ArchUnit, reporte y verificación de cobertura.

### Qué verifica el CI del módulo Android

Evidencia: logs de `quick-build-and-test` (run 37829415387).

| Verificación | ¿Corre en CI? | Cómo |
|---|---|---|
| Tests unitarios (`android/src/test`, 5 tests) | Sí | `:android:testDebugUnitTest`, dentro de `build` (`FROM-CACHE` si las entradas no cambian; una tarea fallida nunca se cachea) |
| Lint | Sí | `:android:lint` |
| Compilación debug y release | Sí | `:android:assembleDebug`, `:android:assembleRelease` |
| Compilación de los tests instrumentados | Sí | `:android:assembleDebugAndroidTest`, agregado explícito a `build` |
| **Ejecución** de los tests instrumentados (`androidTest`) | **No** | No hay emulador en CI |

Decisión (#442, [ADR-0036](adr/0036-android-agp9-tooling-migration.md)): no se
agrega un job con emulador por ahora. Compilar y empaquetar `androidTest` detecta
código roto y dependencias que no resuelven; la brecha aceptada es un fallo que
solo aparezca al ejecutar en un dispositivo.

Reportes: `pr-main.yml` sube los XML de `**/build/test-results/test/` (módulos
JVM) y de `android/build/test-results/testDebugUnitTest/` (convención de AGP). El
patrón original solo cubría el primero y los reportes de Android se perdían.

### Caché de Gradle en CI

- **`setup-gradle` reemplaza a la caché de `setup-java`.** `actions/setup-java`
  queda solo para instalar el JDK (sin `cache: 'gradle'`) y
  `gradle/actions/setup-gradle` (fijada por SHA, con comentario de versión para
  Dependabot) gestiona la caché de Gradle User Home. Aplica a los tres jobs que
  ejecutan Gradle: `quick-build-and-test`, `spotbugs` y `full-build-and-test`.
- **Solo lectura en PRs, escritura desde `develop`.** Por defecto la acción
  escribe la caché únicamente en la rama default (`develop`) y la deja en solo
  lectura en las demás. Consecuencia: tras el merge, las primeras corridas serán
  en frío hasta que un push a `develop` publique la caché. Los tiempos
  antes/después **solo se pueden comparar después del merge**, midiendo corridas
  reales; no se documentan cifras estimadas.
- **El gate de `main` no usa la caché de build.** `full-build-and-test` corre
  `./gradlew build --no-build-cache` para que lo que llega a producción se compile
  y pruebe siempre desde las fuentes. `pr-develop.yml` sí usa la caché de build.
  Ninguno de los dos usa `clean`: el runner es efímero y `clean` anulaba el
  trabajo incremental.
- **Checksum y validación del wrapper.** `gradle-wrapper.properties` fija
  `distributionSha256Sum`: el wrapper rechaza una distribución cuyo hash no
  coincida. Además, `setup-gradle` valida los JAR del wrapper contra los
  checksums oficiales de Gradle (`validate-wrappers: true`, que es el valor por
  defecto; se declara explícito) y falla si no coinciden.
- **Actualizar Gradle.** Obtener el SHA-256 oficial en
  `https://services.gradle.org/distributions/gradle-X-bin.zip.sha256` y ejecutar:

  ```bash
  ./gradlew wrapper --gradle-version X --gradle-distribution-sha256-sum <sha>
  ```

  Esto actualiza `distributionUrl` y `distributionSha256Sum` juntos; no cambiar
  uno sin el otro.

CI verde es obligatorio para mergear. Los PR son registro de trabajo individual:
no requieren aprobación humana y los de `feature/*` a `develop` se integran con
**squash merge**. El release a `main` es la excepción (ver "Git Flow y releases").

## Imágenes de contenedor

Las imágenes que se **descargan** de un registro se fijan a un **tag de versión
exacta** (`mysql:8.0.46`, `redis:7.4.11-alpine`,
`eclipse-temurin:21.0.12.1_1-jre-alpine-3.23`), no a una línea flotante
(`mysql:8.0`, `redis:7-alpine`, `21-jre-alpine`) ni a `latest`. Rige en todos los
lugares donde aparece una imagen: los `docker-compose*.yml`, los `FROM` de los
Dockerfiles, los workflows y las imágenes de **Testcontainers** en los tests
Java. Se eligió el tag exacto y no el digest (`@sha256:`) porque es la
convención que el repo ya seguía (`nginx:1.27.3-alpine`, `grafana/grafana:11.5.2`,
`otel/opentelemetry-collector-contrib:0.131.0`) y se lee sin herramientas; el
digest puede sumarse más adelante como endurecimiento.

Al fijar un tag hay que comprobar que **apunta al mismo contenido** que reemplaza.
Al migrar las líneas flotantes se verificó con la API del registro que
`mysql:8.0.46` y `redis:7.4.11-alpine` tienen el mismo digest que `mysql:8.0` y
`redis:7.4-alpine`, y que `eclipse-temurin:21.0.12.1_1-jre-alpine-3.24` coincidía
con `21-jre-alpine` (la variante `21.0.12_8-…` es otra build, más vieja). Los
Dockerfiles de la API y el BFF usan hoy la variante `alpine-3.23` del mismo JRE: la
`3.24` trae `libexpat` 2.8.5-r0 (CVE-2026-77214, HIGH) y el escaneo de Trivy del
release la rechaza. Se vuelve a `3.24` cuando `eclipse-temurin` la publique con
`libexpat` 2.9.0 o superior.

Aun en `3.23` la imagen de Temurin trae `libexpat` 2.8.5-r0, aunque el repositorio de
Alpine 3.23 ya publica 2.9.0-r0. Por eso los Dockerfiles de la API y el BFF corren
`apk upgrade --no-cache libexpat` antes de pasar al usuario no root (#622). Esa línea y
sus comentarios se quitan cuando la imagen base ya traiga `libexpat` 2.9.0 o superior.
La alternativa, una excepción en `.trivyignore.yaml`, se descartó: el propio archivo la
define como último recurso.

El mismo `RUN` actualiza también `brotli-libs` (#690): la imagen de Temurin trae
1.2.0-r0, con CVE-2026-111497 (CRITICAL, escritura fuera de límites en el decodificador),
y Alpine 3.23 ya publica 1.2.1-r0, que lo corrige. El CVE apareció después de que
`develop` pasara el escaneo y frenó el PR `release/0.3.0` → `main` (#689), así que un
escaneo en verde en `develop` no garantiza que el release lo siga estando días después.
Esa parte de la línea se quita cuando la imagen base traiga `brotli-libs` 1.2.1-r0 o
superior.

**Excepción:** `menta-api` y `menta-bff` pueden usar `latest` en los compose
porque son imágenes que **se construyen localmente** y no se descargan de un
registro. En producción se despliega el **digest inmutable** de la imagen
publicada, nunca un tag (ver "Release a producción").

**Deuda conocida:** `mysql:8.0.46` es la última versión de una línea que llegó a
fin de vida el 2026-04-30 (#449). El tag exacto solo da reproducibilidad; no
resuelve el soporte. Además, el literal de la imagen de MySQL está repetido en
15 tests de integración y Dependabot no lo ve (#409).

## Escaneo de imágenes (Trivy)

Hadolint y Trivy miran cosas distintas. **Hadolint** revisa cómo está escrito el
Dockerfile (usuario no root, `exec form`, tags sin `latest`). **Trivy** revisa lo
que trae la imagen ya construida: paquetes del SO, el JRE y las librerías Java que
van dentro del JAR. Un Dockerfile impecable puede producir una imagen vulnerable.

El job `trivy-image` de `pr-main.yml` construye `menta-api:ci` y `menta-bff:ci` y
ejecuta `scripts/trivy-scan.sh` sobre cada una. `release.yml` (#592) invoca el
mismo script antes de publicar en GHCR. Corre solo en PRs a `main` (ADR-0042).

**Política de severidad:**

| Hallazgo | Resultado |
|---|---|
| `CRITICAL` o `HIGH` con fix disponible | Falla el job |
| `MEDIUM` | Se informa en el reporte, no bloquea |
| Cualquier severidad sin fix | Se informa en el reporte, no bloquea |

El reporte completo se sube como artefacto `trivy-reports` y se copia al resumen del
job. No se usa SARIF ni la pestaña Security: requiere GitHub Code Security, de pago
en repositorios privados.

**Trivy como imagen y no como acción.** Corre con `docker run` sobre la imagen
oficial `aquasec/trivy` con versión exacta y digest, igual que Gitleaks y ShellCheck.
Así no depende de los tags de una acción de terceros y se reproduce en local:

```bash
./gradlew :api:app:bootJar :bff:bootJar
docker build -t menta-api:ci -f infra/docker/app/Dockerfile.api .
docker build -t menta-bff:ci -f infra/docker/app/Dockerfile.bff .
scripts/trivy-scan.sh menta-api:ci
scripts/trivy-scan.sh menta-bff:ci
```

`TRIVY_CACHE_DIR` (por defecto `~/.cache/trivy`) guarda la base de vulnerabilidades y
`TRIVY_REPORT_DIR` guarda el reporte completo. En CI la base se cachea con una clave
por fecha. Para subir Trivy se elige un release, se hace `docker pull
aquasec/trivy:<versión>` y se copia el digest de `docker inspect` a `TRIVY_IMAGE`.

### Cómo triar un hallazgo

1. **Librería Java** (aparece con `(app.jar)`): subir la versión. Si Spring Boot ya
   publicó una versión del BOM que la corrige, se sube Spring Boot. Si no, se
   fija un parche en `gradle/libs.versions.toml` (hoy `tomcat`, `jackson` y
   `netty`, aplicados como `tomcat.version`, `jackson-bom.version` y
   `netty.version` en el `build.gradle.kts` raíz) y se quita cuando el BOM la
   alcance. Hay que comprobar que la versión existe en Maven Central.
2. **Paquete del SO o JRE**: subir el tag exacto de `eclipse-temurin` en los dos
   Dockerfiles.
3. **El fix exige un cambio mayor** (por ejemplo migrar de Spring Boot 3 a 4): no se
   hace en el PR del escaneo. Se registra una excepción en `.trivyignore.yaml` y se
   enlaza el issue que hará la migración.
4. **Falso positivo o inalcanzable**: se registra una excepción solo si se verifica
   en el código que la ruta de ataque no existe (por ejemplo, que no se usa la clase
   afectada).

Cada excepción de `.trivyignore.yaml` exige `statement` (por qué es seguro) y
`expired_at` (fecha de revisión). `scripts/trivy-scan.sh` rechaza una entrada que
no tenga ambos, y al vencer la fecha Trivy deja de aplicarla y el job vuelve a
fallar. No se aceptan excepciones sin motivo ni sin fecha.

## Detección de secretos

La detección se hace en tres capas complementarias (ADR-0042):

1. **Pre-commit (local):** el hook `gitleaks` de `.pre-commit-config.yaml`
   (`rev` fijado a un tag exacto) revisa el diff staged. Es una comodidad: se
   saltea con `--no-verify` o desde un cliente sin `pre-commit`.
2. **CI (bloqueante):** el job `gitleaks` de `pr-develop.yml` y `pr-main.yml`
   escanea **todo el historial** alcanzable (`fetch-depth: 0`) y falla el build
   ante un hallazgo. Usa la imagen oficial con versión exacta: la action
   `gitleaks/gitleaks-action` exige una `GITLEAKS_LICENSE` (gratuita) en
   repositorios de organización, y los PRs de Dependabot y de forks no reciben
   secrets de Actions, por lo que el job fallaría en ellos.
   El job también falla si no se escaneó ningún commit, para que un error de
   git no se confunda con "sin hallazgos".

   **Que bloquee el merge depende de la configuración del repositorio, no del
   workflow:** `gitleaks` debe figurar entre los *required status checks* de la
   protección de `develop` y de `main`. Un job que falla pero no es requerido
   no impide mergear. Para verificarlo:

   ```bash
   gh api repos/urrestarazu-org/menta-dance/branches/develop/protection/required_status_checks --jq '.contexts'
   gh api repos/urrestarazu-org/menta-dance/branches/main/protection/required_status_checks --jq '.contexts'
   ```

   Si `gitleaks` no aparece en alguna de las dos listas, esta capa dejó de ser
   bloqueante.
3. **GitHub Secret Scanning con push protection:** capa gratuita en
   repositorios públicos, habilitada en *Settings → Code security*.

Las supresiones viven en `.gitleaks.toml`. Cada una acota el **valor** exacto y
la **ruta**, y lleva un comentario que explica por qué es un falso positivo.
Nunca se suprime un directorio entero. Antes de agregar una, comprobá que el
valor no sea una credencial real (si es base64, decodificalo).

### Si el escaneo detecta un secreto real

Un secreto en un repositorio público debe considerarse **comprometido** aunque
se borre después: el objeto sigue en el historial, en los forks y en los mirrors.

1. **Rotar primero.** Revocar la credencial en el proveedor (Mercado Pago,
   Bunny.net, Google Calendar, MySQL) y emitir una nueva. No reescribas el
   historial antes de este paso.
2. Reemplazar el valor por configuración externa (variable de entorno o GitHub
   Secret) en un commit nuevo.
3. Recién después, limpiar el historial si hace falta (`git filter-repo`),
   coordinando el force-push con quien tenga clones o forks abiertos.
4. Confirmar con `gitleaks git . --redact` que el historial queda limpio.
5. Revisar los logs de uso de la credencial vieja en busca de accesos ajenos.

## Análisis estático: estilo vs. seguridad

Checkstyle y SpotBugs no se solapan, y Checkstyle **no** cubre seguridad
(ADR-0042, ADR-0043):

| Herramienta | Qué mira | Qué detecta | Qué NO detecta |
|---|---|---|---|
| **Checkstyle** (`config/checkstyle/google_checks.xml`) | Código fuente | Estilo: indentación, nombres, Javadoc, longitud de línea | Ninguna vulnerabilidad. Aprueba un `"... WHERE id = '" + id + "'"` bien indentado |
| **SpotBugs + Find Security Bugs** (`config/spotbugs/exclude.xml`) | Bytecode de producción (`src/main`) | Bugs y patrones vulnerables: inyección SQL, criptografía débil (MD5, SHA-1), deserialización insegura, path traversal, XSS en plantillas, CSRF deshabilitado | Estilo, vulnerabilidades en dependencias (SCA, ver Dependabot) ni secretos (Gitleaks) |

Checkstyle responde "¿está bien escrito?"; SpotBugs responde "¿es explotable?".
Un build con Checkstyle en verde no dice nada sobre seguridad.

### SpotBugs

* **Dónde corre:** el job `spotbugs` de `pr-main.yml` (gate de release, ADR-0042),
  en paralelo con `full-build-and-test`. No corre en `pr-develop.yml`: mantiene
  su presupuesto de feedback rápido (<15 min). Sube los reportes como artefacto
  `spotbugs-reports` aunque falle. El módulo `android` no participa: no usa el
  plugin `java` estándar y no hay una configuración equivalente.
* **Configuración:** una sola vez en el `build.gradle.kts` raíz, aplicada a los 7
  módulos JVM (`api:*` y `bff`). Versiones fijadas en `gradle/libs.versions.toml`
  (`spotbugs-gradle-plugin`, `spotbugs-tool`, `findsecbugs`); Find Security Bugs
  se registra en la configuración `spotbugsPlugins`. `effort = MAX`.
* **Qué falla el build:** `reportLevel = HIGH`, es decir, hallazgos de prioridad 1
  (la confianza más alta de SpotBugs). Los de prioridad 2 y 3 existen pero no
  bloquean. Los de prioridad 2 son ruido (p. ej. `EI_EXPOSE_REP`, que salta en
  cada constructor con inyección de dependencias). Los de categoría `SECURITY`
  son todos de prioridad 3 y **no se descartaron**: incluyen
  `CRLF_INJECTION_LOGS` y `SERVLET_HEADER`, que pueden ser reales y esperan su
  triage (#437). Se analiza sólo `src/main`; los tests no se analizan
  (`spotbugsTest` está deshabilitada).
* **Fuera de `check`:** el plugin de SpotBugs engancha sus tareas a `check`, y
  por tanto a `./gradlew build`, que corre `pr-develop.yml`. El `build.gradle.kts`
  raíz quita esa dependencia a propósito para que el análisis sólo corra donde
  ADR-0042 lo ubica; se invoca con `spotbugsMain`.
* **Correr localmente:**

  ```bash
  ./gradlew spotbugsMain                 # todos los módulos JVM
  ./gradlew :api:auth:spotbugsMain       # un módulo
  # Reporte: <módulo>/build/reports/spotbugs/main.html (y main.xml)
  ```

### Cómo triar un hallazgo

1. Abrí `main.html` del módulo y leé la descripción del patrón y la traza.
2. **Si es real, corregilo** respetando las capas: `domain` no incorpora
   dependencias de frameworks para contentar al analizador. La corrección lleva
   sus tests y no baja los umbrales de cobertura. Si toca lógica de negocio y
   es grande, abrí un issue propio.
3. **Si es un falso positivo**, suprimilo en `config/spotbugs/exclude.xml` con un
   `<Match>` acotado a la clase (y al método si se puede) y a **un** patrón, con
   un comentario XML que explique por qué no es explotable. No se aceptan
   supresiones globales, por patrón en todo el proyecto ni sin justificación.
4. No uses `@SuppressFBWarnings` ni `ignoreFailures = true` para poner el build
   en verde.

Que el job bloquee el merge depende de que `spotbugs` figure entre los *required
status checks* de la protección de `main` (misma salvedad que `gitleaks`); el
workflow por sí solo no lo garantiza.

## Análisis de dependencias (SCA)

El análisis de composición de software es responsabilidad de **Dependabot**
(ADR-0042). `.github/dependabot.yml` cubre:

| Ecosistema | Directorio | Qué vigila |
|---|---|---|
| `gradle` | `/` | Subproyectos (`api:*`, `bff`, `android`) y `gradle/libs.versions.toml` |
| `gradle` | `/buildSrc` | El build incluido `buildSrc`, que Dependabot no ve desde la raíz |
| `github-actions` | `/` | Las actions de `.github/workflows/`; actualiza el SHA completo y su comentario `# vX.Y.Z`, por lo que se conserva el pinning de ADR-0024 |

`npm` no está configurado porque no hay un `package.json` versionado en el
repositorio.

Dependabot corre los lunes y abre PRs contra `develop`. Los bumps *minor* y
*patch* llegan agrupados para no inundar la cola de revisión; los *major* llegan
como PRs individuales a propósito. Dependabot no corre dentro del pipeline de PR,
así que no afecta el presupuesto de `pr-develop.yml`.

Un grupo único bloquea todo lo que contiene si un solo miembro falla, por lo que
el build `gradle` de la raíz se divide en cuatro grupos. Una dependencia cae en el
grupo **más específico** cuyo patrón coincide; el orden en el archivo no importa
(el log de Dependabot lo dice: *"belongs to more specific group"*). El grupo
comodín `*` sólo recibe lo que ningún otro grupo reclama:

| Grupo | Contenido | Por qué separado |
|---|---|---|
| `android-build-tooling` | AGP, Kotlin, KSP y Hilt | Deben moverse juntos, pero un bump aquí no exige por sí solo un `compileSdk` mayor (#425) |
| `androidx-libraries` | Compose BOM, `core-ktx` y el resto de AndroidX | Son las que suben el `compileSdk` mínimo (#425, #451) |
| `jvm-test-tooling` | ArchUnit, WireMock y Testcontainers | Sólo afectan a los tests |
| `gradle-minor-patch` | El resto | Todo lo demás |

**Por qué Android se divide en dos:** en el CI del PR #434, 13 dependencias exigían
API 37 y el proyecto compila contra 36. Las 13 venían de AndroidX: 11 de Compose
1.12.1 (`compose-bom` 2026.09.00) y 2 de Core 1.19.1 (`core-ktx`). Ninguna era del
compilador ni de los plugins. Con un solo grupo, un cambio que probablemente
funcione quedaba bloqueado por otro que depende de `compileSdk`.

El grupo `androidx-libraries` puede quedar rojo hasta resolver #425. Se acepta a
propósito: ignorarlo ocultaría también parches de seguridad.

Al **reorganizar grupos mientras hay un PR de grupo abierto**, Dependabot lo
trata como dueño de sus dependencias durante esa pasada (`already handled by a
previous group`) y cierra ese PR después ("updatable in another way"). Los PRs de
los grupos nuevos aparecen en la pasada siguiente, es decir, la próxima corrida
semanal o un *Check for updates* manual (*Insights → Dependency graph →
Dependabot*). No indica un error de patrones.

### Actualizaciones ignoradas

Estas dependencias dependen de una generación de Spring Boot y no de la cadencia
de Dependabot, por lo que `dependabot.yml` las ignora hasta decidir la
migración (#426):

* `org.junit.platform:*`: `build.gradle.kts` las fija a la plataforma que
  corresponde al Jupiter administrado por el BOM de Spring Boot; moverlas solas
  rompe esa alineación.
* `org.testcontainers:*` (sólo *major*): la rama 2.x renombra los módulos.
* `net.logstash.logback:logstash-logback-encoder` (sólo *major*): la 9.x migra a
  Jackson 3 y exige Java 17, mientras Spring Boot 3.5 usa Jackson 2.
* `org.springframework.boot` (sólo *major*): la 4.x migra a Jackson 3, JUnit 6 y
  Testcontainers 2 y elimina `@MockBean`; la migración se decide en #426 (requiere
  antes #405). Los *minor* y *patch* de la 3.5.x siguen entrando.

Al agregar una exclusión nueva, dejá el motivo como comentario en
`dependabot.yml` y enlazá el issue que decide su levantamiento.

### Criterio de triage de los PRs de Dependabot

1. **Seguridad primero:** un PR que corrige una vulnerabilidad publicada se
   revisa antes que cualquier otro.
2. **Nada se mergea automáticamente.** Todo PR pasa por el mismo CI que el
   resto; sin CI verde no se integra (ADR-0024).
3. **Grupos *minor/patch*:** se integran cuando el CI está verde, tras mirar las
   notas de versión de lo que cambia el BOM de Spring Boot.
4. **Bumps *major*:** se leen las notas de migración y se evalúa el impacto antes
   de integrar. Si hay trabajo de adaptación, se abre un issue en el tablero y
   se cierra el PR.
5. **Si el bump cambia una versión que ADR-0024 fija como baseline**, se
   actualiza el ADR en el mismo PR.
6. Un PR que no se va a integrar se cierra con un comentario que explique el
   motivo; `@dependabot ignore` evita que reaparezca.

Las alertas de Dependabot (*Dependabot alerts*) están habilitadas en la
configuración del repositorio y son gratuitas en repositorios públicos.

## Git Flow y releases

- `feature/*` nace de `develop`.
- `release/*` nace de `develop` y se mergea a `main`.
- Después, `main` se mergea de regreso a `develop`.
- `hotfix/*` nace de `main`; su merge vuelve también a `develop`.

**El PR `release/*` → `main` se mergea con "Create a merge commit", no con squash.** Un
squash deja en `main` un commit que `develop` no conoce: las dos ramas quedan con
historias separadas y el release siguiente choca en todos los archivos tocados por ambos.
Así pasó con la `0.2.0`: `main` tenía dos PR de documentación squasheados que `develop`
había reescrito después, y el PR dio 45 conflictos (44 `add/add` y uno de contenido). Se
resolvió integrando `main` en la rama de release con `git merge -s ours origin/main`, que
conserva el árbol de `develop` y agrega a `main` como padre. Con el merge commit, `main`
incluye todos los commits de `develop`, pero `develop` no recibe el merge commit ni lo que `main`
sumó por su cuenta (un hotfix, por ejemplo). Eso se corrige con el backport de abajo.

### Actualizar una rama de release con `develop`

Si con el PR `release/*` → `main` abierto falla algo que viene de `develop` (un CVE nuevo
en Trivy, por ejemplo), el arreglo se hace en `develop` con un PR normal y después se lleva
a la rama de release. No se commitea directo en `release/*`: la rama de release solo recibe
lo que `develop` ya tiene, así las dos no divergen y no hace falta un backport extra.

1. Se mergea el PR del arreglo a `develop`.
2. `git fetch origin`.
3. `git switch release/<versión>`.
4. `git merge --ff-only origin/develop`.
5. `git push origin release/<versión>`. El push reinicia los checks del PR a `main`.

Si la rama de release no tiene commits propios, el paso 4 es un fast-forward: no crea merge
commit y las dos ramas quedan idénticas (`git rev-list --left-right --count
origin/develop...origin/release/<versión>` da `0 0`). `--ff-only` falla a propósito si la
rama de release tiene algo propio (la `0.2.1` llevó cambios de documentación): en ese caso
se decide a mano, normalmente con `git merge origin/develop`, que sí crea un merge commit.

El botón "Update branch" del PR no sirve para esto: trae la rama base (`main`) a la rama de
release, no `develop`.

Pasó con la `0.3.0`: el PR #689 falló en `trivy-image` por CVE-2026-111497 (CRITICAL) en
`brotli-libs`, publicado después de que `develop` pasara el escaneo. El arreglo entró a
`develop` por el PR #691 (#690) y se llevó a `release/0.3.0` con este procedimiento. Un
escaneo en verde en `develop` no garantiza que el release siga en verde días después: Trivy
baja la base de datos de vulnerabilidades en cada corrida, así que un CVE publicado entremedio
aparece recién en el escaneo siguiente.

### Backport de `main` a `develop`

Después de cada merge de un release o de un hotfix a `main` hay que llevar `main` de vuelta a
`develop`. Con "Require branches to be up to date before merging" (`strict: true`) activo en
`main` y en `develop`, si `develop` no lo recibe el release siguiente aparece desactualizado
respecto de `main` y obliga a actualizar la rama con un merge. Pasó con la `0.2.1`: el PR
`release/0.2.1` → `main` salió marcado como desactualizado porque `main` tenía 8 commits que
`develop` no tenía (dos PR de documentación squasheados y los merge commits de la `0.2.0`).
Mergear `main` en la rama dio un árbol idéntico, así que no cambió ningún archivo, pero hizo
falta rehacer los checks.

Se hace con una rama de sincronización, no con un PR directo desde `main`:

1. Con el release ya mergeado en `main` (y el tag creado): `git fetch origin`.
2. `git checkout -b sync/main-to-develop-<versión> origin/develop`.
3. `git merge origin/main`. Si el árbol de `main` coincide con el de `develop` el merge es
   limpio y no cambia archivos; si hay conflictos, se resuelven a favor de `develop` salvo que
   `main` traiga algo que `develop` no tenga (un hotfix), que hay que conservar.
4. Push de la rama y PR a `develop` con título `chore(release): sincronizar main en develop
   tras <versión>`. Los checks de `develop` corren como en cualquier PR.
5. Se mergea con **"Create a merge commit"**, no con squash: un squash volvería a dejar la
   historia de `main` fuera de `develop`.
6. Se comprueba con `git rev-list --count origin/develop..origin/main`, que debe dar 0.

Por qué una rama de sincronización y no un PR `main` → `develop`: con `strict: true` en
`develop`, un PR cuya rama de origen es `main` quedaría desactualizado en cuanto `develop`
recibe cualquier commit, y el botón "Update branch" haría lo contrario de lo que se quiere:
mergear `develop` dentro de `main`. La rama de sincronización sale de `develop`, así que ya
está al día con él.

**"Require linear history".** La protección de `main` la tenía activada y bloqueaba el PR de
la `0.2.0` ("This branch must not contain merge commits"); el merge commit llegó igual a
`main`, pero no quedó registrado cómo se saltó la regla. El 2026-10-20 se desactivó en
Settings → Branches (`required_linear_history: false`, comprobado con la API) porque Git Flow
necesita el merge commit del release y del backport. `develop` tampoco la exige. El resto de
la protección de `main` no cambió: PR obligatorio, los 7 checks, "Require branches to be up to
date" y sin force push ni borrado.

Los commits y títulos de PR siguen Conventional Commits 1.0.0. La versión de
release es SemVer `vMAJOR.MINOR.PATCH` y etiqueta el mismo commit, JAR, imagen y
GitHub Release.

## Release a producción

> **Estado:** `release.yml` (#592) y `deploy.sh` (#593) están en el repositorio. El job `deploy` necesita además que `deploy.sh` esté instalado en el servidor y los secretos del Environment `production` (ver "Configuración en GitHub" y el runbook, sección 2.10); hasta entonces solo se puede probar con un tag con sufijo (`v0.1.0-rc.1`), que publica sin desplegar.

Tras el merge `release/* -> main` o `hotfix/* -> main`, se crea el tag SemVer
`vX.Y.Z` sobre `main`. Ese tag dispara el workflow de release
(`.github/workflows/release.yml`), que encadena cuatro jobs:

1. **`build`**: valida el formato del tag, comprueba que un tag estable apunta a un
   commit de `main` y corre `build` (tests, Checkstyle y cobertura) de los módulos
   JVM con `-Pversion=<versión del tag>`. Comprueba que `build-info.properties` de
   cada JAR declare esa versión y sube los dos JAR como artefactos. Android no entra:
   no se despliega en el VPS y ya se compila en `pr-develop.yml`.
2. **`publish`**: construye las imágenes de API y BFF copiando esos JAR (no se
   recompila dentro de Docker), las escanea con `scripts/trivy-scan.sh` (el mismo
   script y las mismas excepciones que `pr-main.yml`; ver "Escaneo de imágenes") y
   recién entonces las publica en `ghcr.io/urrestarazu-org/menta-api` y
   `menta-bff` con la versión como tag. Si el escaneo falla no se publica nada. El
   digest de cada imagen se lee tras el push y pasa a los jobs siguientes.
3. **`release`**: crea la GitHub Release con las notas generadas, los digests y los
   dos JAR (`menta-dance-api-<versión>.jar` y `menta-dance-bff-<versión>.jar`).
4. **`deploy`**: solo para tags estables. Entra por SSH al servidor y le pasa los
   **digests inmutables**, nunca un tag, junto con la configuración del stack del
   tag (compose, `nginx.conf`, `conf.d`). Usa el Environment `production` de GitHub.

Un tag con sufijo (`v0.1.0-rc.1`) ejecuta `build`, `publish` y `release` (la Release
queda marcada como pre-release) pero no `deploy`, y puede salir de una rama
`release/*`. `concurrency: production` sin cancelación impide dos releases a la vez;
GitHub conserva una sola ejecución en espera, así que si llegan dos tags mientras
otro corre, el más viejo de los dos pendientes se descarta y hay que volver a
empujarlo.

Cada job declara los permisos mínimos: `build` y `publish` leen el contenido,
`publish` además escribe paquetes, `release` escribe contenido y `deploy` no usa el
`GITHUB_TOKEN`. Las acciones están fijadas por SHA.

### Configuración en GitHub (una vez)

El job `deploy` lee sus datos del Environment `production` (Settings → Environments):

| Secreto | Contenido |
|---|---|
| `DEPLOY_HOST` | Nombre o IP del servidor |
| `DEPLOY_PORT` | Puerto SSH del servidor (si falta, el workflow usa 22) |
| `DEPLOY_USER` | `deploy` |
| `DEPLOY_SSH_KEY` | Clave privada de despliegue, cuya parte pública tiene el comando forzado en el servidor |
| `DEPLOY_KNOWN_HOSTS` | Línea de `known_hosts` del servidor, para el puerto real (`[host]:puerto ssh-ed25519 AAAA...`) |

Conviene comprobar la huella contra la del servidor (`ssh-keygen -lf
/etc/ssh/ssh_host_ed25519_key.pub`, ejecutado en el VPS) antes de guardarla: es lo
único que impide aceptar un servidor falso, porque el workflow usa
`StrictHostKeyChecking=yes`. El puerto va como secreto y no como variable para que no
quede visible en los logs del workflow cuando el repositorio sea público.

El Environment tiene al operador como *required reviewer*: el job de deploy espera una
aprobación manual ("Review deployments" en la página del run) antes de tocar producción.
El revisor se agregó el 2026-10-19, cuando el repositorio pasó a ser público: con el
repositorio privado la regla fallaba (`HTTP 422`: el plan no la soporta) y el Environment
se había creado sin reglas de protección. `prevent_self_review` está apagado a propósito:
el proyecto lo mantiene una sola persona, que es quien publica el tag y quien aprueba. Si
se suma más gente, conviene encenderlo.

La primera vez que se publican las imágenes, GHCR crea los paquetes `menta-api` y
`menta-bff` vinculados al repositorio por la etiqueta `org.opencontainers.image.source`.
El servidor las lee con el PAT de solo lectura de la sección 2.7 del runbook.

### Contrato con `deploy.sh`

La clave de despliegue ejecuta siempre `/opt/menta/deploy.sh` (comando forzado de
`authorized_keys`). El workflow le entrega dos cosas:

- los dos digests, como comando SSH. El servidor los recibe en `SSH_ORIGINAL_COMMAND`:
  `sha256:<64 hex de la API> sha256:<64 hex del BFF>`;
- por la entrada estándar, un `tar.gz` con `prod/` y `nginx/conf/conf.d/` de
  `infra/docker/`. Así el compose y el `nginx.conf` del servidor son siempre los del
  tag que se despliega, sin clonar el repositorio ni dejar credenciales de Git en el
  VPS.

`deploy.sh` valida los dos digests y el contenido del `tar.gz` (solo archivos y
carpetas dentro de esas dos rutas, tope de 5 MB) antes de usarlos: es lo que se ejecuta
con la clave. Después hace, en este orden:

1. toma un lock (un solo deploy a la vez) y deja la configuración en `/opt/menta/stack`;
2. `mysqldump` de MySQL a `/opt/menta/backups` (se conservan los últimos 14; si el
   backup falla o queda incompleto, no se despliega);
3. descarga las imágenes por digest (si falla, nada cambió);
4. guarda los digests vigentes en `/opt/menta/previous-digests` y escribe los nuevos
   en `/opt/menta/.env`;
5. `docker compose up -d` (Nginx se recrea solo si cambió la configuración);
6. espera hasta 240 s a que API, BFF y Nginx respondan UP;
7. deja de cada imagen solo la actual y la anterior.

Una salida distinta de 0 hace fallar el job `deploy`. Si el health check no llega a UP, el
script no revierte nada por su cuenta: imprime el comando para volver a la versión
anterior (ver "Rollback"). `deploy.sh` no se actualiza solo: si el release trae una
versión distinta de la instalada, lo avisa en el log y hay que reinstalarlo a mano
(runbook, sección 2.10).

### Rollback

El rollback es **manual**: si el health check falla, el script termina con error y
se vuelve a ejecutar con los digests de la release anterior
(`/opt/menta/deploy.sh $(cat /opt/menta/previous-digests)`; pasos y restauración del
backup en el runbook, sección 2.10). No existe rollback
automático de base de datos: Flyway solo avanza. Si la release trajo una migración
incompatible con la versión anterior, se restaura el backup previo al deploy. Las
migraciones destructivas se ejecutan solo en una release posterior a dejar de usar
los datos o columnas.

El rollback automático condicionado a evidencia de compatibilidad (ADR-0018) y la
publicación del JAR en GitHub Packages quedan fuera del flujo actual: el JAR se
adjunta a la GitHub Release.
