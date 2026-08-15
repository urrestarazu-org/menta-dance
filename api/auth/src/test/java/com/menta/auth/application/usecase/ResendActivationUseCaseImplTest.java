package com.menta.auth.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.auth.application.dto.ResendActivationCommand;
import com.menta.auth.application.dto.ResendActivationResult;
import com.menta.auth.application.port.out.ActivationDeliveryCipher;
import com.menta.auth.application.port.out.ActivationRateLimitPort;
import com.menta.auth.application.port.out.ActivationTokenGenerator;
import com.menta.auth.application.port.out.ActivationTokenHasher;
import com.menta.auth.application.port.out.ActivationTokenRepository;
import com.menta.auth.application.port.out.Clock;
import com.menta.auth.application.port.out.DeliveryEnvelope;
import com.menta.auth.application.port.out.OutboxAppender;
import com.menta.auth.application.port.out.RateLimitDecision;
import com.menta.auth.domain.exception.ActivationRateLimitedException;
import com.menta.auth.domain.model.ActivationToken;
import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.User;
import com.menta.auth.domain.model.UserId;
import com.menta.auth.domain.model.UserStatus;
import com.menta.auth.domain.repository.UserRepository;
import com.menta.shared.domain.vo.Email;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * RED-GREEN discipline: this test references {@code ResendActivationUseCaseImpl}
 * and {@code ResendActivationResult} before either exists, so the file must
 * not compile until GREEN provides them.
 *
 * Covers auth-account-activation spec scenario "Reenvío no enumerativo": the
 * observable response is byte-for-byte identical for a nonexistent email, an
 * already-active email, and a pending email; only the pending case causes a
 * persistence side-effect (design.md decision #6).
 */
@ExtendWith(MockitoExtension.class)
class ResendActivationUseCaseImplTest {

    private static final String EMAIL = "student@example.com";
    private static final DeliveryEnvelope ENVELOPE =
        DeliveryEnvelope.of("cipher".getBytes(StandardCharsets.UTF_8), new byte[12], 1);
    private static final Instant FIXED_NOW = Instant.parse("2026-08-14T00:00:00Z");

    @Mock private UserRepository userRepository;
    @Mock private ActivationTokenRepository activationTokenRepository;
    @Mock private ActivationTokenGenerator activationTokenGenerator;
    @Mock private ActivationTokenHasher activationTokenHasher;
    @Mock private ActivationDeliveryCipher activationDeliveryCipher;
    @Mock private ActivationRateLimitPort activationRateLimitPort;
    @Mock private OutboxAppender outboxAppender;
    @Mock private Clock clock;

    private ResendActivationUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new ResendActivationUseCaseImpl(
            userRepository, activationTokenRepository, activationTokenGenerator,
            activationTokenHasher, activationDeliveryCipher, activationRateLimitPort, outboxAppender,
            clock, Duration.ofHours(24)
        );
    }

    private void allowRateLimit() {
        when(activationRateLimitPort.consume(anyString(), any())).thenReturn(RateLimitDecision.allowed());
    }

    private User userWithStatus(UserStatus status) {
        return new User(
            UserId.generate(), Email.of(EMAIL), "hash", Role.STUDENT,
            status, LocalDateTime.now(), LocalDateTime.now()
        );
    }

    private void stubTokenIssuance() {
        when(activationTokenGenerator.generate()).thenReturn("raw-activation-token");
        when(activationTokenHasher.hash(anyString())).thenReturn("c".repeat(64));
        when(activationDeliveryCipher.encrypt(anyString())).thenReturn(ENVELOPE);
        when(activationTokenRepository.save(any(ActivationToken.class), any(DeliveryEnvelope.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(clock.now()).thenReturn(FIXED_NOW);
    }

    private void verifyNoTokenIssuance() {
        verify(activationTokenRepository, never()).invalidateActiveByUserId(any(), any());
        verify(activationTokenRepository, never()).save(any(ActivationToken.class), any(DeliveryEnvelope.class));
        verify(activationTokenGenerator, never()).generate();
        verify(outboxAppender, never()).append(anyString(), anyString(), anyString());
    }

    @Test
    void rejects_resend_over_the_rate_limit_before_any_lookup() {
        when(activationRateLimitPort.consume(anyString(), any()))
            .thenReturn(RateLimitDecision.limited(Duration.ofSeconds(30)));

        assertThatThrownBy(() -> useCase.resend(new ResendActivationCommand(EMAIL, "client-fp")))
            .isInstanceOf(ActivationRateLimitedException.class);

        verify(userRepository, never()).findByEmail(any());
        verifyNoTokenIssuance();
    }

    @Test
    void returns_the_uniform_acknowledgement_for_a_nonexistent_email_without_side_effects() {
        allowRateLimit();
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());

        ResendActivationResult result = useCase.resend(new ResendActivationCommand(EMAIL, "client-fp"));

        assertThat(result).isEqualTo(ResendActivationResult.ACKNOWLEDGED);
        verifyNoTokenIssuance();
    }

    @Test
    void returns_the_uniform_acknowledgement_for_an_already_active_email_without_side_effects() {
        allowRateLimit();
        when(userRepository.findByEmail(any())).thenReturn(Optional.of(userWithStatus(UserStatus.ACTIVE)));

        ResendActivationResult result = useCase.resend(new ResendActivationCommand(EMAIL, "client-fp"));

        assertThat(result).isEqualTo(ResendActivationResult.ACKNOWLEDGED);
        verifyNoTokenIssuance();
    }

    @Test
    void reissues_a_fresh_token_and_invalidates_prior_ones_for_a_pending_email() {
        allowRateLimit();
        User pending = userWithStatus(UserStatus.PENDING_ACTIVATION);
        when(userRepository.findByEmail(any())).thenReturn(Optional.of(pending));
        stubTokenIssuance();

        ResendActivationResult result = useCase.resend(new ResendActivationCommand(EMAIL, "client-fp"));

        assertThat(result).isEqualTo(ResendActivationResult.ACKNOWLEDGED);
        verify(activationTokenRepository).invalidateActiveByUserId(eq(pending.getId()), any(Instant.class));

        ArgumentCaptor<ActivationToken> savedToken = ArgumentCaptor.forClass(ActivationToken.class);
        verify(activationTokenRepository).save(savedToken.capture(), any(DeliveryEnvelope.class));
        assertThat(savedToken.getValue().getTokenHash()).isEqualTo("c".repeat(64));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(outboxAppender).append(
            eq("auth.AccountActivationRequested"), anyString(), payload.capture()
        );
        assertThat(payload.getValue()).doesNotContain("raw-activation-token");
        assertThat(payload.getValue()).contains("activationTokenId");
    }

    @Test
    void returns_the_identical_response_shape_regardless_of_account_existence_or_state() {
        allowRateLimit();

        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
        ResendActivationResult nonexistent = useCase.resend(new ResendActivationCommand(EMAIL, "client-fp"));

        when(userRepository.findByEmail(any())).thenReturn(Optional.of(userWithStatus(UserStatus.ACTIVE)));
        ResendActivationResult active = useCase.resend(new ResendActivationCommand(EMAIL, "client-fp"));

        when(userRepository.findByEmail(any())).thenReturn(Optional.of(userWithStatus(UserStatus.PENDING_ACTIVATION)));
        stubTokenIssuance();
        ResendActivationResult pending = useCase.resend(new ResendActivationCommand(EMAIL, "client-fp"));

        assertThat(nonexistent)
            .isEqualTo(active)
            .isEqualTo(pending)
            .isEqualTo(ResendActivationResult.ACKNOWLEDGED);
    }
}
