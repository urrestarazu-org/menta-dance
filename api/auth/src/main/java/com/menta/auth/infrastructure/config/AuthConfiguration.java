package com.menta.auth.infrastructure.config;

import com.menta.auth.application.port.in.LoginUseCase;
import com.menta.auth.application.port.in.LogoutUseCase;
import com.menta.auth.application.port.in.RefreshTokenUseCase;
import com.menta.auth.application.port.in.RegisterUserUseCase;
import com.menta.auth.application.port.out.AccessTokenIssuer;
import com.menta.auth.application.port.out.AuthDegradedGuard;
import com.menta.auth.application.port.out.OutboxAppender;
import com.menta.auth.application.port.out.PasswordEncoderPort;
import com.menta.auth.application.port.out.RefreshTokenRepository;
import com.menta.auth.application.port.out.TokenHasher;
import com.menta.auth.application.port.out.TokenBlacklistPort;
import com.menta.auth.application.usecase.LoginUseCaseImpl;
import com.menta.auth.application.usecase.LogoutUseCaseImpl;
import com.menta.auth.application.usecase.RefreshTokenUseCaseImpl;
import com.menta.auth.application.usecase.RegisterUserUseCaseImpl;
import com.menta.auth.infrastructure.transaction.TransactionalLoginUseCase;
import com.menta.auth.infrastructure.transaction.TransactionalLogoutUseCase;
import com.menta.auth.infrastructure.transaction.TransactionalRefreshTokenUseCase;
import com.menta.auth.domain.repository.UserRepository;
import com.menta.auth.infrastructure.security.JwtService;
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
     * Fail-fast validation: reject dev-only secret in production profiles.
     * This prevents accidental deployment with insecure defaults.
     */
    @PostConstruct
    void validateSecretNotDefaultInProduction() {
        if (DEV_DEFAULT_SECRET.equals(jwtBase64Secret) && isProductionProfile()) {
            throw new IllegalStateException(
                "SECURITY: auth.jwt.base64-secret is using the dev-only default in a production profile. "
                    + "Set a strong secret via environment variable or application-prod.yml. "
                    + "Active profiles: " + String.join(", ", environment.getActiveProfiles())
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

    @Bean
    public RegisterUserUseCase registerUserUseCase(
        UserRepository userRepository,
        PasswordEncoderPort passwordEncoder
    ) {
        return new RegisterUserUseCaseImpl(userRepository, passwordEncoder);
    }

    @Bean
    public LoginUseCase loginUseCase(
        UserRepository userRepository,
        PasswordEncoderPort passwordEncoder,
        AccessTokenIssuer accessTokenIssuer,
        TokenHasher tokenHasher,
        RefreshTokenRepository refreshTokenRepository,
        OutboxAppender outboxAppender,
        AuthDegradedGuard authDegradedGuard
    ) {
        LoginUseCaseImpl implementation = new LoginUseCaseImpl(
            userRepository,
            passwordEncoder,
            accessTokenIssuer,
            tokenHasher,
            refreshTokenRepository,
            outboxAppender,
            authDegradedGuard
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
