package com.menta.auth.infrastructure.config;

import com.menta.auth.application.port.in.LoginUseCase;
import com.menta.auth.application.port.in.LogoutUseCase;
import com.menta.auth.application.port.in.RefreshTokenUseCase;
import com.menta.auth.application.port.in.RegisterUserUseCase;
import com.menta.auth.application.port.in.ActivateAccountUseCase;
import com.menta.auth.application.port.in.ResendActivationUseCase;
import com.menta.auth.application.port.out.AccessTokenIssuer;
import com.menta.auth.application.port.out.ActivationDeliveryCipher;
import com.menta.auth.application.port.out.ActivationRateLimitPort;
import com.menta.auth.application.port.out.ActivationTokenGenerator;
import com.menta.auth.application.port.out.ActivationTokenHasher;
import com.menta.auth.application.port.out.ActivationTokenRepository;
import com.menta.auth.application.port.out.AuthDegradedGuard;
import com.menta.auth.application.port.out.Clock;
import com.menta.auth.application.port.out.LoginAttemptAuditPort;
import com.menta.auth.application.port.out.LoginRateLimitPort;
import com.menta.auth.application.port.out.OutboxAppender;
import com.menta.auth.application.port.out.PasswordEncoderPort;
import com.menta.auth.application.port.out.RefreshTokenRepository;
import com.menta.auth.application.port.out.TokenHasher;
import com.menta.auth.application.port.out.TokenBlacklistPort;
import com.menta.auth.application.usecase.LoginUseCaseImpl;
import com.menta.auth.application.usecase.LogoutUseCaseImpl;
import com.menta.auth.application.usecase.RefreshTokenUseCaseImpl;
import com.menta.auth.application.usecase.RegisterUserUseCaseImpl;
import com.menta.auth.application.usecase.ActivateAccountUseCaseImpl;
import com.menta.auth.application.usecase.ResendActivationUseCaseImpl;
import com.menta.auth.infrastructure.activation.AesGcmActivationDeliveryCipher;
import com.menta.auth.infrastructure.activation.RedisActivationRateLimitPort;
import com.menta.auth.infrastructure.activation.SecureRandomActivationTokenGenerator;
import com.menta.auth.infrastructure.activation.Sha256ActivationTokenHasher;
import com.menta.auth.infrastructure.transaction.TransactionalLoginUseCase;
import com.menta.auth.infrastructure.transaction.TransactionalLogoutUseCase;
import com.menta.auth.infrastructure.transaction.TransactionalRefreshTokenUseCase;
import com.menta.auth.infrastructure.transaction.TransactionalRegisterUserUseCase;
import com.menta.auth.infrastructure.transaction.TransactionalActivateAccountUseCase;
import com.menta.auth.infrastructure.transaction.TransactionalResendActivationUseCase;
import com.menta.auth.domain.repository.UserRepository;
import com.menta.auth.infrastructure.security.JwtService;
import com.menta.auth.infrastructure.security.LoggingLoginAttemptAuditPort;
import com.menta.auth.infrastructure.security.RedisLoginRateLimitPort;
import com.menta.auth.infrastructure.security.Sha256TokenHasher;
import com.menta.auth.infrastructure.security.TokenBlacklistPortImpl;

import java.time.Duration;
import java.util.Set;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Wires the application use cases and the JWT issuer bean. Adapter classes
 * (UserRepositoryAdapter, RefreshTokenRepositoryAdapter, TokenBlacklistPortImpl,
 * OutboxJpaAppender, Sha256TokenHasher, PasswordEncoderAdapter) are
 * {@code @Component}-scanned.
 *
 * The {@code @Bean}-methods here exist for two reasons:
 *   1. Constructor injection: JwtService needs the resolved base64 secret +
 *      TTL at construction; Spring cannot satisfy that with @Autowired
 *      alone. The {@code @Value} placeholders ({@code auth.jwt.base64-secret},
 *      {@code auth.access-token-ttl-seconds}) tie the secret to application.yml.
 *   2. Composition: each use case is a plain Java class; this config is
 *      where they are wired to their port dependencies. Calling the use cases
 *      directly from controllers keeps the wiring explicit and traceable
 *      (no implicit {@code @Autowired} on use-case classes — the orchestrator
 *      wants ports visible at the boundary).
 *
 * The TokenBlacklistPortImpl is bound to BOTH {@link TokenBlacklistPort} and
 * {@link AuthDegradedGuard} (it implements both). The latter is what the
 * login/refresh/logout use cases consult for the fail-closed 503 path.
 */
@Configuration
public class AuthConfiguration {

    /**
     * Dev-only default secret. Used to detect insecure configuration in production.
     */
    private static final String DEV_DEFAULT_SECRET =
        "ZGV2LW9ubHktc2VjcmV0LXdpdGgtMzItYnl0ZXMtbWluaW11bS0zMmJ5dGVzLW1pbmltdW0tMzJieXRlcw==";
    private static final String DEV_DEFAULT_ACTIVATION_DELIVERY_KEY =
        "YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWE=";

