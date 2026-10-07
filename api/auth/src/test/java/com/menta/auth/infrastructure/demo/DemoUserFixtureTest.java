package com.menta.auth.infrastructure.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.menta.auth.application.port.out.PasswordEncoderPort;
import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.User;
import com.menta.auth.domain.model.UserId;
import com.menta.auth.domain.model.UserStatus;
import com.menta.auth.domain.repository.UserRepository;
import com.menta.shared.demo.DemoIds;
import com.menta.shared.demo.DemoSeedOrder;
import com.menta.shared.domain.vo.Email;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

class DemoUserFixtureTest {

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoderPort passwords = mock(PasswordEncoderPort.class);
    private final MockEnvironment environment = new MockEnvironment();
    private DemoUserFixture fixture;

    @BeforeEach
    void setUp() {
        environment.setProperty("demo.users.admin-password", "pw-admin");
        environment.setProperty("demo.users.instructor-password", "pw-instructor");
        environment.setProperty("demo.users.student-password", "pw-student");
        environment.setProperty("demo.users.receptionist-password", "pw-receptionist");
        when(passwords.encode(anyString())).thenAnswer(call -> "hash:" + call.getArgument(0));
        when(users.findByEmail(any())).thenReturn(Optional.empty());
        fixture = new DemoUserFixture(users, passwords, environment);
    }

    @Test
    void first_run_creates_the_four_active_users_with_fixed_ids_and_hashed_passwords() throws Exception {
        fixture.run(new DefaultApplicationArguments());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users, org.mockito.Mockito.times(4)).save(saved.capture());
        List<User> created = saved.getAllValues();
        assertThat(created).extracting(user -> user.getEmail().getValue()).containsExactly(
            "admin@menta.dance", "profesor@menta.dance", "alumno@menta.dance", "recepcion@menta.dance");
        assertThat(created).extracting(User::getRole).containsExactly(
            Role.ADMIN, Role.INSTRUCTOR, Role.STUDENT, Role.RECEPTIONIST);
        assertThat(created).extracting(user -> user.getId().getValue()).containsExactly(
            DemoIds.ADMIN_USER_ID, DemoIds.INSTRUCTOR_USER_ID, DemoIds.STUDENT_USER_ID,
            DemoIds.RECEPTIONIST_USER_ID);
        assertThat(created).extracting(User::getStatus).containsOnly(UserStatus.ACTIVE);
        assertThat(created).extracting(User::getPasswordHash).containsExactly(
            "hash:pw-admin", "hash:pw-instructor", "hash:pw-student", "hash:pw-receptionist");
    }

    @Test
    void second_run_saves_nothing_and_does_not_reset_existing_passwords() throws Exception {
        for (User existing : List.of(
            existing(DemoIds.ADMIN_USER_ID, "admin@menta.dance", Role.ADMIN),
            existing(DemoIds.INSTRUCTOR_USER_ID, "profesor@menta.dance", Role.INSTRUCTOR),
            existing(DemoIds.STUDENT_USER_ID, "alumno@menta.dance", Role.STUDENT),
            existing(DemoIds.RECEPTIONIST_USER_ID, "recepcion@menta.dance", Role.RECEPTIONIST))) {
            when(users.findByEmail(existing.getEmail())).thenReturn(Optional.of(existing));
        }

        fixture.run(new DefaultApplicationArguments());

        verify(users, never()).save(any());
        verify(passwords, never()).encode(anyString());
    }

    @Test
    void partially_seeded_database_creates_only_the_missing_users() throws Exception {
        User admin = existing(DemoIds.ADMIN_USER_ID, "admin@menta.dance", Role.ADMIN);
        when(users.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));

        fixture.run(new DefaultApplicationArguments());

        verify(users, org.mockito.Mockito.times(3)).save(any());
    }

    @Test
    void same_email_with_a_different_id_aborts_with_a_recreate_hint() {
        User other = existing(java.util.UUID.randomUUID(), "admin@menta.dance", Role.ADMIN);
        when(users.findByEmail(other.getEmail())).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> fixture.run(new DefaultApplicationArguments()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("admin@menta.dance")
            .hasMessageContaining("recreate");
        verify(users, never()).save(any());
    }

    @Test
    void a_missing_password_property_aborts_startup() {
        MockEnvironment empty = new MockEnvironment();
        DemoUserFixture withoutPasswords = new DemoUserFixture(users, passwords, empty);

        assertThatThrownBy(() -> withoutPasswords.run(new DefaultApplicationArguments()))
            .isInstanceOf(IllegalStateException.class);
        verify(users, never()).save(any());
    }

    @Test
    void runs_in_the_auth_slot_of_the_seed_order() {
        assertThat(fixture.getOrder()).isEqualTo(DemoSeedOrder.AUTH);
        verifyNoInteractions(mock(Object.class));
    }

    private static User existing(java.util.UUID id, String email, Role role) {
        LocalDateTime now = LocalDateTime.now();
        return User.rehydrate(UserId.of(id), Email.of(email), "old-hash", role, UserStatus.ACTIVE, now, now, 1L);
    }
}
