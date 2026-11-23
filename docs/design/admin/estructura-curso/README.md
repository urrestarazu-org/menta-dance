# Referencia de diseño: estructura de un curso virtual

Pantalla `/gestion/cursos-virtuales/{id}/estructura` según la referencia que dejó el dueño del proyecto,
generada con Stitch (issue #849). También es el ejemplo de cómo se quieren ver **las páginas de administración**
en general.

- `screen.png`: captura de la referencia.
- `code.html`: el HTML que generó Stitch.

> **Es guía visual, NO código a copiar.** `code.html` carga Tailwind desde un CDN y la fuente desde Google
> Fonts, y usa colores en hexadecimal sueltos. El BFF compila Tailwind con Gradle, aloja Poppins y usa los
> tokens `brand-*` y `feedback-*` (ver `docs/31-BFF-TAILWIND.md`). No se ejecuta ni se enlaza desde ninguna
> plantilla.

## Qué se tomó y qué no

- **Paleta y tipografía distintas a propósito**: se mantiene Poppins y los tokens existentes; los colores de
  la referencia (por ejemplo el verde `#0d3b2e` o el crema `#f4f4ea`) se mapearon al token más cercano
  (`brand-deep`, `brand-surface`). Es un rediseño de la disposición y de los botones, no de la paleta.
- **Se tomó**: pestañas de gestión como píldoras, vínculo "Volver" con flecha, tarjeta de cabecera con estado
  (etiqueta con punto) y estadísticas con íconos, aviso con ícono, dos columnas (módulos a la izquierda, detalle
  del módulo seleccionado a la derecha), lecciones como tarjetas con etiquetas, botones chicos y "Borrar" en rojo.
- **Se omitió** (no existe como función; este issue es solo de estilo): "Vista previa" y "Ajustes" del curso, el
  asa de arrastrar, "Contraer todos", "Resolución HD", "Pendiente subir contenido", el botón de cerrar el aviso,
  el pie con enlaces y los textos "Editando lecciones" / "Click para ver lecciones".
- **Se adaptó por accesibilidad**: el gris claro de los textos secundarios y las etiquetas de advertencia
  amarillas no alcanzan 4,5:1; se usan los tokens con contraste medido (`docs/ACCESSIBILITY.md`).
