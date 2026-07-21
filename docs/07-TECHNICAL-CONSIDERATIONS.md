# Consideraciones Técnicas

[← Volver al índice](./README.md)

> [!CAUTION]
> **HISTÓRICO — PENDIENTE DE ALINEACIÓN. NO IMPLEMENTAR.** Este documento
> contiene configuraciones para microservicios distribuidos (RabbitMQ, múltiples
> instancias Redis, salts por servicio) que **no aplican** a la arquitectura
> vigente. La autoridad está en [02-ARCHITECTURE.md](./02-ARCHITECTURE.md) y
> [ADR-0020](./adr/0020-modular-monolith.md). Revisar sección por sección antes
> de usar cualquier configuración.

---

## 1. Redis: Caché y Sesiones

### 1.1 Arquitectura de Caché

```
┌─────────────────────────────────────────────────────────────────┐
│                         REDIS CLUSTER                           │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐             │
│  │   Cache     │  │   Sessions  │  │   Tokens    │             │
│  │   DB: 0     │  │   DB: 1     │  │   DB: 2     │             │
│  └─────────────┘  └─────────────┘  └─────────────┘             │
│                                                                 │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐             │
│  │ Rate Limit  │  │   Locks     │  │  Pub/Sub    │             │
│  │   DB: 3     │  │   DB: 4     │  │   DB: 5     │             │
│  └─────────────┘  └─────────────┘  └─────────────┘             │
└─────────────────────────────────────────────────────────────────┘
```

### 1.2 Estrategias de Caché por Servicio

> [!NOTE]
> **Simplificado para monolito.** En la arquitectura vigente existe un único
> JAR API; las estrategias de caché se configuran a nivel de módulo pero
> comparten la misma instancia Redis. La tabla siguiente usa nomenclatura
> histórica de servicios separados.

| Servicio | Datos en Caché | TTL | Estrategia |
|----------|----------------|-----|------------|
| **Auth API** | User sessions, refresh tokens | 7 días | Write-through |
| **Virtual API** | Catálogo cursos, planes | 1 hora | Cache-aside |
| **Physical API** | Horarios, asistencias del día | 15 min | Cache-aside |
| **Billing API** | Estados de suscripción | 5 min | Write-through |
| **BFF** | Respuestas agregadas | 2 min | Cache-aside |

### 1.3 Configuración Spring Boot

```yaml
# application-redis.yml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      timeout: 2000ms
      lettuce:
        pool:
          max-active: 10
          max-idle: 5
          min-idle: 2
          max-wait: 1000ms

  cache:
    type: redis
    redis:
      time-to-live: 3600000  # 1 hora default
      cache-null-values: false
      key-prefix: "menta:"
```

### 1.4 Implementación Cache-Aside

```java
@Service
public class CourseServiceImpl implements CourseService {

    private final CourseRepository repository;
    private final CacheManager cacheManager;

    @Override
    @Cacheable(value = "courses", key = "#hashId", unless = "#result == null")
    public CourseDTO findByHashId(String hashId) {
        Long id = hashidsService.decode(hashId);
        return repository.findById(id)
            .map(courseMapper::toDTO)
            .orElse(null);
    }

    @Override
    @CacheEvict(value = "courses", key = "#hashId")
    public CourseDTO update(String hashId, UpdateCourseRequest request) {
        // Actualizar curso
    }

    @Override
    @CacheEvict(value = "courses", allEntries = true)
    public void refreshCatalog() {
        // Forzar refresh del catálogo completo
    }
}
```

### 1.5 Rate Limiting con Redis

```java
@Component
public class RateLimiter {

    private final RedisTemplate<String, String> redisTemplate;

    /**
     * Verifica si la request está dentro del límite permitido.
     * Algoritmo: Sliding Window con Redis ZADD.
     */
    public boolean isAllowed(String key, int maxRequests, Duration window) {
        String redisKey = "rate:" + key;
        long now = System.currentTimeMillis();
        long windowStart = now - window.toMillis();

        // Limpiar requests fuera de la ventana
        redisTemplate.opsForZSet().removeRangeByScore(redisKey, 0, windowStart);

        // Contar requests en la ventana actual
        Long count = redisTemplate.opsForZSet().zCard(redisKey);

        if (count != null && count >= maxRequests) {
            return false;
        }

        // Agregar request actual
        redisTemplate.opsForZSet().add(redisKey, UUID.randomUUID().toString(), now);
        redisTemplate.expire(redisKey, window);

        return true;
    }
}
```

