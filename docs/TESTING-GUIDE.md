# Testing Guide - Menta Virtual Academy

Esta guía documenta las buenas prácticas, herramientas y convenciones para escribir tests en el proyecto.

## Índice

- [Frameworks y Herramientas](#frameworks-y-herramientas)
- [Ejecución de Tests](#ejecución-de-tests)
- [Cobertura de Código](#cobertura-de-código)
- [Tests de Arquitectura (ArchUnit)](#tests-de-arquitectura-archunit)
- [Buenas Prácticas de Checkstyle en Tests](#buenas-prácticas-de-checkstyle-en-tests)
- [Convenciones de Nombres](#convenciones-de-nombres)
- [Estructura de Tests](#estructura-de-tests)

## Frameworks y Herramientas

### Unit & Integration Tests

- **Framework**: JUnit 5 (Jupiter)
- **Mocking**: Mockito
- **Test database**: Testcontainers MySQL (NO H2)
- **Assertions**: JUnit 5, AssertJ

> Ver [14-TEST-STRATEGY.md](./14-TEST-STRATEGY.md) para la estrategia completa.

### Cobertura de Código (Modelo 100/80/0)

- **Herramienta**: JaCoCo

| Capa | Paquetes | Cobertura |
|------|----------|-----------|
| **Core** | `billing.domain.*`, `billing.application.*`, `auth.domain.*`, `auth.application.*` | **100%** |
| **Importancia** | `virtual.*`, `physical.*` | **80%** |
| **Infraestructura** | DTOs, configs, repositorios JPA | **0% unitarios** |

- **Exclusiones de cobertura**: DTOs, mappers, repository interfaces, config classes, exceptions
- **NO excluir**: `domain` de módulos core (Billing/Auth requieren 100%)

### Tests de Arquitectura

- **Framework**: ArchUnit 1.3.0
- **Ubicación**: `src/test/java/com/menta/dance/architecture/`

## Ejecución de Tests

### Comandos Básicos

```bash
# Ejecutar todos los tests
./gradlew test

# Ejecutar test específico
./gradlew test --tests ClassName.methodName

# Ejecutar tests de arquitectura
./gradlew test --tests "com.menta.dance.architecture.*"

# Ejecutar tests con reporte de cobertura
./gradlew test jacocoTestReport

# Verificar umbrales de cobertura
./gradlew jacocoTestCoverageVerification
```

### Reportes

- **Tests**: `build/reports/tests/test/index.html`
- **Cobertura**: `build/reports/jacoco/test/html/index.html`
- **Checkstyle**: `build/reports/checkstyle/test.html`

## Cobertura de Código

### Configuración JaCoCo

Ver modelo 100/80/0 en [14-TEST-STRATEGY.md](./14-TEST-STRATEGY.md).

### Clases Excluidas de Cobertura Global

Las siguientes clases están excluidas de los umbrales:

- DTOs (`**/*Dto.class`, `**/*Form.class`, `**/*ViewModel.class`)
- Mappers (`**/*Mapper.class`, `**/*MapperImpl.class`)
- Repository interfaces (`**/*Repository.class`)
- Configuración (`**/config/**`)
- Exceptions (`**/exception/**`)

> ⚠️ **domain NO está excluido.** Los módulos Billing y Auth requieren 100%
> de cobertura en `domain` y `application`.

### Visualizar Cobertura

```bash
./gradlew jacocoTestReport
open build/reports/jacoco/test/html/index.html
```

## Tests de Arquitectura (ArchUnit)

### Propósito

ArchUnit valida automáticamente que la arquitectura del proyecto cumple con las reglas definidas.

### Reglas Implementadas

1. **Controllers cannot access repositories directly**
   - Los controladores DEBEN delegar el acceso a datos a servicios
   - Previene acoplamiento directo entre capas

2. **Controllers only depend on services**
   - No se permiten dependencias directas de repositorios
   - Asegura separación de responsabilidades

3. **Repositories only accessed by services**
   - Solo servicios pueden acceder a repositorios
   - Mantiene arquitectura de capas limpia

### Ubicación

```
src/test/java/com/menta/dance/architecture/
├── LayeredArchitectureTest.java
```

### Ejecución

```bash
./gradlew test --tests "com.menta.dance.architecture.*"
```

### Beneficios

- ✅ Previene violaciones arquitectónicas en tiempo de compilación
- ✅ Mantiene separación limpia entre capas (Controller → Service → Repository)
- ✅ Aplicación automática de mejores prácticas
- ✅ Mejora mantenibilidad y testeabilidad del código

## Buenas Prácticas de Checkstyle en Tests

### Reglas Específicas para Tests

#### 1. AbbreviationAsWordInName

Las abreviaturas en nombres de métodos y variables deben tener máximo 1 mayúscula consecutiva.

**❌ Incorrecto:**

```java
@Test
void documentE2ETestRequirements() { }  // E2E = 2 mayúsculas consecutivas

void parseHTMLContent() { }
void createXMLFile() { }
void validateAPIResponse() { }
```

**✅ Correcto:**

```java
@Test
void documentE2eTestRequirements() { }  // E2e = solo 1 mayúscula al inicio

void parseHtmlContent() { }
void createXmlFile() { }
void validateApiResponse() { }
```

**Abreviaturas comunes:**

- `E2E` → `E2e`
- `HTML` → `Html`
- `XML` → `Xml`
- `JSON` → `Json`
- `API` → `Api`
- `URL` → `Url`
- `HTTP` → `Http`
- `SQL` → `Sql`

#### 2. VariableDeclarationUsageDistance

Variables deben declararse cerca de su primer uso (máximo 3 líneas de distancia).

**❌ Incorrecto:**

```java
@Test
void testCreateSubscription() {
  // Given
  String userId = "123";           // ← Declarada aquí
  Long planId = 1L;
  Long courseId = 1L;

  // 10 líneas de setup de mocks...
  when(userService.findById(any())).thenReturn(user);
  when(planService.findById(any())).thenReturn(plan);
  when(courseRepository.findById(any())).thenReturn(course);
  when(subscriptionService.create(any())).thenReturn(subscription);
  when(paymentService.process(any())).thenReturn(payment);
  // ...más setup...

  // When
  service.createSubscription(userId, planId, courseId);  // ← Usada aquí (>3 líneas)
}
```

**✅ Correcto - Opción 1 (usar `final`):**

```java
@Test
void testCreateSubscription() {
  // Given
  final String userId = "123";     // ← Declarada como final
  final Long planId = 1L;
  final Long courseId = 1L;

  // Setup de mocks...
  when(userService.findById(any())).thenReturn(user);
  when(planService.findById(any())).thenReturn(plan);
  // ...

  // When
  service.createSubscription(userId, planId, courseId);  // ← OK, es final
}
```

**✅ Correcto - Opción 2 (mover cerca del uso):**

```java
@Test
void testCreateSubscription() {
  // Given - Solo setup de mocks
  when(userService.findById(any())).thenReturn(user);
  when(planService.findById(any())).thenReturn(plan);
  when(courseRepository.findById(any())).thenReturn(course);
  // ...

  // Declarar variables justo antes de usarlas
  String userId = "123";           // ← Cerca del uso
  Long planId = 1L;
  Long courseId = 1L;

  // When
  service.createSubscription(userId, planId, courseId);  // ← Usada inmediatamente
}
```

**Cuándo usar cada opción:**

- **Use `final`**: Cuando la variable se necesita al inicio para configurar mocks o es parte del contexto del test
- **Mueva la declaración**: Cuando la variable solo se usa en una sección específica del test

#### 3. Otras Reglas Importantes

**LineLength**: Máximo 100 caracteres por línea

```java
// ❌ Línea demasiado larga
when(subscriptionRepository.findByUserIdAndPlanIdAndStatusOrderByCreatedAtDesc(userId, planId, SubscriptionStatus.ACTIVE)).thenReturn(Optional.of(subscription));

// ✅ Dividir en múltiples líneas
when(subscriptionRepository.findByUserIdAndPlanIdAndStatusOrderByCreatedAtDesc(
    userId, planId, SubscriptionStatus.ACTIVE))
    .thenReturn(Optional.of(subscription));
```

**NeedBraces**: Siempre usar llaves en bloques if/else/for/while

```java
// ❌ Sin llaves
if (user == null) return;

// ✅ Con llaves
if (user == null) {
  return;
}
```

## Convenciones de Nombres

### Nombres de Clases de Test

```java
// ✅ Correcto
UserServiceImplTest.java         // Para implementaciones
CourseServiceTest.java            // Para servicios
AdminControllerTest.java          // Para controladores
LayeredArchitectureTest.java     // Para tests de arquitectura
```

### Nombres de Métodos de Test

Usar convención `when<Condition>_then<Expected>` o `given<Context>_when<Action>_then<Expected>`:

```java
@Test
@DisplayName("whenFindByIdWithExistingId_thenReturnsUser")
void whenFindByIdWithExistingId_thenReturnsUser() { }

@Test
@DisplayName("whenFindByIdWithNonExistingId_thenReturnsEmpty")
void whenFindByIdWithNonExistingId_thenReturnsEmpty() { }

@Test
@DisplayName("whenSaveUserWithNullEmail_thenThrowsException")
void whenSaveUserWithNullEmail_thenThrowsException() { }
```

### DisplayName

Siempre incluir `@DisplayName` con descripción clara:

```java
// ✅ Correcto - describe el escenario y resultado esperado
@DisplayName("whenCreateSubscriptionPreferenceWithValidData_thenReturnsInitPoint")

// ❌ Evitar - nombre poco descriptivo
@DisplayName("test1")
```

## Estructura de Tests

### Patrón Given-When-Then

Todos los tests deben seguir el patrón Given-When-Then:

```java
@Test
@DisplayName("whenFindByIdWithExistingId_thenReturnsUser")
void whenFindByIdWithExistingId_thenReturnsUser() {
  // Given - Preparación
  Long userId = 1L;
  User expectedUser = new User();
  expectedUser.setId(userId);
  expectedUser.setEmail("user@example.com");

  when(userRepository.findById(userId)).thenReturn(Optional.of(expectedUser));

  // When - Acción
  Optional<User> result = userService.findById(userId);

  // Then - Verificación
  assertTrue(result.isPresent());
  assertEquals(userId, result.get().getId());
  assertEquals("user@example.com", result.get().getEmail());
  verify(userRepository, times(1)).findById(userId);
}
```

### Organización de Mocks

Agrupar configuración de mocks de forma lógica:

```java
@Test
void testComplexScenario() {
  // Given - Domain objects
  User user = createTestUser();
  Plan plan = createTestPlan();

  // Given - Repository mocks
  when(userRepository.findById(anyLong())).thenReturn(Optional.of(user));
  when(planRepository.findById(anyLong())).thenReturn(Optional.of(plan));

  // Given - Service mocks
  when(emailService.send(anyString())).thenReturn(true);
  when(paymentService.process(any())).thenReturn(payment);

  // When
  Result result = subscriptionService.createSubscription(userId, planId);

  // Then
  assertNotNull(result);
  verify(emailService).send(anyString());
}
```

### Tests Negativos

Siempre incluir tests para casos de error:

```java
@Test
@DisplayName("whenFindByIdWithNonExistingId_thenReturnsEmpty")
void whenFindByIdWithNonExistingId_thenReturnsEmpty() {
  // Given
  Long nonExistingId = 999L;
  when(userRepository.findById(nonExistingId)).thenReturn(Optional.empty());

  // When
  Optional<User> result = userService.findById(nonExistingId);

  // Then
  assertFalse(result.isPresent());
}

@Test
@DisplayName("whenSaveUserWithNullEmail_thenThrowsException")
void whenSaveUserWithNullEmail_thenThrowsException() {
  // Given
  UserDto userDto = new UserDto();
  userDto.setEmail(null);

  // When & Then
  assertThrows(IllegalArgumentException.class, () -> {
    userService.saveUser(userDto);
  });
}
```

## Validación de Checkstyle en Tests

### Comando de Validación

```bash
# Validar solo tests
./gradlew checkstyleTest

# Ver reporte
open build/reports/checkstyle/test.html
```

### Errores Comunes y Soluciones

**Error: "Abreviatura en nombre debe contener no más de 1 mayúsculas"**

```bash
# Error
warning: Abreviatura en nombre 'documentE2ETestRequirements' debe contener no más de '1' mayúsculas.

# Solución: Cambiar E2E → E2e
void documentE2eTestRequirements() { }
```

**Error: "La distancia entre la declaración de la variable y su primer uso es X"**

```bash
# Error
warning: La distancia entre la declaración de la variable 'userId' y su primer uso es 10,
pero el máximo permitido es 3.

# Solución: Agregar final
final String userId = "123";
```

## Automatización con Pre-Commit Hooks

Para evitar olvidos y asegurar que el código siempre cumple con los estándares del proyecto antes de confirmar un commit, el repositorio incluye soporte para el framework **`pre-commit`**.

Este hook automatiza la ejecución de Checkstyle y las pruebas unitarias del proyecto (`./gradlew checkstyleMain test`) en cada confirmación de Git.

### Requisitos e Instalación

Para activar el hook en tu entorno local, sigue estos pasos:

1. **Instalar el framework `pre-commit`** en tu máquina (se requiere una única vez):
   - **macOS** (vía Homebrew):

     ```bash
     brew install pre-commit
     ```

   - **Cualquier SO** (vía Pip/Python):

     ```bash
     pip install pre-commit
     ```

2. **Instalar el hook de Git** en el repositorio (posicionado en la raíz del proyecto):

   ```bash
   pre-commit install
   ```

### Uso y Funcionamiento

- **Ejecución automática**: Cada vez que ejecutes `git commit`, el hook validará el estilo y correrá los tests. Si falla alguno, el commit será cancelado automáticamente para evitar subir código incorrecto.
- **Omitir validaciones temporales**: Si por algún motivo de urgencia necesitas forzar un commit omitiendo este paso, añade el parámetro `--no-verify`:

  ```bash
  git commit -m "Mensaje rápido" --no-verify
  ```

## Checklist Pre-Commit

Antes de hacer commit, verificar:

- [ ] ✅ Todos los tests pasan: `./gradlew test`
- [ ] ✅ Checkstyle pasa: `./gradlew checkstyleTest`
- [ ] ✅ Cobertura adecuada: `./gradlew jacocoTestReport`
- [ ] ✅ Tests de arquitectura pasan: `./gradlew test --tests "*.architecture.*"`
- [ ] ✅ Nombres de métodos siguen convención `when*_then*`
- [ ] ✅ Todos los métodos tienen `@DisplayName`
- [ ] ✅ Tests usan patrón Given-When-Then
- [ ] ✅ Variables declaradas cerca del uso o marcadas como `final`
- [ ] ✅ Abreviaturas usan formato correcto (E2e, Html, Api, etc.)

## Referencias

- **JUnit 5**: <https://junit.org/junit5/docs/current/user-guide/>
- **Mockito**: <https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html>
- **ArchUnit**: <https://www.archunit.org/userguide/html/000_Index.html>
- **JaCoCo**: <https://www.jacoco.org/jacoco/trunk/doc/>
- **Checkstyle**: <https://checkstyle.sourceforge.io/>
