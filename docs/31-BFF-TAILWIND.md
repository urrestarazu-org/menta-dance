# 31 - Pipeline de Tailwind CSS del BFF

Línea base: [ADR-0024](adr/0024-technology-baseline.md) (Tailwind CSS 3.4.1,
Node.js 20.11.1, PostCSS 8.4.35, Autoprefixer 10.4.17). Issue origen: #536.

> Estado: **slice 2**. Existen el pipeline, los tokens, los componentes
> (`@layer components`), el layout común y `login.html` / `register.html` como
> adoptantes. La migración del resto de las vistas llega en el slice siguiente.

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
| `.field`, `.field-label`, `.field-input`, `.field-error` | Campo de formulario. `aria-invalid="true"` en el input activa el borde de error. |
| `.card` | Contenedor con borde, esquinas `rounded-xl` y sombra `shadow-lg`. |
| `.badge` + `.badge-success` / `-warning` / `-danger` / `-neutral` | Etiqueta de estado; el texto siempre lleva el significado, no solo el color. |
| `.container-page` | Ancho máximo y gutter horizontal de la página. |
| `.alert` + `.alert-error` / `-success` / `-info` | Mensaje en bloque; `role="alert"` para errores, `role="status"` para el resto. |

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

### Fragmentos (`fragments/components.html`)

| Fragmento | Parámetros | Notas |
|-----------|------------|-------|
| `field` | `id, label, type, name, autocomplete, value, hint, autofocus, errors` | `value`, `hint` y `errors` pueden ser `null`; `errors` es una lista (`${#fields.errors('email')}`). Enlaza hint/errores con `aria-describedby`. |
| `alert` | `kind, message` | `kind`: `error`, `success` o `info`. |
| `notice` | `heading, message` | Página informativa: `<h1>` más una `.card` con un párrafo ("revisá tu correo", "cuenta activada"). Los enlaces de salida van después, fuera del fragmento. |

```html
<div th:replace="~{fragments/components :: field('email', 'Email:', 'email', 'email', 'username', *{email}, null, true, ${#fields.errors('email')})}"></div>
<div th:replace="~{fragments/components :: alert('error', ${formError})}"></div>
```

Los tests que recorren enlaces ignoran los anchors internos (`#main`) del skip link.

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