### 1.6 Configuración de Rate Limits

| Endpoint | Límite | Ventana | Key |
|----------|--------|---------|-----|
| `POST /auth/login` | 5 intentos | 15 min | IP + email |
| `POST /auth/register` | 3 intentos | 1 hora | IP |
| `POST /auth/forgot-password` | 3 intentos | 1 hora | email |
| `POST /payments/*` | 10 requests | 1 min | userId |
| `GET /courses/*` | 100 requests | 1 min | IP |
| `POST /checkin/*` | 30 requests | 1 min | deviceId |

---

## 2. RabbitMQ: Mensajería Asíncrona

> [!WARNING]
> **NO APLICA.** La arquitectura vigente (ADR-0020) usa un monolito modular donde
> los módulos se comunican mediante interfaces Java. RabbitMQ no se usa para
> comunicación interna. Esta sección se conserva como referencia histórica.

### 2.1 Arquitectura de Exchanges y Queues

```
┌─────────────────────────────────────────────────────────────────────┐
│                          RABBITMQ                                   │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                    EXCHANGES                                │   │
│  │  ┌───────────────┐  ┌───────────────┐  ┌───────────────┐   │   │
│  │  │ menta.auth    │  │ menta.billing │  │ menta.events  │   │   │
│  │  │   (topic)     │  │   (topic)     │  │   (fanout)    │   │   │
│  │  └───────┬───────┘  └───────┬───────┘  └───────┬───────┘   │   │
│  └──────────┼──────────────────┼──────────────────┼───────────┘   │
│             │                  │                  │               │
│  ┌──────────┼──────────────────┼──────────────────┼───────────┐   │
│  │          ▼                  ▼                  ▼           │   │
│  │  ┌───────────────┐  ┌───────────────┐  ┌───────────────┐   │   │
│  │  │ auth.user.*   │  │billing.payment│  │ events.all    │   │   │
│  │  │               │  │.completed     │  │               │   │   │
│  │  └───────────────┘  └───────────────┘  └───────────────┘   │   │
│  │                    QUEUES                                   │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 2.2 Definición de Eventos

```java
// Eventos de Auth
public sealed interface AuthEvent permits
    UserRegistered, UserActivated, UserDeactivated, PasswordChanged {

    String eventId();
    Instant timestamp();
    Long userId();
}

public record UserRegistered(
    String eventId,
    Instant timestamp,
    Long userId,
    String email,
    String fullName
) implements AuthEvent {}

// Eventos de Billing
public sealed interface BillingEvent permits
    PaymentCompleted, PaymentFailed, SubscriptionActivated, SubscriptionExpired {

    String eventId();
    Instant timestamp();
    Long userId();
}

public record PaymentCompleted(
    String eventId,
    Instant timestamp,
    Long userId,
    Long subscriptionId,
    BigDecimal amount,
    String paymentMethod
) implements BillingEvent {}

// Eventos de Physical
public sealed interface PhysicalEvent permits
    CheckInCompleted, AttendanceRecorded, ClassStarted {

    String eventId();
    Instant timestamp();
}

public record CheckInCompleted(
    String eventId,
    Instant timestamp,
    Long userId,
    Long locationId,
    CheckInMethod method
) implements PhysicalEvent {}
```

### 2.3 Configuración Spring AMQP

```yaml
# application-rabbitmq.yml
spring:
  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USER:menta}
    password: ${RABBITMQ_PASS:menta_secret}
    virtual-host: /menta

    listener:
      simple:
        acknowledge-mode: manual
        prefetch: 10
        retry:
          enabled: true
          initial-interval: 1000ms
          max-attempts: 3
          max-interval: 10000ms
          multiplier: 2.0
```

### 2.4 Configuración de Exchanges y Queues

```java
@Configuration
public class RabbitMQConfig {

    // Exchanges
    @Bean
    public TopicExchange authExchange() {
        return ExchangeBuilder
            .topicExchange("menta.auth")
            .durable(true)
            .build();
    }

