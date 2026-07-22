# US-AUTH-003: Activación de cuenta

## Historia
Como usuario recién registrado, quiero activar mi cuenta desde un enlace de
correo para poder iniciar sesión.

## Aceptación

- `GET /api/v1/auth/activate/{token}` valida token de un solo uso y expiración,
  activa `auth_users` y lo invalida.
- `POST /api/v1/auth/resend-activation` emite un token nuevo sin revelar si el
  email existe.
- El envío de correo usa un puerto/worker interno, sin RabbitMQ ni HTTP interno.
- Errores siguen `application/problem+json`; tokens y datos personales no se
  incluyen en logs.

## Hecho cuando

Pruebas cubren token válido, vencido, reutilizado, reenvío y rate limiting.
