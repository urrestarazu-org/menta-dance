# US-VIRTUAL-007: Acceso de suscripción al contenido virtual

## Historia
Como alumno sin suscripción activa, quiero distinguir contenido de muestra de
contenido restringido para entender cómo recuperar acceso.

## Cascada de acceso

El acceso **no se guarda como un flag "este usuario tiene este curso"**: se
deriva en cada consulta, evaluando en este orden. La primera regla que concede
acceso corta la cascada.

1. **¿La lección tiene `isFree = true`?** → acceso libre, sin autenticar.
2. **¿El módulo tiene `isPreview = true`?** → acceso libre a todas sus
   lecciones, aunque cada lección tenga `isFree = false`. La cascada es
   jerárquica a propósito (ver [US-VIRTUAL-003](US-VIRTUAL-003.md)).
3. **¿El curso no pertenece a ningún plan?** → acceso libre. Un curso que no
   está incluido en ningún plan es un curso abierto; no hay nada que comprar
   para acceder a él.
4. **Si no** → requiere una suscripción del alumno en estado `ACTIVE` y no
   vencida, cuyo **conjunto de cursos habilitados** incluya este curso.

No se registra con qué plan entró el alumno ni hace falta: si tiene más de una
suscripción vigente, alcanza con que **alguna** habilite el curso.

## Aceptación

- Virtual valida acceso en backend antes de devolver
  `GET /api/v1/virtual/lessons/{lessonId}/stream`.
- El alumno puede obtener primero `GET /api/v1/virtual/lessons/{lessonId}` con
  metadatos protegidos; ese endpoint tampoco expone la URL de streaming.
- El BFF puede mostrar contenido de muestra y un mensaje de suscripción, pero
  no decide autorización.
- Virtual consulta el derecho actual por un puerto de Billing y puede invalidar
  Caffeine mediante evento interno `SubscriptionStatusChanged`; no usa HTTP
  interno ni RabbitMQ. La invalidación es **por usuario**, nunca un vaciado
  global del caché.
- La denegación usa `application/problem+json` y nunca expone URL de streaming.
- Las reglas 1 a 3 conceden acceso **sin requerir autenticación**: un visitante
  anónimo accede igual que un alumno logueado.

## El acceso se evalúa contra el snapshot, no contra el plan vivo

La regla 4 usa los cursos que el plan habilitaba **al momento de activarse la
suscripción**, no los que habilita hoy. Si un admin desactiva el plan o le quita
un curso, quien ya pagó **conserva su acceso hasta que su suscripción venza**.

Es el mismo principio de inmutabilidad que
[US-BILLING-009](US-BILLING-009.md) ya aplica al pricing presencial: un cambio
administrativo no altera lo que un alumno ya compró. Ver
[US-BILLING-010](US-BILLING-010.md) para dónde se persiste ese snapshot.

## Hecho cuando

Existen pruebas de:

- acceso con suscripción `ACTIVE` vigente;
- denegación con suscripción vencida y con suscripción cancelada;
- las tres vías de acceso libre (lección `isFree`, módulo `isPreview`, curso sin
  plan), cada una sin autenticación;
- acceso conservado tras desactivarse el plan que originó la suscripción.