    @Bean
    public TopicExchange billingExchange() {
        return ExchangeBuilder
            .topicExchange("menta.billing")
            .durable(true)
            .build();
    }

    @Bean
    public FanoutExchange eventsExchange() {
        return ExchangeBuilder
            .fanoutExchange("menta.events")
            .durable(true)
            .build();
    }

    // Queues
    @Bean
    public Queue userRegisteredQueue() {
        return QueueBuilder
            .durable("auth.user.registered")
            .withArgument("x-dead-letter-exchange", "menta.dlx")
            .withArgument("x-dead-letter-routing-key", "dlq.auth.user.registered")
            .build();
    }

    @Bean
    public Queue paymentCompletedQueue() {
        return QueueBuilder
            .durable("billing.payment.completed")
            .withArgument("x-dead-letter-exchange", "menta.dlx")
            .withArgument("x-dead-letter-routing-key", "dlq.billing.payment")
            .build();
    }

    // Bindings
    @Bean
    public Binding userRegisteredBinding(Queue userRegisteredQueue,
                                          TopicExchange authExchange) {
        return BindingBuilder
            .bind(userRegisteredQueue)
            .to(authExchange)
            .with("user.registered");
    }
}
```

### 2.5 Publicación de Eventos

```java
@Service
@RequiredArgsConstructor
public class EventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public void publishUserRegistered(User user) {
        UserRegistered event = new UserRegistered(
            UUID.randomUUID().toString(),
            Instant.now(),
            user.getId(),
            user.getEmail(),
            user.getFullName()
        );

        rabbitTemplate.convertAndSend(
            "menta.auth",
            "user.registered",
            event,
            message -> {
                message.getMessageProperties().setContentType("application/json");
                message.getMessageProperties().setMessageId(event.eventId());
                message.getMessageProperties().setTimestamp(Date.from(event.timestamp()));
                return message;
            }
        );

        log.info("Evento publicado: UserRegistered userId={}", user.getId());
    }
}
```

### 2.6 Consumo de Eventos

```java
@Component
@RequiredArgsConstructor
public class BillingEventConsumer {

    private final NotificationService notificationService;
    private final SubscriptionService subscriptionService;

    @RabbitListener(queues = "billing.payment.completed")
    public void handlePaymentCompleted(PaymentCompleted event,
                                       Channel channel,
                                       @Header(AmqpHeaders.DELIVERY_TAG) long tag) {
        try {
            log.info("Procesando PaymentCompleted: {}", event.eventId());

            // Activar suscripción
            subscriptionService.activate(event.subscriptionId());

            // Enviar notificación
            notificationService.sendPaymentConfirmation(event.userId(), event.amount());

            // ACK manual
            channel.basicAck(tag, false);

        } catch (Exception e) {
            log.error("Error procesando PaymentCompleted: {}", event.eventId(), e);
            // NACK con requeue (irá a DLQ después de max-attempts)
            channel.basicNack(tag, false, true);
        }
    }
}
```

### 2.7 Dead Letter Queue (DLQ)

```java
@Configuration
public class DLQConfig {

    @Bean
    public DirectExchange deadLetterExchange() {
        return ExchangeBuilder
            .directExchange("menta.dlx")
            .durable(true)
            .build();
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder
            .durable("menta.dlq")
            .withArgument("x-message-ttl", 604800000L) // 7 días
            .build();
    }

    // Consumer para procesar/alertar mensajes en DLQ
    @RabbitListener(queues = "menta.dlq")
    public void handleDeadLetter(Message message) {
        String originalQueue = message.getMessageProperties()
            .getHeader("x-first-death-queue");
        String reason = message.getMessageProperties()
            .getHeader("x-first-death-reason");

        log.error("Mensaje en DLQ - Queue: {}, Reason: {}, Body: {}",
            originalQueue, reason, new String(message.getBody()));

        // Alertar a admin
        alertService.sendDLQAlert(originalQueue, reason, message);
    }
}
```

---

## 3. Hashids: Ofuscación de IDs

### 3.1 Configuración

```java
@Configuration
public class HashidsConfig {

    @Value("${app.security.hashids.salt}")
    private String salt;

    @Value("${app.security.hashids.min-length:8}")
    private int minLength;

