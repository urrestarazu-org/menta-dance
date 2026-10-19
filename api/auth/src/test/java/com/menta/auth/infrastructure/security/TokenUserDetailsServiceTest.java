package com.menta.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class TokenUserDetailsServiceTest {

    private static final UUID USER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Mock private UserRepository userRepository;

    private TokenUserDetailsService service;

    private TokenUserDetailsService newService() {
        return new TokenUserDetailsService(userRepository);
    }

    @Test
    void loads_a_user_found_by_email() {
        service = newService();
        User user = activeUser(Role.STUDENT);
        when(userRepository.findByEmail(Email.of("student@example.com"))).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("student@example.com");

        assertThat(details.getUsername()).isEqualTo(USER_ID.toString());
        assertThat(details.getPassword()).isEqualTo("hashed-password");
        assertThat(details.getAuthorities())
            .extracting(Object::toString)
            .containsExactly("ROLE_STUDENT");
    }

    @Test
    void falls_back_to_userId_lookup_when_username_is_not_a_valid_email() {
        service = newService();
        User user = activeUser(Role.ADMIN);
        when(userRepository.findById(UserId.of(USER_ID))).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername(USER_ID.toString());

        assertThat(details.getUsername()).isEqualTo(USER_ID.toString());
        assertThat(details.getAuthorities())
            .extracting(Object::toString)
            .containsExactly("ROLE_ADMIN");
    }

    @Test
    void throws_when_username_matches_neither_an_email_nor_a_valid_userId() {
        service = newService();

        assertThatThrownBy(() -> service.loadUserByUsername("not-an-email-or-uuid"))
            .isInstanceOf(UsernameNotFoundException.class)
            .hasMessageContaining("User not found");
    }

    @Test
    void throws_when_the_userId_lookup_finds_no_user() {
        service = newService();
        when(userRepository.findById(UserId.of(USER_ID))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername(USER_ID.toString()))
            .isInstanceOf(UsernameNotFoundException.class)
            .hasMessageContaining("User not found");
    }

    @Test
    void throws_when_the_matched_user_is_locked() {
        service = newService();
        User locked = new User(
            UserId.of(USER_ID), Email.of("student@example.com"), "hashed-password",
            Role.STUDENT, UserStatus.LOCKED, LocalDateTime.now(), LocalDateTime.now()
        );
        when(userRepository.findByEmail(Email.of("student@example.com"))).thenReturn(Optional.of(locked));

        assertThatThrownBy(() -> service.loadUserByUsername("student@example.com"))
            .isInstanceOf(UsernameNotFoundException.class)
            .hasMessageContaining("User locked");
    }

    @Test
    void empty_authorities_helper_returns_an_empty_list() {
        assertThat(TokenUserDetailsService.emptyAuthorities()).isEmpty();
    }

    private static User activeUser(Role role) {
        return new User(
            UserId.of(USER_ID), Email.of("student@example.com"), "hashed-password",
            role, UserStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now()
        );
    }
}
