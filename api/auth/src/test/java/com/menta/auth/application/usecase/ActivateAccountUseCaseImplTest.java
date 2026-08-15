package com.menta.auth.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * RED-GREEN discipline: this test references {@code ActivateAccountUseCaseImpl}
 * and {@code ActivationTokenInvalidException} before either exists, so the
 * file must not compile until GREEN provides them.
 *
 * Covers auth-account-activation spec scenarios:
 *   - "Token válido" — user transitions to ACTIVE, token consumed, single call.
 *   - "Reutilización" — used token rejected with the generic exception.
 *   - "Expiración o invalidación" — expired/invalidated token rejected.
 *   - "Activación concurrente" — a valid token whose user is already ACTIVE
 *     (racing activation) is rejected without a different exception type.
 */
@ExtendWith(MockitoExtension.class)
class ActivateAccountUseCaseImplTest {

    private static final String RAW_TOKEN = "raw-activation-token";
    private static final String TOKEN_HASH = "c".repeat(64);
    private static final Instant NOW = Instant.parse("2026-08-14T00:00:00Z");

    @Mock private ActivationTokenRepository activationTokenRepository;
    @Mock private ActivationTokenHasher activationTokenHasher;
    @Mock private UserRepository userRepository;
    @Mock private Clock clock;

    private ActivateAccountUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new ActivateAccountUseCaseImpl(
            activationTokenRepository, activationTokenHasher, userRepository, clock
        );
        when(activationTokenHasher.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
    }

    private void stubClock() {
        when(clock.now()).thenReturn(NOW);
    }

    private User pendingUser(UserId id) {
        return new User(
            id, Email.of("student@example.com"), "hash", Role.STUDENT,
            UserStatus.PENDING_ACTIVATION, LocalDateTime.now(), LocalDateTime.now()
        );
    }

    @Test
    void activates_the_pending_user_and_consumes_the_token_in_the_same_call() {
        UserId userId = UserId.generate();
        ActivationToken token = ActivationToken.issue(userId, TOKEN_HASH, NOW.plus(Duration.ofHours(24)), NOW);
        User user = pendingUser(userId);

        stubClock();
        when(activationTokenRepository.findByHash(TOKEN_HASH)).thenReturn(Optional.of(token));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(activationTokenRepository.consumeIfActive(any(ActivationToken.class), org.mockito.ArgumentMatchers.eq(NOW)))
            .thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        useCase.activate(new ActivateAccountCommand(RAW_TOKEN));

        ArgumentCaptor<ActivationToken> consumedToken = ArgumentCaptor.forClass(ActivationToken.class);
        verify(activationTokenRepository).consumeIfActive(
            consumedToken.capture(), org.mockito.ArgumentMatchers.eq(NOW)
        );
        assertThat(consumedToken.getValue().getUsedAt()).isNotNull();

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertThat(savedUser.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void rejects_a_lost_conditional_consume_without_activating_the_user() {
        UserId userId = UserId.generate();
        ActivationToken token = ActivationToken.issue(userId, TOKEN_HASH, NOW.plus(Duration.ofHours(24)), NOW);
        User user = pendingUser(userId);

        stubClock();
        when(activationTokenRepository.findByHash(TOKEN_HASH)).thenReturn(Optional.of(token));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(activationTokenRepository.consumeIfActive(any(ActivationToken.class), org.mockito.ArgumentMatchers.eq(NOW)))
            .thenReturn(false);

        assertThatThrownBy(() -> useCase.activate(new ActivateAccountCommand(RAW_TOKEN)))
            .isInstanceOf(ActivationTokenInvalidException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void rejects_an_unknown_token_hash_with_the_generic_exception_and_no_persistence() {
        when(activationTokenRepository.findByHash(TOKEN_HASH)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.activate(new ActivateAccountCommand(RAW_TOKEN)))
            .isInstanceOf(ActivationTokenInvalidException.class);

        verify(userRepository, never()).findById(any());
        verify(activationTokenRepository, never()).save(any(ActivationToken.class));
        verify(userRepository, never()).save(any());
    }

    @Test
    void rejects_an_already_used_token_with_the_generic_exception_and_no_persistence() {
        UserId userId = UserId.generate();
        Instant issuedAt = NOW.minus(Duration.ofHours(1));
        ActivationToken usedToken = ActivationToken.reconstitute(
            UUID.randomUUID(), userId, TOKEN_HASH,
            issuedAt.plus(Duration.ofHours(24)), issuedAt, issuedAt.plusSeconds(60), null
        );

        stubClock();
        when(activationTokenRepository.findByHash(TOKEN_HASH)).thenReturn(Optional.of(usedToken));

        assertThatThrownBy(() -> useCase.activate(new ActivateAccountCommand(RAW_TOKEN)))
            .isInstanceOf(ActivationTokenInvalidException.class);

        verify(userRepository, never()).findById(any());
        verify(activationTokenRepository, never()).save(any(ActivationToken.class));
        verify(userRepository, never()).save(any());
    }

    @Test
    void rejects_an_expired_token_with_the_generic_exception_and_no_persistence() {
        UserId userId = UserId.generate();
        Instant issuedAt = NOW.minus(Duration.ofHours(48));
        ActivationToken expiredToken = ActivationToken.issue(
            userId, TOKEN_HASH, issuedAt.plus(Duration.ofHours(24)), issuedAt
        );

        stubClock();
        when(activationTokenRepository.findByHash(TOKEN_HASH)).thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> useCase.activate(new ActivateAccountCommand(RAW_TOKEN)))
            .isInstanceOf(ActivationTokenInvalidException.class);

        verify(userRepository, never()).findById(any());
        verify(activationTokenRepository, never()).save(any(ActivationToken.class));
        verify(userRepository, never()).save(any());
    }

    @Test
    void rejects_an_invalidated_token_with_the_generic_exception_and_no_persistence() {
        UserId userId = UserId.generate();
        Instant issuedAt = NOW.minus(Duration.ofHours(1));
        ActivationToken invalidatedToken = ActivationToken.reconstitute(
            UUID.randomUUID(), userId, TOKEN_HASH,
            issuedAt.plus(Duration.ofHours(24)), issuedAt, null, issuedAt.plusSeconds(30)
        );

        stubClock();
        when(activationTokenRepository.findByHash(TOKEN_HASH)).thenReturn(Optional.of(invalidatedToken));

        assertThatThrownBy(() -> useCase.activate(new ActivateAccountCommand(RAW_TOKEN)))
            .isInstanceOf(ActivationTokenInvalidException.class);

        verify(userRepository, never()).findById(any());
        verify(activationTokenRepository, never()).save(any(ActivationToken.class));
        verify(userRepository, never()).save(any());
    }

    @Test
    void rejects_a_racing_activation_where_the_user_is_no_longer_pending_without_persistence() {
        UserId userId = UserId.generate();
        ActivationToken token = ActivationToken.issue(userId, TOKEN_HASH, NOW.plus(Duration.ofHours(24)), NOW);
        User alreadyActiveUser = new User(
            userId, Email.of("student@example.com"), "hash", Role.STUDENT,
            UserStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now()
        );

        stubClock();
        when(activationTokenRepository.findByHash(TOKEN_HASH)).thenReturn(Optional.of(token));
        when(userRepository.findById(userId)).thenReturn(Optional.of(alreadyActiveUser));

        assertThatThrownBy(() -> useCase.activate(new ActivateAccountCommand(RAW_TOKEN)))
            .isInstanceOf(ActivationTokenInvalidException.class);

        verify(activationTokenRepository, never()).save(any(ActivationToken.class));
        verify(userRepository, never()).save(any());
    }
}
