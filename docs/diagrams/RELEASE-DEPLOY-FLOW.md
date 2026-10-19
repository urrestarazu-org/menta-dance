# Diagramas de Release y Deploy

Cómo una imagen llega a producción: desde el tag hasta el stack corriendo en el VPS. Sintaxis Mermaid.

Fuentes: `.github/workflows/release.yml` (#592), `infra/docker/prod/deploy.sh` (#593), ADR-0018 y ADR-0045. El procedimiento en el servidor está en [`32-VPS-DONWEB-RUNBOOK.md`](../32-VPS-DONWEB-RUNBOOK.md) y el pipeline en [`16-CICD-PIPELINE.md`](../16-CICD-PIPELINE.md).

## Índice

1. [Visión general del release](#1-visión-general-del-release)
2. [Construcción y publicación de las imágenes](#2-construcción-y-publicación-de-las-imágenes)
3. [Deploy en el VPS](#3-deploy-en-el-vps)
4. [Vuelta atrás manual](#4-vuelta-atrás-manual)

---

## 1. Visión general del release

Un tag `vX.Y.Z[-sufijo]` dispara `release.yml`. Los JAR se construyen una sola vez y las imágenes solo los copian: lo que se escanea, se publica y se despliega es el mismo artefacto. El deploy referencia las imágenes por digest, nunca por tag.

Un tag con sufijo (`v0.2.0-rc.2`) publica las imágenes y un GitHub Release marcado como pre-release, pero **no despliega**. Un tag estable solo se acepta si su commit ya está en `main`.

```mermaid
flowchart TD
    Tag["Tag vX.Y.Z o vX.Y.Z-sufijo"] --> Valid{"¿Formato válido?"}
    Valid -- No --> Fail1(["Falla: tag inválido"])
    Valid -- Sí --> Stable{"¿Tag estable?<br/>sin sufijo"}
    Stable -- Sí --> OnMain{"¿Commit en main?"}
    OnMain -- No --> Fail2(["Falla: un release estable sale de main"])
    OnMain -- Sí --> Build
    Stable -- "No (pre-release)" --> Build

    Build["Job build<br/>Gradle build + tests<br/>JAR de API y BFF"] --> Publish

    Publish["Job publish<br/>imágenes + Trivy + push a GHCR<br/>(ver sección 2)"] --> Scan{"¿Trivy sin CRITICAL/HIGH<br/>con fix disponible?"}
    Scan -- No --> Fail3(["Falla: no se publica nada"])
    Scan -- Sí --> Release

    Release["Job release<br/>GitHub Release con JAR y digests"] --> Pre{"¿Pre-release?"}
    Pre -- Sí --> EndPre(["Fin: imágenes y release publicados,<br/>sin deploy"])
    Pre -- No --> Deploy

    Deploy["Job deploy<br/>Environment production<br/>(ver sección 3)"] --> EndProd(["Producción actualizada"])
```

El workflow corre con `concurrency: production` y `cancel-in-progress: false`: un solo release a la vez y ninguno se cancela a mitad, porque un deploy interrumpido deja el VPS a medias.

---

## 2. Construcción y publicación de las imágenes

El escaneo de Trivy ocurre **antes** del push a GHCR. Si el gate falla, el job termina ahí y el registro queda sin la imagen.

```mermaid
sequenceDiagram
    autonumber
    participant Dev as Equipo
    participant GH as GitHub Actions
    participant Gradle
    participant Docker as Docker (runner)
    participant Trivy
    participant GHCR as GHCR

    Dev->>GH: git push tag vX.Y.Z[-sufijo]
    GH->>GH: Valida formato del tag y deriva la versión
    opt Tag estable
        GH->>GH: Verifica que el commit está en main
    end

    rect rgb(235, 245, 255)
    Note over GH,Gradle: Job build
    GH->>Gradle: build + test de api:* y bff con -Pversion
    Gradle-->>GH: menta-dance-api.jar y menta-dance-bff.jar
    GH->>GH: Verifica build.version dentro de cada JAR
    GH->>GH: Sube los JAR como artefactos del workflow
    end

    rect rgb(240, 255, 240)
    Note over GH,GHCR: Job publish
    GH->>Docker: docker build Dockerfile.api y Dockerfile.bff<br/>(copian el JAR, no compilan)
    Docker-->>GH: imágenes locales menta-api y menta-bff
    GH->>Trivy: scripts/trivy-scan.sh por imagen
    alt Hallazgo CRITICAL/HIGH con fix
        Trivy-->>GH: Falla
        GH-->>Dev: Release cortado, nada publicado
    else Sin hallazgos bloqueantes
        Trivy-->>GH: OK
        GH->>GHCR: docker push de ambas imágenes
        GHCR-->>GH: Digests sha256 de API y BFF
        GH->>GH: Valida formato del digest y lo expone como output
    end
    end

    rect rgb(255, 250, 235)
    Note over GH: Job release
    GH->>GH: gh release create con los JAR y los digests<br/>(--prerelease si el tag tiene sufijo)
    end
```

---

## 3. Deploy en el VPS

Solo corre con un tag estable. El Environment `production` guarda los secretos de deploy y, si se configura, exige una aprobación antes de que el job arranque.

La clave SSH del workflow tiene un `command=` forzado en `authorized_keys`: lo único que puede ejecutar es `/opt/menta/deploy.sh`. Los digests llegan al script en `SSH_ORIGINAL_COMMAND` y la configuración del stack (compose, `nginx.conf`, `conf.d`) por la entrada estándar como `tar.gz`, de modo que el servidor siempre usa la configuración del tag que se despliega.

```mermaid
sequenceDiagram
    autonumber
    participant Rev as Revisor
    participant GH as GitHub Actions
    participant VPS as VPS (deploy.sh)
    participant MySQL as menta-mysql
    participant GHCR as GHCR
    participant Compose as Docker Compose

    GH->>Rev: Solicita aprobación (Environment production)
    Rev-->>GH: Aprueba
    GH->>GH: Configura SSH con la clave y el known_hosts guardados
    GH->>VPS: ssh "api-digest bff-digest" + tar.gz del stack por stdin

    VPS->>VPS: Valida los digests (sha256 de 64 hex)
    VPS->>VPS: Toma el lock (un deploy a la vez)
    VPS->>VPS: Valida el tar.gz y reemplaza /opt/menta/stack

    VPS->>MySQL: mysqldump comprimido antes de tocar nada
    alt Backup falla o queda truncado
        VPS-->>GH: Error: no se despliega
    end

    VPS->>GHCR: compose pull con los digests nuevos
    alt Descarga falla
        VPS-->>GH: Error: nada cambió
    end

    VPS->>VPS: Guarda los digests actuales en previous-digests
    VPS->>VPS: Escribe API_DIGEST y BFF_DIGEST en .env
    VPS->>Compose: compose up -d
    opt La configuración del stack cambió
        VPS->>Compose: Recrea Nginx
    end

    loop Hasta HEALTH_TIMEOUT (240 s)
        VPS->>Compose: Health de API, BFF y Nginx
    end

    alt Stack saludable
        VPS->>VPS: Deja solo la imagen actual y la anterior
        VPS-->>GH: Deploy completado
    else No responde UP a tiempo
        VPS-->>GH: Error con logs y comando de vuelta atrás
        Note over VPS: No hay rollback automático
    end
```

Qué protege cada paso:

| Paso | Riesgo que cubre |
|---|---|
| Digest en lugar de tag | El tag es mutable; el digest identifica exactamente lo que se escaneó |
| `command=` forzado en la clave | Una clave filtrada solo puede lanzar `deploy.sh`, no una shell |
| Validación de digests y del `tar.gz` | Lo único que viaja desde afuera no llega a ningún comando sin validar |
| `mysqldump` previo | Flyway solo avanza: el backup es la única vuelta atrás de los datos |
| `pull` antes de modificar `.env` | Si la descarga falla, el estado del servidor no cambia |
| Lock con `flock` | Dos deploys simultáneos no se pisan |

---

## 4. Vuelta atrás manual

`deploy.sh` no revierte solo. La base de datos no se puede deshacer automáticamente (Flyway no tiene rollback), así que quién opera decide.

```mermaid
flowchart TD
    Fail(["Deploy falla o la versión nueva se comporta mal"]) --> Cause{"¿Qué falló?"}

    Cause -- "pull de imágenes" --> NoChange["El servidor no cambió:<br/>revisar el token de GHCR (runbook 2.7)"]
    Cause -- "Health check tras compose up" --> Logs["Revisar logs de api y bff"]

    Logs --> Schema{"¿La versión nueva<br/>aplicó migraciones Flyway?"}
    Schema -- No --> Roll["Volver a la versión anterior:<br/>/opt/menta/deploy.sh $(cat /opt/menta/previous-digests)"]
    Schema -- Sí --> Compat{"¿La versión anterior es compatible<br/>con el esquema nuevo?"}
    Compat -- Sí --> Roll
    Compat -- No --> Restore["Restaurar el backup previo<br/>de /opt/menta/backups<br/>y luego volver a la versión anterior"]

    Roll --> Verify["Verificar health de API, BFF y Nginx"]
    Restore --> Verify
    NoChange --> Verify
    Verify --> Done(["Servicio recuperado"])
```

Al volver atrás, `deploy.sh` usa la configuración del stack ya instalada (sin entrada estándar) y las imágenes anteriores siguen en el servidor, porque el script conserva la actual y la anterior.
