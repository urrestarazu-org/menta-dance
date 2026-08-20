package com.menta.auth.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.User;
import com.menta.auth.domain.model.UserId;
import com.menta.auth.domain.model.UserStatus;
import com.menta.auth.infrastructure.persistence.entity.UserJpaEntity;
import com.menta.auth.infrastructure.persistence.repository.UserJpaRepository;
import com.menta.shared.domain.vo.Email;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserRepositoryAdapterTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 16, 12, 0);

    @Mock private UserJpaRepository jpaRepository;

    private UserRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new UserRepositoryAdapter(jpaRepository);
    }

    @Test
    void save_maps_domain_to_entity_persists_and_returns_mapped_domain() {
        User domain = new User(
            UserId.of(USER_ID), Email.of("student@example.com"), "hashed-password",
            Role.STUDENT, UserStatus.ACTIVE, NOW, NOW
        );
        when(jpaRepository.save(org.mockito.ArgumentMatchers.any(UserJpaEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        User saved = adapter.save(domain);

        assertThat(saved.getId()).isEqualTo(domain.getId());
        assertThat(saved.getEmail()).isEqualTo(domain.getEmail());
    }

    @Test
    void findById_maps_the_entity_when_found() {
        UserJpaEntity entity = new UserJpaEntity(
            USER_ID, "student@example.com", "hashed-password", Role.STUDENT,
            UserStatus.ACTIVE, NOW, NOW, 1L
        );
        when(jpaRepository.findById(USER_ID)).thenReturn(Optional.of(entity));

        Optional<User> found = adapter.findById(UserId.of(USER_ID));

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(UserId.of(USER_ID));
    }

    @Test
    void findById_returns_empty_when_not_found() {
        when(jpaRepository.findById(USER_ID)).thenReturn(Optional.empty());

        Optional<User> found = adapter.findById(UserId.of(USER_ID));

        assertThat(found).isEmpty();
    }

    @Test
    void findByEmail_maps_the_entity_when_found() {
        UserJpaEntity entity = new UserJpaEntity(
            USER_ID, "student@example.com", "hashed-password", Role.STUDENT,
            UserStatus.ACTIVE, NOW, NOW, 1L
        );
        when(jpaRepository.findByEmail("student@example.com")).thenReturn(Optional.of(entity));

        Optional<User> found = adapter.findByEmail(Email.of("student@example.com"));

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo(Email.of("student@example.com"));
    }

    @Test
    void findByEmail_returns_empty_when_not_found() {
        when(jpaRepository.findByEmail("student@example.com")).thenReturn(Optional.empty());

        Optional<User> found = adapter.findByEmail(Email.of("student@example.com"));

        assertThat(found).isEmpty();
    }

    @Test
    void deleteById_delegates_to_the_jpa_repository() {
        adapter.deleteById(UserId.of(USER_ID));

        verify(jpaRepository).deleteById(USER_ID);
    }

    @Test
    void existsByEmail_delegates_to_the_jpa_repository() {
        when(jpaRepository.existsByEmail("student@example.com")).thenReturn(true);

        boolean exists = adapter.existsByEmail(Email.of("student@example.com"));

        assertThat(exists).isTrue();
    }
}
