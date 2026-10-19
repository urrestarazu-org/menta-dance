package com.menta.auth.infrastructure.persistence.adapter;

import com.menta.auth.domain.model.UserId;
import com.menta.auth.domain.repository.UserRepository;
import com.menta.shared.auth.UserContactPort;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Implements the cross-module {@link UserContactPort} on top of {@code auth}'s own domain {@link
 * UserRepository} (#33, US-BILLING-005, design C8) — same shape as {@link UserExistenceAdapter},
 * next to which it is deliberately placed.
 *
 * <p>{@code @Component}-scanned only — no {@code @Bean} anywhere. {@code
 * api:app}'s {@code @SpringBootApplication(scanBasePackages = "com.menta")} picks it up; a
 * missing bean fails the context at startup, never a request.</p>
 */
@Component
public class UserContactAdapter implements UserContactPort {

    private final UserRepository userRepository;

    public UserContactAdapter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Optional<String> emailOf(UUID userId) {
        return userRepository.findById(UserId.of(userId)).map(user -> user.getEmail().getValue());
    }
}
