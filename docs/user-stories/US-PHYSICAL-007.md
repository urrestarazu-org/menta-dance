# US-PHYSICAL-007: Gestión de dispositivos QR

**ID:** US-PHYSICAL-007
**Módulo:** Physical
**Prioridad:** Must Have
**Estado:** Draft

## Historia de usuario

> Como administrador, quiero registrar y gestionar lectores QR para que puedan
> procesar check-ins de alumnos de forma segura.

## Criterios de aceptación

**Escenario 1: registrar dispositivo**

- **Dado** un usuario con rol `ADMIN`.
- **Cuando** envía `POST /api/v1/physical/devices` con nombre descriptivo y
  ubicación.
- **Entonces** Physical crea el dispositivo, genera un secreto opaco único,
  almacena solo su hash, y devuelve el secreto **una única vez** en la respuesta.
- **Y** el dispositivo queda en estado `ACTIVE` con fecha de expiración configurable.

**Escenario 2: secreto no recuperable**

- **Dado** un dispositivo registrado.
- **Cuando** el administrador solicita el secreto nuevamente.
- **Entonces** responde `404 Not Found`; el secreto no se almacena en texto claro.

**Escenario 3: rotar secreto**

- **Dado** un dispositivo activo cuyo secreto fue comprometido o está por expirar.
- **Cuando** envía `POST /api/v1/physical/devices/{deviceId}/rotate-secret`.
- **Entonces** Physical genera un nuevo secreto, invalida el anterior, y devuelve
  el nuevo secreto **una única vez**.

**Escenario 4: revocar dispositivo**

- **Dado** un dispositivo activo o expirado.
- **Cuando** envía `POST /api/v1/physical/devices/{deviceId}/revoke`.
- **Entonces** el dispositivo pasa a estado `REVOKED` y no puede procesar más
  check-ins. La revocación es irreversible.

**Escenario 5: dispositivo expirado**

- **Dado** un dispositivo cuya fecha de expiración ha pasado.
- **Cuando** intenta procesar un check-in.
- **Entonces** Physical rechaza la solicitud con `401 Unauthorized` y
  `DEVICE_EXPIRED`.

**Escenario 6: listar dispositivos**

- **Dado** un administrador.
- **Cuando** solicita `GET /api/v1/physical/devices`.
- **Entonces** recibe la lista de dispositivos con nombre, ubicación, estado,
  fecha de creación y expiración. Sin secretos.

## Contrato de registro

```json
{
  "name": "Lector Puerta Principal",
  "location": "Recepción planta baja",
  "expiresAt": "2027-01-01T00:00:00Z"
}
```

## Respuesta de registro (única vez)

```json
{
  "deviceId": "dev-uuid",
  "name": "Lector Puerta Principal",
  "secret": "dvc_xxx...yyy",
  "status": "ACTIVE",
  "expiresAt": "2027-01-01T00:00:00Z"
}
```

## Notas Técnicas

- El dispositivo es una entidad técnica, no un usuario. No tiene roles humanos.
- El secreto usa un algoritmo de hash seguro (bcrypt o argon2).
- La auditoría registra creación, rotación y revocación con timestamp y actor.
- La autenticación del dispositivo es independiente de JWT de usuarios.

## Definition of Done

- [ ] Pruebas de registro y entrega única del secreto.
- [ ] Pruebas de rotación y revocación.
- [ ] Pruebas de rechazo por expiración y revocación.
- [ ] Prueba de que el secreto no es recuperable después del registro.
- [ ] Contrato OpenAPI actualizado.
