# Investigación: el video de la lección no se reproduce (`403` de Bunny.net)

**Estado:** cerrada el 2026-11-14 — el video se reproduce en local. Este documento es una bitácora de
referencia (como [TESTCONTAINERS-CONNECTION-REFUSED-INVESTIGATION.md](TESTCONTAINERS-CONNECTION-REFUSED-INVESTIGATION.md)):
deja el camino de evidencia, las causas que se descartaron y los errores de diagnóstico, para que quien vuelva a
ver el síntoma no tenga que rehacerlo. La guía operativa vigente está en
[26-LOCAL-DEV-SETUP-HOWTO.md](26-LOCAL-DEV-SETUP-HOWTO.md) ("Ver un video real en local" y "Si el video no se
reproduce") y la batería de Bruno en [`bruno/README.md`](../bruno/README.md) ("Diagnóstico de video").

**Issue:** #751 (firma real de las URLs de Bunny.net). **ADR:** [0040](adr/0040-local-bunny-net-signature-adapter.md).

> Los valores reales de la biblioteca (host, id de biblioteca, `videoId`, clave) no se escriben en el repositorio.
> Acá aparecen como `<host>`, `<videoId>`, etc.

## Síntoma

La página de una lección (`/courses/<curso>/lessons/<lección>`) mostraba el reproductor `<video>` en `0:00`, sin
duración ni imagen. Ni el log de la API ni el del BFF mostraban nada relacionado: el navegador pide el MP4
**directo al CDN**, así que un `403` o un `404` de Bunny.net no pasa por nuestros servidores.

## Resultado

El video se reproduce cuando se cumplen a la vez estas condiciones (cada una fue, en algún momento, una causa del
síntoma):

1. La API corre con el **adaptador real** de firma (`Bunny.net signature adapter: token authentication (real CDN)`
   en su log de arranque), no con el local de la demo (host falso `*.invalid`).
2. La lección usa un **`videoId` real** de la biblioteca (no la etiqueta `demo-…-free` del fixture).
3. `APP_CDN_BUNNYNET_SECURITYKEY` es la clave de la tarjeta **"Token authentication key"** de la página
   **Security > General** de la biblioteca de Stream (el campo se llama "API key" en esa tarjeta).
4. La biblioteca tiene **CDN token authentication** activado y **MP4 Fallback** activo para el video.
5. La petición del navegador trae un `Referer` (siempre lo trae el `<video>` de una página) y la configuración de
   dominios de Bunny.net no lo rechaza.

## Cronología y causas encontradas

### 1. La demo usaba un adaptador sin CDN real

Los perfiles `demo` y `e2e-bunny-net` usaban `LocalBunnyNetSignatureService` (ADR-0040): firma con un salt público
y apunta a un host inexistente a propósito, para no salir nunca a la CDN real. Además, `DemoVirtualCatalogFixture`
guardaba etiquetas de texto como `videoId`. Resultado: el navegador pedía un video a un dominio que no existe.

Y el adaptador "no local" (`StringFormatBunnyNetSignatureService`) era un placeholder: devolvía
`host/biblioteca/videoId` sin firma ni vencimiento, y ese formato no es el de Bunny Stream (que sirve
`https://<host>/<videoId>/play_720p.mp4`). Es decir, ni siquiera apuntando la demo al CDN real había un camino
que funcionara. Se resolvió con el issue #751: adaptador `TokenAuthBunnyNetSignatureService`, selección por perfil
y clave, y el fixture de la demo con un `videoId` configurable.

**Cómo se reconoce:** el log de arranque de la API **no** dice `token authentication (real CDN)`, o el `<video src>`
apunta a un host `*.invalid` / `invalid-missing-config` / `local-bunny-net`.

### 2. El pull zone exigía Token Authentication

Sin firma, todos los archivos directos (MP4, HLS, miniaturas) devuelven `403`. Se comprobó con `HEAD` contra el
host real: el MP4, `playlist.m3u8` y hasta la miniatura daban `403` sin token. Por eso el `videoId` y el host solos
no alcanzaban: hacía falta una URL firmada con la clave de la biblioteca.

### 3. La API en ejecución era anterior al cambio

La API y el BFF locales se habían levantado antes de tener el código de #751 y la clave. Las variables de entorno
solo se leen al arrancar: hay que reiniciar (`SPRING_PROFILES_ACTIVE=demo ./scripts/dev.sh restart`) y mirar el
log de arranque. Tras reiniciar, el log de arranque confirma que se aplicó: `Bunny.net configuration: host=… securityKey=configured`.

### 4. La clave no era la correcta (causa raíz del `403` con firma)

Con el adaptador real activo y una clave puesta, la sonda del servidor y la batería de Bruno seguían dando `403`,
**incluso con `Referer`**. Al reemplazar el valor de `APP_CDN_BUNNYNET_SECURITYKEY` por el de la tarjeta
**"Token authentication key"** (Security > General de la biblioteca) y reiniciar la API, la misma petición pasó de
`403` a `200`.

Qué valor se había puesto antes no se registró (no se inspeccionó el `.env`). Lo más probable, no confirmado, es
que fuera otra clave con nombre parecido: la biblioteca muestra tres, y dos se llaman "API Key".

| Dónde se ve | Nombre en el panel | ¿Sirve para firmar? |
|-------------|--------------------|---------------------|
| Security > General, tarjeta **Token authentication key** | "API key" (así se etiqueta el campo) | **Sí** |
| Pestaña **API** de la biblioteca | API Key | No |
| Pestaña **API** de la biblioteca | Read-only API Key | No |

La documentación oficial lo dice: *"The same key signs CDN token authentication URLs for the library, so resetting
it breaks both"* (embeds y URLs del CDN) — `stream/player/signed-embeds`. **No la regeneres**: rompe los embeds y
las URLs firmadas.

### 5. "Block direct url file access" volvió engañosa la sonda

La biblioteca tenía **Block direct url file access** activado y la lista **Allowed domains** vacía. Esa opción
rechaza las peticiones **sin cabecera `Referer`**, y la sonda del servidor (un `HEAD` de la API) no manda ninguno:
daba `403` aunque el token fuera correcto. Lo mismo pasaba con las requests de la primera versión de la batería de
Bruno que no enviaban `Referer`. Con la clave correcta, en esta biblioteca:

| Petición | Resultado |
|----------|-----------|
| `HEAD` con `Referer: http://localhost:8080/` | `200` |
| `GET` con `Referer` y `Range: bytes=0-1023` (lo que hace el navegador) | `206` |
| `HEAD` sin `Referer` | `403` |
| `HEAD` sin token, con `Referer` | `403` (la Token Authentication está activa) |

Por eso se agregaron la propiedad `APP_CDN_BUNNYNET_DIAGNOSTICPROBEREFERER` y las variantes con y sin `Referer` de la
batería: un `403` sin `Referer` es esperable cuando el bloqueo está activo y **no** indica un problema de firma.

## Lo que se afirmó mal en el camino (para no repetirlo)

- **"La clave de la biblioteca de Stream no sirve para el CDN; hay que usar la del pull zone."** Era incorrecto.
  Para una biblioteca de Stream, la clave de la tarjeta *Token authentication key* firma tanto los embeds como las
  URLs del CDN (documentación citada arriba). Estuvo escrito en `docs/26`, el README de Bruno, el runbook del VPS y
  ADR-0040; se corrigió. Ojo: la guía genérica de Token Authentication de un *pull zone suelto* habla de la
  "URL Token Authentication Key" del pull zone. Si con la clave de la biblioteca sigue el `403` y el resto está
  bien, ese es el segundo lugar a probar (biblioteca > API > Pull zone > Manage > Security).
- **"Hay que agregar `localhost` a Allowed domains."** No hizo falta. Con la lista vacía, una petición **con**
  `Referer` pasó (`200`/`206`), aunque la documentación advierte que "Block Direct URL File Access" con la lista
  vacía produce `403` en enlaces directos. Esa advertencia no se reprodujo para peticiones con `Referer` en esta
  biblioteca. `localhost` no aparece en ninguna página de la documentación de Bunny.net, y el panel puede no
  aceptarlo.
- **"El `403` de la sonda prueba que el token está mal."** No: sin `Referer` y con el bloqueo activo es `403`
  siempre. La sonda, y las requests de la batería, deben mandar el `Referer` del BFF para ser representativas.

## Cómo diagnosticarlo la próxima vez

1. **Log de arranque de la API** (`./scripts/dev.sh logs api`): ¿dice `token authentication (real CDN)` y
   `securityKey=configured`? ¿El host y el archivo son los esperados? Si no, es la causa 1 o 3.
2. **Batería de Bruno**, que prueba todo en orden y no imprime nunca la URL ni el token:

   ```bash
   cd bruno && bru run "Diagnostics/Video" --env local \
     --reporter-skip-all-headers --reporter-skip-body
   ```

   Se lee con la tabla síntoma → causa → arreglo de `bruno/Diagnostics/Video/folder.bru` y de `bruno/README.md`:
   `04` (`HEAD` con `Referer`) en `200` y `06` (sin `Referer`) en `403` es el estado sano con el bloqueo activo;
   `04` en `403` apunta a la clave, al esquema de firma o a los dominios; `04` en `404` es que el MP4 no existe
   (MP4 Fallback, o el nombre de archivo, `APP_CDN_BUNNYNET_FILENAME`).
3. **Sonda del servidor** (opcional, apagada por defecto): `APP_CDN_BUNNYNET_DIAGNOSTICPROBE=true` y
   `APP_CDN_BUNNYNET_DIAGNOSTICPROBEREFERER=http://localhost:8080/` en el `.env`, reiniciar y buscar
   `Bunny.net CDN probe` en el log de la API.
4. **Pestaña Network del navegador:** la petición al MP4 y su código de estado son la otra fuente de verdad.
5. **Reiniciar siempre** tras cambiar el `.env`: las variables solo se leen al arrancar.

## Para producción

- La clave va en el entorno del servidor (`APP_CDN_BUNNYNET_SECURITYKEY`), nunca en el repositorio. **Cualquier
  despliegue que no sea `demo` falla al arrancar sin ella** (fail-closed).
- Agregá a **Allowed domains** los dominios reales del sitio (sin esquema; `*.dominio.com` no incluye el dominio
  raíz: agregá `dominio.com` aparte si se sirve ahí) o, si se mantiene *Block direct url file access* con la lista
  vacía, verificá con la batería (`--env production`) que las peticiones con `Referer` responden `200`/`206`. La
  decisión de cambiar esa configuración es del operador de la biblioteca.
- Activá *CDN token authentication* y *MP4 Fallback* (solo los videos subidos después de activarlo generan el MP4).
- El pull zone tiene además su propia *Hotlink Protection*, independiente de la biblioteca: un `403` puede venir de
  cualquiera de los dos lugares.
- Corré la batería contra producción con un alumno de diagnóstico dedicado y credenciales como variables secretas
  (ver `bruno/environments/production.bru` y el runbook del VPS). Es solo lectura.

## Sin verificar

- Qué valor tenía la clave antes de corregirla (no se inspeccionó el `.env`).
- Si "Enable direct play" influye en las URLs de archivo del CDN: la documentación consultada no lo cubre y no se
  tocó.
- El camino de producción: la batería y la firma solo se ejercitaron en local.
- El esquema de firma **básico** (MD5) no se implementó: el avanzado (HMAC-SHA256) funcionó contra el pull zone
  de la biblioteca de prueba. Si algún pull zone solo aceptara el básico, habría que agregarlo.

## Referencias

- Bunny.net: [Seguridad de Stream](https://bunny.net/docs/stream/security),
  [Token Authentication del CDN](https://bunny.net/docs/cdn/security/token-authentication),
  [Hotlink Protection](https://bunny.net/docs/cdn/security/hotlink-protection).
- Repositorio: `TokenAuthBunnyNetSignatureService`, `ProbingBunnyNetSignatureService`, `BunnyNetProperties`
  (`api/virtual/.../infrastructure/cdn`), `bruno/Diagnostics/Video/`.
