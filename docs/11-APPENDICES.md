# Apéndices

[← Volver al índice](./README.md) | [← ADRs](./10-ADRS.md)

---

## A. Glosario de Términos

### Arquitectura

| Término | Definición |
|---------|------------|
| **API** | Application Programming Interface. Interfaz que permite la comunicación entre sistemas. |
| **BFF** | Backend For Frontend. Capa intermedia que adapta y agrega datos de múltiples APIs para un cliente específico. |
| **Microservicio** | Servicio pequeño e independiente que implementa una funcionalidad de negocio específica. |
| **Monolito** | Aplicación donde todos los componentes están en un único proceso desplegable. |
| **Strangler Fig** | Patrón de migración que permite reemplazar gradualmente un sistema legacy. |
| **Event-Driven** | Arquitectura donde los componentes se comunican mediante eventos asíncronos. |
| **CQRS** | Command Query Responsibility Segregation. Separación de operaciones de lectura y escritura. |
| **Saga** | Patrón para manejar transacciones distribuidas mediante secuencia de transacciones locales. |

### Seguridad

| Término | Definición |
|---------|------------|
| **JWT** | JSON Web Token. Token firmado para autenticación stateless. |
| **OAuth 2.0** | Protocolo de autorización estándar para acceso delegado. |
| **CORS** | Cross-Origin Resource Sharing. Mecanismo para permitir requests entre dominios. |
| **CSP** | Content Security Policy. Header HTTP que controla qué recursos puede cargar una página. |
| **mTLS** | Mutual TLS. Autenticación bidireccional con certificados. |
| **HashId** | ID ofuscado para ocultar IDs secuenciales en URLs públicas. |
| **Rate Limiting** | Limitación del número de requests permitidas en un período de tiempo. |

### Infraestructura

| Término | Definición |
|---------|------------|
| **CDN** | Content Delivery Network. Red de servidores que distribuye contenido estático. |
| **Redis** | Base de datos en memoria usada para caché, sesiones y colas. |
| **RabbitMQ** | Message broker que implementa AMQP para mensajería asíncrona. |
| **Docker** | Plataforma de contenedores para empaquetar y ejecutar aplicaciones. |
| **NGINX** | Servidor web y reverse proxy de alto rendimiento. |
| **VPS** | Virtual Private Server. Servidor virtual alojado en la nube. |

### Negocio Menta

| Término | Definición |
|---------|------------|
| **Academia Virtual** | Línea de negocio de cursos online con videos streaming. |
| **Academia Física** | Línea de negocio de cursos presenciales con control de acceso. |
| **Alumno** | Usuario que consume contenido (puede ser virtual, presencial o ambos). |
| **Profesor** | Usuario que crea contenido y/o da clases (virtual o presencial). |
| **Check-in** | Registro de acceso físico a la academia mediante QR o manual. |
| **Suscripción** | Acceso temporal a contenido mediante pago recurrente. |
| **Plan** | Configuración de precio, duración y contenido accesible. |
| **Combo** | Plan que incluye acceso a ambas academias con descuento. |
| **Curso presencial** | Oferta recurrente de un profesor, con día, horario y capacidad definidos. |
| **Sesión** | Ocurrencia concreta y fechada de un curso presencial. |
| **Mensualidad** | Precio fijo que cubre las sesiones programadas dentro de un período mensual. |
| **Costo efectivo por sesión** | Mensualidad dividida por la cantidad de sesiones programadas del período. |
| **Pago individual** | Compra de una sesión concreta con recargo positivo sobre el costo efectivo mensual. |
| **Hold técnico** | Retención interna, breve y expirable de capacidad para prevenir overselling; no es una reserva del alumno. |
| **Asignación de cupo** | Derecho confirmado, creado automáticamente tras el pago, para asistir a una sesión. |

---

## B. Referencias Técnicas

### Frameworks y Librerías

| Tecnología | Versión | Documentación |
|------------|---------|---------------|
| Spring Boot | 3.x | <https://docs.spring.io/spring-boot/docs/current/reference/html/> |
| Spring Security | 6.x | <https://docs.spring.io/spring-security/reference/> |
| Spring Data JPA | 3.x | <https://docs.spring.io/spring-data/jpa/docs/current/reference/html/> |
| Thymeleaf | 3.x | <https://www.thymeleaf.org/documentation.html> |
| Tailwind CSS | 3.x | <https://tailwindcss.com/docs> |
| Alpine.js | 3.x | <https://alpinejs.dev/start-here> |
| Hashids Java | 1.x | <https://hashids.org/java/> |
| Resilience4j | 2.x | <https://resilience4j.readme.io/docs> |

### Servicios Externos

