# ADR-0029: Java Type Design Guidelines

**Estado:** Aceptado
**Fecha:** 2026-07-30
**Decisores:** Equipo de desarrollo

## Contexto y Problema

En un proyecto Java/Spring Boot con Clean Architecture, frecuentemente debemos elegir entre diferentes estructuras para representar datos: enums, clases con constantes, records, sealed classes, etc. Decisiones inconsistentes generan confusión y dificultan el mantenimiento.

Este ADR establece guidelines para elegir la estructura apropiada según el contexto de uso.

## Factores Clave (Decision Drivers)

* Type-safety dentro del módulo vs flexibilidad cross-module
* Serialización a BD, APIs, o sistemas de mensajería
* Extensibilidad futura sin breaking changes
* Claridad de intención para otros desarrolladores

## Guidelines por Tipo de Estructura

### Enum: Valores finitos del dominio interno

**Usar cuando:**
- El conjunto de valores es cerrado y conocido en compile-time
- Los valores NO cruzan boundaries de serialización externa
- Se necesita type-safety y exhaustive matching (switch expressions)

**Ejemplos en el proyecto:**
```java
// ✅ Correcto: valores internos del dominio
public enum Role { STUDENT, INSTRUCTOR, ADMIN }
public enum UserStatus { ACTIVE, LOCKED }
public enum RefreshTokenStatus { VALID, REVOKED, COMPROMISED }
```

**Anti-pattern:**
```java
// ❌ Incorrecto: event types que se persisten en BD
public enum OutboxEventType { AUTH_USER_LOGGED_IN, ... }
// Problema: cambiar el nombre del enum rompe datos históricos
```

---

### String Constants: Contratos de serialización

**Usar cuando:**
- Los valores se persisten en BD o se envían a sistemas externos
- Múltiples módulos pueden definir sus propios valores
- El valor literal ES el contrato (no el nombre Java)

**Ejemplos en el proyecto:**
```java
// ✅ Correcto: event types del outbox
public final class AuthOutboxEventTypes {
    public static final String AUTH_USER_LOGGED_IN = "auth.AuthUserLoggedIn";
    public static final String REFRESH_ROTATED = "auth.RefreshRotated";
    // El string "auth.AuthUserLoggedIn" es el contrato,
    // otros módulos consumirán este valor literal
    private AuthOutboxEventTypes() {}
}
```

**Patrón:** Clase final con constructor privado + constantes `public static final String`.

**Justificación para Outbox:**
- Los event types se persisten en `common_outbox_events.event_type`
- Consumidores cross-module matchean contra el string literal
- Renombrar la constante Java no rompe datos históricos
- Cada módulo puede tener su propia clase `*OutboxEventTypes`

---

### Record: DTOs y Value Objects inmutables

**Usar cuando:**
- Se necesita un contenedor de datos inmutable
- No hay comportamiento complejo (solo getters, equals, hashCode, toString)
- Se usa en boundaries (DTOs de entrada/salida, comandos, queries)

**Ejemplos en el proyecto:**
```java
// ✅ Correcto: comandos de aplicación
public record LoginCommand(String email, String password) {}
public record TokenPair(String accessToken, String refreshToken, long expiresIn) {}

// ✅ Correcto: value objects simples
public record UserId(UUID value) {}
public record Email(String value) {}
```

---

### Sealed Classes/Interfaces: Jerarquías cerradas con comportamiento

**Usar cuando:**
- Se necesita polimorfismo con un conjunto conocido de subtipos
- Cada subtipo tiene comportamiento o datos distintos
- Se quiere exhaustive pattern matching en Java 21+

**Ejemplo hipotético:**
```java
public sealed interface AuthEvent permits LoginEvent, LogoutEvent, RefreshEvent {
    UUID userId();
    Instant timestamp();
}
```

---

### Class con campos: Entidades y agregados

**Usar cuando:**
- Se necesita identidad (entidades de dominio)
- Hay estado mutable o lifecycle
- Se requiere encapsulación de invariantes

**Ejemplos en el proyecto:**
```java
// ✅ Correcto: entidad de dominio con identidad
public class User {
    private final UserId id;
    private UserStatus status;
    private long tokenVersion;
    // Métodos que protegen invariantes
    public void lock() { this.status = UserStatus.LOCKED; }
}
```

## Decisión

Adoptamos las guidelines anteriores como estándar del proyecto.

## Consecuencias

### Positivas

* Consistencia en decisiones de diseño de tipos
* Claridad de intención: el tipo elegido comunica el contexto de uso
* Menos bugs por serialización incorrecta de enums

### Negativas / Deuda Técnica

* Requiere revisión en code review para asegurar adherencia
* String constants pierden type-safety (typos no detectados en compile-time)

### Riesgos y Reversibilidad

* **Riesgo:** Desarrolladores usen enum para event types por costumbre
* **Mitigación:** Este ADR + revisión en PRs
* **Reversibilidad:** Alta — refactorizar entre estructuras es mecánico

## Referencias y Decisiones Relacionadas

* [ADR-0021: Clean Architecture Mandatory](0021-clean-architecture-mandatory.md)
* [Effective Java, Item 34: Use enums instead of int constants](https://www.oreilly.com/library/view/effective-java/9780134686097/)
* [Transactional Outbox Pattern](https://microservices.io/patterns/data/transactional-outbox.html)
