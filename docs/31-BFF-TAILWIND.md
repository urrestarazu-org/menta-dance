# 31 - Pipeline de Tailwind CSS del BFF

Línea base: [ADR-0024](adr/0024-technology-baseline.md) (Tailwind CSS 3.4.1,
Node.js 20.11.1, PostCSS 8.4.35, Autoprefixer 10.4.17). Issue origen: #536.

> Estado: **slice 3b (migración completa)**. Existen el pipeline, los tokens, los
> componentes (`@layer components`) y el layout común; todas las vistas de
> `templates/` lo adoptan (cuenta, catálogo, curso, lección, planes, panel y compra).

## Estructura

```
bff/
├── package.json              # versiones exactas, engines.node = 20.11.1
├── package-lock.json         # fuente de verdad para `npm ci`
├── tailwind.config.js        # content scan + tema (tokens)
├── postcss.config.js         # tailwindcss + autoprefixer
└── src/main/
    ├── frontend/css/input.css        # CSS fuente (NO es un recurso servido)
    ├── frontend/scripts/copy-fonts.js # copia los woff2 de Poppins a /fonts
    └── resources/templates/
        ├── fragments/head.html       # único lugar que carga app.css
        └── *.html                    # el content scan lee estos archivos
```

La salida se genera en `bff/build/generated/tailwind/static/css/app.css`, entra
al classpath como `static/css/app.css` y se sirve en `/css/app.css`. **Nunca se
versiona** (`build/` y `bff/node_modules/` están en `.gitignore`).

## Comandos

Node lo provisiona Gradle (plugin `com.github.node-gradle.node`, `download = true`,
`version = "20.11.1"`); no hace falta tener Node instalado ni usar `nvm`. El runtime
queda cacheado en `bff/.gradle/nodejs`.

| Qué | Comando |
|-----|---------|
| Instalar dependencias desde el lockfile (`npm ci`) | `./gradlew :bff:npmInstall` |
| Compilar y minificar el CSS | `./gradlew :bff:tailwindBuild` |
| Build normal del BFF (ya incluye el CSS) | `./gradlew :bff:build` |
| Modo watch local | `./gradlew :bff:npmInstall :bff:processResources`, luego `npm run watch` dentro de `bff/` (con el Node provisionado en `bff/.gradle/nodejs/node-v20.11.1-*/bin` en el `PATH`) |

`processResources` depende de `tailwindBuild`, y `tailwindBuild` de `npmInstall`.
Las entradas declaradas son `package.json`, `package-lock.json`, las dos
configuraciones, `src/main/frontend` y `src/main/resources/templates`: si ninguna
cambia, Gradle no vuelve a ejecutar Tailwind.

Para correr la app con el watch activo y que el hash de la URL siga al archivo,
exportá `SPRING_WEB_RESOURCES_CHAIN_CACHE=false` antes de `bootRun`.

Para cambiar una dependencia: editá `package.json` con la versión **exacta**,
ejecutá `npm install --package-lock-only` con Node 20.11.1 y commiteá ambos archivos.

## Tipografía

Fuente: **Poppins**, alineada con el proyecto de referencia (`menta-virtual-academy`).
Escala (tokens `fontSize` en `tailwind.config.js`; el resto de las medidas de Tailwind se conserva):

| Token | Tamaño | Uso |
|-------|--------|-----|
| `text-body` | 16px | Texto base (`html`). |
| `text-small` | 12px | Texto secundario. |
| `text-subtitle` | 24px | `h2` (semibold). |
| `text-title` | 36px | `h1` (bold). |

`fontFamily.sans` = Poppins + el stack de sistema como fallback (mientras carga o si falla).

### Self-hosting de la fuente

No se usa Google Fonts ni ningún CDN: la CSP de producción es `default-src 'self'`
(`infra/docker/nginx/conf/nginx.production.conf.example`) y un CDN rompería la política.

- Dependencia: `@fontsource/poppins` (versión exacta, solo `devDependency`).
- `npm run build` ejecuta Tailwind y luego `src/main/frontend/scripts/copy-fonts.js`, que copia
  **solo** `poppins-latin-{400,500,600,700}-normal.woff2` a `build/generated/tailwind/static/fonts/`.
- `input.css` declara los cuatro `@font-face` con `font-display: swap`.
- Spring las sirve en `/fonts/**`; `BffSecurityConfig` permite **solo GET** (igual que `/css/**`).
- Las URLs de `/fonts/**` son **estables** (sin hash de contenido): el versionado de Spring solo
  reescribe referencias en plantillas, no los `url()` dentro del CSS. NGINX cachea `woff2`
  30 días; al actualizar `@fontsource/poppins` hay que considerar ese plazo.
- Licencia: Poppins se distribuye bajo **SIL Open Font License 1.1 (OFL)**, que permite
  uso, embebido y redistribución con la aplicación.

## Formas

| Elemento | Clases |
|----------|--------|
| `.btn` | `px-6 py-2 rounded-lg font-semibold` |
| `.card` | `rounded-xl shadow-lg p-6` (conserva el borde) |
| `.field-input` | `rounded-lg px-4 py-2` |
| `.badge` | `rounded-full px-3 py-1 text-sm font-medium` |
| `.container-page` | `px-4 sm:px-6 lg:px-8` |

## Cache-busting

NGINX cachea `*.css` 30 días como `immutable` (`infra/docker/nginx/conf/conf.d/locations.conf`).
El BFF activa la estrategia de versionado por **contenido** de Spring
(`spring.web.resources.chain.strategy.content`, solo `/css/**`): `th:href="@{/css/app.css}"`
se reescribe a `/css/app-<md5>.css`. Un build con CSS distinto produce otra URL, así que la
caché inmutable nunca sirve una hoja vieja. No requiere cambios en NGINX.

## Tokens

- La paleta Menta vive **una sola vez**, como variables CSS en `input.css`
  (`--color-brand-deep`, ...) y se expone a Tailwind con nombres semánticos en
  `tailwind.config.js`. En las vistas nunca se escribe un hex.
- Convención de nombres: `brand-<rol>`.

| Token | Valor | Uso |
|-------|-------|-----|
| `brand-deep` | `#043F2E` | texto principal, bordes fuertes |
| `brand-action` | `#2A6F2B` | acciones, enlaces, foco |
| `brand-bright` | `#78C51C` | acentos y decoración |
| `brand-light` | `#C8F169` | resaltados y fondos suaves |
| `brand-surface` | `#EEF2E3` | fondo de página |

Contraste: `brand-bright` y `brand-light` **no** sirven como color de texto sobre fondo
claro. Texto blanco sobre `brand-action` y `brand-deep` sobre `brand-surface` cumplen AA.

## Utility, componente o CSS específico

1. **Utility** (por defecto): estilo de un solo uso o de una vista. Se escribe en el
   template con clases de Tailwind y tokens.
2. **Componente** (`@layer components`): el mismo conjunto de utilities se repite en
   3 o más lugares y tiene nombre de dominio (botón, card, badge). Se define con `@apply`
   y se documenta junto a la clase. Un componente que no usa ninguna vista no se agrega.
3. **CSS específico**: solo para lo que Tailwind no puede expresar. Va en un archivo
   complementario pequeño con dueño explícito y una justificación por regla.

Nunca se duplica la misma regla en más de una de las tres capas.

## Componentes

Definidos en `input.css` (`@layer components`) con `@apply` y tokens. Se incluyen
siempre completos vía `safelist` en `tailwind.config.js`: Tailwind descartaría los
que ninguna vista usa todavía.