    @Bean
    public Hashids hashids() {
        return new Hashids(salt, minLength, "abcdefghijklmnopqrstuvwxyz1234567890");
    }
}
```

### 3.2 Servicio Centralizado

```java
@Service
@RequiredArgsConstructor
public class HashidsService {

    private final Hashids hashids;

    /**
     * Codifica un ID numérico a hashId.
     */
    public String encode(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("ID debe ser positivo");
        }
        return hashids.encode(id);
    }

    /**
     * Decodifica un hashId a ID numérico.
     */
    public Long decode(String hashId) {
        if (hashId == null || hashId.isBlank()) {
            throw new InvalidHashIdException("HashId no puede ser vacío");
        }

        long[] decoded = hashids.decode(hashId);
        if (decoded.length == 0) {
            throw new InvalidHashIdException("HashId inválido: " + hashId);
        }

        return decoded[0];
    }

    /**
     * Intenta decodificar, retorna Optional.empty si falla.
     */
    public Optional<Long> tryDecode(String hashId) {
        try {
            return Optional.of(decode(hashId));
        } catch (InvalidHashIdException e) {
            return Optional.empty();
        }
    }
}
```

### 3.3 Uso por Dominio

> [!NOTE]
> **Simplificado para monolito.** En la arquitectura vigente (ADR-0020) se usa
> un único salt compartido (`HASHIDS_SALT`) para todo el JAR API. La tabla
> siguiente es referencia histórica.

| Dominio | Entidades con HashId | Salt (diferente por servicio) |
|---------|---------------------|------------------------------|
| **Auth** | User | `${AUTH_HASHIDS_SALT}` |
| **Virtual** | Course, Module, Lesson, Video | `${VIRTUAL_HASHIDS_SALT}` |
| **Physical** | PhysicalCourse, Schedule, Attendance | `${PHYSICAL_HASHIDS_SALT}` |
| **Billing** | Subscription, Payment, Plan | `${BILLING_HASHIDS_SALT}` |

### 3.4 Consideraciones de Seguridad

```
⚠️ IMPORTANTE:
- En monolito: usar un único HASHIDS_SALT compartido
- Nunca commitear salts en el repositorio
- Cambiar salt invalida TODOS los hashIds existentes
- Los hashIds NO son encriptación, solo ofuscación
- No usar hashIds para datos sensibles (usar UUID)
```

---

## 4. CORS: Configuración Cross-Origin

### 4.1 Arquitectura CORS

```
┌───────────────┐      ┌───────────────┐      ┌───────────────┐
│   Browser     │      │   NGINX       │      │   APIs        │
│               │      │  (Gateway)    │      │               │
│  Origin:      │─────▶│  CORS Headers │─────▶│  No CORS      │
│  example.com  │◀─────│  (Preflight)  │◀─────│  (Internal)   │
└───────────────┘      └───────────────┘      └───────────────┘
```

**Estrategia**: CORS se maneja en NGINX (gateway), no en cada microservicio.

### 4.2 Configuración NGINX

```nginx
# /etc/nginx/conf.d/cors.conf

# Definir orígenes permitidos
map $http_origin $cors_origin {
    default "";
    "https://mentavirtual.com" $http_origin;
    "https://www.mentavirtual.com" $http_origin;
    "https://admin.mentavirtual.com" $http_origin;
    "~^https://.*\.mentavirtual\.com$" $http_origin;
    # Desarrollo local
    "http://localhost:3000" $http_origin;
    "http://localhost:8080" $http_origin;
}

