package com.menta.auth.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.menta.auth.application.dto.RegisterUserCommand;
import com.menta.auth.application.dto.UserResult;
import com.menta.auth.application.port.out.ActivationDeliveryCipher;
import com.menta.auth.application.port.out.ActivationRateLimitPort;
import com.menta.auth.application.port.out.ActivationTokenGenerator;
import com.menta.auth.application.port.out.ActivationTokenHasher;
import com.menta.auth.application.port.out.ActivationTokenRepository;
import com.menta.auth.application.port.out.Clock;
import com.menta.auth.application.port.out.DeliveryEnvelope;
import com.menta.auth.application.port.out.OutboxAppender;
import com.menta.auth.application.port.out.PasswordEncoderPort;
import com.menta.auth.application.port.out.RateLimitDecision;
import com.menta.auth.domain.exception.ActivationRateLimitedException;
import com.menta.auth.domain.model.ActivationToken;
import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.User;
import com.menta.auth.domain.model.UserStatus;
import com.menta.auth.domain.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegisterUserUseCaseImplTest {

    private static final DeliveryEnvelope ENVELOPE =
        DeliveryEnvelope.of("cipher".getBytes(StandardCharsets.UTF_8), new byte[12], 1);
    private static final Instant FIXED_NOW = Instant.parse("2026-08-14T00:00:00Z");
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoderPort passwordEncoder;
    @Mock private ActivationTokenRepository activationTokenRepository;
    @Mock private ActivationTokenGenerator activationTokenGenerator;
    @Mock private ActivationTokenHasher activationTokenHasher;
    @Mock private ActivationDeliveryCipher activationDeliveryCipher;
    @Mock private ActivationRateLimitPort activationRateLimitPort;
    @Mock private OutboxAppender outboxAppender;
    @Mock private Clock clock;

    private RegisterUserUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = newUseCase(DEFAULT_TTL);
    }

    private RegisterUserUseCaseImpl newUseCase(Duration ttl) {
        return new RegisterUserUseCaseImpl(
            userRepository,
            passwordEncoder,
            activationTokenRepository,
            activationTokenGenerator,
            activationTokenHasher,
            activationDeliveryCipher,
            activationRateLimitPort,
            outboxAppender,
            clock,
            ttl
        );
    }

    private void allowRateLimit() {
        when(activationRateLimitPort.consume(anyString(), any())).thenReturn(RateLimitDecision.allowed());
    }

    private void stubHappyPathCollaborators() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode("Sup3rSecret!")).thenReturn("password-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(activationTokenGenerator.generate()).thenReturn("raw-activation-token");
        when(activationTokenHasher.hash("raw-activation-token")).thenReturn("c".repeat(64));
        when(activationDeliveryCipher.encrypt(anyString())).thenReturn(ENVELOPE);
        when(activationTokenRepository.save(any(ActivationToken.class), any(DeliveryEnvelope.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(clock.now()).thenReturn(FIXED_NOW);
    }

    @Test
    void rejects_admin_public_registration_before_any_write_or_lookup() {
        RegisterUserCommand command = new RegisterUserCommand(
            "admin@example.com", "Sup3rSecret!", Role.ADMIN, "client-fp"
        );

        assertThatThrownBy(() -> useCase.register(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("STUDENT");

        verifyNoInteractions(
            userRepository, passwordEncoder, activationTokenRepository,
            activationTokenGenerator, activationTokenHasher, activationDeliveryCipher,
            activationRateLimitPort, outboxAppender, clock
        );
    }

    @Test
    void rejects_instructor_public_registration_before_any_write_or_lookup() {
        RegisterUserCommand command = new RegisterUserCommand(
            "instructor@example.com", "Sup3rSecret!", Role.INSTRUCTOR, "client-fp"
        );

        assertThatThrownBy(() -> useCase.register(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("STUDENT");

        verifyNoInteractions(
            userRepository, passwordEncoder, activationTokenRepository,
            activationTokenGenerator, activationTokenHasher, activationDeliveryCipher,
            activationRateLimitPort, outboxAppender, clock
        );
    }

    @Test
    void rejects_registration_over_the_rate_limit_before_any_persistence() {
        when(activationRateLimitPort.consume(anyString(), any()))
            .thenReturn(RateLimitDecision.limited(Duration.ofSeconds(30)));
        RegisterUserCommand command = new RegisterUserCommand(
            "student@example.com", "Sup3rSecret!", Role.STUDENT, "client-fp"
        );

        assertThatThrownBy(() -> useCase.register(command))
            .isInstanceOf(ActivationRateLimitedException.class)
            .satisfies(ex ->
                assertThat(((ActivationRateLimitedException) ex).getRetryAfter())
                    .isEqualTo(Duration.ofSeconds(30))
            );

        verify(userRepository, never()).existsByEmail(any());
        verify(userRepository, never()).save(any());
        verifyNoInteractions(
            passwordEncoder, activationTokenRepository, activationTokenGenerator,
            activationTokenHasher, activationDeliveryCipher, outboxAppender, clock
        );
    }

    @Test
    void rejects_a_duplicate_email_after_the_rate_limit_check() {
        allowRateLimit();
        when(userRepository.existsByEmail(any())).thenReturn(true);
        RegisterUserCommand command = new RegisterUserCommand(
            "student@example.com", "Sup3rSecret!", Role.STUDENT, "client-fp"
        );

        assertThatThrownBy(() -> useCase.register(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");

        verify(userRepository, never()).save(any());
        verifyNoInteractions(
            passwordEncoder, activationTokenRepository, activationTokenGenerator,
            activationTokenHasher, activationDeliveryCipher, outboxAppender, clock
        );
    }

    @Test
    void defaults_a_missing_public_role_to_student_and_registers_pending() {
        allowRateLimit();
        stubHappyPathCollaborators();

        UserResult result = useCase.register(new RegisterUserCommand(
            "student@example.com", "Sup3rSecret!", null, "client-fp"
        ));

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertThat(result.role()).isEqualTo(Role.STUDENT);
        assertThat(savedUser.getValue().getRole()).isEqualTo(Role.STUDENT);
        assertThat(result.status()).isEqualTo(UserStatus.PENDING_ACTIVATION);
        assertThat(savedUser.getValue().getStatus()).isEqualTo(UserStatus.PENDING_ACTIVATION);
        assertThat(savedUser.getValue().getTokenVersion()).isEqualTo(1L);
    }

    @Test
    void accepts_an_explicit_student_public_registration() {
        allowRateLimit();
        stubHappyPathCollaborators();

        UserResult result = useCase.register(new RegisterUserCommand(
            "student@example.com", "Sup3rSecret!", Role.STUDENT, "client-fp"
        ));

        assertThat(result.role()).isEqualTo(Role.STUDENT);
        assertThat(result.id()).isEqualTo(UUID.fromString(result.id()).toString());
    }

    @Test
    void issues_and_persists_an_activation_token_with_a_24_hour_ttl() {
        allowRateLimit();
        stubHappyPathCollaborators();

        useCase.register(new RegisterUserCommand(
            "student@example.com", "Sup3rSecret!", Role.STUDENT, "client-fp"
        ));

        ArgumentCaptor<ActivationToken> savedToken = ArgumentCaptor.forClass(ActivationToken.class);
        verify(activationTokenRepository).save(savedToken.capture(), any(DeliveryEnvelope.class));
        ActivationToken token = savedToken.getValue();
        assertThat(token.getTokenHash()).isEqualTo("c".repeat(64));
        assertThat(Duration.between(token.getCreatedAt(), token.getExpiresAt()))
            .isEqualTo(Duration.ofHours(24));
    }

    @Test
    void appends_exactly_one_outbox_event_without_the_raw_token() {
        allowRateLimit();
        stubHappyPathCollaborators();

        useCase.register(new RegisterUserCommand(
            "student@example.com", "Sup3rSecret!", Role.STUDENT, "client-fp"
        ));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(outboxAppender).append(
            org.mockito.ArgumentMatchers.eq("auth.AccountActivationRequested"),
            anyString(),
            payload.capture()
        );
        assertThat(payload.getValue()).doesNotContain("raw-activation-token");
        assertThat(payload.getValue()).contains("activationTokenId");
    }

    @Test
    void encrypts_the_delivery_envelope_from_the_raw_token_before_storage() {
        allowRateLimit();
        stubHappyPathCollaborators();

        useCase.register(new RegisterUserCommand(
            "student@example.com", "Sup3rSecret!", Role.STUDENT, "client-fp"
        ));

        ArgumentCaptor<String> plaintext = ArgumentCaptor.forClass(String.class);
        verify(activationDeliveryCipher).encrypt(plaintext.capture());
        assertThat(plaintext.getValue()).contains("raw-activation-token");
        assertThat(plaintext.getValue()).contains("student@example.com");
    }

    @Test
    void checks_the_rate_limit_using_an_email_fingerprint_never_the_raw_email() {
        allowRateLimit();
        stubHappyPathCollaborators();

        useCase.register(new RegisterUserCommand(
            "student@example.com", "Sup3rSecret!", Role.STUDENT, "client-fp"
        ));

        ArgumentCaptor<String> emailFingerprint = ArgumentCaptor.forClass(String.class);
        verify(activationRateLimitPort).consume(emailFingerprint.capture(), org.mockito.ArgumentMatchers.eq("client-fp"));
        assertThat(emailFingerprint.getValue()).doesNotContain("student@example.com");
        assertThat(emailFingerprint.getValue()).hasSize(64);
    }

    @Test
    void uses_the_injected_ttl_instead_of_a_hardcoded_duration() {
        RegisterUserUseCaseImpl customTtlUseCase = newUseCase(Duration.ofHours(6));
        allowRateLimit();
        stubHappyPathCollaborators();

        customTtlUseCase.register(new RegisterUserCommand(
            "student@example.com", "Sup3rSecret!", Role.STUDENT, "client-fp"
        ));

        ArgumentCaptor<ActivationToken> savedToken = ArgumentCaptor.forClass(ActivationToken.class);
        verify(activationTokenRepository).save(savedToken.capture(), any(DeliveryEnvelope.class));
        assertThat(Duration.between(savedToken.getValue().getCreatedAt(), savedToken.getValue().getExpiresAt()))
            .isEqualTo(Duration.ofHours(6))
            .isNotEqualTo(DEFAULT_TTL);
    }
}
