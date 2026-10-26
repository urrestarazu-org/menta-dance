# Accesibilidad del BFF web

Auditoría de accesibilidad de las vistas Thymeleaf del BFF (issue #529). Slice 1:
auditoría y correcciones; slice 2: comprobaciones automáticas en CI; slice 3:
re-auditoría tras el nuevo estilo visual, las fotos, el registro con datos
personales y las páginas legales (#649 a #654). El objetivo de referencia es
**WCAG 2.1 nivel AA**.

> Este documento registra lo que se midió y lo que se revisó. **No** declara
> conformidad con WCAG ni cumplimiento legal: una herramienta automática detecta
> solo una parte de los problemas posibles y los puntos pendientes figuran abajo.

## Alcance

- **Incluido**: vistas del BFF (`bff/src/main/resources/templates`), el CSS
  compilado con Tailwind (`bff/src/main/frontend/css/input.css`), los fragmentos
  comunes (`fragments/layout.html`, `fragments/components.html`,
  `fragments/site.html`, `fragments/legal.html`), las páginas legales
  (`templates/legal/`) y los recursos estáticos que muestran las vistas (fotos,
  logo y video de marca en `static/images/`).
- **Fuera de alcance**: app Android, contenido de los videos (subtítulos, ver
  más abajo), correos transaccionales y la página de pago de Mercado Pago.
- **Automatización**: las comprobaciones de regresión con Playwright + axe-core
  corren en CI (ver "Comprobaciones automáticas en CI"). Detectan regresiones
  detectables por herramienta; no reemplazan la revisión manual.

## Método

| Técnica | Detalle |
|---|---|
| Automática | axe-core 4.14.0 sobre Chromium (Playwright), tags `wcag2a`, `wcag2aa`, `wcag21a`, `wcag21aa`. Se registran *violations* e *incomplete* (revisión manual requerida). |
| Contraste | Cálculo de la razón de luminancia relativa (fórmula WCAG) para cada par texto/fondo de los tokens, los bordes de componentes y el indicador de foco. En el slice 3 se midió con un script descartable y, además, el cálculo quedó como prueba de build (`StylesheetContrastTest`) sobre el CSS compilado. El texto sobre la foto del hero se midió sobre los píxeles reales de la imagen. Axe `color-contrast` se usó en la auditoría inicial. |
| Foco | Recorrido con Tab en `/login` y `/`, leyendo el estilo computado (`outline`, `box-shadow`, `border-color`) de cada control. |
| Código | Revisión de plantillas: jerarquía de encabezados, `main` único, landmarks, errores de formulario, regiones vivas, nombres accesibles, `<video>`, `<img>` y `<svg>`, `autocomplete` de los campos (1.3.5). |

La auditoría inicial usó herramientas instaladas fuera del repositorio. Las
comprobaciones de regresión viven en `bff/e2e-a11y/` (paquete npm propio; su
lockfile fija axe-core 4.13.0, mientras que la auditoría inicial registró 4.14.0).

### Páginas auditadas

Anónimas: `/`, `/classes`, detalle de clase, `/plans`, `/login` (y con error),
`/registro` (y con errores de validación), `/recuperar-contrasena`,
`/recuperar-contrasena/enviado`, `/restablecer-contrasena/{token}` (token
inválido), `/reenviar-activacion`, `/registro/pendiente`, `/cuenta-activada`,
`/activar/{token}` (token inválido), 404 (`/classes/not-a-uuid`), `/error`,
detalle de curso.
Slice 3 (revisión de código, medición de contraste y pruebas de integración,
**sin** nueva corrida de axe): `/registro` con los campos nuevos (nombre,
apellido, teléfono, fecha de nacimiento) y las siete páginas legales
(`/terminos-condiciones`, `/politica-privacidad`, `/politica-cookies`,
`/politica-devolucion`, `/propiedad-intelectual`, `/accesibilidad`, `/contacto`),
además de la cabecera, el pie, el hero y la página de error con las fotos y el
video de marca.
Con sesión de alumno: `/dashboard`, detalle de clase, detalle de curso, dos
lecciones, cotización (`/classes/{id}/cotizar`) y datos de transferencia
(`/compras/transferencia`).

### Resultado de axe

Corresponde a la auditoría de los slices 1 y 2, sobre las 25 vistas **anteriores**
a #649-#654. En el slice 3 no se levantó el stack ni se volvió a correr axe: los
recorridos nuevos de Playwright están escritos (ver "Comprobaciones automáticas
en CI") y su primer resultado válido es el de CI.

| Estado | Páginas | Violations | Incomplete |
|---|---|---|---|
| Antes de las correcciones | 25 | 0 | 1 (`video-caption`, lección reproducible) |
| Después | 25 | 0 | 1 (`video-caption`, ver "Subtítulos") |

Cero violaciones automáticas **no** implica conformidad: axe no evalúa, por
ejemplo, la calidad de los textos alternativos, el orden lógico real ni el uso
con lector de pantalla. Por eso se hizo además la revisión manual y de código.

## Contraste medido

Medido en el slice 3 sobre la paleta vigente (#653), con la fórmula de luminancia
relativa de WCAG 2.1. Mínimos: 4,5:1 para texto, 3:1 para texto grande y
componentes no textuales (bordes de controles, indicador de foco, íconos).
`StylesheetContrastTest` repite estos cálculos sobre el CSS compilado.

Tokens: `brand-deep #043F2E`, `brand-action #2A6F2B`, `brand-bright #78C51C`,
`brand-light #C8F169`, `brand-surface #EEF2E3`; feedback: error `#9B1C1C` sobre
`#FEF2F2`, éxito `#043F2E` sobre `#E2F5BE`, advertencia `#78350F` sobre `#FEF3C7`.

### Texto (mínimo 4,5:1)

| Par | Razón |
|---|---|
| Texto `#043F2E` sobre `#EEF2E3` (página) / blanco (card) / `#F9FAFB` (campo de cuenta) | 10,51 / 11,96 / 11,45 |
| Link `#2A6F2B` sobre blanco / `#EEF2E3` | 6,16 / 5,41 |
| Link sobre superficies de éxito / error / advertencia | 5,29 / 5,63 / 5,54 |
| `.btn-primary`: blanco sobre `#2A6F2B`; en hover, blanco sobre `#043F2E` | 6,16; 11,96 |
| `.btn-secondary`: `#043F2E` sobre blanco; en hover, sobre `#EEF2E3` | 11,96; 10,51 |
| `.btn-light`: `#043F2E` sobre `#C8F169`; en hover, sobre `#78C51C` | 9,25; 5,58 |
| `.btn-outline-light`: `#C8F169` sobre `#043F2E`; en hover (relleno sólido ahora), `#043F2E` sobre `#C8F169` | 9,25; 9,25 |
| Cabecera y menú: `#EEF2E3` sobre `#043F2E`; hover `#C8F169` | 10,51; 9,25 |
| Pie: texto (`#EEF2E3` al 90 %) / links / títulos `#C8F169`, sobre `#043F2E` | 8,81 / 10,51 / 9,25 |
| Panel de marca de las pantallas de cuenta: blanco sobre `#043F2E` | 11,96 |
| Error sobre `#FEF2F2` (alert, badge) / blanco (campo en card) / `#EEF2E3` | 7,45 / 8,15 / 7,16 |
| Éxito `#043F2E` sobre `#E2F5BE` | 10,27 |
| Advertencia `#78350F` sobre `#FEF3C7` | 8,15 |
| Skip link: `#2A6F2B` sobre blanco | 6,16 |
| Placeholder del plugin de formularios `#6B7280` sobre blanco / `#F9FAFB` | 4,83 / 4,63 |

Notas: ninguna plantilla usa `placeholder` hoy (el valor base de Tailwind,
`#9CA3AF`, mide 2,54:1 sobre blanco y el plugin de formularios lo reemplaza; si
se agrega un placeholder, no debe reemplazar a la etiqueta). Ninguna plantilla
tiene controles `disabled`; los componentes inactivos están exentos de 1.4.3
(para referencia, `.btn-primary` deshabilitado, al 50 % de opacidad, mide 2,22:1).

### Texto sobre la foto del hero

El texto va sobre `salon.jpg` / `salon-960.jpg` con un degradé de `#043F2E`.
Medido pixel a pixel sobre la foto real (caja de 1280x440 px en escritorio y de
390x620 px en móvil; "zona de texto" = tramo central de la caja):

| Degradé | Texto `#EEF2E3`, mínimo (zona de texto) | `#C8F169` (botón de contorno, link), mínimo (zona de texto) |
|---|---|---|
| Antes: 55 % a 85 % | escritorio 2,88 (3,61); móvil 3,49 (4,53) | escritorio 2,54 (3,17); móvil 3,07 (3,99) |
| Ahora: 78 % a 90 % | escritorio 5,49 (6,07); móvil 5,98 (6,76) | escritorio 4,83 (5,34); móvil 5,27 (5,95) |

Caso límite (foto blanca pura bajo el degradé más claro): antes 2,86 / 2,51;
ahora 5,47 / 4,82. El texto grande del `h1` solo exige 3:1; el resto (20 px,
botones de 18 px) exige 4,5:1.

### No textual (mínimo 3:1)

| Par | Razón |
|---|---|
| Foco `#2A6F2B` sobre blanco / `#EEF2E3` / `#F9FAFB` | 6,16 / 5,41 / 5,90 |
| Foco sobre superficies de éxito / error / advertencia | 5,29 / 5,63 / 5,54 |
| Foco sobre `#043F2E` (cabecera, pie, hero, panel de cuenta, skip link): antes `#2A6F2B` | 1,94 (no cumplía) |
| Foco sobre esas mismas superficies, ahora `#C8F169` (skip link: blanco) | 9,25 (11,96) |
| Foco sobre `#043F2E` del reproductor de lecciones (no modificado) | 1,94 (no cumple, hallazgo 8) |
| Borde de campo (`.field-input`) `#043F2E` sobre blanco | 11,96 |
| Borde de campo de las pantallas de cuenta sobre la card blanca: antes `#E5E7EB` | 1,24 (no cumplía; el relleno `#F9FAFB` mide 1,05) |
| Ídem, ahora `brand-deep` al 60 % (contra el blanco / contra el relleno) | 3,71 / 3,55 |
| Borde de campo inválido `#9B1C1C` sobre blanco / `#F9FAFB` | 8,15 / 7,80 |
| Ícono de beneficios `#2A6F2B` sobre blanco | 6,16 |
| Borde de `alert-success` / `alert-error` sobre su superficie | 5,29 / 7,45 |
| Borde de `alert-info`: antes `brand-deep` al 40 % sobre blanco; ahora al 60 % | 2,24; 3,71 |
| Link `#2A6F2B` frente al texto que lo rodea `#043F2E` (sin subrayado no alcanza 3:1, 1.4.1) | 1,94 |
| Borde de `card` (20 %) y anillo de `badge-neutral` (30 %), decorativos | 1,45 / 1,76 (no exigido: el contenido se identifica por texto y sombra) |

Valores de la paleta: no se modificó ningún token. Los ajustes del slice 3 son
de CSS (degradé del hero, color del foco sobre fondos oscuros, borde de campos y
de `alert-info`, subrayado de links, hover del botón de contorno).

## Hallazgos

Prioridad por impacto en el usuario: **Alta** (bloquea o confunde con
tecnología asistiva), **Media**, **Baja**.

| # | Hallazgo | Criterio WCAG | Prioridad | Estado |
|---|---|---|---|---|
| 1 | Inputs de formulario: `@tailwindcss/forms` reemplazaba el outline por un anillo azul de 1 px (`#2563EB`), fuera de la paleta y más débil que el resto del sitio. Ahora usan el mismo outline de 2 px y borde `brand-action`. | 2.4.7 Foco visible | Media | Resuelto |
| 2 | `<video>` sin nombre accesible ni texto alternativo para navegadores sin soporte. Ahora tiene `aria-label` ("Video de la lección: ...") y texto de respaldo; `preload="metadata"`. | 4.1.2, 1.1.1 | Media | Resuelto |
| 3 | Varios `<nav>` sin nombre (migas de pan en 5 vistas y accesos en `/`). Ahora todos llevan `aria-label`. | 1.3.1, 2.4.1 | Media | Resuelto |
| 4 | Destino del skip link (`<main id="main">`) no enfocable: en algunos navegadores el foco no se movía. Ahora `tabindex="-1"` (sin outline, no es un control). | 2.4.1 Saltar bloques | Media | Resuelto |
| 5 | Página de error en inglés mezclado con español ("Status:", "Error: Not Found", "Go to Home") dentro de `lang="es"`. Ahora en español y sin exponer el mensaje crudo. | 3.1.2 Idioma de las partes, 3.3.x | Baja | Resuelto |
| 6 | Errores de campo vinculados con `aria-invalid` y `aria-describedby` (ya implementado en `components :: field`); se agregó prueba de regresión. | 3.3.1, 1.3.1, 4.1.2 | Alta | Verificado (sin cambios) |
| 7 | Sin subtítulos (`<track>`) en el reproductor. Axe lo marca como *incomplete* (`video-caption`). | 1.2.2 Subtítulos (grabado) | Alta | Conocido, fuera de este slice (ver abajo) |
| 8 | Foco sobre el fondo oscuro del reproductor mide 1,94:1; además el contenedor tiene `overflow-hidden`, que puede recortar el outline. Los controles nativos del navegador dibujan su propio foco. | 1.4.11 Contraste no textual, 2.4.7 | Baja | Conocido |
| 9 | Borde de `alert-info` (2,24:1). El mensaje se entiende por el texto (contraste 11,96:1), el borde no era esencial. En el slice 3 se subió a `brand-deep` al 60 % (3,71:1 contra el blanco). | 1.4.11 | Baja | Resuelto (slice 3) |
| 10 | Texto de 12 px (`text-small`) del tamaño tipográfico de referencia; verificar zoom 200 % y *reflow* 320 px de forma manual. En el slice 3 ninguna plantilla usa `text-small` (el texto más chico es `text-sm`, 14 px), pero el zoom y el *reflow* con el nuevo layout (cabecera con menú móvil, hero, tarjeta de dos paneles) no se probaron. | 1.4.4, 1.4.10 | Media | Pendiente de prueba manual |
| 11 | Modo oscuro (`darkMode`) no habilitado: no hay paleta oscura que medir. Si se habilita, hay que medir contraste en oscuro. | 1.4.3 | Baja | Fuera de alcance |
| 12 | Fondo animado de login/registro de la app de referencia no portado: las pantallas de cuenta usan un fondo verde sólido (`auth-shell`) sin animación. Si se porta una animación, respetar `prefers-reduced-motion` (ya hay una regla global) y medir contraste sobre ella. El degradé del hero sí se midió (hallazgo 15). | 2.3.3, 1.4.3 | Baja | Fuera de alcance |
| 13 | Links subrayados siempre (no se distinguen solo por color). Se rompió con #651 en las pantallas de cuenta (hallazgo 18) y se restituyó en el slice 3. | 1.4.1 Uso del color | Media | Verificado (ver hallazgo 18) |
| 14 | Contraste del botón principal de la app de referencia (~2,1:1): no replicar; el BFF usa `#2A6F2B` con 6,16:1. | 1.4.3 | Media | Verificado (corregir en la app de referencia) |
| 15 | Texto del hero sobre la foto: con el degradé 55 %-85 % el texto `#EEF2E3` medía 2,88:1 como mínimo (3,61:1 en la zona de texto, escritorio) y `#C8F169` (botones de contorno, link) 2,54:1 (3,17:1), bajo el 4,5:1. El degradé pasó a 78 %-90 % (mínimos 5,49:1 y 4,83:1 sobre la foto real; 5,47:1 y 4,82:1 con foto blanca pura). El hover del botón de contorno ahora rellena sólido, en vez de aclarar la foto (9,25:1). Axe suele no poder evaluar texto sobre imágenes de fondo, por eso se cubre con `StylesheetContrastTest`. | 1.4.3 Contraste mínimo | Alta | Resuelto (slice 3) |
| 16 | Foco sobre superficies oscuras (cabecera, pie, hero, panel de marca, skip link sobre la cabecera): el anillo verde `#2A6F2B` medía 1,94:1 contra `#043F2E`. Ahora es `#C8F169` (9,25:1) y blanco en el skip link (11,96:1). Excepción: el reproductor de lecciones (hallazgo 8). | 2.4.7, 1.4.11 | Media | Resuelto (slice 3) |
| 17 | Borde de los campos de login, registro, recuperación y reenvío (`auth-body`): `#E5E7EB` sobre la card blanca medía 1,24:1 y el relleno 1,05:1, así que el campo casi no se distinguía. Ahora el borde es `brand-deep` al 60 % (3,71:1 contra el blanco, 3,55:1 contra el relleno). | 1.4.11 Contraste no textual | Media | Resuelto (slice 3) |
| 18 | Links dentro de las pantallas de cuenta sin subrayado (`no-underline`): el verde de los links frente al texto que los rodea mide 1,94:1 (hace falta 3:1 o un indicador no cromático). Se restituyó el subrayado global. | 1.4.1 Uso del color | Media | Resuelto (slice 3) |
| 19 | Video de marca de la página de error: arrancaba solo, mudo y en bucle (`loop`), sin controles. Un bucle dura más de 5 s y no se podía pausar. Se quitó `loop`: el video dura 4,04 s, se reproduce una vez y queda en el logo final, y con movimiento reducido ya se reemplazaba por la imagen quieta. Una prueba exige que dure 5 s o menos. | 2.2.2 Pausar, detener, ocultar | Media | Resuelto (slice 3) |
| 20 | Registro con datos personales (#652): `autocomplete` correcto en nombre (`given-name`), apellido (`family-name`), teléfono (`tel`, `type="tel"`), fecha de nacimiento (`bday`, `type="date"`) y contraseñas (`new-password`); el email usa `username` (válido en la lista de propósitos de 1.3.5). Etiquetas asociadas y campos obligatorios con `required`; el opcional lo dice en la etiqueta. Se agregaron pruebas de regresión. | 1.3.5, 1.3.1, 3.3.2 | Media | Verificado (sin cambios) |
| 21 | La fecha de nacimiento no explicaba el formato: un navegador sin calendario muestra un campo de texto y el servidor espera `AAAA-MM-DD`. Se agregó una ayuda vinculada con `aria-describedby` (junto con el error, cuando lo hay). | 3.3.2 Etiquetas o instrucciones, 1.3.1 | Media | Resuelto (slice 3) |
| 22 | Imágenes: las cuatro fotos de la home tienen texto alternativo descriptivo (revisado en el código); el logo, los íconos SVG de beneficios y del menú son decorativos (`alt=""` o `aria-hidden="true"`); el video de la página de error es `aria-hidden` y la imagen quieta de respaldo lleva `alt="Menta Dance"`. La pertinencia de los textos alternativos es un juicio editorial y no se verificó con usuarios. | 1.1.1 Contenido no textual | Media | Verificado (revisión de código) |
| 23 | Tras enviar un formulario inválido la página se vuelve a cargar y los errores quedan junto a cada campo, sin resumen de errores ni foco movido a ellos. No se probó cómo lo anuncian los lectores de pantalla. | 3.3.1, 3.3.3, 4.1.3 | Media | Pendiente de prueba manual |

### Revisión de código

- **Encabezados**: un único `h1` por vista; sin saltos de nivel (cubierto por
  `assertHeadingsInOrder` en las pruebas de integración). Los títulos del pie son
  `h2`.
- **Landmarks**: un único `<main>`; `header` y `footer` únicos, por lo que no
  requieren nombre; los `nav` (principal, principal móvil, pie de página e
  información legal) están todos nombrados.
- **Regiones vivas**: `alert` usa `role="alert"` para errores y `role="status"`
  para el resto; cada mensaje aparece una sola vez. En `/login` el error de
  credenciales se muestra en una única región `role="alert"`.
- **Imágenes/SVG**: hay fotos (`<img>` con `alt`, tamaño y `srcset`), un logo
  decorativo (`alt=""` junto al nombre visible), íconos SVG con `aria-hidden="true"`
  y un `<video>` decorativo (ver hallazgos 19 y 22).
- **Botones y links**: todos tienen texto visible; los botones "Cotizar solo
  esta clase" se asocian a su sesión con `aria-describedby`. El menú móvil es un
  `<details>` con `<summary aria-label>`.
- **Páginas legales**: encabezados en orden, un `h1`, aviso de revisión pendiente
  como `role="note"`; sin hallazgos adicionales en la revisión de código.

## Subtítulos (hueco conocido)

El reproductor de lecciones (`lesson.html`) **no** ofrece pistas de subtítulos
(`<track kind="captions">`): el contrato de la API de streaming no expone
archivos de subtítulos ni su idioma. Es el principal hueco accesible del
producto y afecta a personas sordas o con dificultad auditiva (criterio 1.2.2).
No se resuelve en este slice porque requiere cambios de contrato y de carga de
contenido; no se declara conformidad con ese criterio.

### Tarea de seguimiento propuesta

> **Subtítulos para videos de lecciones (WCAG 1.2.2)**
>
> - Definir en la API virtual (módulo `virtual`) el modelo de subtítulos por
>   lección: idioma, etiqueta y URL de un archivo WebVTT, y exponerlos en la
>   respuesta de `GET /api/v1/virtual/lessons/{id}/stream` (o endpoint
>   asociado).
> - Permitir a admin/instructor cargar y reemplazar el archivo WebVTT.
> - En `lesson.html`, renderizar `<track kind="captions" srclang label src>`
>   por cada pista (con `default` para el idioma de la interfaz).
> - Revisar la política CSP (`media-src`, `connect-src`) para el origen de los
>   `.vtt`.
> - Criterio de aceptación: una lección con subtítulos los muestra en el
>   reproductor y axe deja de marcar `video-caption` como *incomplete*; las
>   lecciones sin subtítulos muestran un aviso textual.

## Verificación del slice 3

| Qué | Cómo | Estado |
|---|---|---|
| Contraste de tokens, degradé del hero, foco sobre fondos oscuros, borde de campos, subrayado de links, `alert-info` | `StylesheetContrastTest` (JUnit sobre el CSS compilado) | Cubierto por prueba de build |
| Registro: propósito de los campos, ayuda de fecha de nacimiento, `aria-describedby` válido | `RegistrationIntegrationTest` | Cubierto por prueba de integración |
| Video de la página de error sin bucle y de 5 s o menos | `SiteImagesIntegrationTest` | Cubierto por prueba de integración |
| Skip link con foco blanco | `PageLayoutIntegrationTest` | Cubierto por prueba de integración |
| Recorridos Playwright + axe de las páginas legales y del registro nuevo | `bff/e2e-a11y/tests/journeys.spec.js` | Escritos, **no ejecutados** en este slice |
| Teclado, lector de pantalla, zoom 200 % y *reflow* 320 px | Procedimiento de prueba manual (abajo) | **Pendiente de prueba manual** |
| Subtítulos de lecciones (hallazgo 7, #568) | Fuera de alcance | Conocido |

Nada de esto declara conformidad con WCAG 2.1 AA.

## Procedimiento de prueba manual

Hacerlo antes de cada release que cambie plantillas o CSS.

### Teclado (sin mouse)

1. Abrir `/` y presionar Tab: el primer foco es "Saltar al contenido" y es
   visible. Enter debe mover el foco al contenido principal.
2. Seguir con Tab por toda la página: el orden debe seguir el orden visual y
   cada control debe mostrar un indicador de foco claro (outline verde sobre
   fondos claros; claro sobre la cabecera, el pie, el hero y el panel de marca).
3. En `/login` y `/registro`: completar y enviar con teclado; los errores de
   validación deben mostrarse junto al campo y el foco no debe quedar atrapado.
4. En una lección: llegar al reproductor con Tab y operarlo con teclado
   (espacio, flechas).
5. Shift+Tab debe recorrer en sentido inverso sin saltos.

### Lector de pantalla (VoiceOver en macOS o NVDA en Windows)

1. Navegar por landmarks (rotor / lista de regiones): banner, navegación
   nombrada, principal, contenido de pie.
2. Navegar por encabezados: un único nivel 1 y niveles en orden.
3. En formularios: cada campo se anuncia con su etiqueta, "no válido" y el
   mensaje de error / ayuda asociados.
4. Enviar un formulario con error: el mensaje de `role="alert"` se anuncia
   una sola vez.
5. En una lección: el video se anuncia como "Video de la lección: ...".

### Zoom y ajustes

1. Zoom del navegador al 200 % y ancho 320 px: sin pérdida de contenido ni
   scroll horizontal (1.4.4, 1.4.10).
2. Activar "Reducir movimiento" del sistema: no debe haber animaciones (en la
   página de error se ve el logo quieto en lugar del video). Sin esa opción, el
   video de la página de error se reproduce una vez y se detiene.
3. Probar con contraste alto del sistema operativo.

### Automática (local)

Ver "Comprobaciones automáticas en CI" más abajo: la misma suite se ejecuta
localmente contra el stack de desarrollo.

## Comprobaciones automáticas en CI

Suite: `bff/e2e-a11y/` (Playwright + `@axe-core/playwright`, Chromium, tags
`wcag2a`, `wcag2aa`, `wcag21a`, `wcag21aa`). Es un paquete npm **independiente**
del pipeline de Tailwind (`bff/package.json`), con versiones exactas y
`engines.node` 20.11.1.

| Qué | Detalle |
|---|---|
| Recorridos | Públicos (`/`, `/classes`, detalle de clase, detalle de curso, `/plans`, 404), legales (las siete páginas: términos, privacidad, cookies, devolución, propiedad intelectual, accesibilidad y contacto), autenticación (`/login` y su error, `/registro`, errores de validación del registro con nombre, apellido, teléfono y fecha de nacimiento inválida, propósito de los campos del registro, `/recuperar-contrasena`, `/reenviar-activacion`) y alumno demo (`/dashboard`, una lección). Los recorridos legales y los del registro con los campos nuevos se agregaron en el slice 3; no se ejecutaron localmente al escribirlos (sin stack ni `.env` disponibles), así que su primer resultado válido es el de CI. |
| Datos | Perfil `demo` (datos públicos de siembra, docs/26). Alumno `alumno@menta.dance`; no se usan otras credenciales ni secretos. |
| Falla el job | Violaciones de impacto `critical` o `serious`. |
| Solo informa | Violaciones `moderate`/`minor` y resultados `incomplete` no previstos: anotación `::warning` y resumen del job. |
| Lista permitida | `ALLOWED_INCOMPLETE` en `bff/e2e-a11y/tests/axe.js` (único lugar). Hoy: `video-caption`, hueco conocido de subtítulos, issue #568. |
| Workflow | `.github/workflows/bff-accessibility.yml`: en PR a `develop` cuando cambian `bff/**`, `scripts/dev.sh`, este documento o el propio workflow; también `workflow_dispatch`. |
| Si falla | Se sube el artefacto `bff-accessibility-report` (reporte HTML de Playwright, trazas y `.dev-logs/`). |

**Un resultado verde no es una declaración de conformidad.** Axe cubre solo una
parte de WCAG; el procedimiento manual de arriba sigue siendo necesario.

### Ejecución local

```bash
# 1. Levantar el stack con datos demo (requiere .env, ver docs/26)
SPRING_PROFILES_ACTIVE=demo ./scripts/dev.sh start

# 2. Instalar y correr la suite (Node 20.11.1)
cd bff/e2e-a11y
npm ci
npx playwright install chromium     # la primera vez
BASE_URL=http://localhost:8080 npx playwright test   # BASE_URL es opcional

# 3. Detener el stack
cd ../.. && ./scripts/dev.sh stop
```

Sin el perfil `demo` fallan los recorridos que dependen de datos sembrados.
`BASE_URL` por defecto es `http://localhost:8080`.

### Mantenimiento

- Un recorrido nuevo se agrega en `bff/e2e-a11y/tests/journeys.spec.js` con el
  helper `expectNoBlockingViolations`.
- No agregar reglas a `ALLOWED_INCOMPLETE` sin un issue de seguimiento enlazado
  en el comentario.
- Las dependencias de `bff/e2e-a11y/` se actualizan a mano (versión exacta y
  lockfile); Dependabot no vigila ese paquete hoy.
