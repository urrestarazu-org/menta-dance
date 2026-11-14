# Menta Dance Bruno Collection

Colección unificada para testing de API y BFF. Abrí `bruno/` como la colección raíz y seleccioná un environment antes de ejecutar requests.

## Estructura

```
bruno/
├── environments/           # Configuración compartida
│   ├── local.bru          # Desarrollo local
│   ├── production.bru     # Placeholders + secretos sin valor (diagnóstico de video)
│   └── e2e-catalog-content.bru  # Recorridos aislados E2E/*
│
├── E2E/                    # Recorridos automatizados aislados
│   ├── catalog-content/   # Registro, contenido, catálogo público
│   │   ├── 01-registration/
│   │   └── 02-journey/
│   └── mercadopago/       # Simulador local de Mercado Pago (issue #128)
│       ├── 01-registration/
│       └── 02-journey/
│
├── Diagnostics/           # Diagnósticos de solo lectura
│   └── Video/             # "La lección no reproduce el video" (Bunny.net)
│       ├── folder.bru     # Orden y tabla de decisión
│       └── 01 … 08        # API → login → URL firmada → CDN → página del BFF
│
├── API - Direct/          # Tests directos contra la API
│   ├── auth/              # Autenticación
│   ├── health.bru         # Health checks
│   ├── physical/          # Clases presenciales (placeholders)
│   └── virtual/           # Cursos virtuales (placeholders)
│
└── BFF - Session/         # Tests de integración BFF
    ├── 1. Login.bru
    ├── 2. Dashboard (Authenticated).bru
    ├── 3. Logout.bru
    ├── 4. Dashboard (After Logout).bru
    ├── README.md          # Guía específica de BFF Session
    ├── SETUP.md           # Setup local
    └── TESTING-CURL.md    # Testing con curl
```

## Environments

### local
- **API**: `http://localhost:8081`
- **BFF**: `http://localhost:8080`
- **Credenciales de prueba**:
  ```
  email: student@example.com
  password: password123
  ```

> **Nota**: Las variables `authToken` y `refreshToken` son runtime variables. NUNCA las guardes en archivos versionados.

### e2e-catalog-content

Este environment está reservado para el recorrido automatizado de
`E2E/catalog-content`. No se ejecuta manualmente contra el stack normal: el
comando canónico es, desde la raíz del repositorio:

```bash
scripts/e2e/catalog-content.sh
```

El runner crea su propio Compose project, usa `baseUrl` en el puerto aislado y
orquesta dos fases: registra al estudiante y luego obtiene el mensaje de
Mailpit para inyectar el token de activación únicamente durante la ejecución de
Bruno. Las variables de IDs y access tokens también viven sólo en runtime.

Para limpiar únicamente ese ambiente:

```bash
scripts/e2e/catalog-content.sh --clean
```

No agregues `activationToken`, access tokens ni refresh tokens al environment
versionado. El flujo no llama a proveedores de pago, Bunny.net ni endpoints de
streaming.

### E2E/mercadopago

