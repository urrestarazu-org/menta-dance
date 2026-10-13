# 31 - Pipeline de Tailwind CSS del BFF

Línea base: [ADR-0024](adr/0024-technology-baseline.md) (Tailwind CSS 3.4.1,
Node.js 20.11.1, PostCSS 8.4.35, Autoprefixer 10.4.17). Issue origen: #536.

> Estado: **slice 1**. Existe el pipeline, los tokens base y `login.html` como
> primer adoptante. Los componentes (`@layer components`) y la migración del
> resto de las vistas llegan en los slices siguientes.

## Estructura

```
bff/
├── package.json              # versiones exactas, engines.node = 20.11.1
├── package-lock.json         # fuente de verdad para `npm ci`
├── tailwind.config.js        # content scan + tema (tokens)
├── postcss.config.js         # tailwindcss + autoprefixer
└── src/main/
    ├── frontend/css/input.css        # CSS fuente (NO es un recurso servido)
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
