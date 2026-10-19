# Accesibilidad del BFF web

Auditoría de accesibilidad de las vistas Thymeleaf del BFF (issue #529, slice 1:
auditoría y correcciones). El objetivo de referencia es **WCAG 2.1 nivel AA**.

> Este documento registra lo que se midió y lo que se revisó. **No** declara
> conformidad con WCAG ni cumplimiento legal: una herramienta automática detecta
> solo una parte de los problemas posibles y los puntos pendientes figuran abajo.

## Alcance

- **Incluido**: vistas del BFF (`bff/src/main/resources/templates`), el CSS
  compilado con Tailwind (`bff/src/main/frontend/css/input.css`) y los fragmentos
  comunes (`fragments/layout.html`, `fragments/components.html`).
- **Fuera de alcance**: app Android, contenido de los videos (subtítulos, ver
  más abajo), correos transaccionales y la página de pago de Mercado Pago.
- **Automatización**: las comprobaciones de regresión con Playwright + axe-core
  corren en CI (ver "Comprobaciones automáticas en CI"). Detectan regresiones
  detectables por herramienta; no reemplazan la revisión manual.

## Método

| Técnica | Detalle |
|---|---|
| Automática | axe-core 4.14.0 sobre Chromium (Playwright), tags `wcag2a`, `wcag2aa`, `wcag21a`, `wcag21aa`. Se registran *violations* e *incomplete* (revisión manual requerida). |
| Contraste | Cálculo de la razón de luminancia relativa (fórmula WCAG) para cada par texto/fondo de los tokens y para el indicador de foco; además, axe `color-contrast` sobre las páginas renderizadas. |
| Foco | Recorrido con Tab en `/login` y `/`, leyendo el estilo computado (`outline`, `box-shadow`, `border-color`) de cada control. |
| Código | Revisión de plantillas: jerarquía de encabezados, `main` único, landmarks, errores de formulario, regiones vivas, nombres accesibles, `<video>`. |

La auditoría inicial usó herramientas instaladas fuera del repositorio. Las
comprobaciones de regresión viven en `bff/e2e-a11y/` (paquete npm propio).

### Páginas auditadas

Anónimas: `/`, `/classes`, detalle de clase, `/plans`, `/login` (y con error),
`/registro` (y con errores de validación), `/recuperar-contrasena`,
`/recuperar-contrasena/enviado`, `/restablecer-contrasena/{token}` (token
inválido), `/reenviar-activacion`, `/registro/pendiente`, `/cuenta-activada`,
`/activar/{token}` (token inválido), 404 (`/classes/not-a-uuid`), `/error`,
detalle de curso.
Con sesión de alumno: `/dashboard`, detalle de clase, detalle de curso, dos
lecciones, cotización (`/classes/{id}/cotizar`) y datos de transferencia
(`/compras/transferencia`).

### Resultado de axe

| Estado | Páginas | Violations | Incomplete |
|---|---|---|---|
| Antes de las correcciones | 25 | 0 | 1 (`video-caption`, lección reproducible) |
| Después | 25 | 0 | 1 (`video-caption`, ver "Subtítulos") |

Cero violaciones automáticas **no** implica conformidad: axe no evalúa, por
ejemplo, la calidad de los textos alternativos, el orden lógico real ni el uso
con lector de pantalla. Por eso se hizo además la revisión manual y de código.

## Contraste medido