| Clase | Uso |
|-------|-----|
| `.btn` + `.btn-primary` / `.btn-secondary` | Botón. Estado deshabilitado con `disabled` o `aria-disabled="true"`. |
| `.btn-danger` | Variante destructiva (borrar un curso virtual): el texto del botón nombra la acción, el color solo la refuerza. |
| `.field`, `.field-label`, `.field-input`, `.field-error` | Campo de formulario. `aria-invalid="true"` en el input activa el borde de error. |
| `.card` | Contenedor con borde, esquinas `rounded-xl` y sombra `shadow-lg`. |
| `.badge` + `.badge-success` / `-warning` / `-danger` / `-neutral` | Etiqueta de estado; el texto siempre lleva el significado, no solo el color. |
| `.container-page` | Ancho máximo y gutter horizontal de la página. |
| `.alert` + `.alert-error` / `-success` / `-info` | Mensaje en bloque; `role="alert"` para errores, `role="status"` para el resto. |
| `.error-summary` (+ `-title`, `-list`) | Resumen de errores de un formulario con errores de campo; se usa a través del fragmento `errorSummary`. Tiene `tabindex="-1"` y su propio contorno de foco. |
| `.btn-sm`, `.btn-ghost`, `.btn-ghost-danger`, `.btn-dashed` | Tamaño chico y variantes de botón (#849), siempre junto a `.btn`: `btn-sm` para acciones de una fila, `btn-ghost` (solo texto, relleno suave al pasar el mouse), `btn-ghost-danger` (texto rojo `feedback-error`, para "Borrar") y `btn-dashed` (caja punteada "Crear ..."). El texto nombra la acción; el color solo la refuerza. |
| `.badge-dot`, `.badge-icon` | Con `.badge` y un `badge-*`: un punto o un ícono antes del texto. Se usan a través de los fragmentos `dotBadge` e `iconBadge`. |
| `.card-item`, `.card-item-selected` | Tarjeta de un elemento de lista (menos sombra y padding que `.card`). La seleccionada suma borde oscuro de 2 px (borde + anillo, sin mover el contenido) y **siempre** va con un texto ("Seleccionado") y `aria-current`. |
| `.module-number`, `.module-number-active`, `.icon-tile`, `.icon-tile-warning` | Cuadrado redondeado con el número de un módulo (oscuro si es el seleccionado) y cuadrado con el ícono de una lección (de advertencia si no tiene video). |
| `.admin-tabs`, `.admin-tab`, `.admin-tab-active` | Pestañas de gestión como píldoras (`fragments/management`): la actual va en verde oscuro con texto claro y conserva `aria-current="page"`. |
| `.link-back`, `.link-title`, `.text-muted` | Vínculo "Volver" con flecha (subrayado), título enlazado dentro de una tarjeta y texto secundario (`brand-deep` al 80 %, 6,65:1 sobre blanco). |
| `.alert-with-icon`, `.alert-icon` | Los pone el fragmento `alert`: ícono decorativo antes del texto. Los `.alert` escritos a mano en las plantillas no cambian. |

Tokens de feedback (contraste AA sobre su superficie): `feedback-error`,
`feedback-success`, `feedback-warning` y sus variantes `-surface`.

### Layout

`fragments/layout.html :: page(title, content)` aporta `<head>` (con
`fragments/head :: assets`), enlace "Saltar al contenido", header con la marca,
`<main id="main">` y footer. El título final es `<title> - Menta Dance`. Uso:

```html
<html xmlns:th="http://www.thymeleaf.org"
      th:replace="~{fragments/layout :: page('Crear cuenta', ~{::main-content})}">
<body>
<th:block th:fragment="main-content">
    ...contenido de la página...
</th:block>
</body>
</html>
```

### Patrones de las vistas de catálogo y compra

- **Listados** (cursos, clases, planes, sesiones, lecciones): `<ul>` con un `<li class="card">`
  por elemento; los títulos siguen el orden `h1` → `h2` → `h3` sin saltos.
- **Estados** con `.badge-*` y siempre con texto: "Ya tenés lugar" (`badge-success`),
  lugares libres (`badge-neutral`), "Completo" (`badge-warning`), "Gratis" / "Restringida",
  "Destacado". El color nunca es la única señal.
- **Avisos**: errores con `alert('error', ...)` (`role="alert"`); información con
  `alert('info', ...)` (`role="status"`). `purchase-notice.html` usa `error` cuando el
  controlador marca el atributo de modelo `failure` (`PurchaseFlowErrors.apply`).
- **Video** (`lesson.html`): el `<video>` se conserva tal cual y solo se envuelve en
  `<div class="aspect-video ...">` para que sea responsive. No se toca la URL ni la CSP.
- **Archivos**: el `<input type="file">` lleva `<label for>` e `id` propios (no se anida en el label).
- Ninguna vista usa `<style>` ni `style=""`; el test `CatalogPurchaseLayoutIntegrationTest` lo verifica.

### Fragmentos (`fragments/components.html`)

| Fragmento | Parámetros | Notas |
|-----------|------------|-------|
| `field` | `id, label, type, name, autocomplete, value, hint, autofocus, errors` | `value`, `hint` y `errors` pueden ser `null`; `errors` es una lista (`${#fields.errors('email')}`). Enlaza hint/errores con `aria-describedby`. |
| `alert` | `kind, message` | `kind`: `error`, `success` o `info`. |
| `errorSummary` | `fields` | Resumen de errores (WCAG 3.3.1, 3.3.3). Se llama dentro del `<form th:object>`, después del `<h1>`. `fields` es un mapa ordenado `campo con error → id del input`, en el orden de los inputs: `${ {'email':'email', 'passwordConfirmed':'passwordConfirmation'} }`. No muestra nada sin errores. No es una región viva. Carga `/js/error-summary.js` (con hash de contenido), que mueve el foco al resumen. Con errores, el primer campo pasa `${!#fields.hasErrors('*')}` como `autofocus`. |
| `limitedTextareaField` | `id, label, name, value, rows, maxlength, hint, errors` | Como `textareaField`, con `maxlength` en el navegador para textos que el servidor también acota (#370). |
| `optionalSelectField` | `id, label, name, options, selected, placeholder, hint, errors` | Como `selectField`, pero el `<select>` no es `required`: la opción vacía (`placeholder`) es una respuesta válida (#367, "Yo (administrador)"). `selectField` y esta comparten `selectFieldBase(..., required)`. |
| `checkboxField` | `id, label, name, checked, hint, errors` | Una casilla con su `<label for>`; `checked` es un booleano, `hint` y `errors` pueden ser `null` y se enlazan con `aria-describedby` (#367, "Curso premium"). |
| `notice` | `heading, message` | Página informativa: `<h1>` más una `.card` con un párrafo ("revisá tu correo", "cuenta activada"). Los enlaces de salida van después, fuera del fragmento. |
| `alert` (ícono) | `kind, message` | Desde #849 dibuja un ícono por tipo (éxito, error, info) delante del mensaje; el `<p>` sigue siendo el primer hijo (el ícono se ordena con CSS) y los `role` no cambian. |
| `fragments/icons :: icon` | `name, cls` | Íconos SVG en línea (`arrow-left`, `arrow-up`, `arrow-down`, `plus`, `pencil`, `clock`, `modules`, `lessons`, `video`, `warning`, `check-circle`, `exclamation-circle`, `info-circle`). Siempre decorativos: `aria-hidden="true"` y `focusable="false"`; el texto de al lado da el significado. Nunca son el único contenido de un enlace o botón. |
| `fragments/admin :: backLink` | `href, label` | Vínculo "Volver" con flecha. |
| `fragments/admin :: dotBadge` | `kind, text` | Etiqueta de estado con punto (`kind`: `success`, `warning`, `danger`, `neutral`). |
| `fragments/admin :: iconBadge` | `kind, icon, text` | Igual con un ícono (por ejemplo la advertencia "Sin video"). |
| `fragments/admin :: lessonSummary` | `lessons` | "N lecciones · X min de duración" de un módulo. |

```html
<div th:replace="~{fragments/components :: field('email', 'Email:', 'email', 'email', 'username', *{email}, null, true, ${#fields.errors('email')})}"></div>
<div th:replace="~{fragments/components :: alert('error', ${formError})}"></div>
```

Los tests que recorren enlaces ignoran los anchors internos (`#main`) del skip link.

## Área de gestión (`/gestion`)

Base del área web para el personal (issue #354). Pantallas hoy: el inicio `GET /gestion` y el listado de
usuarios `GET /gestion/usuarios` con su alta `GET /gestion/usuarios/nuevo` y `POST /gestion/usuarios` y el cambio de
rol `POST /gestion/usuarios/{id}/rol` (#378), y la recepción `GET /gestion/recepcion` con el roster de cada sesión y
el check-in manual (#373), y los cursos presenciales `GET /gestion/cursos-presenciales` con sus sesiones y los alumnos de cada una (#368), y los pagos por transferencia `GET /gestion/pagos` las compras con excepción `GET /gestion/compras-con-excepcion` (#370) y el listado de cursos virtuales `GET /gestion/cursos-virtuales` (#367); el resto de las secciones se habilita a medida que llega cada pantalla. La autorización
real la decide la API; el BFF solo evita mostrar pantallas que un rol no puede usar.

### Prefijos y roles

Las reglas viven en `BffSecurityConfig`, de la más específica a la más general (gana la primera que coincide).
Sin sesión, el navegador va a `/login`; con sesión pero sin el rol, recibe un 403.

| Ruta | Roles con acceso |
|------|------------------|
| `GET /gestion` (inicio) | `ADMIN`, `INSTRUCTOR`, `RECEPTIONIST` |
| `/gestion/contenido/**`, `/gestion/sesiones/**` | `ADMIN`, `INSTRUCTOR` |
| `/gestion/cursos-presenciales/**` | `ADMIN`, `INSTRUCTOR` (#368; la API limita al instructor a sus cursos) |
| `/gestion/cursos-virtuales/**` | `ADMIN`, `INSTRUCTOR` (#367; la API limita al instructor a sus cursos) |
| `/gestion/recepcion/**` | `RECEPTIONIST`, `ADMIN` |
| `/gestion/billing/**`, `/gestion/dispositivos/**`, `/gestion/usuarios/**` | `ADMIN` |
| Cualquier otra ruta bajo `/gestion/**` | `ADMIN` (cierra por defecto: un prefijo no previsto no queda abierto al resto del personal) |

### Aterrizaje después del login

`RoleBasedAuthenticationSuccessHandler` aplica la decisión de `PostLoginLanding` (capa `application`):
`ADMIN`, `INSTRUCTOR` y `RECEPTIONIST` van a `/gestion`; `STUDENT` y cualquier otro rol, a `/dashboard`. Si
hay una solicitud guardada (hoy solo `GET /plans/suscribirme`, #361), esa tiene prioridad.

### Página 403

Spring Boot sirve `templates/error/403.html` para cualquier 403. Muestra "No tenés acceso a esta página", no
incluye detalles de la API ni del stack, y ofrece volver a `/gestion` (personal), a `/dashboard` (otro usuario
con sesión) o a `/` (sin sesión). El resto de los errores sigue usando `error.html`.

### Layout y navegación

`fragments/management.html :: page(title, active, content)` envuelve `fragments/layout :: page` (no duplica
header ni footer) y agrega `<nav aria-label="Gestión">` con "Inicio" y las secciones del rol. `active` es la
clave de la sección de la página (`home` para el inicio) y marca ese enlace con `aria-current="page"`. Desde #849 las pestañas son píldoras (`.admin-tabs`, `.admin-tab`; la activa, `.admin-tab-active`, en verde oscuro con texto claro).

```html
<html xmlns:th="http://www.thymeleaf.org"
      th:replace="~{fragments/management :: page('Contenido', 'content', ~{::main-content})}">
```

Las secciones salen del bean `managementNavigation` (`ManagementNavigationConfig`), que usa la clase
`ManagementNavigation` de la capa `application`: ofrece solo las secciones **habilitadas** cuyos roles
coinciden con los del usuario, en el orden declarado. Los roles se declaran sin el prefijo `ROLE_`.

### Cómo habilitar una sección nueva

1. Agregar el controller de la pantalla bajo su prefijo (por ejemplo `/gestion/contenido`) y su vista con
   `fragments/management :: page(..., '<clave>', ...)`. La clave es la que declara la sección en
   `ManagementNavigationConfig` (en inglés: `content`, `sessions`, `cursos-presenciales`, `cursos-virtuales`, `reception`, `payments`, `exception-purchases`, `devices`, `users`).
2. En `ManagementNavigationConfig`, pasar `enabled` de esa sección a `true` (así nunca hay un enlace sin pantalla).
3. Si el prefijo es nuevo, agregarlo en `BffSecurityConfig` antes de `/gestion/**` y sumar sus casos de rol
   rechazado a `ManagementAccessIntegrationTest`. La matriz de ese test mide "permitido" como 404 (nada atiende
   la ruta), así que el acceso permitido de una pantalla que ya existe se prueba en el test de la propia pantalla.

### Usuarios (`/gestion/usuarios`)

Primera pantalla real del área (#378): lista las cuentas con `GET /api/v1/admin/users`, con el token de acceso de
la sesión, y solo la ve `ADMIN`. Desde ahí el administrador da de alta cuentas de staff y cambia el rol de
cualquier cuenta (más abajo): con #378 completo, el `ADMIN` hace todo eso desde la web.

- **Filtros por GET** (se pueden enlazar y recargar): `role` (select con "Todos"), `q` (email, nombre o apellido) y
  `page` (desde 0). Un rol desconocido o vacío y un `q` en blanco equivalen a no filtrar; `q` se recorta a 100
  caracteres antes de llamar a la API; una `page` negativa es la primera y una pasada del final muestra la última.
  El tamaño de página es fijo (20) y no se expone. Una `page` que no es número responde 400.
- **Tabla accesible**: `<caption>` con el total, columnas Email, Nombre y apellido, Rol, Estado, Alta y Cambiar rol con
  `scope="col"`, rol y estado en español y fecha legible (`<time datetime>`). Una cuenta sin perfil muestra
  "Sin nombre".
- **Paginación**: `<nav aria-label="Paginación de usuarios">` con Anterior, "Página X de Y" (`aria-current="page"`) y
  Siguiente, solo si hay más de una página. Los enlaces conservan los filtros y Thymeleaf codifica sus parámetros;
  en los extremos el enlace que no aplica es un `<a role="link" aria-disabled="true">` sin `href`.
- **Estados**: sin coincidencias, "No hay usuarios que coincidan con la búsqueda."; si la API falla (401, 403, 429,
  5xx, cuerpo inválido, timeout) la página responde 503 con un aviso y conserva el formulario. Nunca se muestra el
  cuerpo del error de la API.
- **Privacidad**: la respuesta lleva `Cache-Control: no-store` (contiene emails y nombres), todo dato de usuario se
  escapa (`th:text`, nunca `th:utext`) y ni los emails, ni los nombres, ni el texto buscado, ni el token se
  registran en los logs.

#### Alta de cuentas de staff (`/gestion/usuarios/nuevo`)

El botón **Nueva cuenta** del listado abre el formulario; solo `ADMIN` (lo cubre `/gestion/usuarios/**`). Usa el
layout de gestión con `active='users'` y los fragmentos `errorSummary` y `field`, igual que el registro.

- **Campos**: Nombre, Apellido (hasta 100 caracteres), Email (válido, hasta 255) y Rol, un `<select>` con
  "Elegí un rol" más Profesor/a, Recepcionista y Administrador. `STUDENT` no se ofrece y, aunque llegue a mano, se
  rechaza antes de llamar a la API: los alumnos se registran solos. No hay contraseña: la cuenta nace pendiente, la
  API le manda al titular el correo de activación y él elige la suya (ver [Auth API](03-AUTH-API.md)).
- **Éxito**: `POST /gestion/usuarios` llama a `POST /api/v1/admin/users` y responde Post/Redirect/Get a
  `/gestion/usuarios`, sin datos en la URL. El listado muestra una sola vez, en una región `role="status"`, "Creamos
  la cuenta de <nombre>. Le enviamos un correo para que la active y elija su contraseña." (mensaje *flash* guardado en
  la sesión de Redis, escapado como todo texto).
- **Errores** (siempre se conserva lo tipeado; los de campo enfocan el resumen de errores al cargar):

| Caso | Status | Qué ve el administrador |
|---|---|---|
| Datos inválidos en el formulario | 200 | Resumen de errores y un mensaje por campo (`aria-invalid`, `aria-describedby`) |
| API `409` con `code` `EMAIL_ALREADY_REGISTERED` | 409 | Error en el campo Email: "Ya existe una cuenta con ese email." |
| API `400` | 400 | Aviso general: "Revisá los datos e intentá de nuevo." |
| Cualquier otra respuesta (401, 403, 404, 429, 5xx, otro `409`, cuerpo ilegible, timeout) | 503 | Aviso general: "No pudimos crear la cuenta. Probá de nuevo en unos minutos." |

- **Qué lee el BFF de la respuesta**: solo el status y, en un `409`, el campo `code` del problema. Ningún otro cuerpo
  de error se lee, se muestra ni se registra (el mapeo genérico RFC 9457 es el issue #711).
- **Privacidad**: el formulario y sus errores llevan `Cache-Control: no-store` (hay datos de una tercera persona);
  ni el email, ni los nombres, ni el token van a un log (solo el nombre de la clase de la excepción). CSRF activo.

#### Cambio de rol (`POST /gestion/usuarios/{id}/rol`)

Cada fila del listado lleva su propio formulario (columna **Cambiar rol**): un `<select>` con los cuatro roles
(Administrador, Profesor/a, Recepcionista y Alumno/a, con el rol actual elegido), un `<label class="sr-only">` "Rol de
<email>" que lo nombra para el lector de pantalla y el botón **Cambiar** (con el texto oculto " el rol de <email>",
para que varios botones no suenen iguales). Se muestra en todas las filas: cualquier cuenta puede pasar a cualquier
rol y elegir el que ya tiene es un no-op idempotente de la API, así que ningún botón sobra.

- **Campos**: el select se llama `role` (el rol nuevo) y los ocultos `filterRole`, `q` y `page` guardan el estado del
  listado (el filtro de rol no puede llamarse `role`: chocaría con el nuevo). El `id` de la cuenta va en el path.
- **Validación local** (antes de llamar a la API): el `id` debe ser un UUID (es un segmento del path de la API, que lo
  recibe como variable de URI codificada) y el rol uno de los cuatro. Si no, se responde como un `400`. Sin token de
  sesión se responde como una caída.
- **Redirección**: siempre Post/Redirect/Get al path fijo `/gestion/usuarios` con a lo sumo `role`, `q` y `page`,
  revalidados (rol conocido, `q` no vacío de hasta 100 caracteres, `page` entero no negativo; lo demás se descarta) y
  codificados estrictamente como variables de URI: lo que mande el cliente no puede agregar parámetros, un fragmento
  ni cambiar el destino. Nunca se redirige a una URL del cliente.
- **Resultado** (mensaje *flash* de una sola vez en el listado, escapado):

| Caso | Región | Qué ve el administrador |
|---|---|---|
| API `200` | `role="status"` | "Cambiamos el rol de <nombre o email> a <rol>. Se cerraron sus sesiones: tendrá que iniciar sesión de nuevo." |
| API `409` con `code` `LAST_ADMIN_CANNOT_BE_DEMOTED` | `role="alert"` | "No se puede quitar el rol de administrador al único administrador activo." |
| API `404` con `code` `USER_NOT_FOUND` | `role="alert"` | "No encontramos esa cuenta." |
| API `400`, o `id`/rol inválidos | `role="alert"` | "Revisá los datos e intentá de nuevo." |
| Cualquier otra respuesta (401, 403, 429, 5xx, otro `404` o `409`, otro status, `200` sin cuenta legible, timeout) | `role="alert"` | "No pudimos cambiar el rol. Probá de nuevo en unos minutos." |

  Si el rol elegido ya era el actual la API responde `200` sin tocar sesiones y el mensaje de éxito es el mismo (el
  BFF no lo distingue: no conoce el rol anterior de una cuenta ajena). Un `200` ilegible se informa como caída porque
  repetir el cambio es inocuo. De la respuesta solo se lee el `code` de un `404`/`409`; ningún otro cuerpo de error se
  lee, se muestra ni se registra (el mapeo genérico RFC 9457 es el issue #711).
- **El administrador cambia su propio rol**: la API revoca todas sus sesiones, incluida la que está usando (sube el
  `tokenVersion` y revoca los refresh tokens), de modo que su siguiente llamada fallaría. En lugar de dejarlo en una
  sesión rota, si el `id` coincide con el claim `sub` de su token de acceso y el rol pedido difiere del claim `role`,
  el BFF cierra la sesión web (la invalida, sin llamar a la API: ya está revocada) y redirige a
  `/login?roleChanged`, que muestra en una región `role="status"` "Cambiaste tu propio rol. Iniciá sesión de nuevo."
  Si la API lo rechaza (por ejemplo `409` por ser el último administrador) o pidió el rol que ya tiene, la sesión
  sigue y se ve el resultado normal en el listado. Se lee `sub` con el mismo lector de claims (sin verificar firma) que
  ya extrae `role` en el login: solo decide qué mostrar, la API autoriza cada llamada. Si el token no se puede leer, el
  cambio se trata como ajeno y la sesión revocada se resuelve por el refresco habitual (`/login?sessionExpired=true`).
- **Privacidad**: todas las respuestas llevan `Cache-Control: no-store`; ni el email, ni el nombre, ni el id, ni el token
  van a un log (solo el nombre de la clase de la excepción). CSRF activo; solo `ADMIN` (`/gestion/usuarios/**`).

### Recepción (`/gestion/recepcion`)

Pantalla de `RECEPTIONIST` y `ADMIN` (#373; `INSTRUCTOR` no entra: la API le deja leer sesiones y roster, pero el
check-in manual es solo de recepción y la sección es de ella). Tres rutas, todas bajo `/gestion/recepcion/**`:

| Ruta | Qué hace | API (con el token de la sesión) |
|---|---|---|
| `GET /gestion/recepcion` | Sesiones de hoy: curso, horario, cupo ocupado (`occupied` de `capacity`), estado | `GET /api/v1/admin/physical/sessions?from=&to=` |
| `GET /gestion/recepcion/sesiones/{sessionId}?q=` | Roster: alumnos con cupo confirmado y su asistencia, con búsqueda por nombre | `GET /api/v1/admin/physical/sessions/{sessionId}/roster` |
| `POST /gestion/recepcion/sesiones/{sessionId}/asistencias` | "Registrar asistencia" de un alumno (`studentId` en el formulario) | `POST /api/v1/physical/sessions/{sessionId}/check-ins`, variante `MANUAL` |

- **"Hoy" y las fechas**: el backend trabaja en UTC (ADR-0048), así que el BFF hace la conversión. "Hoy" es el día local
  en la zona de la academia (`menta.classes.display-zone`, `America/Argentina/Buenos_Aires` por defecto, la misma de
  "Mis clases") calculado desde un `Clock` inyectable (`ListTodaySessionsUseCaseImpl`). Pide el rango
  `[inicio del día, inicio del día siguiente)` como instantes UTC con `Z` (en Buenos Aires,
  `from=2026-11-13T03:00:00Z&to=2026-11-14T03:00:00Z`; el máximo de la API es 7 días y se usa 1). Una sesión de las
  00:30 locales cae en el día, una de las 23:30 también, y la de las 00:00 del día siguiente no. Los horarios se
  muestran en esa misma zona (`ClassLabels`).
- **Páginas de clases (`/classes`)**: el horario del curso (`dayOfWeek` + `startTime`, en UTC) se convierte junto, día
  y hora, a la zona de la academia (`ClassLabels.schedule`: miércoles 01:00 UTC se muestra "Martes 22:00"). Las
  lecturas de `GET /api/v1/physical/sessions/me` envían `from`/`to` en UTC con `Z`: la devolución de pago pide desde
  ayer hasta 31 días adelante, y el detalle del curso, el tramo de las sesiones listadas (tope 92 días).
- **Roster**: cada fila trae nombre, tipo de cupo (plan mensual o clase individual), y el estado como **texto**
  ("Presente" con la hora y cómo se registró, `QR` o `manual`; "Pendiente"), nunca solo color. El botón "Registrar
  asistencia" solo está en los pendientes de una sesión no cancelada y su nombre accesible incluye al alumno
  (" de <nombre>", texto oculto). La tabla lleva `<caption>` y `<th scope>`.
- **Búsqueda**: server-side sobre el roster ya recibido (`?q=`, un `<form role="search">` con su `<label>`):
  ignora mayúsculas y acentos, cada palabra tipeada debe estar en el nombre y se recorta a 100 caracteres. Los totales
  del encabezado no cambian con la búsqueda. La API no filtra: el roster de una sesión es chico (acotado por su cupo).
- **Validación local**: `sessionId` y `studentId` deben ser UUID (van en un path y en un body); si no, el GET responde 404
  y el POST vuelve a la lista de hoy con "Revisá los datos e intentá de nuevo.", sin llamar a la API. El BFF solo envía
  `studentId` y `sessionId`: el actor sale del token y la API valida rol, cupo y estado de la sesión.
- **Check-in** (Post/Redirect/Get al roster, con a lo sumo `q`, codificado estrictamente; mensaje *flash* de una vez,
  escapado; CSRF activo):

| Caso | Región | Qué ve la recepcionista |
|---|---|---|
| API `201` | `role="status"` | "Registramos la asistencia." |
| API `200` (reintento idempotente: no se duplica) | `role="status"` | "La asistencia ya estaba registrada (<QR o manual>, <día y hora>). No se duplicó." |
| `403` con `code` `CAPACITY_ASSIGNMENT_REQUIRED` | `role="alert"` | "El alumno no tiene un cupo confirmado en esta sesión, así que no se puede registrar su asistencia." |
| `403` con `code` `OUTSIDE_CHECK_IN_WINDOW` | `role="alert"` | "La sesión está fuera de la ventana de check-in: no se puede registrar la asistencia." |
| `409` con `code` `SESSION_NOT_ACTIVE` (sesión cancelada) | `role="alert"` | "La sesión está cancelada: no admite asistencias." |
| Cualquier otra respuesta (400, 401, otro `403` como `INSUFFICIENT_ROLE`, 404, otro `409` como `ALREADY_PROCESSING`, 429, 5xx, `2xx` sin asistencia legible, timeout) | `role="alert"` | "No pudimos registrar la asistencia. Probá de nuevo en unos minutos." |

  Nota de contrato: hoy la variante `MANUAL` **no** tiene ventana de tiempo (permite cargar asistencia retroactiva, ver
  `docs/05-PHYSICAL-API.md` y US-PHYSICAL-008), así que `OUTSIDE_CHECK_IN_WINDOW` solo lo devuelve la variante QR; el
  BFF lo traduce igual, por si la API la incorporara. Lo que sí devuelve MANUAL para una sesión que no admite
  asistencias es el `409 SESSION_NOT_ACTIVE` (cancelada). Un `{sessionId}` inexistente en MANUAL responde `403
  CAPACITY_ASSIGNMENT_REQUIRED` (no `404`), y se muestra como "sin cupo".
- **Estados**: una caída de la API muestra el aviso propio de la pantalla (`role="alert"`) con status 503; una sesión
  que la API no conoce (`404 SESSION_NOT_FOUND`) o un id mal formado, "No encontramos esa sesión." con status 404.
  Un día sin sesiones y una sesión sin alumnos o una búsqueda sin resultados se avisan en una región `role="status"`.
- **Qué lee el BFF de las respuestas**: el status y, en el `404` del roster y en el `403`/`409` del check-in, el campo
  `code`. Ningún otro cuerpo de error se lee, se muestra ni se registra.
- **Privacidad**: todas las respuestas llevan `Cache-Control: no-store` (hay nombres de alumnos); ni los nombres, ni los
  ids de alumnos o sesiones, ni el token van a un log (solo el nombre de la clase de la excepción o un texto fijo).
  Todo lo que se muestra pasa por `th:text` (escapado).

### Cursos presenciales (`/gestion/cursos-presenciales`) (#368)

Gestión web de cursos presenciales para `ADMIN` e `INSTRUCTOR` (la API limita al instructor a sus cursos). Hoy están
las pantallas de **cursos**, las de las **sesiones** de cada curso y la de los **alumnos** de una sesión (la entrada
`Sesiones` de la navegación sigue deshabilitada y es otra: esta es `cursos-presenciales`, "Cursos presenciales").

- **Rutas**: `GET /gestion/cursos-presenciales` (listado), `GET`/`POST .../nuevo` (alta) y `GET`/`POST
  .../{courseId}/editar` (edición). `STUDENT` y `RECEPTIONIST` reciben 403; un anónimo va al login; todo POST lleva
  CSRF (`th:action`). Un alta o una edición correctas son Post/Redirect/Get al listado con un aviso en una región
  `role="status"` ("Creamos el curso.", "Guardamos los cambios del curso."), sin nada en la URL. Todas las respuestas
  llevan `Cache-Control: no-store`.
- **Listado**: tabla con `<caption>` y `<th scope>`: título, profesor, horario (`scheduleLabel`), duración, nivel en
  español, cupo, estado como **texto** (Activo/Inactivo, además del color) y un enlace "Editar". Sin cursos, un aviso
  informativo; con la API caída, el aviso propio (`role="alert"`) con status 503, sin detalles de la API.
- **Formulario** (`CourseForm`, en `infrastructure/web/dto`): título, descripción, día de la semana, hora de inicio
  (`HH:mm`), duración en minutos (>= 1), nivel (Principiante/Intermedio/Avanzado), cupo (>= 1) y, al editar, estado. Las
  restricciones espejan las de la API y los mensajes están en español junto al campo, más el resumen de errores. Los
  números y la hora viajan como texto y se validan con un patrón, así un valor mal formado es un mensaje y no un fallo de
  binding. El día y la hora son los de la zona de la academia (`menta.classes.display-zone`); los casos de uso los
  convierten a UTC y **la edición siempre envía día y hora juntos**.
- **Profesor**:
  - `ADMIN`: un `select` con los profesores que lista `AdminUsersApiClient.listUsers` (rol `INSTRUCTOR`, **primera
    página de 50**, el máximo de la API; sin paginación: si hay más, un texto de ayuda dice que se muestran los primeros
    50). El `professorId` es obligatorio y se valida en el formulario (nunca se confía en el valor por defecto de la
    API); el nombre que se guarda es el de la lista (nombre y apellido, o el email si la cuenta no tiene perfil), jamás
    uno enviado por el navegador, y un id que no está en la lista se rechaza como si faltara.
  - `INSTRUCTOR`: sin selector; el curso es a su nombre. El id sale del `sub` del token (`AccessTokenClaimsReader`). El
    token no trae nombre, así que el puerto `InstructorDisplayNameSource` lo lee de la API de auth:
    `CurrentUserApiAdapter` llama a `GET /api/v1/users/me` con el token del llamante (#749) y arma el nombre con nombre
    y apellido (sin las partes vacías). Si la cuenta no tiene nombre o la API no responde un 200 legible (401, otro
    estado, caída, timeout, cuerpo inválido), el puerto responde vacío y el alta muestra un aviso (503) sin enviar nada;
    el log sólo registra el estado o la clase de la excepción, nunca el token ni el cuerpo. Editar y listar funcionan
    igual.
  - Al editar, el profesor se muestra de solo lectura (no se puede cambiar).
- **Errores de la API, por `code`** (el cuerpo nunca llega al navegador): `PROFESSOR_REQUIRED` y `PROFESSOR_MISMATCH`
  → error en el campo del profesor (400); `COURSE_HAS_ACTIVE_ASSIGNMENTS` → error en el campo estado, "No se puede
  desactivar: tiene alumnos asignados a sesiones futuras." (409); `INVALID_REQUEST` → alerta general del formulario
  (400, la API no dice qué campo); `COURSE_NOT_OWNED` → página de aviso "No podés gestionar este curso." (403);
  `COURSE_NOT_FOUND` → página de aviso "No encontramos ese curso." (404); cualquier otro código, caída o
  `ServiceUnavailableException` → alerta general sin detalles (503). Un id que no es UUID es un 404 sin llamar a la API.
  Para leer un curso al editar, `GetManagedCourse` lo busca entre los que la API lista al llamante (no hay lectura
  individual), así que un instructor nunca abre un curso ajeno.
- **Fragmentos nuevos** (`fragments/components`): `selectField` y `textareaField` (etiqueta, ayuda y error enlazados con
  `aria-describedby`, `aria-invalid`, `<label for>`) y `fieldHelp`, el bloque de ayuda/error que comparten. Los campos
  numéricos y de hora usan `field` con tipo `number` o `time`.
- **Seguridad**: todo lo que se muestra pasa por `th:text`; las URL de redirección son fijas y la de edición se arma
  con un UUID ya validado.

#### Sesiones de un curso (`/gestion/cursos-presenciales/{courseId}/sesiones`)

El curso va en cada URL; el listado de cursos tiene un enlace "Sesiones" por fila. `ManagementCourseSessionsController`
atiende el listado y las altas; `ManagementSessionChangesController`, la reprogramación y la cancelación, y
`ManagementSessionRosterController`, los alumnos de una sesión.

- **Rutas** (`ADMIN` e `INSTRUCTOR`; `STUDENT` y `RECEPTIONIST` reciben 403; un anónimo va al login; todo POST lleva CSRF
  y toda respuesta `Cache-Control: no-store`): `GET .../sesiones` (listado con los dos formularios de alta en la misma
  página), `POST .../sesiones` (una sesión), `POST .../sesiones/lote` (un rango), `GET`/`POST
  .../sesiones/{sessionId}/reprogramar`, `GET`/`POST .../sesiones/{sessionId}/cancelar` y `GET
  .../sesiones/{sessionId}?q=` (los alumnos de la sesión, solo lectura). Los POST correctos son
  Post/Redirect/Get al listado con un aviso en una región `role="status"` ("Creamos la sesión.", "Creamos 4 sesiones."
  con las fechas creadas, "Reprogramamos la sesión.", "Cancelamos la sesión."); la URL de redirección se arma con el
  `courseId` que devuelve la API.
- **Listado**: primero lo que ya existe, después los formularios (así quien crea ve las sesiones existentes). Contexto del
  curso (título, horario, profesor, cupo) y una tabla con `<caption>` y `<th scope>`: fecha y hora (`momentLabel`, con
  `<time datetime>` en UTC), "ocupados / cupo" (`3 / 10`, donde ocupados son las asignaciones confirmadas más los holds
  vigentes), disponibles, estado como **texto** (Programada/Cancelada, además del color) y las acciones "Ver alumnos"
  (en todas las filas, también en las canceladas), "Reprogramar" y "Cancelar" (estas dos no en las canceladas).
- **Ventana de fechas**: por defecto desde hoy (en la zona de la academia, con el `Clock` inyectable) hasta 90 días
  después; los parámetros opcionales `desde` y `hasta` (`yyyy-MM-dd`) la cambian desde un formulario `GET`. Solo `desde`:
  90 días desde esa fecha; solo `hasta`: desde hoy. Un valor ilegible, una ventana invertida o de más de 366 días da la
  ventana por defecto y un aviso ("No pudimos usar esas fechas. Mostramos los próximos 90 días."), nunca un error
  (`ResolveSessionWindowUseCase`). La ventana se convierte a un rango de instantes UTC. Las sesiones llegan ordenadas por
  inicio.
- **Una sesión** (`SessionForm`): fecha (`yyyy-MM-dd`), hora (`HH:mm`), cupo opcional (>= 1; vacío es el del curso) y notas
  opcionales (hasta 500, la columna de la API). La hora del curso viene precargada. La fecha y la hora son las de la
  academia y se convierten a UTC juntas: martes 22:00 en Buenos Aires se envía como `date` miércoles y `startTime`
  `01:00:00`.
- **Lote** (`BatchForm`): fechas `desde` y `hasta` locales, ambas incluidas; se crea una sesión por cada día del curso en
  el rango, todo o nada (la regla es de la API). Se valida localmente que ambas fechas existan, que el fin no sea anterior
  al inicio y que el rango no supere **366 días**: ese tope es solo de esta pantalla (la API no tiene ninguno) y evita
  pedidos desmesurados. Los mensajes de rango se muestran junto a la fecha final.
- **Conflictos**: el BFF no chequea duplicados; la API responde `409 SESSION_ALREADY_EXISTS` con `conflictingDates`
  (instantes UTC, cualquier estado, también canceladas) y `DescribeSessionConflictsUseCase` las escribe en la zona de la
  academia ("martes 17/11 22:00", en orden cronológico). En una sesión suelta, el error va junto a la fecha ("Ya existe
  una sesión de este curso el martes 17/11 22:00.", 409); en el lote, una alerta lista todas las fechas y dice "No se
  creó ninguna sesión." (409). Reprogramar a un instante que ya tiene sesión **no** lo detecta la API: queda fuera de
  alcance.
- **Reprogramar y cancelar** son páginas de confirmación (ni modal ni script). La API no tiene lectura individual de una
  sesión, así que `GetCourseSession` la busca entre las que lista el curso de la URL (en cualquier estado, con la ventana
  por defecto de ±10 años de la API): una sesión que no es de ese curso, o un id que no es UUID (404 sin llamar a la API),
  es una página de aviso con enlace de vuelta a las sesiones, y un instructor ajeno recibe el 403 de la API en esa misma
  lectura. Reprogramar pide fecha y hora nuevas, siempre juntas, y no toca cupo ni notas. Si la sesión ya tiene lugares
  tomados, una nota visible dice "Esta sesión ya tiene N alumnos asignados." sin afirmar nada sobre qué pasa con ellos
  (la API no lo dice; ocupados incluye los holds vigentes). Una sesión cancelada muestra un aviso en lugar del formulario.
- **Alumnos de una sesión** (`GET .../sesiones/{sessionId}`, plantilla `gestion/cursos/alumnos`): solo lectura, sin
  formularios que cambien algo ni botón de check-in (registrar asistencia es de la recepción, `/gestion/recepcion`, que
  sigue cerrada al `INSTRUCTOR`). Muestra el curso, el momento en la zona de la academia, el estado como **texto**
  (Programada/Cancelada, también si está cancelada, con sus alumnos), el cupo total, los alumnos con cupo y los que ya
  tienen asistencia, y una tabla con `<caption>` y `<th scope>`: alumno, tipo de cupo (plan mensual o clase
  individual), asistencia como **texto** (Presente/Pendiente, además del color) y registro (hora local `HH:mm` y
  origen, QR o manual, solo si hay asistencia). Sin alumnos: "Todavía no hay alumnos asignados a esta sesión."; la
  búsqueda por nombre `?q=` es la de la recepción (mismo caso de uso, `GetSessionRosterUseCase`, sin máquinas nuevas).
  - **Pertenencia**: el endpoint de roster de la API deja leer a un instructor el roster de cualquier curso, así que el
    BFF no lo llama a ciegas. `GetCourseSessionRosterUseCase` primero busca la sesión entre las del curso de la URL
    (`GetCourseSession`: un instructor ajeno recibe el 403 de la API en esa lectura y el roster ni se pide; una sesión
    que no es de ese curso, un 404) y recién después lee el roster de la sesión que la API listó; además exige que el
    roster diga que es del mismo curso (`SessionRoster.courseId`), si no es un 404. Un id que no es UUID es un 404 sin
    llamar a la API.
  - **Fallos**: sesión o curso inexistente → página de aviso 404 con enlace de vuelta a las sesiones; curso ajeno →
    "No podés gestionar este curso." (403); API caída o respuesta inutilizable → "No pudimos cargar los alumnos de la
    sesión en este momento. Intentá nuevamente en unos minutos." (503). Nunca se muestra texto de la API. Sin CSRF
    porque es solo `GET`; la respuesta lleva `Cache-Control: no-store` (hay nombres) y todo pasa por `th:text`.
- **Errores de la API, por `code`** (el cuerpo nunca llega al navegador): `SESSION_ALREADY_EXISTS` → ver arriba;
  `INVALID_REQUEST` → en el lote, error junto al rango ("No hay ninguna fecha del curso entre esas fechas.", 400), en el
  resto alerta general (400); `SESSION_ALREADY_OCCURRED` → junto a la fecha al reprogramar ("No se puede reprogramar una
  sesión que ya ocurrió.") o alerta en la confirmación al cancelar (409); `CAPACITY_BELOW_ASSIGNED` → alerta general
  (409; no debería ocurrir sin cambiar el cupo); `COURSE_NOT_OWNED` → "No podés gestionar este curso." (403);
  `COURSE_NOT_FOUND` / `SESSION_NOT_FOUND` → páginas de aviso (404); cualquier otro código, caída o
  `ServiceUnavailableException` → alerta general sin detalles (503). Un lote sobre un curso ajeno de un instructor es un
  404, no un 403: el caso de uso busca el curso entre los propios.

Base de la issue (ya mergeada antes de las pantallas):

- **Puerto y adaptador**: `PhysicalCourseManagementApiClient` / `PhysicalCourseManagementApiAdapter` consumen
  `/api/v1/admin/physical` con el token de la sesión (listar, crear y editar cursos; listar, crear, crear en lote y
  editar sesiones). El roster de una sesión sigue en `PhysicalReceptionApiClient`. Un rechazo esperado de la API
  (`400`, `403`, `404` o `409` con un `code` conocido) es una `CourseManagementException` con el `code`
  (`PROFESSOR_REQUIRED`, `PROFESSOR_MISMATCH`, `INVALID_REQUEST`, `COURSE_NOT_OWNED`, `COURSE_NOT_FOUND`,
  `SESSION_NOT_FOUND`, `COURSE_HAS_ACTIVE_ASSIGNMENTS`, `SESSION_ALREADY_EXISTS`, `CAPACITY_BELOW_ASSIGNED`,
  `SESSION_ALREADY_OCCURRED`) y, en `SESSION_ALREADY_EXISTS`, las `conflictingDates` (instantes UTC); todo lo demás
  (401, 429, 5xx, estado o `code` inesperado, cuerpo ilegible, timeout) es `ServiceUnavailableException`. Solo se lee
  el `code` y las `conflictingDates` de un cuerpo de error; el token solo viaja en `Authorization`.
- **Hora local a UTC** (ADR-0048): los formularios trabajan en la zona de la academia y la API en UTC, así que los casos
  de uso convierten **día y hora juntos** con `ClassLabels` (`toUtcSlot`/`toLocalSlot` para la recurrencia semanal:
  martes 22:00 en Buenos Aires es miércoles 01:00 UTC; `toUtc` para una sesión suelta; `toUtcDateRange` para el lote:
  cada extremo del rango local se convierte con la hora del curso, y la API genera una sesión por cada fecha UTC cuyo
  día UTC coincide con el del curso, que son las mismas sesiones que el usuario ve en su rango local). Usa una semana
  de referencia fija, nunca la fecha actual; una recurrencia fijada en UTC es exacta solo para zonas sin horario de
  verano, como Buenos Aires.
- **Casos de uso** (`ListManagedCourses`, `CreateManagedCourse`, `UpdateManagedCourse`, `ListCourseSessions`,
  `CreateCourseSession`, `BatchCreateCourseSessions`, `UpdateCourseSession`): finos, sin reglas de negocio ni
  pre-chequeo de conflictos; capacidad, fechas, duplicados y propiedad las decide la API. `CreateManagedCourse`
  envía siempre un `professorId` explícito (el administrador elige uno; el instructor manda el propio) y sin uno
  utilizable responde `PROFESSOR_REQUIRED` sin llamar a la API. Los ids que no son UUID nunca llegan a un path.

### Pagos por transferencia (`/gestion/pagos`) (#370)

Solo `ADMIN` (`/gestion/pagos/**`); verifica los pagos que esperan verificación manual con `AdminBillingApiClient`
y el token de la sesión. Todas las respuestas llevan `Cache-Control: no-store`.

- **`GET /gestion/pagos`**: tabla con `<caption>` (alumno, monto, fecha, medio, modalidad y "Ver"), paginación
  Anterior/Siguiente con `page` y `size` en la query (20 por defecto, 50 como máximo) y estado vacío. El alumno es el
  `userId` abreviado a 8 caracteres (la API no da nombre); la fecha va en la zona de la academia y el monto en es-AR.
  El formateo vive en los casos de uso (`ListPendingPayments`, `GetPaymentDetail` devuelven vistas ya redactadas).
  Si la API cae: aviso y `503`.
- **`GET /gestion/pagos/{paymentId}`**: datos del pago y, solo con comprobante, "Ver comprobante" (pestaña nueva, lo
  sirve el BFF en `/gestion/pagos/{id}/comprobante`: la URL firmada de la API nunca llega al navegador). Según el estado:
  `AWAITING_MANUAL_VERIFICATION` ofrece aprobar y rechazar (motivo obligatorio), `RECONCILIATION_REQUIRED` el formulario
  de corrección (decisión, motivo y evidencia) y cualquier otro es solo lectura. Un id inválido o inexistente es una
  página `404` con vuelta a la lista.
- **`POST .../aprobar`, `.../rechazar`, `.../corregir`**: Post/Redirect/Get con el resultado en un flash (éxito y error
  en atributos distintos). Aprobar y rechazar vuelven a la lista; corregir y los errores, al detalle. Motivo y evidencia
  son obligatorios y de hasta 500 caracteres: si no validan se muestra el detalle con los errores y no se llama a la API.
  Cada refusal se redacta por su `code` (`ILLEGAL_PAYMENT_STATE_TRANSITION` = "ya fue resuelto", por ejemplo ante una
  doble aprobación); el `detail` y el cuerpo de la API nunca se muestran. CSRF activo.

### Compras con excepción (`/gestion/compras-con-excepcion`) (#370)

Solo `ADMIN` (`/gestion/compras-con-excepcion/**`, sección `exception-purchases`); lista las compras presenciales que
quedaron en `EXCEPTION` (la capacidad se agotó tras el pago) y permite cerrarlas, con `AdminBillingApiClient` y el
token de la sesión. Un solo controller, `ManagementExceptionPurchasesController`. Todas las respuestas llevan
`Cache-Control: no-store`.

- **`GET /gestion/compras-con-excepcion`**: tabla con `<caption>` (alumno, modalidad, monto, fecha, sesiones afectadas y
  estado "En excepción"), paginación Anterior/Siguiente con `page` y `size` y estado vacío. El alumno es el `userId`
  abreviado a 8 caracteres; fecha en la zona de la academia, monto en es-AR. El formateo vive en
  `ListExceptionPurchasesUseCase`. Si la API cae: aviso y `503`, sin nada de su cuerpo.
- **Resolución en la lista**: la API no tiene detalle de una compra, así que cada fila lleva un
  `<details><summary>Resolver</summary>` con un formulario POST sin JavaScript: `resolution` ("Reembolsada" =
  `REFUNDED`, "Compensada" = `COMPENSATED`) y `note` obligatoria de hasta 500 caracteres. Los ids de los campos llevan el
  índice de la fila para que etiqueta, ayuda y error apunten al control correcto.
- **`POST /gestion/compras-con-excepcion/{purchaseId}/resolver`**: Post/Redirect/Get a la lista con el resultado en un
  flash (éxito y error en atributos distintos). Si el formulario no valida se vuelve a mostrar la lista con el error
  junto a los campos de esa fila (su `details` abierto) y no se llama a la API. Cada refusal se redacta por su `code`
  (`PURCHASE_ALREADY_RESOLVED`, `ILLEGAL_PURCHASE_STATE_TRANSITION`, `PURCHASE_NOT_FOUND`, `INVALID_REQUEST`); el `detail`,
  el cuerpo de la API y la nota nunca se muestran ni van a un log. CSRF activo.

### Cursos virtuales (`/gestion/cursos-virtuales`) (#367)

`ADMIN` e `INSTRUCTOR` (`/gestion/cursos-virtuales/**`, sección `cursos-virtuales`; la sección `content` de
`/gestion/contenido` sigue deshabilitada y es otra cosa). `ManagementVirtualCoursesController` lista y
`ManagementVirtualCourseFormController` atiende el alta y la edición, ambos con `VirtualCourseManagementApiClient` y el
token de la sesión. `ManagementVirtualModuleController` atiende la estructura y los módulos, `ManagementVirtualLessonController` las lecciones, `ManagementVirtualLessonActionsController` subir, bajar y borrar una lección (#831) y `ManagementVirtualCourseStatusController` publicar, despublicar y borrar un curso.

- **`GET /gestion/cursos-virtuales`**: tabla con `<caption>` (curso, categoría, nivel, estado y "Premium"/"Gratuito"),
  sin paginación (la API no pagina) y estado vacío "Todavía no hay cursos virtuales." El estado es texto
  ("Borrador", "Publicado", "Archivado"; "Sin dato" si el BFF no lo conoce), nunca solo color. El BFF muestra lo que la
  API devuelve (el administrador todos, el instructor los suyos) y no filtra.
- Si la API cae o rechaza el pedido: aviso, `503` y la lista sin filas, sin nada de su cuerpo. `Cache-Control: no-store`.
- **Alta y edición**: `GET /nuevo`, `POST /gestion/cursos-virtuales`, `GET`/`POST /{courseId}/editar`; todo POST lleva
  CSRF, toda respuesta `no-store`, y el éxito es Post/Redirect/Get a la lista con un aviso `success` ("Curso creado como
  borrador.", "Curso actualizado."). `VirtualCourseForm` valida en español junto al campo (la API no dice qué campo falla
  ni limita longitudes): título 255, descripción corta 500, URL de la imagen 500 (debe empezar con `http://` o
  `https://`), categoría 100, nivel uno de los tres y descripción hasta 5000 (tope del BFF; la columna es TEXT). Los
  textos se recortan con un `@InitBinder`. El curso se crea siempre como borrador; la casilla "Curso premium" solo
  existe al editar, y la edición envía todos los campos del formulario.
- **Profesor al crear**: el administrador ve un select con los instructores (`ListInstructorsUseCase`; avisa si hay más
  de 50 o si no se pudieron cargar, con `503`) cuya primera opción, "Yo (administrador)", no manda `professorId` y deja
  al admin como dueño; el instructor no ve el campo y el BFF nunca manda `professorId` (aunque lo posteen). Al editar
  no hay campo de profesor.
- **Errores de la API, por `code`** (nunca el `detail` ni el cuerpo): `INVALID_REQUEST` → "Revisá los datos
  ingresados." (400); `PROFESSOR_MISMATCH` → error general (400); `COURSE_NOT_OWNED` → página de aviso "No podés
  gestionar este curso." (403); `COURSE_NOT_FOUND` o un id que no es UUID → "No encontramos el curso." (404, sin llamar
  a la API si no es UUID); `COURSE_NOT_DRAFT`, `COURSE_NOT_PUBLISHABLE` y caídas → "No pudimos guardar el curso." (503).
  Al abrir la edición, si la API no responde, es un aviso `503`.

#### Estructura y módulos (#367)

- **`GET /gestion/cursos-virtuales/{courseId}/estructura`** (`estructura.html`, enlace "Estructura" en cada fila de la
  lista; rediseñada en #849 según `docs/design/admin/estructura-curso/`): vínculo "Volver", tarjeta de cabecera
  (título, estado como etiqueta con punto, estadísticas con íconos y "Agregar módulo" como acción principal), aviso con
  ícono y la sección "Módulos" en **dos columnas** desde `lg` (una columna en pantallas angostas). A la izquierda, los
  módulos en un `<ol>` ordenados por `order` como tarjetas (número, título enlazado, "Vista previa", "N lecciones · X min
  de duración", "Subir"/"Bajar", "Editar módulo") y la caja punteada "Crear nuevo módulo"; a la derecha, el detalle del
  **módulo seleccionado**: sus lecciones, también por `order`, como tarjetas (título enlazado a su edición, "Gratuita" /
  "Con suscripción", "Con video" / "Sin video" como advertencia con ícono y texto, duración, "Subir", "Bajar", "Editar",
  "Borrar"; el `videoId` no se muestra), "Agregar lección" y "Editar módulo". Estado vacío "Este curso todavía no tiene
  módulos." y, en un módulo sin lecciones, "Este módulo todavía no tiene lecciones."; sin publicar ni borrar el curso. Un
  id que no es UUID es un `404` sin llamar a la API.
- **Módulo seleccionado, sin JavaScript** (#849): parámetro opcional `?modulo={moduleId}`. Por defecto es el primero
  (por `order`); un id desconocido o vacío vuelve al primero, sin error. Cada tarjeta de módulo es un enlace a
  `?modulo=...`; la seleccionada lleva `aria-current="true"`, borde marcado y el texto "Seleccionado" (nunca solo color).
  El controller (`ManagementVirtualModuleController.structure`) calcula el módulo y lo pasa a la vista como
  `selectedModule`. Las acciones sobre un módulo o una lección vuelven a la estructura **con `?modulo=` del módulo
  trabajado** (`doneIn`): mover, borrar, crear o editar una lección, editar o mover un módulo; crear un módulo vuelve
  sin parámetro (el primero). Como el flash se entrega solo a la URL de destino, el aviso aparece en ese `?modulo=`.
  Las tarjetas muestran solo lo que hay: la lección no tiene más datos que título, duración, gratuita, video y orden.
- **Ancho de los formularios y confirmaciones de cursos virtuales** (#849): las seis plantillas de
  `gestion/cursos-virtuales/` (curso, módulo y lección, borrar lección, confirmar y problema) ya no se limitan con
  `max-w-xl`: ocupan todo el ancho del contenedor, alineadas con la barra de pestañas y con la estructura. Los campos
  cortos (por ejemplo la duración) también se estiran; si molesta, se les puede acotar el ancho campo por campo. Las demás
  pantallas de gestión (cursos presenciales, pagos, usuarios) conservan `max-w-xl`/`max-w-3xl` y se revisan aparte.
- **Qué no se verificó (#849)**: ver "Estructura del curso: qué no se verificó" en `docs/ACCESSIBILITY.md`; en breve, la
  corrida del job `accessibility` de CI, el recorrido manual con la API real, lector de pantalla y otros navegadores. Sí
  se miró la página renderizada en Chrome (HTML generado por las pruebas y el CSS compilado) a 1280, 1024 y 390 px,
  y axe-core sobre esos HTML no dio violaciones ni *incomplete*.
- **Alta y edición de módulo**: `GET /{courseId}/modulos/nuevo`, `POST /{courseId}/modulos`,
  `GET`/`POST /{courseId}/modulos/{moduleId}/editar`, con `ModuleForm` (título obligatorio de hasta 255 caracteres y la
  casilla "Vista previa gratuita"). No hay campo de orden: el módulo nuevo va al final (el BFF no manda `order`). La API
  no tiene lectura de un módulo, así que la edición se prefilla con el detalle del curso (`404` "No encontramos el
  módulo." si no está) y envía `title` y `preview`. El éxito es Post/Redirect/Get a la estructura con un aviso
  ("Módulo creado.", "Módulo actualizado.").
- **Subir y bajar** (`POST .../modulos/{moduleId}/subir` y `/bajar`, formularios sin JavaScript; el primero no tiene
  "Subir" y el último no tiene "Bajar"): el controller lee el detalle, intercambia el módulo con su vecino y manda a
  `ReorderVirtualModulesUseCase` la lista completa de ids. En el borde, o si el módulo ya no existe, no llama a la API y
  avisa. Si otra pestaña cambió el conjunto de módulos, la API responde `MODULE_REORDER_MISMATCH` y el aviso es "La
  estructura del curso cambió. Revisá el orden e intentá de nuevo."; un intercambio de dos módulos reordenados entre la
  lectura y la escritura no se detecta.
- **Errores, por `code`** (nunca el `detail`): `COURSE_NOT_OWNED` → `403`, `COURSE_NOT_FOUND` y `MODULE_NOT_FOUND` →
  `404`, `INVALID_REQUEST` en el formulario → "Revisá los datos ingresados." (400); el resto y las caídas, `503` con
  "No pudimos guardar el módulo.", "No pudimos mover el módulo." o "No pudimos cargar el curso...".

#### Lecciones (#367)

- **Rutas** (`ManagementVirtualLessonController`): `GET /{courseId}/modulos/{moduleId}/lecciones/nueva`, `POST
  .../lecciones` y `GET`/`POST .../lecciones/{lessonId}/editar`. Éxito: Post/Redirect/Get a la estructura con el aviso
  "Lección creada." o "Lección actualizada.". Los errores son los del módulo (`COURSE_NOT_OWNED` `403`, `*_NOT_FOUND`
  `404`, `INVALID_REQUEST` "Revisá los datos ingresados.", caídas `503` "No pudimos guardar la lección.") más
  `LESSON_NOT_FOUND` → "No encontramos la lección."; el controller reutiliza los helpers del de módulos.
- **`LessonForm`**: título (obligatorio, 255), descripción (obligatoria, 5000: la columna es `TEXT` y la API no la limita,
  el tope es el del formulario de curso), identificador de video (opcional, 128, sin espacios; es el id opaco de un video ya
  existente, la subida no entra acá), duración en minutos (texto como los demás formularios numéricos: entero de 0 a
  9999, vacío es 0, un solo mensaje) y la casilla "Lección gratuita". No hay campo de orden: la lección nueva va al final.
- **Video**: la API no permite quitar un video asignado (un `videoId` en blanco responde `400 INVALID_REQUEST`, #845),
  así que en la edición un identificador vacío significa "no cambiar" y no se envía; en el alta vacío es "sin video". La edición se prefilla con el detalle del curso (la API no
  tiene lectura de una lección): `404` "No encontramos el módulo." o "No encontramos la lección." si no están.
- **Acciones sobre cada lección en la estructura** (#831, `ManagementVirtualLessonActionsController`): el título de la
  lección es un enlace a `.../lecciones/{lessonId}/editar` (se conserva "Editar"), y cada lección tiene "Borrar" (enlace a
  la confirmación) y los botones "Subir" y "Bajar" (formularios `POST` con CSRF, sin JavaScript; la primera lección del
  módulo no tiene "Subir" y la última no tiene "Bajar", y no se renderizan deshabilitados). Todos llevan el título de la
  lección en un texto `sr-only`; el enlace del título dice además "lección del módulo {módulo}".
- **Subir y bajar** (`POST .../lecciones/{lessonId}/subir` y `/bajar`): igual que con los módulos, el controller lee el
  detalle, ordena las lecciones por `order` y toma la vecina. A diferencia del módulo, la API no tiene un reorden de
  lecciones: `PUT /api/v1/admin/virtual/lessons/{id}` cambia una por vez, así que el intercambio son **dos `PUT` con solo
  `order`** (a cada lección se le manda el `order` de la otra; no se renumera ni se asume 0..n-1) y no es atómico.
  `SwapVirtualLessonsUseCase` hace el primero y, si el segundo falla, intenta devolver la primera lección a su `order`
  (best effort; si eso también falla se informa el fallo original) para no dejar dos lecciones con el mismo `order`. Éxito:
  Post/Redirect/Get a la estructura con "Lección movida." (se muestra una sola vez). En el borde, o si la lección ya no
  está, no llama a la API y avisa ("La lección ya está en esa posición." / "Esa lección ya no existe. Revisá la estructura
  del curso."). Errores: `COURSE_NOT_OWNED` `403`, `*_NOT_FOUND` `404`, el resto y las caídas `503` "No pudimos mover la
  lección. Intentá de nuevo." Una lectura y dos escrituras no son una transacción: otra pestaña que reordene en medio
  puede dejar un orden inesperado (no se detecta, como el intercambio de módulos).
- **Borrar** (`GET`/`POST .../lecciones/{lessonId}/borrar`, plantilla `leccion-borrar.html`, propia porque `confirmar.html`
  es de cursos): el `GET` busca la lección en el detalle del curso (`GetManagedVirtualLessonUseCase`; un id que no es UUID
  es `404` sin llamar a la API) y muestra su título y el del módulo (siempre con `th:text`), y el efecto: "Se borra también
  el progreso de los alumnos en esta lección. Esta acción no se puede deshacer."; el botón es `.btn-danger` y dice "Sí,
  borrar la lección". El `POST` llama a `DELETE /api/v1/admin/virtual/lessons/{lessonId}` (`204`; la API renumera las
  que quedan) y vuelve a la estructura con "Lección borrada.". Errores, los de siempre y nunca el `detail`:
  `COURSE_NOT_OWNED` `403`, `COURSE_NOT_FOUND`/`MODULE_NOT_FOUND`/`LESSON_NOT_FOUND` `404`, el resto y las caídas `503`
  "No pudimos completar la acción. Intentá de nuevo." sobre la confirmación (o como aviso si no se puede leer la lección).
  Todo `no-store` y con CSRF. RECEPTIONIST y STUDENT reciben `403` sin llamar a la API y un anónimo va a `/login`.
- **Qué no se verificó (#831)**: solo hay pruebas de integración (MockMvc y WireMock). No se probó contra una API real
  (en particular el comportamiento del `PUT` con `order` repetido o la renumeración del `DELETE` con datos reales), ni en
  un navegador, ni con axe, ni con un lector de pantalla.

#### Publicar, despublicar y borrar (#367)

- **Rutas** (`ManagementVirtualCourseStatusController`): `GET`/`POST /gestion/cursos-virtuales/{courseId}/publicar`,
  `/despublicar` y `/borrar`. El `GET` es una página de confirmación (como "Cancelar sesión" de los cursos presenciales)
  que lee el curso (`GetManagedVirtualCourseUseCase`) y muestra su título (siempre escapado), su estado y el efecto de
  la acción; las tres comparten `confirmar.html`, parametrizada (título, pregunta, efecto, botón, ruta del `POST` y
  estilo destructivo para borrar, con la aclaración "Solo se pueden borrar cursos en borrador."). Cada fila de la lista
  ofrece "Publicar" y "Borrar" (solo borrador) o "Despublicar" (solo publicado) según las banderas de la vista; un
  curso archivado no tiene acciones. Cada enlace lleva el título del curso en un texto `sr-only`.
- **El BFF no repite las reglas de la API**: la página se muestra para cualquier estado, con una nota informativa si no
  es el esperado ("Este curso ya está publicado."), y quien decide al confirmar es la API.
- **Éxito**: Post/Redirect/Get a la lista con el aviso "Curso publicado.", "Curso despublicado." o "Curso borrado.".
- **Errores, por `code`**: `COURSE_NOT_PUBLISHABLE` vuelve a mostrar la confirmación (`400`) con el motivo que dio la
  API, en español y escapado (es el único `detail` que llega al navegador; si no vino, "El curso todavía no cumple las
  condiciones para publicarse.") y el enlace "Revisar la estructura"; `COURSE_NOT_DRAFT` (`409`) dice "Solo se puede
  borrar un curso en borrador. Despublicalo primero." (o "...publicar...") en la misma página; `COURSE_HAS_PROGRESS`
  (`409`, #834) solo al borrar: "No se puede borrar el curso porque ya tiene progreso de alumnos." en la misma página
  (sin el `detail` ni el código; al publicar o despublicar no significa nada y es el aviso genérico); `COURSE_NOT_OWNED` →
  `403`, `COURSE_NOT_FOUND` o un id que no es UUID → `404` (sin llamar a la API); `INVALID_REQUEST`, caídas y demás
  códigos → `503` "No pudimos completar la acción. Intentá de nuevo." sobre la confirmación (o como aviso si el curso
  tampoco se puede leer). Al abrir la página, un fallo de lectura es un aviso `503`. Todo `no-store` y con CSRF.
- **Estado del issue**: con esto la gestión de cursos virtuales (listado, alta y edición, estructura, módulos,
  lecciones, publicar, despublicar y borrar) está completa. El recorrido manual del issue (un profesor crea un curso
  con un módulo y dos lecciones, lo publica y lo ve en el catálogo público) **no se pudo verificar** en este
  desarrollo: solo hay pruebas de integración contra una API simulada.

## Accesibilidad base

`input.css` define `:focus-visible` global (outline `brand-action`, 2px) y reduce
animaciones y transiciones bajo `prefers-reduced-motion: reduce`.

## Content scan

Solo `src/main/resources/templates/**/*.html` y `src/main/frontend/js/**/*.js`.
Una clase armada dinámicamente en Java o con concatenación en un template **no** se
detecta; si hiciera falta, se agrega a `safelist` con justificación.

## CI

El job `quick-build-and-test` de `pr-develop.yml` ejecuta `./gradlew :bff:tailwindBuild`
(`npm ci` + Tailwind con Node 20.11.1 descargado por Gradle) antes del build completo.
El `build` de `pr-main.yml` y la imagen `Dockerfile.bff` pasan por `processResources`,
por lo que también compilan el CSS.
