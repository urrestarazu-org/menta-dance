# Guía de Entorno de Desarrollo Local

[← Volver al índice](./README.md)

---

## Requisitos Previos

- **Java 21** (recomendado: SDKMAN o asdf)
- **Docker** o **Podman**
- **IDE**: IntelliJ IDEA o VS Code

---

## Estructura del Proyecto

```
menta-dance/
├── api/                  # Backend (Gradle multi-módulo)
│   ├── shared/
│   ├── auth/
│   ├── virtual/
│   ├── physical/
│   ├── billing/
│   └── app/
├── bff/                  # Frontend web
├── android/              # App móvil
├── build.gradle.kts
└── settings.gradle.kts
```

---

## 1. Levantar Infraestructura

### Base de datos MySQL

```bash
# Crear y levantar MySQL
docker run -d \
  --name menta-mysql \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=menta \
  -p 3306:3306 \
  mysql:8.0

# Verificar
docker logs menta-mysql
```

### Con Docker Compose (recomendado)

Crear `docker-compose.yml` en la raíz:

```yaml
version: '3.8'

services:
  mysql:
    image: mysql:8.0
    container_name: menta-mysql
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: menta
    ports:
      - "3306:3306"
    volumes:
      - menta-mysql-data:/var/lib/mysql

volumes:
  menta-mysql-data:
```

```bash
docker-compose up -d
```

---

## 2. Levantar la API

```bash
# Desde la raíz del proyecto
./gradlew :api:app:bootRun

# O con profile específico
./gradlew :api:app:bootRun --args='--spring.profiles.active=local'
```

La API estará disponible en: `http://localhost:8081`

### Health check

```bash
curl http://localhost:8081/actuator/health
```

---

## 3. Levantar el BFF

```bash
./gradlew :bff:bootRun
```

El BFF estará disponible en: `http://localhost:8080`

---

## 4. Comandos Gradle Útiles

```bash
# Compilar todo
./gradlew build

# Compilar sin tests
./gradlew build -x test

# Solo tests
./gradlew test

# Tests de un módulo específico
./gradlew :api:auth:test

# Un test específico
./gradlew :api:auth:test --tests "*.UserServiceTest"

# Limpiar
./gradlew clean

# Ver dependencias de un módulo
./gradlew :api:auth:dependencies
```

---

## 5. Configuración de Profiles

### application.yml (api/app)

```yaml
spring:
  profiles:
    active: local

---
spring:
  config:
    activate:
      on-profile: local
  datasource:
    url: jdbc:mysql://localhost:3306/menta
    username: root
    password: root
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
```

---

## 6. IDE Setup

### IntelliJ IDEA

1. Abrir la carpeta `menta-dance/` como proyecto
2. Esperar a que Gradle sincronice
3. Marcar `api/*/src/main/java` como Sources Root
4. Configurar JDK 21

### VS Code

Extensiones recomendadas:
- Extension Pack for Java
- Spring Boot Extension Pack
- Gradle for Java

---

## 7. Puertos

| Servicio | Puerto |
|----------|--------|
| API | 8081 |
| BFF | 8080 |
| MySQL | 3306 |

---

## 8. Troubleshooting

### Puerto en uso

```bash
# Ver qué usa el puerto
lsof -i :8081

# Matar proceso
kill -9 <PID>
```

### MySQL no conecta

```bash
# Verificar que el container esté corriendo
docker ps

# Ver logs
docker logs menta-mysql

# Reiniciar
docker restart menta-mysql
```

### Gradle cache corrupto

```bash
./gradlew clean build --refresh-dependencies
```

---

## Referencias

- [02-ARCHITECTURE.md](./02-ARCHITECTURE.md)
- [27-CLEAN-ARCHITECTURE-GUIDE.md](./27-CLEAN-ARCHITECTURE-GUIDE.md)