    /**
     * Profiles considered production environments where dev secrets are forbidden.
     */
    private static final Set<String> PRODUCTION_PROFILES = Set.of("prod", "production", "staging");

    private final Environment environment;

    public AuthConfiguration(Environment environment) {
        this.environment = environment;
    }

    /**
     * Base64-encoded HS256 secret. The auth secret MUST be at least 32 bytes
     * (256 bits) raw — JwtService validates this at construction. The dev-only
     * default below is a 32-byte padding-string; production MUST inject a
     * strong value via application.yml / env (.env never committed).
     */
    @Value("${auth.jwt.base64-secret:" + DEV_DEFAULT_SECRET + "}")
    private String jwtBase64Secret;

    @Value("${auth.access-token-ttl-seconds:900}")
    private long accessTokenTtlSeconds;

    /**
     * TTL for a freshly-issued activation token (design.md decision #3:
     * 24h default, configurable). ISO-8601 duration string; Spring binds
     * {@code java.time.Duration} @Value placeholders natively.
     */
    @Value("${auth.activation.token-ttl:PT24H}")
    private Duration activationTokenTtl;

    @Value("${auth.activation.delivery-key:" + DEV_DEFAULT_ACTIVATION_DELIVERY_KEY + "}")
    private String activationDeliveryKey;

    @Value("${auth.activation.delivery-key-version:1}")
    private int activationDeliveryKeyVersion;

    /**
     * Fail-fast validation: reject dev-only secret in production profiles.
     * This prevents accidental deployment with insecure defaults.
     */
    @PostConstruct
    void validateSecretNotDefaultInProduction() {
        if (isProductionProfile() && (DEV_DEFAULT_SECRET.equals(jwtBase64Secret)
            || DEV_DEFAULT_ACTIVATION_DELIVERY_KEY.equals(activationDeliveryKey))) {
            throw new IllegalStateException(
                "SECURITY: production requires non-default JWT and activation delivery keys. "
                    + "Set them via environment variables. Active profiles: "
                    + String.join(", ", environment.getActiveProfiles())
            );
        }
    }

