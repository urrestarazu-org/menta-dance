# US-VIRTUAL-007: Acceso de suscripción al contenido virtual

## Historia
Como alumno sin suscripción activa, quiero distinguir contenido de muestra de
contenido restringido para entender cómo recuperar acceso.

## Aceptación

- Virtual valida acceso en backend antes de devolver
  `GET /api/v1/virtual/lessons/{lessonId}/stream`.
- El alumno puede obtener primero `GET /api/v1/virtual/lessons/{lessonId}` con
  metadatos protegidos; ese endpoint tampoco expone la URL de streaming.
- El BFF puede mostrar contenido de muestra y un mensaje de suscripción, pero
  no decide autorización.
- Virtual consulta el derecho actual por un puerto de Billing y puede invalidar
  Caffeine mediante evento interno `SubscriptionStatusChanged`; no usa HTTP
  interno ni RabbitMQ.
- La denegación usa `application/problem+json` y nunca expone URL de streaming.

## Hecho cuando

Existen pruebas de acceso activo, cancelado/expirado y preview.