| Servicio | Propósito | Documentación |
|----------|-----------|---------------|
| Mercado Pago | Procesamiento de pagos | <https://www.mercadopago.com.ar/developers/es> |
| Bunny.net | CDN para videos | <https://docs.bunny.net/> |
| Google Calendar API | Sincronización de eventos | <https://developers.google.com/calendar> |
| Firebase FCM | Push notifications (Android) | <https://firebase.google.com/docs/cloud-messaging> |
| Let's Encrypt | Certificados SSL gratuitos | <https://letsencrypt.org/docs/> |

### Patrones y Guías

| Recurso | Descripción |
|---------|-------------|
| [REST API Design](https://restfulapi.net/) | Guía de diseño de APIs REST |
| [12 Factor App](https://12factor.net/) | Metodología para apps cloud-native |
| [OWASP Top 10](https://owasp.org/Top10/) | Top 10 vulnerabilidades web |
| [Microsoft API Guidelines](https://github.com/microsoft/api-guidelines) | Guías de diseño de APIs |
| [Microservices.io](https://microservices.io/patterns/) | Catálogo de patrones de microservicios |

---

## C. Checklist de Implementación

### Fase 0: Infraestructura MVP

```
□ VPS contratado y configurado
□ Docker y Docker Compose instalados
□ NGINX configurado como reverse proxy
□ SSL/TLS con Let's Encrypt
□ Dominio configurado con DNS
□ Firewall configurado (solo 80, 443, 22)
□ MySQL instalado con schemas separados
□ Backups automáticos configurados
□ Monitoreo externo (UptimeRobot) activo
```

### Fase 1: Auth API

```
□ Proyecto Spring Boot creado
□ Modelo de datos (User, Role, Token)
□ Migraciones Flyway ejecutadas
□ Endpoints de autenticación implementados
  □ POST /auth/register
  □ POST /auth/login
  □ POST /auth/refresh
  □ POST /auth/logout
  □ POST /auth/forgot-password
  □ POST /auth/reset-password
□ JWT implementado (access + refresh)
□ Rate limiting configurado
□ Tests unitarios (>80% cobertura)
□ Tests de integración
□ Documentación OpenAPI
□ Deploy a producción
```

### Fase 2: Billing API

```
□ Proyecto Spring Boot creado
□ Modelo de datos (Plan, Subscription, Payment)
□ Migraciones Flyway ejecutadas
□ Endpoints implementados
  □ CRUD Plans (admin)
  □ CRUD Subscriptions
  □ CRUD Payments
  □ Webhooks Mercado Pago
□ Integración con Mercado Pago
□ Sistema de auditoría de precios
□ Tests unitarios (>80% cobertura)
□ Tests de integración
□ Documentación OpenAPI
□ Deploy a producción
```

### Fase 3: Virtual API

```
□ Proyecto Spring Boot creado
□ Modelo de datos migrado del monolito
□ Migraciones Flyway ejecutadas
□ Endpoints implementados
  □ CRUD Courses
  □ CRUD Modules
  □ CRUD Lessons
  □ Video URLs firmadas
  □ Progress tracking
□ Integración con Bunny.net CDN
□ Control de acceso por suscripción
□ Tests unitarios (>80% cobertura)
□ Tests de integración
□ Documentación OpenAPI
□ Deploy a producción
```

### Fase 4: BFF Web

```
□ Proyecto Spring Boot + Thymeleaf
□ Templates migrados del monolito
□ Agregación de APIs configurada
□ Caché implementado
□ Manejo de sesiones
□ SEO y meta tags
□ Google Analytics integrado
□ Tests de integración
□ Deploy a producción
□ Redirect del dominio principal
```

### Fase 5: Physical API

```
□ Proyecto Spring Boot creado
□ Modelo de datos diseñado
□ Endpoints implementados
  □ Check-in (QR y manual)
  □ Gestión de horarios
  □ Control de asistencia
  □ Reportes
□ Autenticación de dispositivos QR
□ Integración Firebase FCM
□ Google Calendar sync
□ Tests unitarios (>80% cobertura)
□ Deploy a producción
```

### Fase 6: Android App

```
□ Proyecto Kotlin + Jetpack Compose
□ Autenticación implementada
□ QR Scanner funcional
□ Push notifications (FCM)
□ Offline mode básico
□ Tests unitarios
□ Build de release firmado
□ Publicación en Play Store (beta)
```

### Fase 7: Decomisión del Monolito

```
□ Todo el tráfico migrado a nuevas APIs
□ Monolito en modo solo lectura
□ Verificación de datos sincronizados
□ Respaldo final del monolito
□ Apagado del monolito
□ Limpieza de infraestructura antigua
□ Documentación actualizada
```

---

## D. Comandos Útiles

### Docker

```bash
# Ver logs de todos los servicios
docker-compose logs -f

# Ver logs de un servicio específico
docker-compose logs -f auth-api

# Reiniciar un servicio
docker-compose restart auth-api

# Ver uso de recursos
docker stats

# Limpiar imágenes no usadas
docker image prune -a

# Entrar a un contenedor
docker exec -it menta-auth-api /bin/sh

# Ver redes
docker network ls
```

### MySQL

```bash
# Conectar a MySQL
docker exec -it menta-mysql mysql -u root -p

# Backup de base de datos
docker exec menta-mysql mysqldump -u root -p menta_auth > backup_auth.sql

# Restaurar backup
docker exec -i menta-mysql mysql -u root -p menta_auth < backup_auth.sql

# Ver tamaño de tablas
SELECT table_name, ROUND((data_length + index_length) / 1024 / 1024, 2) AS "Size (MB)"
FROM information_schema.tables
WHERE table_schema = 'menta_auth'
ORDER BY (data_length + index_length) DESC;
```

### Monitoreo

```bash
# Ver procesos Java
ps aux | grep java

# Ver uso de memoria de Java
jcmd <PID> GC.heap_info

# Ver threads de Java
jcmd <PID> Thread.print

# Health check
curl -s http://localhost:8081/actuator/health | jq

# Métricas
curl -s http://localhost:8081/actuator/metrics | jq
```

### SSL/Let's Encrypt

```bash
# Obtener certificado nuevo
certbot certonly --nginx -d mentavirtual.com -d www.mentavirtual.com

# Renovar certificados
certbot renew

# Verificar certificado
openssl s_client -connect mentavirtual.com:443 -servername mentavirtual.com
```

---

## E. Troubleshooting

### Problemas Comunes

| Problema | Causa Probable | Solución |
|----------|---------------|----------|
| 502 Bad Gateway | Servicio no responde | Verificar logs del servicio, reiniciar |
| Connection refused | Puerto no expuesto | Verificar docker-compose ports |
| JWT invalid | Token expirado o mal formado | Verificar refresh token, re-login |
| CORS error | Origen no permitido | Agregar origen a configuración CORS |
| DB connection timeout | Pool agotado | Aumentar pool size, verificar conexiones |
| OutOfMemoryError | Heap muy pequeño | Aumentar -Xmx en JAVA_OPTS |
| SSL certificate expired | Renovación falló | Ejecutar certbot renew manualmente |

### Logs a Revisar

```bash
# Error de aplicación
grep -i "error\|exception" /var/log/menta/*.log

# Errores de NGINX
tail -f /var/log/nginx/error.log

# Errores de MySQL
docker logs menta-mysql 2>&1 | grep -i error

# Conexiones rechazadas
grep "Connection refused" /var/log/menta/*.log
```

---

## F. Contactos y Escalamiento

### Responsables

| Área | Responsable | Contacto |
|------|-------------|----------|
| **Arquitectura** | Desarrollador principal | Decisiones de diseño |
| **Backend** | Desarrollador principal | APIs y servicios |
| **Frontend** | Desarrollador principal | BFF Web, Thymeleaf |
| **DevOps** | Desarrollador principal | Docker, CI/CD, infra |
| **Android** | Desarrollador principal | App Kotlin (en aprendizaje) |

**Nota:** Proyecto con equipo de 1 persona. Todos los roles consolidados en el desarrollador principal.

### Proveedores

| Servicio | Proveedor | Soporte |
|----------|-----------|---------|
| **Hosting** | Donweb | <https://donweb.com/soporte> |
| **Pagos** | Mercado Pago | <https://www.mercadopago.com.ar/developers/es/support> |
| **CDN** | Bunny.net | <https://support.bunny.net/> |
| **Dominio** | mentavirtual.com (configurado) | Según registrador |

---

## G. Changelog del Documento

| Versión | Fecha | Cambios |
|---------|-------|---------|
| 2.1 | 2026-05-17 | Agregado Billing API como 4to microservicio |
| 2.0 | 2026-04-26 | Arquitectura MVP, BFF Web, Event-Driven documentado |
| 1.1 | 2026-04-26 | Contexto Academia Física + Virtual |
| 1.0 | 2026-04-25 | Versión inicial del análisis |

---

## H. Licencias de Software

| Software | Licencia | Uso Comercial |
|----------|----------|---------------|
| Spring Boot | Apache 2.0 | ✅ Permitido |
| Thymeleaf | Apache 2.0 | ✅ Permitido |
| Tailwind CSS | MIT | ✅ Permitido |
| Alpine.js | MIT | ✅ Permitido |
| MySQL | GPL v2 | ✅ Permitido (como servicio) |
| Redis | BSD-3 | ✅ Permitido |
| RabbitMQ | MPL 2.0 | ✅ Permitido |
| NGINX | BSD-2 | ✅ Permitido |
| Docker | Apache 2.0 | ✅ Permitido |

---

[← Volver al índice](./README.md)
