# Pipeline CI/CD

## GitHub Actions y permisos

GitHub Actions es la única plataforma de CI/CD. El workflow de Pull Request usa
`contents: read` y no recibe secretos de despliegue ni `packages:write`. El de
release recibe permisos mínimos, incluido `packages:write`, y secretos sólo
mediante GitHub Secrets.

Todas las actions se fijan a un **SHA de commit completo**; dependencias,
plugins e imágenes se fijan a versiones exactas. No se permiten `latest`,
`master` ni rangos de versión.

## CI de Pull Request

En cada PR contra `develop` o `master`:

1. compilar backend, BFF y Android;
2. ejecutar Checkstyle, tests, JaCoCo, SonarCloud y ArchUnit;
3. construir assets Node/Tailwind con lockfile;
4. ejecutar Playwright, Gatling acordado y Trivy;
5. publicar resultados sin publicar artefactos ni desplegar.

Además, el job `gitleaks` corre en `pr-develop.yml` y en `pr-main.yml` (ver
[Detección de secretos](#detección-de-secretos)).

CI verde es obligatorio para mergear. Los PR son registro de trabajo individual:
no requieren aprobación humana y se integran con **squash merge**.

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
el build `gradle` de la raíz se divide en tres grupos. Una dependencia cae en el
**primer** grupo cuyo patrón coincide, de modo que el orden en el archivo importa:

| Grupo | Contenido | Por qué separado |
|---|---|---|
| `android-toolchain` | AGP, Kotlin, KSP, Hilt, AndroidX y Compose | Deben moverse juntos y un bump puede exigir cambiar `compileSdk` (#425) |
| `jvm-test-tooling` | ArchUnit, WireMock y Testcontainers | Sólo afectan a los tests |
| `gradle-minor-patch` | El resto | Todo lo demás |

El grupo de Android puede quedar rojo hasta resolver #425. Se acepta a
propósito: ignorarlo ocultaría también parches de seguridad.

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
- `release/*` nace de `develop` y se mergea a `master`.
- Después, `master` se mergea de regreso a `develop`.
- `hotfix/*` nace de `master`; su merge vuelve también a `develop`.

Los commits y títulos de PR siguen Conventional Commits 1.0.0. La versión de
release es SemVer `vMAJOR.MINOR.PATCH` y etiqueta el mismo commit, JAR, imagen y
GitHub Release.

## Release a producción

El merge `release/* -> master` o `hotfix/* -> master` dispara automáticamente:

1. crear tag SemVer;
2. publicar el JAR versionado en GitHub Packages y la imagen en GHCR;
3. registrar y desplegar el **digest inmutable** de la imagen, nunca una tag;
4. ejecutar migraciones Flyway hacia adelante, etiquetadas como
   `backward-compatible` o `manual-recovery-only`, y health checks;
5. si falla el health check, volver automáticamente al digest de la release
   anterior.

No existe rollback automático de base de datos. Si Flyway falla, se detiene la
release y se interviene manualmente. El rollback automático de imagen sólo se
permite con evidencia de compatibilidad: la aplicación anterior debe funcionar
contra el schema migrado y los datos escritos por la release nueva. La etiqueta
`backward-compatible` no sustituye esa prueba; sin evidencia se detiene para
recuperación manual. Las migraciones destructivas se ejecutan sólo en una release
posterior a dejar de usar los datos/columnas.
