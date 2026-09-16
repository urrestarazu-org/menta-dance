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
 * D7/C4 (proposal, design): {@code UserEmailLookupAdapter} maps the shared
 * port's raw {@code UUID} into this module's own {@code UserId}, resolves
 * the {@code User} through the domain {@code UserRepository} — never {@code
 * UserJpaRepository} directly — and returns only its email, exactly as
 * {@link UserExistenceAdapter} does for existence (design.md A9-precedent).
 */
@ExtendWith(MockitoExtension.class)
class UserEmailLookupAdapterTest {

    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock private UserRepository userRepository;

    private UserEmailLookupAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new UserEmailLookupAdapter(userRepository);
    }

    @Test
    void findEmailById_maps_the_uuid_to_a_userId_and_returns_the_resolved_email() {
        User user = User.rehydrate(
            UserId.of(USER_ID), Email.of("student@menta.dance"), "hash", Role.STUDENT,
            UserStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now(), 1L
        );
        when(userRepository.findById(UserId.of(USER_ID))).thenReturn(Optional.of(user));

        Optional<String> email = adapter.findEmailById(USER_ID);

        assertThat(email).contains("student@menta.dance");
    }

    @Test
    void findEmailById_returns_empty_when_the_user_does_not_exist() {
        when(userRepository.findById(UserId.of(USER_ID))).thenReturn(Optional.empty());

        Optional<String> email = adapter.findEmailById(USER_ID);

        assertThat(email).isEmpty();
    }
}
