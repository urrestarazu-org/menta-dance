# US-AUTH-001: Registro de usuario

## Historia
Como potencial alumno, quiero crear una cuenta y activar mi correo para usar la
academia.

## Aceptación

- `POST /api/v1/auth/register` crea `auth_users` en estado pendiente y asigna
  `ALUMNO` por defecto.
- La contraseña se guarda con BCrypt; errores y validaciones usan
  `application/problem+json` sin exponer datos existentes.
- La activación se envía por un puerto de correo o evento interno durable; no
  existe RabbitMQ ni llamada HTTP entre módulos.
- Rate limiting y auditoría protegen registro y reenvío; ningún secreto queda en
  logs.

## Hecho cuando

Pruebas cubren registro, duplicados, validación, activación y entrega de correo.
