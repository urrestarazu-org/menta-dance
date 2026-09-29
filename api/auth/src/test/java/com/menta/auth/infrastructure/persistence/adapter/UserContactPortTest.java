package com.menta.auth.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.User;
import com.menta.auth.domain.model.UserId;
import com.menta.auth.domain.model.UserStatus;
import com.menta.auth.domain.repository.UserRepository;
import com.menta.shared.domain.vo.Email;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * D7 (#33, US-BILLING-005, design C8): {@code UserContactAdapter} implements the new cross-module
 * {@link com.menta.shared.auth.UserContactPort}, mirroring {@link UserExistenceAdapter}'s shape —
 * delegates to the domain {@code UserRepository}, never to {@code UserJpaRepository} directly.
 * Unlike {@code UserExistenceAdapter}, this port is consumed only by billing's infrastructure mail
 * adapter (C8) so a missing user must resolve to {@link Optional#empty()}, never an exception.
 */
@ExtendWith(MockitoExtension.class)
class UserContactPortTest {

    private static final UUID USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Mock private UserRepository userRepository;

    private UserContactAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new UserContactAdapter(userRepository);
    }

    @Test
    void emailOf_returns_the_users_email_when_found() {
        User user = User.rehydrate(
            UserId.of(USER_ID), Email.of("buyer@menta.local"), "hash", Role.STUDENT,
            UserStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now(), 1L
        );
        when(userRepository.findById(UserId.of(USER_ID))).thenReturn(Optional.of(user));

        Optional<String> email = adapter.emailOf(USER_ID);

        assertThat(email).contains("buyer@menta.local");
    }

    @Test
    void emailOf_returns_empty_when_the_user_is_not_found() {
        when(userRepository.findById(UserId.of(USER_ID))).thenReturn(Optional.empty());

        Optional<String> email = adapter.emailOf(USER_ID);

        assertThat(email).isEmpty();
    }
}
