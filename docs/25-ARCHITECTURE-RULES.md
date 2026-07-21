# Reglas de Arquitectura Ejecutables

[← Volver al índice](./README.md)

Estas reglas materializan [ADR-0020](./adr/0020-modular-monolith.md) y
[ADR-0021](./adr/0021-clean-architecture-mandatory.md). Una revisión manual no
reemplaza su verificación automática en CI.

## Topología obligatoria

- `:api:app` ensambla `:api:shared`, `:api:auth`, `:api:virtual`,
  `:api:physical` y `:api:billing` en **un JAR ejecutable**.
- `:bff` produce otro JAR y solo se comunica con la API mediante HTTP.
- `:android:app` consume la API pública mediante HTTP.
- El panel de administración es una página web servida por el BFF.
- Está prohibido introducir HTTP, RabbitMQ, service discovery, circuit breakers
  o credenciales M2M para comunicar módulos dentro del JAR API.

## Dependencias permitidas

```text
domain <- application <- infrastructure

:api:app      -> todos los módulos API (composición y orquestación cross-module)
módulo negocio -> :api:shared (contratos compartidos mínimos)
:bff          -HTTP-> :api:app
:android:app  -HTTP-> :api:app
```

- `domain` no depende de frameworks ni de otras capas.
- `application` depende de `domain` y define puertos; no usa Spring.
- `infrastructure` implementa puertos y contiene Spring, JPA y clientes externos.
- Un módulo de negocio no importa `infrastructure` ni implementaciones de otro.
- Ningún core de negocio depende de otro core. Los flujos cross-module se
  orquestan en `api:app` usando contratos neutrales de `api:shared`.
- La colaboración entre módulos se realiza mediante puertos públicos e interfaces
  Java; los DTOs compartidos deben ser inmutables y mínimos.
- `shared` no contiene lógica de negocio específica ni repositorios concretos.

## Reglas ArchUnit mínimas

Cada módulo API debe verificar:

1. dependencia de capas dirigida hacia `domain`;
2. ausencia de Spring/JPA en `domain` y `application`;
3. ausencia de imports `..infrastructure..` desde otros módulos;
4. acceso entre módulos exclusivamente a paquetes públicos de contratos;
5. inexistencia de clientes HTTP o mensajería destinados a módulos internos;
6. controllers sin acceso directo a repositorios.

Ejemplo de regla inter-módulo:

```java
@ArchTest
static final ArchRule billing_must_not_access_foreign_infrastructure =
    noClasses()
        .that().resideInAPackage("com.menta.billing..")
        .should().dependOnClassesThat()
        .resideInAnyPackage("com.menta.auth.infrastructure..",
            "com.menta.virtual.infrastructure..",
            "com.menta.physical.infrastructure..");
```

La implementación debe declarar la variante simétrica para Auth, Virtual y
Physical, excluyendo la infraestructura propia sin debilitar la prohibición
sobre infraestructura ajena.

## Android

- `domain`: modelos y casos de uso puros, sin Android SDK.
- `data`: repositorios, Retrofit/Room y mappers; implementa puertos de `domain`.
- `presentation`: ViewModels y Compose; depende de casos de uso, no de Retrofit.
- Los ViewModels exponen estado de UI y no contienen acceso directo a red o BD.

## Verificación en CI

```bash
./gradlew test
./gradlew check
```

El build debe fallar ante cualquier violación ArchUnit. Mientras el scaffold de
Gradle no exista, estas reglas son el contrato que debe implementar la primera
configuración de CI.