# Aplicar headers CORS
location /api/ {
    # Preflight OPTIONS
    if ($request_method = 'OPTIONS') {
        add_header 'Access-Control-Allow-Origin' $cors_origin always;
        add_header 'Access-Control-Allow-Methods' 'GET, POST, PUT, PATCH, DELETE, OPTIONS' always;
        add_header 'Access-Control-Allow-Headers' 'Authorization, Content-Type, X-Requested-With, X-Request-ID' always;
        add_header 'Access-Control-Allow-Credentials' 'true' always;
        add_header 'Access-Control-Max-Age' 86400 always;
        add_header 'Content-Type' 'text/plain charset=UTF-8';
        add_header 'Content-Length' 0;
        return 204;
    }

    # Actual request
    add_header 'Access-Control-Allow-Origin' $cors_origin always;
    add_header 'Access-Control-Allow-Credentials' 'true' always;
    add_header 'Access-Control-Expose-Headers' 'X-Request-ID, X-RateLimit-Remaining' always;

    proxy_pass http://backend;
}
```

### 4.3 Configuración Spring (Fallback)

```java
@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // Orígenes permitidos
        allowedOrigins.forEach(config::addAllowedOrigin);

        // Métodos permitidos
        config.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
        ));

        // Headers permitidos
        config.setAllowedHeaders(Arrays.asList(
            "Authorization",
            "Content-Type",
            "X-Requested-With",
            "X-Request-ID"
        ));

        // Permitir credenciales (cookies)
        config.setAllowCredentials(true);

        // Cache preflight por 24 horas
        config.setMaxAge(86400L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);

        return new CorsFilter(source);
    }
}
```

### 4.4 Matriz de Permisos CORS

| Origen | Endpoints Permitidos | Credenciales |
|--------|---------------------|--------------|
| `mentavirtual.com` | Todos | ✅ |
| `*.mentavirtual.com` | Todos | ✅ |
| `localhost:*` (dev) | Todos | ✅ |
| Android App | No aplica (no browser) | N/A |
| Otros | Ninguno | ❌ |

---

## 5. Seguridad: Headers y CSP

### 5.1 Security Headers

```nginx
# /etc/nginx/conf.d/security-headers.conf

# Content Security Policy
add_header Content-Security-Policy "
    default-src 'self';
    script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://www.googletagmanager.com;
    style-src 'self' 'unsafe-inline' https://fonts.googleapis.com;
    img-src 'self' data: https: blob:;
    font-src 'self' https://fonts.gstatic.com;
    connect-src 'self' https://api.mentavirtual.com https://www.google-analytics.com;
    frame-ancestors 'none';
    base-uri 'self';
    form-action 'self';
" always;

# Otros headers de seguridad
add_header X-Content-Type-Options "nosniff" always;
add_header X-Frame-Options "DENY" always;
add_header X-XSS-Protection "1; mode=block" always;
add_header Referrer-Policy "strict-origin-when-cross-origin" always;
add_header Permissions-Policy "geolocation=(), microphone=(), camera=()" always;

# HSTS (solo en producción con HTTPS)
add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
```

### 5.2 Configuración Spring Security

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .headers(headers -> headers
                .contentTypeOptions(Customizer.withDefaults())
                .frameOptions(frame -> frame.deny())
                .xssProtection(xss -> xss.headerValue(
                    XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
                .referrerPolicy(referrer -> referrer
                    .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .permissionsPolicy(permissions -> permissions
                    .policy("geolocation=(), microphone=(), camera=()"))
            )
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .ignoringRequestMatchers("/api/v1/webhooks/**") // Webhooks externos
            )
            .build();
    }
}
```

---

## 6. Observabilidad: Logging y Tracing

### 6.1 Structured Logging

```yaml
# application-logging.yml
logging:
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%X{traceId},%X{spanId}] %-5level [%thread] %logger{36} - %msg%n"
  level:
    root: INFO
    com.menta: DEBUG
    org.springframework.web: INFO
    org.hibernate.SQL: WARN
```

### 6.2 Correlation IDs

```java
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Request-ID";
    public static final String CORRELATION_ID_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        // Agregar a MDC para logs
        MDC.put(CORRELATION_ID_KEY, correlationId);
        MDC.put("traceId", correlationId.substring(0, 8));

        // Agregar a response
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
```

### 6.3 Métricas con Micrometer

```java
@Configuration
public class MetricsConfig {

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> commonTags() {
        return registry -> registry.config()
            .commonTags(
                "application", "menta-api",
                "environment", System.getenv().getOrDefault("ENV", "dev")
            );
    }
}

// Uso en servicios
@Service
public class PaymentServiceImpl implements PaymentService {

    private final MeterRegistry meterRegistry;
    private final Counter paymentCounter;
    private final Timer paymentTimer;

    public PaymentServiceImpl(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.paymentCounter = Counter.builder("payments.total")
            .description("Total de pagos procesados")
            .register(meterRegistry);
        this.paymentTimer = Timer.builder("payments.processing.time")
            .description("Tiempo de procesamiento de pagos")
            .register(meterRegistry);
    }

    @Override
    public PaymentResult processPayment(PaymentRequest request) {
        return paymentTimer.record(() -> {
            PaymentResult result = doProcessPayment(request);
            paymentCounter.increment();
            meterRegistry.counter("payments.status",
                "method", request.method().name(),
                "status", result.status().name()
            ).increment();
            return result;
        });
    }
}
```

