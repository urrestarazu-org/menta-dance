# ADR-0032: Política de Nonce y Rotación de Clave para Activation Delivery Cipher

**Estado:** Aceptado

## Contexto

`AesGcmActivationDeliveryCipher` (`api/auth/src/main/java/com/menta/auth/infrastructure/activation/`)
cifra el material de entrega del token de activación de cuenta con AES-256-GCM.
En GCM, reutilizar un nonce (IV) con la misma clave rompe la confidencialidad y
la autenticación del esquema completo, por lo que la construcción del nonce y el
ciclo de vida de la clave son decisiones de seguridad explícitas, no detalles de
implementación.

## Decisión

El nonce es de 96 bits (`NONCE_BYTES = 12`) y se genera con `SecureRandom` en
cada llamada a `encrypt()`, sin contador ni valor derivado — construcción
"random IV" recomendada por NIST SP 800-38D para GCM.

Con nonces aleatorios de 96 bits, la probabilidad de colisión por cumpleaños
deja de ser despreciable a partir de aproximadamente 2^32 invocaciones bajo la
misma clave. Esa cifra es el límite operacional máximo de uso por
`keyVersion`: al acercarse a ese umbral, la clave debe rotarse.

`keyVersion` (parámetro del constructor, persistido en `DeliveryEnvelope`)
identifica bajo qué clave se cifró cada envelope y permite decodificar
envelopes antiguos mientras se cifra con una clave nueva. La rotación de clave
se resuelve incrementando `keyVersion` y desplegando la clave nueva; no existe
hoy una alerta automática ligada al conteo de usos — la responsabilidad de
monitorear el volumen de activaciones por `keyVersion` y disparar la rotación
antes de acercarse a 2^32 usos es operacional.

## Consecuencias

### Positivas

* El nonce aleatorio por operación elimina el riesgo de reuso determinístico
  (contador mal inicializado, réplicas desincronizadas, etc.).
* `keyVersion` ya soporta rotación sin romper la decodificación de envelopes
  emitidos con una clave anterior.

### Negativas / Deuda Técnica

* No hay métrica ni alerta que dispare la rotación al acercarse al límite de
  2^32 usos por clave. Mientras el volumen de activaciones de cuenta se
  mantenga órdenes de magnitud por debajo de ese límite, el riesgo es
  teórico.

### Riesgos y Reversibilidad

* **Riesgo Principal:** agotar el presupuesto de nonces de una `keyVersion`
  sin rotarla a tiempo, lo que reintroduce riesgo de colisión de nonce.
* **Plan de Mitigación:** instrumentar el conteo de `encrypt()` por
  `keyVersion` y alertar antes de aproximarse a 2^32 usos, si el volumen de
  registro llega a un orden de magnitud relevante.
* **Reversibilidad:** alta — rotar la clave es incrementar `keyVersion` y
  desplegar la clave nueva; no requiere cambios de esquema.

## Referencias y Decisiones Relacionadas

* NIST SP 800-38D — Recommendation for Block Cipher Modes of Operation: Galois/Counter Mode (GCM).
* `api/auth/src/main/java/com/menta/auth/infrastructure/activation/AesGcmActivationDeliveryCipher.java`
* Complementa a: [ADR-0025](0025-auth-token-strategy.md) (estrategia de tokens de sesión; no cubre activation delivery)