| Par | Razón | Mínimo AA |
|---|---|---|
| Error `#9B1C1C` sobre `#FEF2F2` (alert, badge) | 7,45:1 | 4,5:1 |
| Error sobre blanco (mensaje de campo en card) | 8,15:1 | 4,5:1 |
| Error sobre `#EEF2E3` (mensaje fuera de card) | 7,16:1 | 4,5:1 |
| Éxito `#043F2E` sobre `#E2F5BE` | 10,27:1 | 4,5:1 |
| Advertencia `#78350F` sobre `#FEF3C7` | 8,15:1 | 4,5:1 |
| Blanco sobre `#2A6F2B` (`.btn-primary`) | 6,16:1 | 4,5:1 |
| Link `#2A6F2B` sobre blanco / sobre `#EEF2E3` | 6,16:1 / 5,41:1 | 4,5:1 |
| Texto `#043F2E` sobre `#EEF2E3` | 10,51:1 | 4,5:1 |
| Foco `#2A6F2B` sobre blanco (cabecera, card) | 6,16:1 | 3:1 |
| Foco sobre `#EEF2E3` (fondo de página) | 5,41:1 | 3:1 |
| Foco sobre superficies de feedback (éxito / error / advertencia) | 5,29 / 5,63 / 5,54:1 | 3:1 |
| Foco sobre `#043F2E` (fondo del reproductor) | 1,94:1 | 3:1 (no cumple, ver hallazgos) |
| Borde `alert-info` (`brand-deep/40`) sobre blanco | ~2,25:1 | 3:1 (no esencial, ver hallazgos) |

No se modificó ningún valor de la paleta: todos los tokens de texto superan AA.
La cabecera del BFF es blanca (no `brand-deep`), por lo que el foco sobre la
cabecera mide 6,16:1.

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
| 9 | Borde de `alert-info` (~2,25:1). El mensaje se entiende por el texto (contraste 11,96:1), el borde no es esencial. | 1.4.11 | Baja | Conocido |
| 10 | Texto de 12 px (`text-small`) del tamaño tipográfico de referencia; verificar zoom 200 % y *reflow* 320 px de forma manual. | 1.4.4, 1.4.10 | Media | Pendiente de prueba manual |
| 11 | Modo oscuro (`darkMode`) no habilitado: no hay paleta oscura que medir. Si se habilita, hay que medir contraste en oscuro. | 1.4.3 | Baja | Fuera de alcance |
| 12 | Fondo animado de login/registro de la app de referencia no portado: si se porta, respetar `prefers-reduced-motion` (ya hay una regla global) y medir contraste sobre el degradé. | 2.3.3, 1.4.3 | Baja | Fuera de alcance |
| 13 | Links subrayados siempre (no se distinguen solo por color). | 1.4.1 Uso del color | Media | Verificado (sin cambios) |
| 14 | Contraste del botón principal de la app de referencia (~2,1:1): no replicar; el BFF usa `#2A6F2B` con 6,16:1. | 1.4.3 | Media | Verificado (corregir en la app de referencia) |

### Revisión de código (sin hallazgos nuevos)

- **Encabezados**: un único `h1` por vista; sin saltos de nivel (cubierto por
  `assertHeadingsInOrder` en las pruebas de integración).
- **Landmarks**: un único `<main>`; `header` y `footer` únicos, por lo que no
  requieren nombre; los `nav` ahora están todos nombrados.
- **Regiones vivas**: `alert` usa `role="alert"` para errores y `role="status"`
  para el resto; cada mensaje aparece una sola vez. En `/login` el error de
  credenciales se muestra en una única región `role="alert"`.
- **Imágenes/SVG**: las plantillas no usan `<img>` ni `<svg>`; no hay gráficos
  decorativos por ocultar.
- **Botones y links**: todos tienen texto visible; los botones "Cotizar solo
  esta clase" se asocian a su sesión con `aria-describedby`.

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

## Procedimiento de prueba manual

Hacerlo antes de cada release que cambie plantillas o CSS.

### Teclado (sin mouse)

1. Abrir `/` y presionar Tab: el primer foco es "Saltar al contenido" y es
   visible. Enter debe mover el foco al contenido principal.
2. Seguir con Tab por toda la página: el orden debe seguir el orden visual y
   cada control debe mostrar un indicador de foco claro (outline verde).
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
2. Activar "Reducir movimiento" del sistema: no debe haber animaciones.
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
| Recorridos | Públicos (`/`, `/classes`, detalle de clase, detalle de curso, `/plans`, 404), autenticación (`/login` y su error, `/registro` y errores de validación, `/recuperar-contrasena`, `/reenviar-activacion`) y alumno demo (`/dashboard`, una lección). |
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