### 6.4 Health Checks

```java
@Component
public class DatabaseHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;

    @Override
    public Health health() {
        try (Connection conn = dataSource.getConnection()) {
            if (conn.isValid(2)) {
                return Health.up()
                    .withDetail("database", "MySQL")
                    .withDetail("connection", "valid")
                    .build();
            }
        } catch (SQLException e) {
            return Health.down()
                .withException(e)
                .build();
        }
        return Health.down().build();
    }
}

@Component
public class RedisHealthIndicator implements HealthIndicator {

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public Health health() {
        try {
            String pong = redisTemplate.getConnectionFactory()
                .getConnection().ping();
            return Health.up()
                .withDetail("redis", "PONG".equals(pong) ? "connected" : "unknown")
                .build();
        } catch (Exception e) {
            return Health.down()
                .withException(e)
                .build();
        }
    }
}
```

---

## 7. Resiliencia: Circuit Breaker

### 7.1 Configuración Resilience4j

```yaml
# application-resilience.yml
resilience4j:
  circuitbreaker:
    configs:
      default:
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 30s
        permittedNumberOfCallsInHalfOpenState: 3
        slidingWindowType: COUNT_BASED

    instances:
      mercadopago:
        baseConfig: default
        failureRateThreshold: 30
        waitDurationInOpenState: 60s

      bunnynet:
        baseConfig: default
        slowCallRateThreshold: 80
        slowCallDurationThreshold: 2s

  retry:
    configs:
      default:
        maxAttempts: 3
        waitDuration: 1s
        exponentialBackoffMultiplier: 2

    instances:
      mercadopago:
        baseConfig: default
        retryExceptions:
          - java.net.ConnectException
          - java.net.SocketTimeoutException
```

### 7.2 Implementación

```java
@Service
public class MercadoPagoServiceImpl implements MercadoPagoService {

    private final MercadoPagoClient client;

    @Override
    @CircuitBreaker(name = "mercadopago", fallbackMethod = "createPreferenceFallback")
    @Retry(name = "mercadopago")
    @TimeLimiter(name = "mercadopago")
    public CompletableFuture<PreferenceResponse> createPreference(PreferenceRequest request) {
        return CompletableFuture.supplyAsync(() ->
            client.createPreference(request)
        );
    }

    private CompletableFuture<PreferenceResponse> createPreferenceFallback(
            PreferenceRequest request, Throwable t) {
        log.error("Fallback para MercadoPago: {}", t.getMessage());

        // Retornar respuesta de error controlada
        return CompletableFuture.completedFuture(
            PreferenceResponse.unavailable(
                "El servicio de pagos no está disponible. Intente más tarde."
            )
        );
    }
}
```

---

## 8. Base de Datos: Multi-Schema

> [!NOTE]
> **Simplificado para monolito.** En la arquitectura vigente (ADR-0020) el JAR
> API usa un único DataSource con un usuario que accede a todos los schemas.
> La separación de schemas sigue vigente para ownership lógico, pero no hay
> múltiples DataSources ni usuarios de BD por módulo. Ver
> [22-DATA-MODEL.md](./22-DATA-MODEL.md) para el modelo actual.

### 8.1 Arquitectura de Schemas

```
┌─────────────────────────────────────────────────────────────────┐
│                      MySQL 8.0 Server                           │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐             │
│  │ menta_auth  │  │menta_virtual│  │menta_physical│            │
│  │             │  │             │  │              │            │
│  │ - users     │  │ - courses   │  │ - schedules  │            │
│  │ - roles     │  │ - modules   │  │ - attendance │            │
│  │ - tokens    │  │ - lessons   │  │ - locations  │            │
│  └─────────────┘  └─────────────┘  └──────────────┘            │
│                                                                 │
│  ┌─────────────┐                                               │
│  │menta_billing│                                               │
│  │             │                                               │
│  │ - plans     │                                               │
│  │ - subscriptions                                             │
│  │ - payments  │                                               │
│  └─────────────┘                                               │
└─────────────────────────────────────────────────────────────────┘
```

