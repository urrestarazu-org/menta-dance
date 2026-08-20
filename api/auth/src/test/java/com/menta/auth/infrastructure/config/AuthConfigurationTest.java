package com.menta.auth.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.menta.auth.application.port.in.ActivateAccountUseCase;
import com.menta.auth.application.port.in.LoginUseCase;
import com.menta.auth.application.port.in.LogoutUseCase;
import com.menta.auth.application.port.in.RefreshTokenUseCase;
import com.menta.auth.application.port.in.RegisterUserUseCase;
import com.menta.auth.application.port.in.RequestPasswordResetUseCase;
import com.menta.auth.application.port.in.ResendActivationUseCase;
import com.menta.auth.application.port.in.ResetPasswordUseCase;
import com.menta.auth.application.port.in.ValidateActivationTokenUseCase;
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
import com.menta.auth.application.port.out.PasswordResetAttemptRateLimitPort;
import com.menta.auth.application.port.out.PasswordResetDeliveryCipher;
import com.menta.auth.application.port.out.PasswordResetRequestRateLimitPort;
import com.menta.auth.application.port.out.PasswordResetTokenGenerator;
import com.menta.auth.application.port.out.PasswordResetTokenHasher;
import com.menta.auth.application.port.out.PasswordResetTokenRepository;
import com.menta.auth.application.port.out.RefreshTokenRepository;
import com.menta.auth.application.port.out.TokenHasher;
import com.menta.auth.domain.repository.UserRepository;
import com.menta.auth.infrastructure.activation.AesGcmActivationDeliveryCipher;
import com.menta.auth.infrastructure.activation.RedisActivationRateLimitPort;
import com.menta.auth.infrastructure.activation.SecureRandomActivationTokenGenerator;
import com.menta.auth.infrastructure.activation.Sha256ActivationTokenHasher;
import com.menta.auth.infrastructure.passwordreset.AesGcmPasswordResetDeliveryCipher;
import com.menta.auth.infrastructure.passwordreset.RedisPasswordResetAttemptRateLimitPort;
import com.menta.auth.infrastructure.passwordreset.RedisPasswordResetRequestRateLimitPort;
import com.menta.auth.infrastructure.passwordreset.SecureRandomPasswordResetTokenGenerator;
import com.menta.auth.infrastructure.passwordreset.Sha256PasswordResetTokenHasher;
import com.menta.auth.infrastructure.security.JwtService;
import com.menta.auth.infrastructure.security.LoggingLoginAttemptAuditPort;
import com.menta.auth.infrastructure.security.RedisLoginRateLimitPort;
import com.menta.auth.infrastructure.security.Sha256TokenHasher;
import com.menta.auth.infrastructure.transaction.TransactionalActivateAccountUseCase;
import com.menta.auth.infrastructure.transaction.TransactionalLoginUseCase;
import com.menta.auth.infrastructure.transaction.TransactionalLogoutUseCase;
import com.menta.auth.infrastructure.transaction.TransactionalRefreshTokenUseCase;
import com.menta.auth.infrastructure.transaction.TransactionalRegisterUserUseCase;
import com.menta.auth.infrastructure.transaction.TransactionalRequestPasswordResetUseCase;
import com.menta.auth.infrastructure.transaction.TransactionalResendActivationUseCase;
import com.menta.auth.infrastructure.transaction.TransactionalResetPasswordUseCase;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class AuthConfigurationTest {

    private static final String VALID_JWT_SECRET =
        "ZGV2LW9ubHktc2VjcmV0LXdpdGgtMzItYnl0ZXMtbWluaW11bS0zMmJ5dGVzLW1pbmltdW0tMzJieXRlcw==";
    private static final String VALID_ACTIVATION_KEY = "YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWE=";
    private static final String VALID_PASSWORD_RESET_KEY = "YmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmI=";

    private final Environment environment = mock(Environment.class);
    private final AuthConfiguration configuration = new AuthConfiguration(environment);

    @SuppressWarnings("unchecked")
    private static RedisTemplate<String, String> redisTemplateMock() {
        return mock(RedisTemplate.class);
    }

    @Test
    void wires_the_password_encoder_bean() {
        PasswordEncoder encoder = configuration.passwordEncoder();

        assertThat(encoder).isInstanceOf(BCryptPasswordEncoder.class);
    }

    @Test
    void wires_the_token_hasher_bean() {
        TokenHasher hasher = configuration.tokenHasher();

        assertThat(hasher).isInstanceOf(Sha256TokenHasher.class);
    }

    @Test
    void wires_the_access_token_issuer_bean() {
        ReflectionTestUtils.setField(configuration, "jwtBase64Secret", VALID_JWT_SECRET);
        ReflectionTestUtils.setField(configuration, "accessTokenTtlSeconds", 900L);

        AccessTokenIssuer issuer = configuration.accessTokenIssuer();

        assertThat(issuer).isInstanceOf(JwtService.class);
    }

    @Test
    void wires_the_activation_token_generator_bean() {
        ActivationTokenGenerator generator = configuration.activationTokenGenerator();

        assertThat(generator).isInstanceOf(SecureRandomActivationTokenGenerator.class);
    }

    @Test
    void wires_the_activation_token_hasher_bean() {
        ActivationTokenHasher hasher = configuration.activationTokenHasher();

        assertThat(hasher).isInstanceOf(Sha256ActivationTokenHasher.class);
    }

    @Test
    void wires_the_activation_delivery_cipher_bean() {
        ReflectionTestUtils.setField(configuration, "activationDeliveryKey", VALID_ACTIVATION_KEY);
        ReflectionTestUtils.setField(configuration, "activationDeliveryKeyVersion", 1);

        ActivationDeliveryCipher cipher = configuration.activationDeliveryCipher();

        assertThat(cipher).isInstanceOf(AesGcmActivationDeliveryCipher.class);
    }

    @Test
    void wires_the_activation_rate_limit_port_bean() {
        ActivationRateLimitPort port =
            configuration.activationRateLimitPort(redisTemplateMock(), 3, 10, 900);

        assertThat(port).isInstanceOf(RedisActivationRateLimitPort.class);
    }

    @Test
    void wires_the_register_user_use_case_bean() {
        RegisterUserUseCase useCase = configuration.registerUserUseCase(
            mock(UserRepository.class), mock(PasswordEncoderPort.class),
            mock(ActivationTokenRepository.class), mock(ActivationTokenGenerator.class),
            mock(ActivationTokenHasher.class), mock(ActivationDeliveryCipher.class),
            mock(ActivationRateLimitPort.class), mock(OutboxAppender.class), mock(Clock.class)
        );

        assertThat(useCase).isInstanceOf(TransactionalRegisterUserUseCase.class);
    }

    @Test
    void wires_the_activate_account_use_case_bean() {
        ActivateAccountUseCase useCase = configuration.activateAccountUseCase(
            mock(ActivationTokenRepository.class), mock(ActivationTokenHasher.class),
            mock(UserRepository.class), mock(Clock.class)
        );

        assertThat(useCase).isInstanceOf(TransactionalActivateAccountUseCase.class);
    }

    @Test
    void wires_the_validate_activation_token_use_case_bean() {
        ValidateActivationTokenUseCase useCase = configuration.validateActivationTokenUseCase(
            mock(ActivationTokenRepository.class), mock(ActivationTokenHasher.class),
            mock(UserRepository.class), mock(Clock.class)
        );

        assertThat(useCase).isNotNull();
    }

    @Test
    void wires_the_resend_activation_use_case_bean() {
        ResendActivationUseCase useCase = configuration.resendActivationUseCase(
            mock(UserRepository.class), mock(ActivationTokenRepository.class),
            mock(ActivationTokenGenerator.class), mock(ActivationTokenHasher.class),
            mock(ActivationDeliveryCipher.class), mock(ActivationRateLimitPort.class),
            mock(OutboxAppender.class), mock(Clock.class)
        );

        assertThat(useCase).isInstanceOf(TransactionalResendActivationUseCase.class);
    }

    @Test
    void wires_the_password_reset_token_generator_bean() {
        PasswordResetTokenGenerator generator = configuration.passwordResetTokenGenerator();

        assertThat(generator).isInstanceOf(SecureRandomPasswordResetTokenGenerator.class);
    }

    @Test
    void wires_the_password_reset_token_hasher_bean() {
        PasswordResetTokenHasher hasher = configuration.passwordResetTokenHasher();

        assertThat(hasher).isInstanceOf(Sha256PasswordResetTokenHasher.class);
    }

    @Test
    void wires_the_password_reset_delivery_cipher_bean() {
        ReflectionTestUtils.setField(configuration, "passwordResetDeliveryKey", VALID_PASSWORD_RESET_KEY);
        ReflectionTestUtils.setField(configuration, "passwordResetDeliveryKeyVersion", 1);

        PasswordResetDeliveryCipher cipher = configuration.passwordResetDeliveryCipher();

        assertThat(cipher).isInstanceOf(AesGcmPasswordResetDeliveryCipher.class);
    }

    @Test
    void wires_the_password_reset_request_rate_limit_port_bean() {
        PasswordResetRequestRateLimitPort port = configuration.passwordResetRequestRateLimitPort(
            redisTemplateMock(), 3, 10, 3600
        );

        assertThat(port).isInstanceOf(RedisPasswordResetRequestRateLimitPort.class);
    }

    @Test
    void wires_the_password_reset_attempt_rate_limit_port_bean() {
        PasswordResetAttemptRateLimitPort port = configuration.passwordResetAttemptRateLimitPort(
            redisTemplateMock(), 10, 3600
        );

        assertThat(port).isInstanceOf(RedisPasswordResetAttemptRateLimitPort.class);
    }

    @Test
    void wires_the_request_password_reset_use_case_bean() {
        RequestPasswordResetUseCase useCase = configuration.requestPasswordResetUseCase(
            mock(UserRepository.class), mock(PasswordResetTokenRepository.class),
            mock(PasswordResetTokenGenerator.class), mock(PasswordResetTokenHasher.class),
            mock(PasswordResetDeliveryCipher.class), mock(PasswordResetRequestRateLimitPort.class),
            mock(OutboxAppender.class), mock(Clock.class)
        );

        assertThat(useCase).isInstanceOf(TransactionalRequestPasswordResetUseCase.class);
    }

    @Test
    void wires_the_reset_password_use_case_bean() {
        ResetPasswordUseCase useCase = configuration.resetPasswordUseCase(
            mock(PasswordResetTokenRepository.class), mock(PasswordResetTokenHasher.class),
            mock(PasswordResetAttemptRateLimitPort.class), mock(UserRepository.class),
            mock(PasswordEncoderPort.class), mock(RefreshTokenRepository.class),
            mock(LoginRateLimitPort.class), mock(Clock.class), mock(OutboxAppender.class)
        );

        assertThat(useCase).isInstanceOf(TransactionalResetPasswordUseCase.class);
    }

    @Test
    void wires_the_login_rate_limit_port_bean() {
        LoginRateLimitPort port = configuration.loginRateLimitPort(redisTemplateMock(), 10, 50, 900);

        assertThat(port).isInstanceOf(RedisLoginRateLimitPort.class);
    }

    @Test
    void wires_the_login_attempt_audit_port_bean() {
        LoginAttemptAuditPort port = configuration.loginAttemptAuditPort();

        assertThat(port).isInstanceOf(LoggingLoginAttemptAuditPort.class);
    }

    @Test
    void wires_the_login_use_case_bean() {
        LoginUseCase useCase = configuration.loginUseCase(
            mock(UserRepository.class), mock(PasswordEncoderPort.class), mock(AccessTokenIssuer.class),
            mock(TokenHasher.class), mock(RefreshTokenRepository.class), mock(OutboxAppender.class),
            mock(AuthDegradedGuard.class), mock(LoginRateLimitPort.class), mock(LoginAttemptAuditPort.class)
        );

        assertThat(useCase).isInstanceOf(TransactionalLoginUseCase.class);
    }

    @Test
    void wires_the_refresh_token_use_case_bean() {
        RefreshTokenUseCase useCase = configuration.refreshTokenUseCase(
            mock(UserRepository.class), mock(RefreshTokenRepository.class), mock(AccessTokenIssuer.class),
            mock(TokenHasher.class), mock(OutboxAppender.class), mock(AuthDegradedGuard.class)
        );

        assertThat(useCase).isInstanceOf(TransactionalRefreshTokenUseCase.class);
    }

    @Test
    void wires_the_logout_use_case_bean() {
        LogoutUseCase useCase = configuration.logoutUseCase(
            mock(UserRepository.class), mock(RefreshTokenRepository.class), mock(TokenHasher.class),
            mock(OutboxAppender.class), mock(AuthDegradedGuard.class)
        );

        assertThat(useCase).isInstanceOf(TransactionalLogoutUseCase.class);
    }

    @Test
    void validateSecretNotDefaultInProduction_passes_outside_production_profiles() {
        when(environment.getActiveProfiles()).thenReturn(new String[] {"dev"});

        assertThatCode(configuration::validateSecretNotDefaultInProduction).doesNotThrowAnyException();
    }

    @Test
    void validateSecretNotDefaultInProduction_passes_in_production_with_custom_secrets() {
        when(environment.getActiveProfiles()).thenReturn(new String[] {"production"});
        ReflectionTestUtils.setField(configuration, "jwtBase64Secret", "a-real-production-jwt-secret");
        ReflectionTestUtils.setField(configuration, "activationDeliveryKey", "a-real-activation-key");
        ReflectionTestUtils.setField(configuration, "passwordResetDeliveryKey", "a-real-reset-key");

        assertThatCode(configuration::validateSecretNotDefaultInProduction).doesNotThrowAnyException();
    }

    @Test
    void validateSecretNotDefaultInProduction_rejects_default_jwt_secret_in_production() {
        when(environment.getActiveProfiles()).thenReturn(new String[] {"production"});
        ReflectionTestUtils.setField(configuration, "jwtBase64Secret", VALID_JWT_SECRET);
        ReflectionTestUtils.setField(configuration, "activationDeliveryKey", "a-real-activation-key");
        ReflectionTestUtils.setField(configuration, "passwordResetDeliveryKey", "a-real-reset-key");

        assertThatThrownBy(configuration::validateSecretNotDefaultInProduction)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("SECURITY");
    }

    @Test
    void validateSecretNotDefaultInProduction_rejects_default_activation_key_in_production() {
        when(environment.getActiveProfiles()).thenReturn(new String[] {"production"});
        ReflectionTestUtils.setField(configuration, "jwtBase64Secret", "a-real-production-jwt-secret");
        ReflectionTestUtils.setField(configuration, "activationDeliveryKey", VALID_ACTIVATION_KEY);
        ReflectionTestUtils.setField(configuration, "passwordResetDeliveryKey", "a-real-reset-key");

        assertThatThrownBy(configuration::validateSecretNotDefaultInProduction)
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validateSecretNotDefaultInProduction_rejects_default_password_reset_key_in_production() {
        when(environment.getActiveProfiles()).thenReturn(new String[] {"production"});
        ReflectionTestUtils.setField(configuration, "jwtBase64Secret", "a-real-production-jwt-secret");
        ReflectionTestUtils.setField(configuration, "activationDeliveryKey", "a-real-activation-key");
        ReflectionTestUtils.setField(configuration, "passwordResetDeliveryKey", VALID_PASSWORD_RESET_KEY);

        assertThatThrownBy(configuration::validateSecretNotDefaultInProduction)
            .isInstanceOf(IllegalStateException.class);
    }
}
