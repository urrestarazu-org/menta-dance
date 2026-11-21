package com.menta.auth.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.menta.auth.application.dto.CurrentUser;
import com.menta.auth.domain.exception.UserNotFoundException;
import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.User;
import com.menta.auth.domain.model.UserId;
import com.menta.auth.domain.model.UserProfile;
import com.menta.auth.domain.model.UserStatus;
import com.menta.auth.domain.repository.UserRepository;
import com.menta.shared.domain.vo.Email;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetCurrentUserUseCaseImplTest {

    private static final UUID USER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Mock private UserRepository userRepository;

    private GetCurrentUserUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetCurrentUserUseCaseImpl(userRepository);
    }

    private static User user(Role role, UserProfile profile) {
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        return User.rehydrate(
            UserId.of(USER_ID), Email.of("ada@example.com"), "hash", role, UserStatus.ACTIVE,
            created, created, 0L, profile
        );
    }

    @Test
    void returns_the_id_names_and_role_of_a_user_with_profile() {
        UserProfile profile = new UserProfile("Ada", "Lovelace", "+5491112345678", LocalDate.of(1990, 12, 10));
        when(userRepository.findById(UserId.of(USER_ID))).thenReturn(Optional.of(user(Role.STUDENT, profile)));

        CurrentUser result = useCase.get(USER_ID);

        assertThat(result).isEqualTo(new CurrentUser(USER_ID, "Ada", "Lovelace", Role.STUDENT));
    }

    @Test
    void returns_null_names_for_a_user_without_profile() {
        when(userRepository.findById(UserId.of(USER_ID))).thenReturn(Optional.of(user(Role.ADMIN, null)));

        CurrentUser result = useCase.get(USER_ID);

        assertThat(result).isEqualTo(new CurrentUser(USER_ID, null, null, Role.ADMIN));
    }

    @Test
    void throws_user_not_found_when_the_user_no_longer_exists() {
        when(userRepository.findById(UserId.of(USER_ID))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.get(USER_ID))
            .isInstanceOf(UserNotFoundException.class)
            .hasMessageContaining(USER_ID.toString());
    }
}
