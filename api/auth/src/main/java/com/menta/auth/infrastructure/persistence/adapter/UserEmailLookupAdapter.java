package com.menta.auth.infrastructure.persistence.adapter;

import com.menta.auth.domain.model.UserId;
import com.menta.auth.domain.repository.UserRepository;
import com.menta.shared.auth.UserEmailLookupPort;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Implements the cross-module {@link UserEmailLookupPort} on top of {@code auth}'s own domain
 * {@link UserRepository} (proposal D3/D7, design.md C4).
 *
 * <p>{@code @Component}-scanned only — no {@code @Bean} anywhere. {@code
 * api:app}'s {@code @SpringBootApplication(scanBasePackages = "com.menta")} picks it up; a
 * missing bean fails the context at startup, never a request. Sibling of {@link
 * UserExistenceAdapter}.</p>
 */
@Component
public class UserEmailLookupAdapter implements UserEmailLookupPort {

    private final UserRepository userRepository;

    public UserEmailLookupAdapter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Optional<String> findEmailById(UUID userId) {
        return userRepository.findById(UserId.of(userId))
            .map(user -> user.getEmail().getValue());
    }
}