### 8.2 Configuración de DataSource (Por Microservicio)

Cada microservicio se conecta **únicamente** a su propio schema. No hay configuración Multi-DataSource en el código, simplemente se define la URL correcta en el `application.yml` de cada API.

```yaml
# application.yml en Auth API
spring:
  datasource:
    url: jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/menta_auth
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: validate
```

```yaml
# application.yml en Virtual API
spring:
  datasource:
    url: jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/menta_virtual
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
```

### 8.3 Migraciones con Flyway

```
src/main/resources/db/migration/
├── auth/
│   ├── V1__create_users_table.sql
│   ├── V2__create_roles_table.sql
│   └── V3__add_refresh_tokens.sql
├── virtual/
│   ├── V1__create_courses_table.sql
│   ├── V2__create_modules_table.sql
│   └── V3__create_lessons_table.sql
├── physical/
│   ├── V1__create_schedules_table.sql
│   └── V2__create_attendance_table.sql
└── billing/
    ├── V1__create_plans_table.sql
    ├── V2__create_subscriptions_table.sql
    └── V3__create_payments_table.sql
```

---

## 9. Variables de Entorno

### 9.1 Catálogo Completo

```bash
# ============================================
# VARIABLES DE ENTORNO - MENTA ACADEMY
# ============================================

# --- Base de Datos ---
DB_HOST=localhost
DB_PORT=3306
DB_AUTH_NAME=menta_auth
DB_VIRTUAL_NAME=menta_virtual
DB_PHYSICAL_NAME=menta_physical
DB_BILLING_NAME=menta_billing
DB_USERNAME=menta_user
DB_PASSWORD=<secure-password>

# --- Redis ---
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=<secure-password>

# --- RabbitMQ ---
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USER=menta
RABBITMQ_PASS=<secure-password>
RABBITMQ_VHOST=/menta

# --- JWT ---
JWT_SECRET=<base64-encoded-256-bit-secret>
JWT_ACCESS_EXPIRATION=900000      # 15 minutos
JWT_REFRESH_EXPIRATION=604800000  # 7 días

# --- Hashids (uno por servicio) ---
AUTH_HASHIDS_SALT=<unique-salt-auth>
VIRTUAL_HASHIDS_SALT=<unique-salt-virtual>
PHYSICAL_HASHIDS_SALT=<unique-salt-physical>
BILLING_HASHIDS_SALT=<unique-salt-billing>

# --- Integraciones ---
MERCADOPAGO_ACCESS_TOKEN=<mp-access-token>
MERCADOPAGO_PUBLIC_KEY=<mp-public-key>
BUNNY_CDN_API_KEY=<bunny-api-key>
BUNNY_CDN_STORAGE_ZONE=menta-videos
GOOGLE_CALENDAR_API_KEY=<google-api-key>
FIREBASE_SERVER_KEY=<fcm-server-key>

# --- CORS ---
CORS_ALLOWED_ORIGINS=https://mentavirtual.com,https://www.mentavirtual.com

# --- Logging ---
LOG_LEVEL=INFO
LOG_FORMAT=json

# --- Actuator ---
MANAGEMENT_ENDPOINTS_ENABLED=health,info,metrics
```

### 9.2 Generación de Secrets

```bash
# JWT Secret (256 bits)
openssl rand -base64 32

# Hashids Salt
openssl rand -base64 24

# Database Password
openssl rand -base64 16

# Redis Password
openssl rand -base64 16
```

---

## 10. Checklist de Configuración

### Por Ambiente

| Configuración | Desarrollo | Staging | Producción |
|--------------|------------|---------|------------|
| Redis | Local/Docker | Managed | Managed (HA) |
| RabbitMQ | Local/Docker | Managed | Managed (HA) |
| MySQL | Local/Docker | Managed | Managed (HA) |
| SSL/TLS | No | Sí | Sí |
| Rate Limiting | Deshabilitado | Habilitado | Habilitado |
| CORS | Permisivo | Restrictivo | Restrictivo |
| Logging | DEBUG | INFO | WARN |
| Circuit Breaker | Test mode | Habilitado | Habilitado |

---

[Siguiente: MVP Simplificado →](./08-MVP-SIMPLIFIED.md)
