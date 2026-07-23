# API de Catálogo

## Propósito

El catálogo muestra cursos de danza con una experiencia de lectura única, sin
unificar las reglas de compra de sus modalidades.

```text
GET /api/v1/catalog/courses
GET /api/v1/catalog/courses/{courseId}
```

Cada curso tiene exactamente una modalidad: `PHYSICAL` o `VIRTUAL`. La respuesta
incluye información común (nombre, profesor, nivel y descripción) y un bloque de
modalidad: sesiones/capacidad para presencial o contenido/acceso para virtual.
`courseId` es un UUID globalmente único; su modalidad permite a `api:app` enrutar
la consulta al módulo dueño sin tabla ni acceso directo entre módulos.
Estas son las únicas rutas de lectura de cursos para alumnos y visitantes. Las
rutas de cursos de Physical y Virtual son exclusivamente de gestión
administrativa/interna.

## Límites

La proyección es compuesta por `api:app`/BFF mediante puertos de los módulos
Physical y Virtual. No crea una tabla compartida ni utiliza FK, `JOIN`, HTTP
interno o repositorios entre módulos. Sólo es lectura: cotizaciones presenciales
se solicitan a `POST /api/v1/billing/physical/quotes`; suscripciones virtuales
usan los flujos de Billing correspondientes.

Todos los errores usan `application/problem+json`.
