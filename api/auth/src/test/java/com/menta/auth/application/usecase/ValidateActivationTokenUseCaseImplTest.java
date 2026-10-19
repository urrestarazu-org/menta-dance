package com.menta.auth.application.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.auth.application.dto.ActivateAccountCommand;
import com.menta.auth.application.port.out.ActivationTokenHasher;
import com.menta.auth.application.port.out.ActivationTokenRepository;
import com.menta.auth.application.port.out.Clock;
import com.menta.auth.domain.exception.ActivationTokenInvalidException;
import com.menta.auth.domain.model.ActivationToken;
import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.User;
import com.menta.auth.domain.model.UserId;
import com.menta.auth.domain.model.UserStatus;
import com.menta.auth.domain.repository.UserRepository;
import com.menta.shared.domain.vo.Email;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ValidateActivationTokenUseCaseImplTest {

    private static final String RAW_TOKEN = "raw-activation-token";
    private static final String TOKEN_HASH = "c".repeat(64);
    private static final Instant NOW = Instant.parse("2026-08-14T00:00:00Z");

    @Mock private ActivationTokenRepository activationTokenRepository;
    @Mock private ActivationTokenHasher activationTokenHasher;
    @Mock private UserRepository userRepository;
    @Mock private Clock clock;

    private ValidateActivationTokenUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new ValidateActivationTokenUseCaseImpl(
            activationTokenRepository, activationTokenHasher, userRepository, clock
        );
        when(activationTokenHasher.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(clock.now()).thenReturn(NOW);
    }

    @Test
    void validates_an_active_token_for_a_pending_user_without_persisting_state() {
        UserId userId = UserId.generate();
        ActivationToken token = ActivationToken.issue(
            userId, TOKEN_HASH, NOW.plus(Duration.ofHours(24)), NOW
        );
        User user = new User(
            userId, Email.of("student@example.com"), "hash", Role.STUDENT,
            UserStatus.PENDING_ACTIVATION, LocalDateTime.now(), LocalDateTime.now()
        );
        when(activationTokenRepository.findByHash(TOKEN_HASH)).thenReturn(Optional.of(token));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        useCase.validate(new ActivateAccountCommand(RAW_TOKEN));

        verify(activationTokenRepository, never()).consumeIfActive(any(), any());
        verify(activationTokenRepository, never()).save(any(ActivationToken.class));
        verify(userRepository, never()).save(any());
    }

    @Test
    void rejects_an_expired_token_without_persisting_state() {
        UserId userId = UserId.generate();
        ActivationToken token = ActivationToken.issue(
            userId, TOKEN_HASH, NOW.minus(Duration.ofHours(1)), NOW.minus(Duration.ofHours(25))
        );
        when(activationTokenRepository.findByHash(TOKEN_HASH)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> useCase.validate(new ActivateAccountCommand(RAW_TOKEN)))
            .isInstanceOf(ActivationTokenInvalidException.class);

        verify(activationTokenRepository, never()).consumeIfActive(any(), any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void rejects_a_token_when_its_user_is_already_active_without_persisting_state() {
        UserId userId = UserId.generate();
        ActivationToken token = ActivationToken.issue(
            userId, TOKEN_HASH, NOW.plus(Duration.ofHours(24)), NOW
        );
        User user = new User(
            userId, Email.of("student@example.com"), "hash", Role.STUDENT,
            UserStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now()
        );
        when(activationTokenRepository.findByHash(TOKEN_HASH)).thenReturn(Optional.of(token));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> useCase.validate(new ActivateAccountCommand(RAW_TOKEN)))
            .isInstanceOf(ActivationTokenInvalidException.class);

        verify(activationTokenRepository, never()).consumeIfActive(any(), any());
        verify(userRepository, never()).save(any());
    }
}
