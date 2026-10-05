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