    private boolean isProductionProfile() {
        for (String profile : environment.getActiveProfiles()) {
            if (PRODUCTION_PROFILES.contains(profile.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public TokenHasher tokenHasher() {
        return new Sha256TokenHasher();
    }

    @Bean
    public AccessTokenIssuer accessTokenIssuer() {
        return new JwtService(jwtBase64Secret, Duration.ofSeconds(accessTokenTtlSeconds));
    }

    /**
     * PR2 (tasks 2.1/2.2/2.3/2.4) replaces every placeholder bean below with a
     * real JPA/Redis/AES-GCM adapter. Exposed individually so the upcoming
     * {@code ActivateAccountUseCase}/{@code ResendActivationUseCase} beans
     * (task 1.6) can reuse the same instances.
     */
    @Bean
    public ActivationTokenGenerator activationTokenGenerator() {
        return new SecureRandomActivationTokenGenerator();
    }

    @Bean
    public ActivationTokenHasher activationTokenHasher() {
        return new Sha256ActivationTokenHasher();
    }

    @Bean
    public ActivationDeliveryCipher activationDeliveryCipher() {
        return new AesGcmActivationDeliveryCipher(
            activationDeliveryKey, activationDeliveryKeyVersion
        );
    }

    @Bean
    public ActivationRateLimitPort activationRateLimitPort(
        org.springframework.data.redis.core.RedisTemplate<String, String> redisTemplate,
        @Value("${auth.activation.rate-limit.email-max-attempts:3}") long emailMaxAttempts,
        @Value("${auth.activation.rate-limit.client-max-attempts:10}") long clientMaxAttempts,
        @Value("${auth.activation.rate-limit.window-seconds:900}") long windowSeconds
    ) {
        return new RedisActivationRateLimitPort(
            redisTemplate, emailMaxAttempts, clientMaxAttempts, Duration.ofSeconds(windowSeconds)
        );
    }

    @Bean
    public RegisterUserUseCase registerUserUseCase(
        UserRepository userRepository,
        PasswordEncoderPort passwordEncoder,
        ActivationTokenRepository activationTokenRepository,
        ActivationTokenGenerator activationTokenGenerator,
        ActivationTokenHasher activationTokenHasher,
        ActivationDeliveryCipher activationDeliveryCipher,
        ActivationRateLimitPort activationRateLimitPort,
        OutboxAppender outboxAppender,
        Clock clock
    ) {
        RegisterUserUseCaseImpl implementation = new RegisterUserUseCaseImpl(
            userRepository,
            passwordEncoder,
            activationTokenRepository,
            activationTokenGenerator,
            activationTokenHasher,
            activationDeliveryCipher,
            activationRateLimitPort,
            outboxAppender,
            clock,
            activationTokenTtl
        );
        return new TransactionalRegisterUserUseCase(implementation);
    }

    @Bean
    public ActivateAccountUseCase activateAccountUseCase(
        ActivationTokenRepository activationTokenRepository,
        ActivationTokenHasher activationTokenHasher,
        UserRepository userRepository,
        Clock clock
    ) {
        return new TransactionalActivateAccountUseCase(new ActivateAccountUseCaseImpl(
            activationTokenRepository, activationTokenHasher, userRepository, clock
        ));
    }

    @Bean
    public ResendActivationUseCase resendActivationUseCase(
        UserRepository userRepository,
        ActivationTokenRepository activationTokenRepository,
        ActivationTokenGenerator activationTokenGenerator,
        ActivationTokenHasher activationTokenHasher,
        ActivationDeliveryCipher activationDeliveryCipher,
        ActivationRateLimitPort activationRateLimitPort,
        OutboxAppender outboxAppender,
        Clock clock
    ) {
        ResendActivationUseCaseImpl implementation = new ResendActivationUseCaseImpl(
            userRepository,
            activationTokenRepository,
            activationTokenGenerator,
            activationTokenHasher,
            activationDeliveryCipher,
            activationRateLimitPort,
            outboxAppender,
            clock,
            activationTokenTtl
        );
        return new TransactionalResendActivationUseCase(implementation);
    }

    /**
     * Separate key namespace and budget from {@link #activationRateLimitPort}:
     * burning activation attempts must never cost a user their ability to log
     * in. These budgets count FAILURES only (ADR-0035); the volumetric ceiling
     * lives in NGINX.
     */
    @Bean
    public LoginRateLimitPort loginRateLimitPort(
        org.springframework.data.redis.core.RedisTemplate<String, String> redisTemplate,
        @Value("${auth.login.rate-limit.email-max-failures:10}") long emailMaxFailures,
        @Value("${auth.login.rate-limit.client-max-failures:50}") long clientMaxFailures,
        @Value("${auth.login.rate-limit.window-seconds:900}") long windowSeconds
    ) {
        return new RedisLoginRateLimitPort(
            redisTemplate, emailMaxFailures, clientMaxFailures, Duration.ofSeconds(windowSeconds)
        );
    }

    @Bean
    public LoginAttemptAuditPort loginAttemptAuditPort() {
        return new LoggingLoginAttemptAuditPort();
    }

    @Bean
    public LoginUseCase loginUseCase(
        UserRepository userRepository,
        PasswordEncoderPort passwordEncoder,
        AccessTokenIssuer accessTokenIssuer,
        TokenHasher tokenHasher,
        RefreshTokenRepository refreshTokenRepository,
        OutboxAppender outboxAppender,
        AuthDegradedGuard authDegradedGuard,
        LoginRateLimitPort loginRateLimitPort,
        LoginAttemptAuditPort loginAttemptAuditPort
    ) {
        LoginUseCaseImpl implementation = new LoginUseCaseImpl(
            userRepository,
            passwordEncoder,
            accessTokenIssuer,
            tokenHasher,
            refreshTokenRepository,
            outboxAppender,
            authDegradedGuard,
            loginRateLimitPort,
            loginAttemptAuditPort
        );
        return new TransactionalLoginUseCase(implementation);
    }

    @Bean
    public RefreshTokenUseCase refreshTokenUseCase(
        UserRepository userRepository,
        RefreshTokenRepository refreshTokenRepository,
        AccessTokenIssuer accessTokenIssuer,
        TokenHasher tokenHasher,
        OutboxAppender outboxAppender,
        AuthDegradedGuard authDegradedGuard
    ) {
        RefreshTokenUseCaseImpl implementation = new RefreshTokenUseCaseImpl(
            userRepository,
            refreshTokenRepository,
            accessTokenIssuer,
            tokenHasher,
            outboxAppender,
            authDegradedGuard
        );
        return new TransactionalRefreshTokenUseCase(implementation);
    }

    @Bean
    public LogoutUseCase logoutUseCase(
        UserRepository userRepository,
        RefreshTokenRepository refreshTokenRepository,
        TokenHasher tokenHasher,
        OutboxAppender outboxAppender,
        AuthDegradedGuard authDegradedGuard
    ) {
        LogoutUseCaseImpl implementation = new LogoutUseCaseImpl(
            userRepository,
            refreshTokenRepository,
            tokenHasher,
            outboxAppender,
            authDegradedGuard
        );
        return new TransactionalLogoutUseCase(implementation);
    }

    /**
     * Both ports are satisfied by the single TokenBlacklistPortImpl bean — no
     * need for a second instance. Kept here as documentation of the deliberate
     * wiring; the bean itself is @Component-scanned.
     */
    @SuppressWarnings("unused")
    private static final Class<?> DUAL_PORT_IMPL = TokenBlacklistPortImpl.class;
}
