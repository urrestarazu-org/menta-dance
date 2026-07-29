package com.menta.auth.infrastructure.security;

import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.User;
import com.menta.auth.domain.model.UserId;
import com.menta.auth.domain.model.UserStatus;
import com.menta.auth.domain.repository.UserRepository;
import com.menta.shared.domain.vo.Email;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads the authenticated principal's UserDetails from the UserRepository.
 *
 * The JWT carries the role claim, so in steady state this lookup is rarely
 * needed for role enforcement. It exists for:
 *   - Spring Security internals that require a UserDetailsService bean.
 *   - Code paths that re-validate the user's status (e.g. TOKEN_VERSION bump
 *     after compromise) — failed re-validation surfaces a NOT_FOUND, the
 *     AuthenticationEntryPoint will treat this as 401.
 *
 * Lookup by email (UserRepository.findByEmail) — the principal name in the
 * JwtAuthenticationFilter is a userId UUID, so this service also accepts
 * UUID strings for `loadUserByUsername`.
 */
@Service
public class TokenUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public TokenUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        Optional<User> maybeUser = lookup(username);
        if (maybeUser.isEmpty()) {
            throw new UsernameNotFoundException("User not found: " + username);
        }
        User user = maybeUser.get();
        if (user.getStatus() == UserStatus.LOCKED) {
            // LOCKED users cannot be re-loaded — surface as not-found so the
            // entry point returns 401.
            throw new UsernameNotFoundException("User locked: " + user.getId().getValue());
        }
        List<GrantedAuthority> authorities = List.of(
            new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
        );
        return new org.springframework.security.core.userdetails.User(
            user.getId().getValue().toString(),
            user.getPasswordHash(),
            true, true, true, true,
            authorities
        );
    }

    private Optional<User> lookup(String username) {
        // Try email first — that's the public-facing identifier on UserDetailsService.
        Optional<User> byEmail = safeFindByEmail(username);
        if (byEmail.isPresent()) {
            return byEmail;
        }
        // Fall back to UUID parse — JwtAuthenticationFilter uses userId as principal.
        try {
            return userRepository.findById(UserId.of(username));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private Optional<User> safeFindByEmail(String username) {
        try {
            Email email = Email.of(username);
            return userRepository.findByEmail(email);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Defensive helper for nullable authorities fallback in tests. */
    static List<GrantedAuthority> emptyAuthorities() {
        return Collections.emptyList();
    }
}