Recorrido del simulador local de Mercado Pago (issue #128): checkout, webhook
firmado, fulfillment por el worker existente, idempotencia de webhooks
duplicados y reconciliación por inconsistencia. Reutiliza el environment
`e2e-catalog-content` y corre automáticamente al final de:

```bash
scripts/e2e/catalog-content.sh
```

Usa dos estudiantes independientes (uno para el camino aprobado/duplicado, otro
para el camino de mismatch/reconciliación) porque un resultado inconsistente
deja la suscripción en `PENDING` — no la cancela — ocupando el cupo de
suscripción única de esa cuenta. El endpoint de preparación
(`/api/v1/e2e/mercadopago/*-webhook`) sólo existe bajo el perfil
`e2e-mercadopago`, nunca calcula ni loguea el secreto HMAC, y sólo entrega la
firma ya calculada para que Bruno reenvíe el webhook al controller público
real. Detalle completo en
[26-LOCAL-DEV-SETUP-HOWTO.md](../docs/26-LOCAL-DEV-SETUP-HOWTO.md#simulador-local-de-mercado-pago-issue-128).

## Cómo usar

### Opción 1: Bruno Desktop (GUI)

1. Abrir Bruno
2. **Open Collection** → seleccionar carpeta `bruno/`
3. Seleccionar environment **local** en el dropdown superior
4. Navegar a un folder:
   - **API - Direct** → Para testear la API directamente
   - **BFF - Session** → Para testear el flujo de sesiones del BFF

### Opción 2: Bruno CLI

**Ejecutar toda la colección:**
```bash
cd bruno && npx @usebruno/cli run --env local .
```

**Ejecutar solo BFF Session:**
```bash
cd bruno && npx @usebruno/cli run --env local --folder "BFF - Session" .
```

**Ejecutar solo API Direct:**
```bash
cd bruno && npx @usebruno/cli run --env local --folder "API - Direct" .
```

## Folder: API - Direct

Tests directos contra la API (`http://localhost:8081`).

### Authentication flow

Ejecutar en este orden:

1. **Register User** - Durante el desarrollo de `auth-account-activation` el endpoint está deshabilitado y debe retornar `503`. No crea un usuario fixture.
2. **Auth Login** - Requiere una cuenta local ya existente y activa; retorna `access_token` en JSON y `refresh_token` en header `X-Refresh-Token`. El script post-response guarda ambos como runtime variables.
3. **Auth Refresh** - Envía `X-Refresh-Token`, verifica rotación, actualiza runtime variables.
4. **Auth Logout** - Envía bearer token + `X-Refresh-Token`, retorna `204 No Content`, limpia runtime variables.

> **Importante**: El refresh token NUNCA aparece en el body JSON de request o response, solo en el header `X-Refresh-Token`.

### Health check

`health.bru` verifica `GET /actuator/health` retorna `200` y `status: UP`.

### Physical y Virtual (placeholders)

Los módulos physical classes y virtual courses aún no tienen endpoints implementados. Sus `.bru` son placeholders de documentación:
- No tienen assertions de success
- El collection runner los skipea
- Las variables `physicalClassesUrl` y `virtualCoursesUrl` están intencionalmente sin definir

Definí esas URLs solo cuando los controllers estén implementados.

## Folder: BFF - Session

Tests de integración del flujo completo de autenticación con sesiones (`http://localhost:8080`).

Ver documentación detallada en `BFF - Session/README.md`.

### Quick start

1. Configurar `email` y `password` del environment `local` con una cuenta local
   ya existente y activa. El registro público está temporalmente deshabilitado
   mientras se completa `auth-account-activation`.

2. Ejecutar requests EN ORDEN:
   - **1. Login** → 302, cookie SESSION
   - **2. Dashboard (Authenticated)** → 200 OK
   - **3. Logout** → 302, SESSION expirada
   - **4. Dashboard (After Logout)** → 302 a /login

3. O ejecutar con CLI:
   ```bash
   cd bruno && npx @usebruno/cli run --env local --folder "BFF - Session" .
   ```

**Resultado esperado**: las 11 aserciones de BFF Session pasan cuando el
environment usa una cuenta activa válida.

## Diagnóstico de video (Diagnostics/Video)

Batería de requests de **solo lectura** (GET/HEAD; el único POST es el login, que no crea datos) para saber en qué
eslabón falla la reproducción de una lección. El navegador pide el MP4 **directo al CDN de Bunny.net**, así que un
`403`/`404` no aparece en los logs de la API ni del BFF: estas requests le preguntan al CDN lo mismo que el
navegador. Contexto y la sonda opcional del lado del servidor:
[26-LOCAL-DEV-SETUP-HOWTO.md](../docs/26-LOCAL-DEV-SETUP-HOWTO.md#si-el-video-no-se-reproduce).

Caso real, con la cronología y los errores de diagnóstico, en
[BUNNY-VIDEO-403-INVESTIGATION.md](../docs/BUNNY-VIDEO-403-INVESTIGATION.md).

### Cómo correrla

Con Bruno CLI 4.2.1 (`bru`) no hace falta ningún flag especial: los scripts funcionan en el sandbox por defecto
(`safe`), sin `--sandbox developer` ni `-r`. Correr siempre la carpeta completa y **en orden**, porque cada request
usa lo que guardó la anterior (URL firmada, host, estado del `HEAD`).

```bash
# Local (alumno demo de la tabla de cuentas del README: ya está en environments/local.bru)
cd bruno && bru run "Diagnostics/Video" --env local

# Producción (usar una cuenta de alumno DEDICADA al diagnóstico; nunca versionar credenciales)
cd bruno && bru run "Diagnostics/Video" --env production \
  --env-var baseUrl=https://<api-real> --env-var bff_url=https://<bff-real> \
  --env-var diagLessonId=<id> --env-var diagCourseId=<id> \
  --env-var diagEmail=<cuenta-diagnostico> --env-var diagPassword=<contraseña>
```

`environments/production.bru` solo trae placeholders (`api.example.com`, `www.example.com`) y declara
`diagEmail`/`diagPassword` como `vars:secret` **sin valor**: en Bruno Desktop se cargan como secretos del
environment; en el CLI, con `--env-var`. Si falta `diagLessonId`, `diagEmail` o `diagPassword`, las 8 requests se
omiten solas, por lo que una corrida de toda la colección con un environment no preparado (por ejemplo los `e2e-*`)
no las ejecuta. Ningún script de `scripts/e2e/` ni de CI corre esta carpeta.

### Qué dice cada request

| # | Request | Qué responde |
|---|---------|--------------|
| 01 | API health | La API está arriba. |
| 02 | Login (diagnostic student) | La cuenta de diagnóstico entra; el token vive solo en la variable de runtime `diagAuthToken`. |
| 03 | Get lesson stream | La API firma una URL real: `https`, host que no es placeholder (adaptador local falso), `token` y `expires` presentes, TTL restante, ruta `/<videoId>/<archivo>.mp4` y `videoId` real (no la etiqueta `demo-…`). |
| 04 | CDN HEAD with Referer | El CDN acepta la URL firmada con el `Referer` del BFF (`<bff_url>/`), como el `<video>` del navegador (`200`/`206`, `video/mp4`, `accept-ranges: bytes`). |
| 05 | CDN GET first bytes with Referer (Range) | El CDN responde `206` con `video/mp4` y un cuerpo chico, con `Referer`, como el `<video>`. |
| 06 | CDN HEAD without Referer (control) | El mismo `HEAD` de 04 sin `Referer`. `403` aquí con 04 en `2xx` significa que *Block Direct URL File Access* está activo (normal: el navegador manda `Referer`). |
| 07 | CDN HEAD without token (control) | Sin token pero **con** el mismo `Referer` que 04, el CDN responde `403` (CDN token authentication activa): un `403` aquí solo puede deberse a la falta de token. Un `200` significa que el video es público. Solo es concluyente si 04 dio `2xx`. |
| 08 | BFF lesson page has a video source | La página de la lección del BFF trae `<video src>` con host real y el mismo host que firmó la API. Hace el login del BFF dentro del script (cookie `Secure` enviada a mano). |

### Tabla de decisión

| Síntoma | Causa probable | Arreglo |
|---------|----------------|---------|
| 03: "host is not a placeholder" | Adaptador local falso activo | Falta `APP_CDN_BUNNYNET_SECURITYKEY` o el host del pull zone; reiniciar la API |
| 03: "videoId ... not a demo label" | La lección conserva `demo-sensual-free` | `DEMO_VIRTUAL_FREE_LESSON_VIDEO_ID` con el id real; reiniciar la API |
| 04 con `Referer` `2xx` y 06 sin `Referer` `403` | *Block Direct URL File Access* activo en la biblioteca | Nada: el navegador siempre manda `Referer`. Un `curl`/`HEAD` sin `Referer` da `403` aunque el token sea perfecto |
| 04 `403` y 06 `2xx` | El hostname del `Referer` (`localhost`) no está permitido: *Allowed domains* de la biblioteca (Security > General) o *Hotlink Protection* del pull zone (CDN > pull zone > Security; desde la biblioteca: API > Pull zone > Manage), que dan `403` de forma independiente | Producción: agregar los dominios reales (`example.com` y `*.example.com`: el comodín no incluye el raíz; sin esquema). Local: `localhost` no figura en la documentación de Bunny.net y puede no poder agregarse; opciones de Bunny.net: apagar *Block Direct URL File Access* de la biblioteca solo durante la prueba y reactivarlo, o usar una biblioteca de prueba aparte (ajustes independientes por biblioteca). En el pull zone, esa opción aparece recién con *allowed referrers* configurados y bloquea el `Referer` vacío. Lo decide quien administra la biblioteca |
| 04 `403`, 06 `403` y 07 `403` | Token rechazado (clave/esquema) **o** `Referer` no permitido: estas requests no los distinguen | Clave = tarjeta **Token authentication key** de Security > General de la biblioteca Stream (rotulada *API key*; no la API key de la página *API* ni la Read-only; no resetearla: rompe embeds y CDN), con *CDN token authentication* activada ahí; esquema avanzado (HS256, el que firma la API) y no el básico (MD5), documentados en `/cdn/security/token-authentication`. Para aislar: apagar de forma temporal *Allowed domains* y *Block Direct URL File Access*; si 04 sigue en `403`, es clave/esquema; si pasa a `2xx`, era el `Referer`/dominio. También pueden bloquear reglas del pull zone (IP allowlist, Edge Rules, hotlink protection) |
| 04 `404` | El MP4 no existe | Activar *MP4 Fallback* y volver a subir el video; o `APP_CDN_BUNNYNET_FILENAME` (por ejemplo `play_480p.mp4`) |
| 07 `200` | *CDN token authentication* apagada | Activarla en Security > General de la biblioteca |
| 04/05 `2xx` pero el navegador no reproduce | Del lado del navegador | Pestaña *Network*: contenido mixto, códec, `Content-Type`, reloj vs `expires`, y que el `Referer` real de la página (distinto de `bff_url`) esté permitido |
| 08 sin `<video src>` | Cableado BFF/API | Logs del BFF; que la API devuelva `stream.url` |

Para una corrida por CLI que no guarde secretos: `bru run "Diagnostics/Video" --env local --reporter-skip-all-headers --reporter-skip-body`
(sin cabeceras ni cuerpos; igualmente no compartir la salida si usás un reporter de archivo).

El detalle completo está en `Diagnostics/Video/folder.bru` (pestaña *Docs* de la carpeta en Bruno Desktop).

### Seguridad

- La URL firmada contiene el token. Los scripts no la imprimen ni la guardan en archivos; solo vive en la variable
  de runtime `cdnSignedUrl`, y los tests comparan booleanos para que Chai no la muestre al fallar.
- **La timeline y la consola de Bruno Desktop SÍ muestran la URL firmada** de las requests 04 a 07 (y la de la
  respuesta de 03). Tratá las capturas de pantalla como secretos.
- Los reporters del CLI (`--reporter-json|html|junit`) guardan el cuerpo de 03 (URL firmada), el access token de 02,
  la contraseña enviada y la cabecera `Authorization`. Evitalos o agregá `--reporter-skip-all-headers
  --reporter-skip-body`, y no compartas el archivo. No uses `--verbose`.
- La salida normal del CLI no incluye la URL, el token ni cabeceras (verificado buscando `token=`, `HS256`,
  `expires=` y `Bearer` en la salida de una corrida completa).

## Troubleshooting

### Variables no se cargan en Bruno GUI

1. Verificá que seleccionaste el environment **local** en el dropdown superior
2. Si sigue sin funcionar, cerrá y volvé a abrir la colección
3. Alternativa: Usá Bruno CLI que siempre funciona

### "Connection refused"

Verificá que los servicios estén corriendo:
```bash
./scripts/dev.sh status
```

Si no están corriendo:
```bash
./scripts/dev.sh start
```

### Testing con curl

Si preferís curl, seguí la guía en `BFF - Session/TESTING-CURL.md`.

## Referencias

- [Guía de Setup Local](../docs/26-LOCAL-DEV-SETUP-HOWTO.md)
- [Documentación de la API Auth](../docs/03-AUTH-API.md)
