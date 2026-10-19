package com.menta.auth.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.menta.auth.domain.model.UserId;
import com.menta.auth.domain.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * D8 (US-BILLING-012): {@code UserExistenceAdapter} maps the shared port's raw {@code UUID} into
 * this module's own {@code UserId} value object and returns the verdict unchanged, delegating to
 * the domain {@code UserRepository} — never to {@code UserJpaRepository} directly (design.md A9).
 */
@ExtendWith(MockitoExtension.class)
class UserExistenceAdapterTest {

    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock private UserRepository userRepository;

    private UserExistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new UserExistenceAdapter(userRepository);
    }

    @Test
    void existsById_maps_the_uuid_to_a_userId_and_returns_true_unchanged() {
        when(userRepository.existsById(UserId.of(USER_ID))).thenReturn(true);

        boolean exists = adapter.existsById(USER_ID);

        assertThat(exists).isTrue();
    }

    @Test
    void existsById_maps_the_uuid_to_a_userId_and_returns_false_unchanged() {
        when(userRepository.existsById(UserId.of(USER_ID))).thenReturn(false);

        boolean exists = adapter.existsById(USER_ID);

        assertThat(exists).isFalse();
    }
}
