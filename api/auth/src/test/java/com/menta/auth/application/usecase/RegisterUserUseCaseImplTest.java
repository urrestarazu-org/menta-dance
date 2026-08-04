package com.menta.auth.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.auth.application.dto.RegisterUserCommand;
import com.menta.auth.application.dto.UserResult;
import com.menta.auth.application.port.out.PasswordEncoderPort;
import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.User;
import com.menta.auth.domain.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegisterUserUseCaseImplTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoderPort passwordEncoder;

    private RegisterUserUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new RegisterUserUseCaseImpl(userRepository, passwordEncoder);
    }

    @Test
    void rejects_admin_public_registration_before_any_write_or_lookup() {
        RegisterUserCommand command = new RegisterUserCommand(
            "admin@example.com", "Sup3rSecret!", Role.ADMIN
        );

        assertThatThrownBy(() -> useCase.register(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("STUDENT");

        verify(userRepository, never()).existsByEmail(any());
        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void rejects_instructor_public_registration_before_any_write_or_lookup() {
        RegisterUserCommand command = new RegisterUserCommand(
            "instructor@example.com", "Sup3rSecret!", Role.INSTRUCTOR
        );

        assertThatThrownBy(() -> useCase.register(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("STUDENT");

        verify(userRepository, never()).existsByEmail(any());
        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void defaults_a_missing_public_role_to_student() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode("Sup3rSecret!")).thenReturn("password-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResult result = useCase.register(new RegisterUserCommand(
            "student@example.com", "Sup3rSecret!", null
        ));

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertThat(result.role()).isEqualTo(Role.STUDENT);
        assertThat(savedUser.getValue().getRole()).isEqualTo(Role.STUDENT);
        assertThat(savedUser.getValue().getTokenVersion()).isEqualTo(1L);
    }

    @Test
    void accepts_an_explicit_student_public_registration() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode("Sup3rSecret!")).thenReturn("password-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResult result = useCase.register(new RegisterUserCommand(
            "student@example.com", "Sup3rSecret!", Role.STUDENT
        ));

        assertThat(result.role()).isEqualTo(Role.STUDENT);
        assertThat(result.id()).isEqualTo(UUID.fromString(result.id()).toString());
    }
}
