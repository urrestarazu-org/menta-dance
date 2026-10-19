package com.menta.auth.infrastructure.persistence.adapter;

import com.menta.auth.domain.model.UserId;
import com.menta.auth.domain.repository.UserRepository;
import com.menta.shared.auth.UserExistencePort;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Implements the cross-module {@link UserExistencePort} on top of {@code auth}'s own domain
 * {@link UserRepository} (US-BILLING-012, design.md A9/A10).
 *
 * <p>{@code @Component}-scanned only — no {@code @Bean} anywhere. {@code
 * api:app}'s {@code @SpringBootApplication(scanBasePackages = "com.menta")} picks it up; a
 * missing bean fails the context at startup, never a request.</p>
 */
@Component
public class UserExistenceAdapter implements UserExistencePort {

    private final UserRepository userRepository;

    public UserExistenceAdapter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean existsById(UUID userId) {
        return userRepository.existsById(UserId.of(userId));
    }
}
